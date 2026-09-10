"""OEM reporting port: canonical views or an explicitly configured internal API."""
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

DOMAINS = ("events", "ticketing", "gnm", "cacf", "delivery", "data-collection")
MODES = ("postgresql", "internal-api")
CONFIG_PATH = Path(os.getenv("OEM_DASHBOARD_CONFIG", "/tmp/oem-dashboards/config.json"))


class DashboardError(Exception):
    pass


def validate_config(value):
    if not isinstance(value, dict) or set(value) != {"version", "default", "sources"}:
        raise DashboardError("Configuración inválida: se requieren version, default y sources.")
    if type(value["version"]) is not int or value["version"] != 1 or value["default"] not in MODES:
        raise DashboardError("Versión o fuente no soportada.")
    if not isinstance(value["sources"], dict) or any(
        key not in DOMAINS or mode not in MODES for key, mode in value["sources"].items()
    ):
        raise DashboardError("Fuente o dashboard no soportado.")
    return value


def read_config(path=CONFIG_PATH):
    try:
        return validate_config(json.loads(path.read_text()))
    except FileNotFoundError:
        return {"version": 1, "default": "postgresql", "sources": {}}
    except (ValueError, OSError) as exc:
        raise DashboardError("No se pudo leer la configuración.") from exc


def source_for(config, domain):
    if domain not in DOMAINS:
        raise DashboardError("Dashboard desconocido.")
    return config["sources"].get(domain, config["default"])


def parse_query(params):
    if set(params) - {"tenant", "status", "q", "page", "limit"}:
        raise DashboardError("Filtro no soportado.")
    if any(len(v) != 1 for v in params.values()):
        raise DashboardError("No se permiten filtros repetidos.")
    result = {key: params.get(key, [""])[0].strip() for key in ("tenant", "status", "q")}
    if any(len(v) > 128 for v in result.values()):
        raise DashboardError("Filtro demasiado largo.")
    try:
        result.update(page=int(params.get("page", ["1"])[0]), limit=int(params.get("limit", ["25"])[0]))
    except ValueError as exc:
        raise DashboardError("Paginación inválida.") from exc
    if not 1 <= result["page"] <= 100000 or not 1 <= result["limit"] <= 100:
        raise DashboardError("Paginación fuera de rango.")
    return result


def timestamp(value):
    if not isinstance(value, str):
        return False
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).utcoffset() is not None
    except ValueError:
        return False


def validate_snapshot(data, domain, query):
    """Validate and reconstruct public DTO; never forward arbitrary upstream properties."""
    def integer(v):
        return type(v) is int and v >= 0

    def string(v):
        return isinstance(v, str) and 0 < len(v) <= 512

    def require(condition):
        if not condition:
            raise ValueError("Invalid contract")

    try:
        require(data["schemaVersion"] == "1.0" and data["domain"] == domain)
        require(timestamp(data["observedAt"]))
        require(data["lastUpdatedAt"] is None or timestamp(data["lastUpdatedAt"]))
        require(integer(data["total"]) and integer(data["page"]) and integer(data["limit"]) and data["page"] == query["page"] and data["limit"] == query["limit"])
        require(isinstance(data["counts"], dict) and len(data["counts"]) <= 256)
        require(all(string(k) and integer(v) for k, v in data["counts"].items()))
        require(sum(data["counts"].values()) == data["total"])
        require(isinstance(data["rows"], list))
        require(len(data["rows"]) == min(query["limit"], max(0, data["total"] - (query["page"]-1)*query["limit"])))
        rows = []
        for row in data["rows"]:
            require(all(string(row[k]) for k in ("id", "tenant", "status", "eventId")))
            require(all(row[k] is None or string(row[k]) for k in ("reference", "outcome")))
            require(timestamp(row["updatedAt"]))
            require(row["severity"] is None or (integer(row["severity"]) and row["severity"] <= 5))
            require(row["tally"] is None or integer(row["tally"]))
            require(not query["tenant"] or row["tenant"] == query["tenant"])
            require(not query["status"] or row["status"] == query["status"])
            rows.append({key: row[key] for key in ("id", "tenant", "status", "eventId", "reference", "updatedAt", "severity", "tally", "outcome")})
        require(len({r["id"] for r in rows}) == len(rows))
        return {**{k: data[k] for k in ("schemaVersion", "domain", "observedAt", "lastUpdatedAt", "total", "page", "limit", "counts")}, "rows": rows}
    except (AssertionError, KeyError, TypeError, ValueError) as exc:
        raise DashboardError("La API interna no cumple el contrato de dashboards v1.") from exc


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise DashboardError("La API interna no puede redirigir consultas.")


