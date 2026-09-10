import { useEffect, useState } from "react";
import { useI18n } from "../../shared/i18n/I18nProvider";
import type { PlatformService } from "./platform";
type Action = "start" | "stop" | "restart";
type Operation = { id: string; service: string; action: Action; status: "running" | "succeeded" | "failed" | "interrupted"; exitCode: number | null };
const labels = { start: "Encender", stop: "Apagar", restart: "Reiniciar" };
export function ServiceActions({ service, onComplete }: { service: PlatformService; onComplete: () => void }) {
  const { t } = useI18n();
  const key = `console.operation.${service.id}`;
  const [pending, setPending] = useState<Action>();
  const [operation, setOperation] = useState<Operation>();
  const [operationId, setOperationId] = useState(() => { try { return localStorage.getItem(key) ?? ""; } catch { return ""; } });
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const busy = sending || !!operationId;
  useEffect(() => {
    if (!operationId) return;
    let active = true;
    const controller = new AbortController();
    const poll = async () => {
      try {
        const response = await fetch(`/api/administration/operations/${operationId}`, { signal: controller.signal, cache: "no-store" });
        if (!response.ok) throw new Error();
        const data: Operation = await response.json();
        if (data.id !== operationId || data.service !== service.id || !["running", "succeeded", "failed", "interrupted"].includes(data.status)) throw new Error();
        if (!active) return;
        setOperation(data); setError("");
        if (data.status !== "running") {
          setOperationId("");
          try { localStorage.removeItem(key); } catch { /* Optional persistence. */ }
          onComplete();
        }
      } catch { if (active) setError("No se pudo consultar el resultado. Se reintentará sin repetir la operación."); }
    };
    void poll();
    const timer = window.setInterval(() => void poll(), 2000);
    return () => { active = false; controller.abort(); window.clearInterval(timer); };
  }, [operationId, service.id]);
  async function execute() {
    if (!pending || busy) return;
    const id = crypto.randomUUID();
    setSending(true); setError(""); setOperation(undefined);
    const controller = new AbortController(); const timer = window.setTimeout(() => controller.abort(), 8000);
    try {
      const response = await fetch("/api/administration/operations", { method: "POST", signal: controller.signal, headers: { "Content-Type": "application/json", "X-Console-Action": "1" }, body: JSON.stringify({ id, service: service.id, action: pending }) });
      if (response.status === 409) { setError("Ya hay una operación en curso. Espera a que termine."); return; }
      if (!response.ok) { setError("La operación fue rechazada. Revisa el servicio e intenta nuevamente."); return; }
      setOperationId(id);
      try { localStorage.setItem(key, id); } catch { /* Optional persistence. */ }
      setPending(undefined);
    } catch {
      // Submission may have succeeded. Only look up the same operation; never replay.
      setOperationId(id);
      try { localStorage.setItem(key, id); } catch { /* Optional persistence. */ }
      setError("No se pudo consultar el resultado. Se reintentará sin repetir la operación.");
    } finally { window.clearTimeout(timer); setSending(false); }
  }
  if (!service.actions?.length) return <p className="admin-note">{t(service.id === "frontend-management-api" ? "La API de control se administra por CLI para no interrumpir las operaciones." : "Este componente no admite estas acciones en el administrador actual.")}</p>;
  return <section className="service-actions"><h3>{t("Control del servicio")}</h3><p>{t("Usa la misma lógica del administrador local: valida dependencias y comprueba el estado final.")}</p><div className="service-action-buttons">{(["start", "stop", "restart"] as const).map(action => <button className="admin-button" key={action} disabled={busy || !service.actions?.includes(action) || (action === "start" && service.status === "healthy") || (action !== "start" && ["stopped", "missing"].includes(service.runtime ?? ""))} onClick={() => { setPending(action); setError(""); }}>{t(labels[action])}</button>)}</div>{pending && <div className="admin-alert"><strong>{t(labels[pending])}: {service.name}</strong><p>{t(pending === "start" ? "Se encenderá el servicio y las dependencias que necesite." : "El servicio seleccionado dejará de estar disponible durante esta operación. Sus datos se conservan.")}</p>{service.id === "event-management-console" && <p>{t("Esta consola y sus dashboards se desconectarán. Si la apagas, necesitarás encenderla por CLI.")}</p>}<div className="service-action-buttons"><button className="admin-button" disabled={busy} onClick={() => setPending(undefined)}>{t("Cancelar")}</button><button className="admin-button" disabled={busy} onClick={() => void execute()}>{t("Confirmar operación")}</button></div></div>}<div aria-live="polite">{busy && <p>{t("Operación en curso. Esperando la validación del servicio…")}</p>}{operation && <p>{t(operation.status === "succeeded" ? "Operación completada y validada." : operation.status === "interrupted" ? "La ejecución se interrumpió. Comprueba el estado antes de reintentar." : operation.status === "failed" ? "La operación falló o no alcanzó el estado esperado. Revisa el diagnóstico del servicio." : "Operación en curso.")}{operation.exitCode !== null && operation.exitCode !== 0 && ` (${operation.exitCode})`}</p>}{error && <p role="alert">{t(error)}</p>}</div></section>;
}
