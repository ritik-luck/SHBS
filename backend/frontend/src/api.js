import axios from "axios";

const API = axios.create({
  baseURL: "http://localhost:8080",
  timeout: 10000,
});

// ── Health ──
export const getHealth = () => API.get("/health");
export const getHealthHistory = () => API.get("/health/history");

// ── System State ──
export const getSystemState = () => API.get("/system/state");

// ── Failure Metrics ──
export const getFailures = () => API.get("/metrics/failures");
export const getFailuresByService = () => API.get("/metrics/failures/services");
export const getFailuresByType = () => API.get("/metrics/failures/type");
export const getFailuresBySeverity = () => API.get("/metrics/failures/severity");
export const getFailuresTimeline = (period = "1h") =>
  API.get(`/metrics/failures/timeline?period=${period}`);

// ── Recovery ──
export const getRecoveryMetrics = () => API.get("/metrics/recovery");
export const getRecoverySummary = () => API.get("/metrics/recovery/summary");

// ── Admin: Audit Logs ──
export const getAuditLogs = (page = 0, size = 20) =>
  API.get(`/admin/audit-logs?page=${page}&size=${size}`);
export const getAuditLogsByAction = (actionType, page = 0, size = 20) =>
  API.get(`/admin/audit-logs/action/${actionType}?page=${page}&size=${size}`);

// ── Admin: Alerts ──
export const getAlerts = () => API.get("/admin/alerts");

// ── Admin: Configuration ──
export const getConfig = () => API.get("/admin/config");
export const updateConfig = (key, value) =>
  API.put(`/admin/config/${key}?value=${encodeURIComponent(value)}`);
export const updateFallback = (endpoint, message) =>
  API.put(`/admin/fallback?endpoint=${encodeURIComponent(endpoint)}&message=${encodeURIComponent(message)}`);

// ── Admin: Actions ──
export const simulateFailure = (type, service, count) =>
  API.post("/admin/simulate", { type, service, count });
export const resetCircuit = () => API.post("/admin/reset-circuit");
export const clearMetrics = () => API.post("/admin/clear-metrics");

export default API;