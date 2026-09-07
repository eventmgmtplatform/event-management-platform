const state = { tickets: structuredClone(window.MOCK_TICKETS), selected: null, page: 1, pageSize: 7 };
const $ = id => document.getElementById(id);
const labels = {OPEN:"Abierto",IN_PROGRESS:"En progreso",PENDING:"Pendiente",RESOLVED:"Resuelto",CLOSED:"Cerrado"};

function filtered() {
  const q = $("search").value.trim().toLowerCase();
  return state.tickets.filter(t =>
    (!q || [t.number,t.eventId,t.summary,t.tower].some(v => v.toLowerCase().includes(q))) &&
    (!$("statusFilter").value || t.status === $("statusFilter").value) &&
    (!$("severityFilter").value || String(t.severity) === $("severityFilter").value) &&
    (!$("towerFilter").value || t.tower === $("towerFilter").value));
}

function renderCards() {
  const n = s => state.tickets.filter(t => t.status === s).length;
  const cards = [["Tickets activos",state.tickets.filter(t=>!["CLOSED","RESOLVED"].includes(t.status)).length,"blue"],["Abiertos",n("OPEN"),"cyan"],["En progreso",n("IN_PROGRESS"),"amber"],["Severidad crítica",state.tickets.filter(t=>t.severity===5 && t.status!=="CLOSED").length,"red"]];
  $("cards").innerHTML = cards.map(c=>`<article class="card ${c[2]}"><span>${c[0]}</span><strong>${c[1]}</strong><small>Vista local de demostración</small></article>`).join("");
}

function render() {
  const list = filtered(); const pages = Math.max(1, Math.ceil(list.length/state.pageSize));
  state.page = Math.min(state.page,pages); const start=(state.page-1)*state.pageSize;
  $("ticketRows").innerHTML = list.slice(start,start+state.pageSize).map(t=>`<tr data-id="${t.id}" tabindex="0"><td><strong>${t.number}</strong><small>${t.instance}</small></td><td><span class="status ${t.status.toLowerCase()}">${labels[t.status]}</span></td><td><span class="sev sev-${t.severity}">${t.severity}</span></td><td>${t.tower}</td><td><code>${t.eventId}</code></td><td>${t.summary}</td><td>${new Date(t.updatedAt).toLocaleString("es-MX",{dateStyle:"short",timeStyle:"short"})}</td></tr>`).join("");
  $("empty").hidden=list.length>0; $("countLabel").textContent=`${list.length} tickets encontrados`; $("pageLabel").textContent=`Página ${state.page} de ${pages}`;
  $("prevBtn").disabled=state.page===1; $("nextBtn").disabled=state.page===pages;
  document.querySelectorAll("tbody tr").forEach(row => { row.addEventListener("contextmenu",showMenu); row.addEventListener("dblclick",()=>showDetail(row.dataset.id)); });
  renderCards();
}

function fillFilters() {
  [...new Set(state.tickets.map(t=>t.status))].forEach(v=>$("statusFilter").add(new Option(labels[v],v)));
  [...new Set(state.tickets.map(t=>t.severity))].sort().forEach(v=>$("severityFilter").add(new Option(`Severidad ${v}`,v)));
  [...new Set(state.tickets.map(t=>t.tower))].sort().forEach(v=>$("towerFilter").add(new Option(v,v)));
}

function showMenu(e) { e.preventDefault(); state.selected=state.tickets.find(t=>t.id===e.currentTarget.dataset.id); const m=$("contextMenu"); m.hidden=false; m.style.left=`${Math.min(e.clientX,innerWidth-190)}px`; m.style.top=`${Math.min(e.clientY,innerHeight-150)}px`; }
function hideMenu(){ $("contextMenu").hidden=true; }
function openModal(){ hideMenu(); $("modalTicket").value=state.selected.number; $("closeForm").reset(); $("modalTicket").value=state.selected.number; $("modal").hidden=false; $("closeCode").focus(); }
function closeModal(){ $("modal").hidden=true; }
function toast(msg){ const t=$("toast"); t.textContent=msg;t.hidden=false;setTimeout(()=>t.hidden=true,3200); }
function showDetail(id){ hideMenu(); const t=state.tickets.find(x=>x.id===id); $("detailContent").innerHTML=`<small>Detalle del ticket</small><h2>${t.number}</h2><dl><dt>Estado</dt><dd>${labels[t.status]}</dd><dt>Severidad</dt><dd>${t.severity}</dd><dt>Torre</dt><dd>${t.tower}</dd><dt>Evento</dt><dd>${t.eventId}</dd><dt>Resumen</dt><dd>${t.summary}</dd><dt>Instancia</dt><dd>${t.instance}</dd></dl>`; $("detailPanel").hidden=false; }

$("contextMenu").addEventListener("click",e=>{ const a=e.target.dataset.action;if(a==="close")openModal();if(a==="detail")showDetail(state.selected.id);if(a==="copy"){navigator.clipboard?.writeText(state.selected.number);hideMenu();toast("Número copiado");}});
$("closeForm").addEventListener("submit",e=>{e.preventDefault();const t=state.selected;t.status="CLOSED";t.updatedAt=new Date().toISOString();const audit={operationId:crypto.randomUUID(),ticket:t.number,closeCode:$("closeCode").value,notes:$("closeNotes").value,closeEvent:$("closeEvent").checked,status:"COMPLETED",completedAt:t.updatedAt};const history=JSON.parse(localStorage.getItem("itsm-demo-audit")||"[]");history.push(audit);localStorage.setItem("itsm-demo-audit",JSON.stringify(history));closeModal();render();toast(`${t.number} cerrado en modo demostración`);});
["search","statusFilter","severityFilter","towerFilter"].forEach(id=>$(id).addEventListener(id==="search"?"input":"change",()=>{state.page=1;render();}));
$("clearBtn").onclick=()=>{["search","statusFilter","severityFilter","towerFilter"].forEach(id=>$(id).value="");state.page=1;render();};
$("prevBtn").onclick=()=>{state.page--;render();};$("nextBtn").onclick=()=>{state.page++;render();};$("refreshBtn").onclick=()=>{render();toast("Dashboard actualizado");};
$("cancelBtn").onclick=closeModal;$("modalX").onclick=closeModal;$("drawerX").onclick=()=>$("detailPanel").hidden=true;document.addEventListener("click",e=>{if(!$("contextMenu").contains(e.target))hideMenu();});document.addEventListener("keydown",e=>{if(e.key==="Escape"){hideMenu();closeModal();$("detailPanel").hidden=true;}});
fillFilters();render();

