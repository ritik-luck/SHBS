import axios from "axios";

const API = "http://localhost:8080";

export const getFailures = () => axios.get(`${API}/metrics/failures`);

export const getServiceFailures = () => axios.get(`${API}/metrics/services`);

export const getSystemState = () => axios.get(`${API}/system/state`);

export const simulateFailure = () => axios.get(`${API}/simulate-failure`);

export const resetCircuit = () =>
  axios.post(`${API}/admin/reset-circuit`);

export const clearMetrics = () =>
  axios.post(`${API}/admin/clear-metrics`);