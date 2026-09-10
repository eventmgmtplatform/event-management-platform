import {useEffect,useMemo,useRef,useState} from "react";
import {useSearchParams} from "react-router-dom";
import {getDelivery,targetNames,targets,type Bucket,type DeliveryQuery,type DeliveryRow,type DeliverySnapshot} from "./delivery-data";
import {useI18n} from "./i18n";

export function DeliveryPage() {
  const {t,locale}=useI18n();
  const [params,setParams]=useSearchParams();
  const queryString=params.toString();
  const query=useMemo(()=>{
    const p=new URLSearchParams(queryString);
    return {target:p.get("target")??"",applid:p.get("applid")??"",customer:p.get("customer")??"",state:p.get("state")??"",severity:p.get("severity")??"",q:p.get("q")??"",page:Number(p.get("page")??1),limit:25};
  },[queryString]);
  const [data,setData]=useState<DeliverySnapshot|null>(null);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState("");
  const [revision,refresh]=useState(0);
  const [selected,setSelected]=useState<DeliveryRow|null>(null);
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{
    const controller=new AbortController(); let active=true; let timeout=false;
    const timer=setTimeout(()=>{timeout=true;controller.abort();},12000);
    setLoading(true);setError("");setData(null);setSelected(null);
    getDelivery(query,controller.signal).then(d=>{if(active)setData(d);}).catch(e=>{if(active)setError(timeout ? "La consulta superó el tiempo de espera. Intenta actualizar." : e instanceof Error ? e.message : "No se pudo consultar el dashboard.");}).finally(()=>{clearTimeout(timer);if(active)setLoading(false);});
    return ()=>{active=false;clearTimeout(timer);controller.abort();};
  },[query,revision]);
  useEffect(()=>{if(selected)dialog.current?.showModal();else dialog.current?.close();},[selected]);
  const set=(key:keyof DeliveryQuery,value:string)=>{
    const next=new URLSearchParams(params);
    if(value)next.set(key,value);else next.delete(key);
    if(key!=="page")next.delete("page");
    setParams(next);
  };
  const stateName=(s:string|number)=>t(({0:"Habilitado",1:"Deshabilitado",2:"Auditoría"} as Record<string,string>)[s]??String(s));
  const severityName=(s:string)=>s==="any" ? t("Cualquier severidad") : `${s} · ${t(["Limpio","Indeterminada","Advertencia","Menor","Mayor","Crítica"][Number(s)]??s)}`;
  const applidName=(s:string)=>s==="__ANY__" ? t("Cualquier APPLID") : s;
  const chart=(title:string,buckets:Bucket[],field:"applid"|"severity",label:(s:string)=>string)=><article className="distribution"><h2>{t(title)}</h2>{buckets.length ? buckets.map(b=><div className="state-row" key={b.value}><button aria-pressed={query[field]===b.value} onClick={()=>set(field,query[field]===b.value?"":b.value)}>{label(b.value)}</button><div className="bar"><span style={{width:`${data?.total ? b.count/data.total*100:0}%`}}/></div><strong>{b.count.toLocaleString(locale)}</strong></div>) : <p>{t("Sin distribución para esta selección.")}</p>}</article>;
  const criteria=(r:DeliveryRow)=><ul className="criteria">{Object.entries(r.criteria).map(([field,c])=><li key={field}><strong>{field}</strong> <code>{c.operator}</code> {String(c.value)}</li>)}{!Object.keys(r.criteria).length&&<li>{t("Sin condiciones adicionales")}</li>}</ul>;
  return <main id="content" aria-busy={loading}>
    <div className="heading"><div><span className="eyebrow">05 · {t("Configuraciones de entrega")}</span><h1>Delivery</h1><p>{t("Filtros registrados y acciones por destino, APPLID y severidad.")}</p></div><button disabled={loading} onClick={()=>refresh(n=>n+1)}>{t(loading?"Consultando…":"↻ Actualizar")}</button></div>
    <div className="source-line"><span>{t("Fuente")}: <strong>{data ? data.source==="postgresql"?"PostgreSQL":t("API interna"):t("Sin verificar")}</strong></span><span>{t("Consulta")}: {data?new Date(data.observedAt).toLocaleString(locale):"—"}</span><span>{t("Última actualización de registros")}: {data?.lastUpdatedAt?new Date(data.lastUpdatedAt).toLocaleString(locale):"—"}</span></div>
    <form key={`${query.customer}|${query.q}`} className="filters" onSubmit={e=>{e.preventDefault();const form=new FormData(e.currentTarget);const next=new URLSearchParams(params);for(const key of ["customer","q"]){const value=String(form.get(key)??"").trim();if(value)next.set(key,value);else next.delete(key);}next.delete("page");setParams(next);}}>
      <label>{t("Cliente")}<input name="customer" defaultValue={query.customer} maxLength={100} placeholder={t("Customer code exacto")}/></label>
      <label className="search">{t("Nombre o ID del filtro")}<input name="q" defaultValue={query.q} maxLength={128}/></label>
      <label>{t("Estado")}<select value={query.state} onChange={e=>set("state",e.target.value)}><option value="">{t("Todos los estados")}</option>{[0,1,2].map(s=><option key={s} value={s}>{stateName(s)}</option>)}</select></label>
      <button type="submit">{t("Aplicar")}</button><button type="button" className="secondary" onClick={()=>setParams({})}>{t("Limpiar")}</button>
    </form>
    <div className="selection" aria-label={t("Selección actual")}>{(["target","applid","severity"] as const).map(key=>query[key]&&<button className="secondary" key={key} onClick={()=>set(key,"")} aria-label={`${t("Quitar selección")}: ${query[key]}`}>{key==="target"?targetNames[query[key]]:key==="applid"?applidName(query[key]):severityName(query[key])} ×</button>)}</div>
    {error&&<div className="error" role="alert"><strong>{t("No pudimos cargar los datos")}</strong><p>{t(error)}</p><button onClick={()=>refresh(n=>n+1)}>{t("Reintentar")}</button></div>}
    {loading&&<p className="loading" role="status">{t("Consultando…")}</p>}
    {data&&!loading&&<>
      <div className="delivery-targets">{targets.map(target=><button key={target} className={`delivery-target ${query.target===target?"chosen":""}`} aria-pressed={query.target===target} onClick={()=>set("target",query.target===target?"":target)}><span>{targetNames[target]}</span><strong>{(data.facets.targets.find(b=>b.value===target)?.count??0).toLocaleString(locale)}</strong><small>{t("Filtros registrados")}</small></button>)}</div>
      <div className="delivery-totals"><div><strong>{data.total.toLocaleString(locale)}</strong> {t("Filtros registrados")}</div><div><strong>{data.facets.applids.filter(b=>b.value!=="__ANY__").length}</strong> {t("APPLID específicos")}</div>{data.facets.states.map(b=><button className="secondary" key={b.value} onClick={()=>set("state",query.state===b.value?"":b.value)}>{stateName(b.value)}: {b.count.toLocaleString(locale)}</button>)}</div>
      <p className="scope-note">{t("Los conteos representan filtros, no eventos entregados. Un filtro puede incluir varios destinos y severidades.")}</p>
      <div className="delivery-charts">{chart("Por APPLID",data.facets.applids,"applid",applidName)}{chart("Por severidad",data.facets.severities,"severity",severityName)}</div>
      <section className="records"><div className="section-title"><h2>{t("Registros")}</h2><span>{data.total.toLocaleString(locale)} · {t("página")} {query.page}</span></div>{data.rows.length?<div className="table-scroll"><table><caption className="sr-only">Delivery · {t("Filtros registrados")}</caption><thead><tr>{["Nombre","Cliente","APPLID","Severidad","Estado","Peso","Destinos y acciones","Criterios"].map(h=><th key={h}>{t(h)}</th>)}</tr></thead><tbody>{data.rows.map(r=><tr key={r.id}><td><button className="row-link" onClick={()=>setSelected(r)}>{r.name}</button><small>{r.id}</small>{r.origin==="demo"&&<small>{t("Datos de demostración")}</small>}</td><td>{r.customerCode==="C00"?t("Global (C00)"):r.customerCode}</td><td>{r.applid??t("Cualquier APPLID")}</td><td>{r.severities?.join(", ")??t("Cualquier severidad")}</td><td>{stateName(r.state)}</td><td>{r.weight}</td><td>{r.targets.map(target=><div key={target.target}>{targetNames[target.target]}<small>{t({enable:"Habilitar",force_off:"Forzar apagado",overlay:"Superponer"}[target.behavior])}</small></div>)}{!r.targets.length&&t("Sin acciones configuradas")}</td><td>{criteria(r)}</td></tr>)}</tbody></table></div>:<div className="empty"><h3>{t("No hay coincidencias")}</h3><p>{t("Ajusta los filtros para consultar otros registros.")}</p></div>}
      <div className="pagination"><button className="secondary" disabled={query.page<=1} onClick={()=>set("page",String(query.page-1))}>{t("Anterior")}</button><span>{query.limit} {t("por página")}</span><button className="secondary" disabled={query.page*query.limit>=data.total} onClick={()=>set("page",String(query.page+1))}>{t("Siguiente")}</button></div></section>
    </>}
    <dialog ref={dialog} onClose={()=>setSelected(null)} aria-labelledby="delivery-detail-title"><div className="section-title"><h2 id="delivery-detail-title">{t("Detalle de configuración")}</h2><button onClick={()=>setSelected(null)}>{t("Cerrar")}</button></div>{selected&&<><h3>{selected.name}</h3><p>{selected.description}</p><p>{t("Origen")}: {selected.origin} · {t("Actualizado")}: {new Date(selected.updatedAt).toLocaleString(locale)}</p>{selected.origin==="demo"&&<p>{t("Filtros de demostración; no ejecutan acciones.")}</p>}{criteria(selected)}{selected.targets.map(target=><section key={target.target}><h3>{targetNames[target.target]}</h3><dl>{[[t("Referencia"),target.actionReference??"—"],[t("Grupo"),target.assignmentGroup??"—"],[t("Demora (segundos)"),target.delaySeconds??"—"],[t("Depende de Ticketing"),t(target.dependsOnTicketing?"Sí":"No")]].map(([k,v])=><div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl></section>)}</>}</dialog>
  </main>;
}
