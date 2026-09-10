export const states = { healthy: "Saludable", degraded: "Degradado", stopped: "Detenido", unknown: "Sin verificar", error: "Error", completed: "Finalizado" } as const;
export type ServiceStatus = keyof typeof states;
export type PlatformService = {
  id: string; name: string; category: string; description: string;
  actions?: string[]; reason?: string; container?: string; runtime?: string; health?: string; restartCount?: number | null;
  status: ServiceStatus; version: string | null; port: number | null;
};
export type Snapshot = { observedAt: string; services: PlatformService[] };
const inventory = [
  ["kafka", "Kafka", "Infraestructura", "Transporte de eventos, comandos y resultados."],
  ["postgres", "PostgreSQL", "Infraestructura", "Estado operativo canónico y metadatos."],
  ["opensearch", "OpenSearch", "Infraestructura", "Proyección de búsqueda y analítica."],
  ["event-gateway", "Event Gateway", "Core", "Recepción y validación de eventos."],
  ["event-processor", "Event Processor", "Core", "Procesamiento y normalización de eventos."],
  ["integration-worker", "Integration Worker", "Core", "Integraciones con proveedores y ejecución de comandos."],
  ["event-state-service", "Event State Service", "Core", "Persistencia y ciclo de vida del estado operativo."],
  ["event-management-console", "Management Console", "Interfaces", "Administración central de la plataforma."],
  ["itsm-ticketing-dashboard", "ITSM Dashboard", "Interfaces", "Panel especializado de tickets."],
  ["kafka-ui", "Kafka UI", "Interfaces", "Diagnóstico de tópicos y grupos de consumidores."],
  ["opensearch-dashboards", "OpenSearch Dashboards", "Interfaces", "Exploración y visualización de eventos."],
];
export const referenceServices: PlatformService[] = inventory.map(([id, name, category, description]) => ({
  id, name, category, description, status: "unknown", version: null, port: null,
}));
export function demoSnapshot(): Snapshot {
  return { observedAt: new Date().toISOString(), services: referenceServices.map((service, index) => ({
    ...service, status: index === 5 ? "degraded" : index === 8 ? "stopped" : "healthy",
    version: "demo", port: null,
  })) };
}
export function parseSnapshot(value: unknown): Snapshot {
  if (!value || typeof value !== "object") throw new Error("Respuesta de plataforma inválida.");
  const data = value as Record<string, unknown>;
  if (typeof data.observedAt !== "string" || !Number.isFinite(Date.parse(data.observedAt)) || !Array.isArray(data.services)) throw new Error("Contrato de plataforma inválido.");
  const ids = new Set<string>();
  for (const entry of data.services) {
    if (!entry || typeof entry !== "object") throw new Error("Servicio inválido.");
    const s = entry as Record<string, unknown>;
    if (![s.id, s.name, s.category, s.description].every(v => typeof v === "string" && v.trim().length > 0)
      || typeof s.status !== "string" || !Object.hasOwn(states, s.status)
      || !(s.version === null || typeof s.version === "string")
      || !(s.port === null || (typeof s.port === "number" && Number.isInteger(s.port) && s.port > 0 && s.port <= 65535))
      || ![s.reason, s.container, s.runtime, s.health].every(v => v === undefined || typeof v === "string")
      || !(s.restartCount === undefined || s.restartCount === null || (typeof s.restartCount === "number" && Number.isInteger(s.restartCount) && s.restartCount >= 0))
      || !(s.actions === undefined || (Array.isArray(s.actions) && s.actions.every(a => ["start", "stop", "restart"].includes(a))))
      || ids.has(s.id as string)) throw new Error("Contrato de servicio inválido.");
    ids.add(s.id as string);
  }
  return data as Snapshot;
}
export async function readPlatform(signal: AbortSignal): Promise<Snapshot> {
  const response = await fetch("/api/administration/platform", { signal, headers: { Accept: "application/json" }, cache: "no-store" });
  if (!response.ok) throw new Error("No fue posible consultar la plataforma.");
  if (!response.headers.get("content-type")?.includes("application/json")) throw new Error("La API interna de administración aún no está conectada.");
  return parseSnapshot(await response.json());
}