class InternalApiSource:
    def load(self, domain, query):
        base = os.getenv("OEM_INTERNAL_API_URL", "").rstrip("/")
        parsed = urlsplit(base)
        if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise DashboardError("Configura OEM_INTERNAL_API_URL con la URL base de la API OEM.")
        headers = {"Accept": "application/json"}
        token = os.getenv("OEM_INTERNAL_API_TOKEN")
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(f"{base}/api/dashboards/{domain}?{urlencode(query)}", headers=headers)
        try:
            with build_opener(NoRedirect()).open(request, timeout=5) as response:
                if response.headers.get_content_type() != "application/json":
                    raise DashboardError("La API interna debe entregar application/json.")
                body = response.read(2_000_001)
                if len(body) > 2_000_000:
                    raise DashboardError("La respuesta excede el límite permitido.")
                data = json.loads(body)
            if domain == "data-collection":
                from collection import validate_collection
                return validate_collection(data, query)
            if domain == "delivery":
                from delivery import validate_delivery
                return validate_delivery(data, query)
            return validate_snapshot(data, domain, query)
        except DashboardError:
            raise
        except Exception as exc:
            raise DashboardError("API interna no disponible o respuesta inválida.") from exc


class PostgresSource:
    def load(self, domain, query):
        if domain not in DOMAINS:
            raise DashboardError("Dashboard desconocido.")
        if domain == "data-collection":
            from collection import load_collection
            return load_collection(query)
        if domain == "delivery":
            from delivery import load_delivery
            return load_delivery(query)
        dsn = os.getenv("OEM_POSTGRES_DSN")
        if not dsn:
            raise DashboardError("Configura OEM_POSTGRES_DSN con un usuario de lectura.")
        try:
            import psycopg
            from psycopg.rows import dict_row
            conditions, args = [], []
            for field in ("tenant", "status"):
                if query[field]:
                    conditions.append(f"{field} = %s")
                    args.append(query[field])
            if query["q"]:
                conditions.append("(strpos(lower(id), lower(%s)) > 0 OR strpos(lower(event_id), lower(%s)) > 0 OR strpos(lower(coalesce(reference, '')), lower(%s)) > 0)")
                args.extend([query["q"]] * 3)
            where = " WHERE " + " AND ".join(conditions) if conditions else ""
            relation = "dashboard_read." + domain  # validated closed registry; no SQL from callers
            with psycopg.connect(dsn, connect_timeout=3, row_factory=dict_row) as conn:
                with conn.cursor() as cur:
                    cur.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
                    cur.execute("SET LOCAL statement_timeout = '4000ms'")
                    cur.execute(f"SELECT status, count(*) AS count, max(updated_at) AS updated FROM {relation}{where} GROUP BY status", args)
                    groups = cur.fetchall()
                    cur.execute(f"SELECT * FROM {relation}{where} ORDER BY updated_at DESC, id LIMIT %s OFFSET %s", args + [query["limit"], (query["page"]-1)*query["limit"]])
                    rows = cur.fetchall()
            latest = max((g["updated"] for g in groups), default=None)
            return {
                "schemaVersion": "1.0", "domain": domain,
                "observedAt": datetime.now(timezone.utc).isoformat(),
                "lastUpdatedAt": latest.isoformat() if latest else None,
                "total": sum(g["count"] for g in groups), "counts": {g["status"]: g["count"] for g in groups},
                "page": query["page"], "limit": query["limit"],
                "rows": [{"id": r["id"], "tenant": r["tenant"], "status": r["status"], "eventId": r["event_id"],
                          "reference": r["reference"], "updatedAt": r["updated_at"].isoformat(), "severity": r["severity"],
                          "tally": r["tally"], "outcome": r["outcome"]} for r in rows],
            }
        except Exception as exc:
            raise DashboardError("PostgreSQL no disponible. Verifica credenciales de lectura y migración 018.") from exc


def load_dashboard(config, domain, query):
    mode = source_for(config, domain)
    adapter = PostgresSource() if mode == "postgresql" else InternalApiSource()
    return {**adapter.load(domain, query), "source": mode}
