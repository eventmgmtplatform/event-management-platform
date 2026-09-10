import { Link } from "react-router-dom";
import "../ess/ess.css";
import { ServiceActions } from "./ServiceActions";
import { SourceConnections } from "./SourceConnections";
import { useI18n } from "../../shared/i18n/I18nProvider";
import { useEffect, useRef, useState } from "react";
import { demoSnapshot, readPlatform, referenceServices, states, type Snapshot, type ServiceStatus } from "./platform";
import "./administration.css";

export function AdministrationPage() {
  const { t, locale } = useI18n();
  const [demo, setDemo] = useState(false);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [category, setCategory] = useState("all");
  const [selected, setSelected] = useState<string | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setError(""); setLoading(true);
    if (demo) { setSnapshot(demoSnapshot()); setLoading(false); return; }
    const timeout = window.setTimeout(() => controller.abort(), 8000);
    readPlatform(controller.signal).then(data => { if (active) setSnapshot(data); }).catch(reason => {
      if (active) { setSnapshot(null); setError(controller.signal.aborted ? "La API no respondió en 8 segundos. Puedes volver a intentar." : reason instanceof Error ? reason.message : "No fue posible consultar la plataforma."); }
    }).finally(() => { window.clearTimeout(timeout); if (active) setLoading(false); });
    return () => { active = false; controller.abort(); window.clearTimeout(timeout); };
  }, [demo, revision]);
  useEffect(() => {
    if (demo) return;
    const interval = window.setInterval(() => setRevision(v => v + 1), 30000);
    return () => window.clearInterval(interval);
  }, [demo]);
  useEffect(() => { if (selected) dialog.current?.showModal(); }, [selected]);
  const services = snapshot?.services ?? referenceServices;
  const filtered = services.filter(s => (status === "all" || s.status === status) && (category === "all" || s.category === category) && `${s.name} ${s.id}`.toLocaleLowerCase().includes(query.toLocaleLowerCase()));
  const detail = services.find(s => s.id === selected);
  const count = (s: ServiceStatus) => services.filter(service => service.status === s).length;
  const categories = [...new Set(services.map(s => s.category))];
  const overall = !snapshot ? "Sin telemetría" : !services.length ? "Sin servicios" : count("degraded") || count("stopped") || count("error") ? "Requiere atención" : count("unknown") ? "Verificación pendiente" : "Servicios saludables";
  const reasons: Record<string, string> = { healthy: "Healthcheck correcto", unhealthy: "Healthcheck fallido", stopped: "Contenedor detenido", exit_error: "Terminó con error", completed: "Tarea finalizada correctamente", no_healthcheck: "Sin healthcheck configurado", starting: "Iniciando verificación de salud", restarting: "Contenedor reiniciando", paused: "Contenedor pausado", dead: "Contenedor no recuperable", removing: "Contenedor en eliminación", missing: "El contenedor no existe", docker_unavailable: "Docker no disponible", unverified: "Sin verificar" };
  function closeDetail() { dialog.current?.close(); setSelected(null); }
  return <section className="page administration">
    <div className="page-heading"><div><span className="eyebrow">{t("Control de plataforma")}</span><h1>{t("Administración")}</h1><p>{t("Visibilidad de los servicios y sus conexiones, en un solo lugar.")}</p></div><button className="admin-button primary" disabled={loading} onClick={() => setRevision(v => v + 1)}>{t(loading ? "Consultando…" : "↻ Actualizar")}</button></div>
    <div className="ess-access"><div><h2>{t("Estado de eventos · ESS")}</h2><p>{t("Consulta estados e historial por tenant y el resumen global de cuarentena.")}</p></div><Link className="admin-button" to="/administration/ess">{t("Abrir administración ESS")} ↗</Link></div>
    <div className="admin-source"><div><span className={`admin-indicator ${snapshot && !demo ? "healthy" : "unknown"}`}/><strong>{t(demo ? "Demostración" : "API interna")}</strong><span>{t(demo ? "Datos simulados · sin conexión al runtime" : "Fuente de salud e inventario")}</span></div><label className="admin-toggle"><input type="checkbox" checked={demo} onChange={e => { setDemo(e.target.checked); setSnapshot(null); setSelected(null); dialog.current?.close(); }}/>{t("Ver demostración")}</label></div>
    <div aria-live="polite">{error && <div className="admin-alert" role="alert"><strong>{t("No se pudo verificar el runtime")}</strong><p>{t(error)} {t("El inventario de referencia no indica disponibilidad real.")}</p></div>}{demo && <div className="admin-alert"><strong>{t("Vista de demostración")}</strong><p>{t("Los estados son ficticios y sirven para revisar el diseño de administración.")}</p></div>}</div>
    <div className="admin-metrics"><article><span>{t("Estado general")}</span><strong className="admin-overall">{t(overall)}</strong><small>{snapshot ? `${t("Consulta")}: ${new Date(snapshot.observedAt).toLocaleString(locale)}` : t("Sin una consulta válida")}</small></article><article><span>{t("Servicios")}</span><strong>{services.length}</strong><small>{t(snapshot ? "En la fuente seleccionada" : "En el inventario de referencia")}</small></article><article><span>{t("Saludables")}</span><strong className="healthy">{snapshot ? count("healthy") : "—"}</strong><small>{t("Verificación de salud")}</small></article><article><span>{t("Requieren atención")}</span><strong className="degraded">{snapshot ? count("degraded") + count("stopped") + count("error") : "—"}</strong><small>{count("unknown")} {t("sin verificar")}</small></article></div>
    <p className="admin-note">{t("Actualización automática cada 30 segundos")}</p><section className="admin-panel"><div className="admin-panel-heading"><div><h2>{t("Inventario de servicios")}</h2><p>{t("Infraestructura, procesamiento e interfaces de la plataforma.")}</p></div><span className="admin-count">{filtered.length} {t("de")} {services.length}</span></div>
      <div className="admin-filters"><label className="admin-search">{t("Buscar servicio")}<input type="search" placeholder={t("Nombre o identificador…")} value={query} onChange={e => setQuery(e.target.value)}/></label><label>{t("Categoría")}<select value={category} onChange={e => setCategory(e.target.value)}><option value="all">{t("Todas las categorías")}</option>{categories.map(c => <option key={c} value={c}>{t(c)}</option>)}</select></label><label>{t("Estado")}<select value={status} onChange={e => setStatus(e.target.value)}><option value="all">{t("Todos los estados")}</option>{Object.entries(states).map(([key, label]) => <option key={key} value={key}>{t(label)}</option>)}</select></label></div>
      <div className="admin-table-wrap" aria-busy={loading}><table className="admin-table"><thead><tr><th>{t("Servicio")}</th><th>{t("Categoría")}</th><th>{t("Estado")}</th><th>{t("Versión")}</th><th>{t("Puerto")}</th><th><span className="admin-sr">{t("Detalle")}</span></th></tr></thead><tbody>{filtered.map(s => <tr key={s.id}><td><strong>{s.name}</strong><small>{s.id}</small></td><td>{t(s.category)}</td><td><span className={`admin-status ${s.status}`}><span className={`admin-indicator ${s.status}`}/>{t(states[s.status])}</span><small>{s.reason && t(reasons[s.reason] ?? "Sin verificar")}</small></td><td><code>{s.version ?? "—"}</code></td><td>{s.port ?? "—"}</td><td><button className="admin-link" onClick={() => setSelected(s.id)} aria-label={`${t("Ver detalle de")} ${s.name}`}>{t("Detalle ↗")}</button></td></tr>)}</tbody></table>{!filtered.length && <div className="admin-empty"><h3>{t("No hay servicios para mostrar")}</h3><p>{t(services.length ? "Prueba con otro nombre o ajusta los filtros." : "La API devolvió un inventario vacío.")}</p>{services.length > 0 && <button className="admin-button" onClick={() => { setQuery(""); setStatus("all"); setCategory("all"); }}>{t("Limpiar filtros")}</button>}</div>}</div>
    </section>
    <div className="admin-bottom"><section className="admin-panel"><h2>{t("Herramientas especializadas")}</h2><p>{t("Consolas instaladas y estado de su contenedor. La salud de cada aplicación se consulta al abrirla.")}</p>{[
      {id:"oem-dashboards",name:"ITSM Dashboard",description:"Eventos, Ticketing, GNM y CACF",port:8091},
      {id:"kafka-ui",name:"Kafka UI",description:"Tópicos, consumidores y mensajes",port:8085},
      {id:"opensearch-dashboards",name:"OpenSearch Dashboards",description:"Observabilidad del producto · APIs y configuración",port:5601},
      {id:"open-webui",name:"Open WebUI",description:"Interfaz de asistentes y modelos",port:3000},
      {id:"itsm-ticketing-dashboard",name:"ITSM Dashboard legacy",description:"Consola anterior de tickets",port:8088}
    ].map(tool=>{const service=!demo&&snapshot?.services.find(x=>x.id===tool.id);const state=service?service.status:"unknown";return <a className="admin-tool" key={tool.id} href={`http://${window.location.hostname}:${tool.port}${tool.id === "opensearch-dashboards" ? "/app/dashboards#/view/product-observability" : ""}`} target="_blank" rel="noreferrer"><div><strong>{tool.name}</strong><small>{t(tool.description)} · {window.location.hostname}:{tool.port}</small></div><span className={`admin-status ${state}`}>{t(states[state])}</span><span aria-hidden="true">↗</span></a>;})}<h3>{t("Vistas de ITSM · puerto 8091")}</h3><div className="admin-dashboard-links">{[["events","Eventos"],["ticketing","Ticketing"],["gnm","GNM"],["cacf","CACF"],["delivery","Delivery"]].map(([path,label])=><a className="admin-button" key={path} href={`http://${window.location.hostname}:8091/dashboards/${path}`} target="_blank" rel="noreferrer">{t(label)} ↗</a>)}</div></section></div>
    <SourceConnections/>
    <dialog ref={dialog} className="admin-dialog" onCancel={closeDetail} onClose={() => setSelected(null)} aria-labelledby="service-title">{detail && <><div className="admin-panel-heading"><span className="eyebrow">{t("Detalle de servicio")}</span><button className="admin-button" onClick={closeDetail} aria-label={t("Cerrar detalle")}>{t("Cerrar ×")}</button></div><h2 id="service-title">{detail.name}</h2><p>{t(referenceServices.find(s => s.id === detail.id)?.description ?? detail.name)}</p><span className={`admin-status ${detail.status}`}>{t(states[detail.status])}</span><dl className="admin-config"><div><dt>{t("Diagnóstico")}</dt><dd>{t(reasons[detail.reason ?? "unverified"] ?? "Sin verificar")}</dd></div><div><dt>{t("Contenedor")}</dt><dd>{detail.container ?? "—"}</dd></div><div><dt>{t("Reinicios")}</dt><dd>{detail.restartCount ?? "—"}</dd></div><div><dt>{t("Identificador")}</dt><dd>{detail.id}</dd></div><div><dt>{t("Categoría")}</dt><dd>{t(detail.category)}</dd></div><div><dt>{t("Versión")}</dt><dd>{detail.version ?? t("Sin verificar")}</dd></div><div><dt>{t("Puerto")}</dt><dd>{detail.port ?? t("Sin verificar")}</dd></div><div><dt>{t("Fuente")}</dt><dd>{t(demo ? "Datos simulados" : snapshot ? "API interna" : "Inventario de referencia")}</dd></div></dl><ServiceActions key={detail.id} service={demo ? {...detail, actions: []} : detail} onComplete={() => setRevision(v => v + 1)}/></>}</dialog>
  </section>;
}
