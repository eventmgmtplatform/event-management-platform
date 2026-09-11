import {NavigationFrame} from "./NavigationFrame";
import React, {useEffect, useState} from "react";
import {createRoot} from "react-dom/client";
import {BrowserRouter, Navigate, NavLink, Route, Routes, useParams} from "react-router-dom";
import {domains, getSnapshot, type Domain, type Query, type Snapshot} from "./data";
import {I18nProvider, useI18n} from "./i18n";
import {DataCollectionPage} from "./DataCollectionPage";
import {ApiManagementPage} from "./ApiManagementPage";
import {DeliveryPage} from "./DeliveryPage";
import "./tokens.css";
import "./styles.css";
import "./appearance.css";
import {ThemeProvider} from "./ThemeProvider";

const initial: Query = {tenant: "", status: "", q: "", page: 1, limit: 25};


function Dashboard({domain}: {domain: Domain}) {
  const {t,locale} = useI18n();
  const formatDate = (value: string | null) => value ? new Date(value).toLocaleString(locale) : t("Sin registros");
  const info = domains[domain];
  const [draft, setDraft] = useState(initial);
  const [query, setQuery] = useState(initial);
  const [revision, refresh] = useState(0);
  const [data, setData] = useState<Snapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    let timedOut = false;
    const timeout = setTimeout(() => {timedOut = true; controller.abort();}, 12000);
    setLoading(true); setError(""); setData(null);
    getSnapshot(domain, query, controller.signal).then(result => {if (active) setData(result);}).catch(err => {
      if (active) setError(timedOut ? "La consulta superó el tiempo de espera. Intenta actualizar." : err instanceof Error ? err.message : "No se pudo consultar el dashboard.");
    }).finally(() => {clearTimeout(timeout); if (active) setLoading(false);});
    return () => {active = false; clearTimeout(timeout); controller.abort();};
  }, [domain, query, revision]);
  const filtered = Boolean(query.tenant || query.status || query.q);
  const counts = data ? Object.entries(data.counts).sort((a,b) => b[1]-a[1]) : [];
  return <main id="content" aria-busy={loading}>
    <div className="heading"><div><span className="eyebrow">{t("Operación")} / {t(info.short)}</span><h1>{info.title}</h1><p>{t(info.description)}</p></div><button onClick={() => refresh(v=>v+1)} disabled={loading}>{loading ? t("Consultando…") : t("↻ Actualizar")}</button></div>
    <div className="source-line"><span>{t("Fuente")}: <strong>{data ? data.source === "postgresql" ? "PostgreSQL" : t("API interna") : t("Sin verificar")}</strong></span><span>{t("Consulta")}: {data ? formatDate(data.observedAt) : "—"}</span><span>{t("Última actualización de registros")}: {data ? formatDate(data.lastUpdatedAt) : "—"}</span></div>
    <form className="filters" onSubmit={e => {e.preventDefault(); setQuery({...draft, page: 1});}}>
      <label>{t("Cliente")}<input maxLength={128} placeholder={domain === "cacf" ? t("Customer code exacto") : t("Tenant exacto")} value={draft.tenant} onChange={e=>setDraft({...draft,tenant:e.target.value})}/></label>
      <label>{t("Estado")}<input maxLength={128} placeholder={t("Estado exacto")} value={draft.status} onChange={e=>setDraft({...draft,status:e.target.value})}/></label>
      <label className="search">{t("Buscar identificador")}<input maxLength={128} placeholder={t("Evento o referencia")} value={draft.q} onChange={e=>setDraft({...draft,q:e.target.value})}/></label>
      <button type="submit">{t("Aplicar")}</button><button className="secondary" type="button" onClick={()=>{setDraft(initial);setQuery(initial);}}>{t("Limpiar")}</button>
    </form>
    {error && <div className="error" role="alert"><strong>{t("No pudimos cargar los datos")}</strong><p>{t(error)}</p><button onClick={()=>refresh(v=>v+1)}>{t("Reintentar")}</button></div>}
    {loading && <div role="status" className="loading">{t("Consultando")} {t(info.short)}…</div>}
    {!loading && data && <>
      <div className="summary"><article className="total"><span>{t(info.unit)}</span><strong>{data.total.toLocaleString(locale)}</strong><small>{filtered ? t("Resultado de los filtros aplicados") : t("Estado actual · todos los registros")}</small></article><article className="distribution"><h2>{t("Distribución por estado")}</h2>{counts.length ? counts.map(([status,count])=><div className="state-row" key={status}><button title={`${t("Filtrar")} ${status}`} onClick={()=>{setDraft({...draft,status});setQuery({...query,status,page:1});}}>{status}</button><div className="bar"><span style={{width:`${data.total ? count/data.total*100 : 0}%`}}/></div><strong>{count.toLocaleString(locale)}</strong></div>) : <p>{t("Sin estados registrados para esta consulta.")}</p>}</article></div>
      <section className="records"><div className="section-title"><h2>{t("Registros")}</h2><span>{t(info.unit)} · {t("página")} {query.page}</span></div>{data.rows.length ? <div className="table-scroll"><table><caption className="sr-only">{t("Registros")} · {info.title}</caption><thead><tr><th>{t("Evento / ID")}</th><th>{t("Cliente")}</th><th>{t("Estado")}</th><th>{t("Referencia")}</th>{domain === "events" && <><th>{t("Severidad")}</th><th>Tally</th></>}{domain === "cacf" && <th>{t("Resultado")}</th>}<th>{t("Actualizado")}</th></tr></thead><tbody>{data.rows.map(row=><tr key={row.id}><td><strong>{row.eventId}</strong><small>{row.id}</small></td><td>{row.tenant}{row.tenant === "DEMO-DASHBOARDS" && <small>{t("Datos de demostración")}</small>}</td><td><span className="status">{row.status}</span></td><td>{row.reference ?? "—"}</td>{domain === "events" && <><td>{row.severity ?? "—"}</td><td>{row.tally ?? "—"}</td></>}{domain === "cacf" && <td>{row.outcome ?? t("Pendiente")}</td>}<td>{formatDate(row.updatedAt)}</td></tr>)}</tbody></table></div> : <div className="empty"><h3>{filtered ? t("No hay coincidencias") : t("Aún no hay registros")}</h3><p>{filtered ? t("Ajusta los filtros para consultar otros registros.") : t("La fuente respondió correctamente. Los datos aparecerán cuando estén disponibles.")}</p></div>}
      <div className="pagination"><button className="secondary" disabled={query.page===1} onClick={()=>setQuery({...query,page:query.page-1})}>{t("Anterior")}</button><span>{data.total} {t("registros")} · {query.limit} {t("por página")}</span><button className="secondary" disabled={query.page*query.limit>=data.total} onClick={()=>setQuery({...query,page:query.page+1})}>{t("Siguiente")}</button></div></section>
    </>}
    <aside className="checklist"><h2>{t("Revisión operativa")}</h2><ul>{info.checklist.map(item=><li key={t(item)}>{t(item)}</li>)}</ul><p>{t("Guía de revisión; no representa verificaciones automáticas.")}</p></aside>
  </main>;
}

function DomainRoute() {
  const {t} = useI18n();
  const {domain} = useParams();
  if (domain === "data-collection") return <DataCollectionPage/>;
  if (domain === "api-management") return <ApiManagementPage/>;
  if (domain === "delivery") return <DeliveryPage/>;
  return domain && Object.hasOwn(domains, domain) ? <Dashboard key={domain} domain={domain as Domain}/> : <main><h1>{t("Dashboard no encontrado")}</h1><NavLink to="/dashboards/events">{t("Volver a eventos")}</NavLink></main>;
}
function App() {
  const {t} = useI18n();
  return <BrowserRouter><a className="skip" href="#content">{t("Ir al contenido")}</a><NavigationFrame><Routes><Route path="/" element={<Navigate to="/dashboards/events" replace/>}/><Route path="/dashboards/:domain" element={<DomainRoute/>}/><Route path="*" element={<Navigate to="/dashboards/events" replace/>}/></Routes></NavigationFrame></BrowserRouter>;
}
createRoot(document.getElementById("root")!).render(<React.StrictMode><I18nProvider><ThemeProvider><App/></ThemeProvider></I18nProvider></React.StrictMode>);
