import { useEffect, useState } from "react";
import { useI18n } from "../../shared/i18n/I18nProvider";
type Source = { id: "runtime" | "tickets"; health: "healthy" | "error"; endpoint: string; target: string };
export function SourceConnections() {
  const { t } = useI18n();
  const [sources, setSources] = useState<Source[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    setLoading(true);
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    fetch("/api/administration/sources", { signal: controller.signal, cache: "no-store" }).then(async response => {
      if (!response.ok) throw new Error();
      const data = await response.json();
      if (!Array.isArray(data.sources) || data.sources.length !== 2 || new Set(data.sources.map((s: Source) => s?.id)).size !== 2 || !data.sources.every((s: Source) => s && ["runtime", "tickets"].includes(s.id) && ["healthy", "error"].includes(s.health) && typeof s.endpoint === "string" && typeof s.target === "string")) throw new Error();
      if (active) { setSources(data.sources); setFailed(false); }
    }).catch(() => { if (active) { setSources([]); setFailed(true); } }).finally(() => { window.clearTimeout(timeout); if (active) setLoading(false); });
    return () => { active = false; window.clearTimeout(timeout); controller.abort(); };
  }, [revision]);
  useEffect(() => { const interval = window.setInterval(() => setRevision(v => v + 1), 30000); return () => window.clearInterval(interval); }, []);
  return <section className="admin-panel source-connections"><div className="admin-panel-heading"><div><span className="eyebrow">{t("Conexión y control")}</span><h2>{t("¿De dónde vienen los datos?")}</h2></div><button className="admin-button" disabled={loading} onClick={() => setRevision(v => v + 1)}>{t("Probar conexiones")}</button></div><p>{t("Cada módulo utiliza su propia fuente. Estas dos conexiones están configuradas y se verifican en este entorno.")}</p>{failed && <p role="alert">{t("No se pudieron verificar las conexiones.")}</p>}<div className="source-grid">{(["runtime", "tickets"] as const).map(id => {
    const source = sources.find(s => s.id === id);
    return <article key={id}><div className="source-title"><h3>{t(id === "runtime" ? "Fuente 1 · Plataforma local" : "Fuente 2 · ServiceNow de práctica")}</h3><span className={`admin-status ${source?.health ?? "unknown"}`}>{t(source ? source.health === "healthy" ? "Conectada" : "Error de conexión" : "Sin verificar")}</span></div><p>{t(id === "runtime" ? "Alimenta Administración / Inventario de servicios." : "Alimenta Plugins / Tickets.")}</p><dl className="admin-config"><div><dt>{t("Destino")}</dt><dd>{source?.target ?? "—"}</dd></div><div><dt>{t("Consulta por API")}</dt><dd><code>{source?.endpoint ?? "—"}</code></dd></div><div><dt>{t("Permite")}</dt><dd>{t(id === "runtime" ? "Leer estado y ejecutar acciones de servicio" : "Buscar tickets y guardar su cierre")}</dd></div><div><dt>{t("Dónde se guardan los cambios")}</dt><dd>{t(id === "runtime" ? "Acciones validadas por el administrador local" : "Base persistente exclusiva del mock de la consola")}</dd></div></dl><p className="admin-note">{t(id === "runtime" ? "Ejemplo: ves Enrichment Engine detenido porque Docker reporta ese estado." : "Ejemplo: buscas INC0019284, lo cierras y otra búsqueda devuelve Cerrado.")}</p></article>;
  })}</div><p className="admin-note">{t("La fuente de Tickets es una copia aislada. El mock original usado por las pruebas no recibe estas consultas ni cierres.")}</p></section>;
}
