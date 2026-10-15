import axios from "axios";

const API_BASE = process.env.REACT_APP_API_URL || "/api";

const API = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
});

// ── JWT Auth Interceptors ──
API.interceptors.request.use((config) => {
  const token = localStorage.getItem("jwtToken");
  if (token && config.url.startsWith("/admin")) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

API.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response && error.response.status === 403) {
      const username = prompt("Admin Authentication Required.\nUsername:");
      if (username) {
        const password = prompt("Password:");
        if (password) {
          try {
            const loginRes = await axios.post(`${API_BASE}/admin/login`, {
              username,
              password,
            });
            const token = loginRes.data.token;
            localStorage.setItem("jwtToken", token);
            // Retry the original request with the new token
            error.config.headers.Authorization = `Bearer ${token}`;
            return axios(error.config);
          } catch (loginErr) {
            alert("Login failed. Invalid credentials.");
          }
        }
      }
    }
    return Promise.reject(error);
  }
);

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

// ── SLO ──
export const getSLOMetrics = () => API.get("/metrics/slo");

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
