import { useEffect, useRef, useState } from "react";
import { useI18n } from "../../../shared/i18n/I18nProvider";
import { PartialTicketingError, ticketingRepository as repository } from "../services/ticketing.repository";
import { ticketStatuses, type Ticket, type TicketQuery, type Status } from "../services/ticketing.port";
import "../../../shared/theme/ticketing.css";
const labels: Record<Status | "All", string> = { All: "Todos", Open: "Abiertos", "In Progress": "En progreso", Pending: "Pendientes", Resolved: "Resueltos", Closed: "Cerrados", Failed: "Fallidos" };
const statusLabels: Record<Status, string> = { Open: "Abierto", "In Progress": "En progreso", Pending: "Pendiente", Resolved: "Resuelto", Closed: "Cerrado", Failed: "Fallido" };
export function TicketDashboardPage() {
  const { t, locale } = useI18n();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [totals, setTotals] = useState<Ticket[]>([]);
  const [input, setInput] = useState("");
  const [query, setQuery] = useState<TicketQuery>({ number: "", status: "All" });
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<Ticket>();
  const [closing, setClosing] = useState(false);
  const [code, setCode] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [closeError, setCloseError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [observed, setObserved] = useState("");
  const controller = useRef<AbortController>();
  const dialog = useRef<HTMLDialogElement>(null);
  const queryRef = useRef(query);
  const size = 6;
  async function load(next: TicketQuery) {
    controller.current?.abort();
    const current = new AbortController(); controller.current = current;
    const timeout = window.setTimeout(() => current.abort(), 8000);
    setLoading(true); setError("");
    try {
      const inventory = await repository.search({ number: "", status: "All" }, current.signal);
      const items = inventory.filter(ticket => (next.status === "All" || ticket.status === next.status) && ticket.id.toUpperCase().includes(next.number.toUpperCase()));
      if (controller.current !== current) return;
      setTickets(items); setTotals(inventory); setPage(1); setObserved(new Date().toISOString());
    } catch (reason) {
      if (controller.current !== current) return;
      const partial = reason instanceof PartialTicketingError ? reason.tickets : [];
      setTickets(partial.filter(ticket => (next.status === "All" || ticket.status === next.status) && ticket.id.toUpperCase().includes(next.number.toUpperCase())));
      setTotals(partial); setPage(1); setObserved(partial.length ? new Date().toISOString() : "");
      setError(reason instanceof Error && reason.name !== "AbortError" ? reason.message : "La API no respondió en 8 segundos. Puedes volver a intentar.");
    } finally {
      window.clearTimeout(timeout);
      if (controller.current === current) setLoading(false);
    }
  }
  useEffect(() => { void load(queryRef.current); return () => { controller.current?.abort(); controller.current = undefined; }; }, []);
  useEffect(() => { if (selected) dialog.current?.showModal(); }, [selected]);
  function search(next: TicketQuery) { setQuery(next); queryRef.current = next; setNotice(""); void load(next); }
  function open(ticket: Ticket) { setSelected(ticket); setClosing(false); setCloseError(""); setCode(""); setNote(""); }
  function dismiss() { if (saving) return; dialog.current?.close(); setSelected(undefined); setClosing(false); }
  async function close() {
    if (!selected || saving) return;
    setSaving(true); setCloseError("");
    try {
      const updated = await repository.close(selected, code, note);
      setSelected(updated); setClosing(false); setNotice(`${updated.id}: ${t("Cierre confirmado por el proveedor.")}`);
      // Show the closed incident even when the prior card filtered open incidents.
      const next: TicketQuery = { number: updated.id, status: "All" };
      setInput(updated.id); setQuery(next); queryRef.current = next;
      await load(next);
    } catch (reason) {
      setCloseError(reason instanceof Error && reason.name !== "AbortError" ? reason.message : "No se confirmó el cierre. Vuelve a buscar el ticket antes de reintentar.");
    } finally { setSaving(false); }
  }
  const pages = Math.max(1, Math.ceil(tickets.length / size));
  const current = Math.min(page, pages);
  const rows = tickets.slice((current - 1) * size, current * size);
  return <section className="page ticket-page">
    <div className="page-heading"><div><span className="eyebrow">{t("Plugin Ticketing")}</span><h1>{t("Dashboard de tickets")}</h1><p>{t("Consulta y cierre por API · ServiceNow y GLPI.")}</p></div><span className="mock-chip">{t("ServiceNow · GLPI")}</span></div>
    <div className="ticket-kpis" aria-label={t("Filtrar por estado")}>{(["All", ...ticketStatuses] as const).map(status => <button type="button" key={status} className={query.status === status ? "selected" : ""} aria-pressed={query.status === status} disabled={loading || saving} onClick={() => search({ ...query, status })}><small>{t(labels[status])}</small><strong>{error && !totals.length ? "—" : status === "All" ? totals.length : totals.filter(ticket => ticket.status === status).length}</strong></button>)}</div>
    <form className="ticket-filters" onSubmit={event => { event.preventDefault(); search({ ...query, number: input.trim() }); }}><label><span>{t("Buscar ticket")}</span><input aria-label={t("Buscar ticket")} value={input} onChange={event => setInput(event.target.value)} placeholder="INC0019284 / GLPI-123" maxLength={40} pattern="[a-zA-Z0-9-]*" title={t("Escribe el número completo o una parte del ticket.")}/></label><button type="submit" disabled={loading || saving}>{t("Buscar")}</button><button type="button" disabled={loading || saving} onClick={() => void load(query)}>{t("↻ Refresh")}</button></form>
    <p className="ticket-query-note">{t("Filtro activo")}: <strong>{t(labels[query.status])}</strong> · {query.number || t("Todos los tickets")}{observed && <> · {t("Consulta")}: {new Date(observed).toLocaleString(locale)}</>}</p>
    {error && <div className="ticket-alert" role="alert">{t(error)}</div>}{notice && <div className="ticket-notice" role="status">{notice}</div>}
    <div className="table-wrap" aria-busy={loading}><table><thead><tr>{["Ticket", "Proveedor", "Estado", "Prioridad", "Cliente", "Recurso", "Resumen", "Torre", "Actualizado", "Detalle"].map(label => <th key={label}>{t(label)}</th>)}</tr></thead><tbody>{rows.map(ticket => <tr key={`${ticket.provider}:${ticket.sysId}`} onDoubleClick={() => open(ticket)} onContextMenu={event => { event.preventDefault(); open(ticket); }}><td><button className="ticket-link" onClick={() => open(ticket)}>{ticket.id}</button></td><td>{ticket.provider === "GLPI" ? "GLPI" : "ServiceNow"}</td><td><span className={`badge ticket-status-${ticket.status.replaceAll(" ", "-")}`}>{t(statusLabels[ticket.status])}</span></td><td>{t(ticket.priority)}</td><td>{ticket.customer}</td><td>{ticket.resource}</td><td title={ticket.summary}>{ticket.summary}</td><td>{ticket.tower}</td><td>{new Date(ticket.updated).toLocaleString(locale)}</td><td><button className="row-action" onClick={() => open(ticket)} aria-label={`${t("Ver detalle de")} ${ticket.id}`}>•••</button></td></tr>)}</tbody></table>{!rows.length && <div className="no-results">{t(loading ? "Consultando…" : error ? "Sin datos disponibles" : "Sin resultados")}</div>}</div>
    <div className="pagination"><span>{tickets.length} {t("resultados")} · {t("Página")} {current} {t("de")} {pages}</span><div><button disabled={current === 1} onClick={() => setPage(current - 1)}>{t("Anterior")}</button><button disabled={current === pages} onClick={() => setPage(current + 1)}>{t("Siguiente")}</button></div></div>
    <dialog ref={dialog} className="ticket-dialog" aria-labelledby="ticket-title" onCancel={event => { event.preventDefault(); dismiss(); }} onClose={() => { setSelected(undefined); setClosing(false); }}>{selected && <><header><div><span className="eyebrow">{t(closing ? "Cerrar ticket" : "Detalle")}</span><h2 id="ticket-title">{selected.id}</h2></div><button onClick={dismiss} disabled={saving} aria-label={t("Cerrar panel")}>×</button></header><span className="badge">{t(statusLabels[selected.status])}</span><h3>{selected.summary}</h3><p>{selected.description}</p>{closing ? <><p>{t("El cierre se guardará en el proveedor del ticket.")}</p><label>{t("Código de cierre")}<select value={code} disabled={saving} onChange={event => setCode(event.target.value)}><option value="">{t("Selecciona")}</option>{(selected.provider === "GLPI" ? ["0"] : ["Solved (Permanently)", "Solved (Work Around)", "Not Solved"]).map(value => <option key={value} value={value}>{value === "0" ? t("Solución general (GLPI)") : t(value)}</option>)}</select></label><label>{t("Nota de cierre")}<textarea rows={4} maxLength={4000} value={note} disabled={saving} onChange={event => setNote(event.target.value)}/></label><small>{t("Mínimo 10 caracteres")}</small>{closeError && <p className="ticket-alert" role="alert">{t(closeError)}</p>}<footer><button disabled={saving} onClick={() => setClosing(false)}>{t("Cancelar")}</button><button disabled={saving || !code || note.trim().length < 10} onClick={() => void close()}>{t(saving ? "Guardando…" : "Confirmar cierre")}</button></footer></> : <><dl>{[["Proveedor", selected.provider === "GLPI" ? "GLPI" : "ServiceNow"], [selected.provider === "GLPI" ? "Entidad GLPI (ID)" : "Cliente", selected.customer], ["Recurso", selected.resource], ["Prioridad", t(selected.priority)], [selected.provider === "GLPI" ? "Categoría GLPI (ID)" : "Torre", selected.tower], ["Código de cierre", t(selected.closeCode) || "—"], ["Nota de cierre", selected.closeNotes || "—"]].map(([label, value]) => <div key={label}><dt>{t(label)}</dt><dd>{value}</dd></div>)}</dl><footer><button onClick={dismiss}>{t("Cerrar panel")}</button><button disabled={selected.status === "Closed" || saving} onClick={() => setClosing(true)}>{t("Cerrar ticket")}</button></footer></>}</>}</dialog>
  </section>;
}
