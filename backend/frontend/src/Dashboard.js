import React, { useEffect, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import {
  getHealth,
  getSystemState,
  getFailures,
  getFailuresByService,
  getFailuresByType,
  getFailuresBySeverity,
  getFailuresTimeline,
  getRecoveryMetrics,
  getRecoverySummary,
  getAuditLogs,
  getAlerts,
  getConfig,
  simulateFailure,
  resetCircuit,
  clearMetrics,
  updateConfig,
} from "./api";

import HealthGauge from "./components/HealthGauge";
import CircuitBreakerCard from "./components/CircuitBreakerCard";
import StatsCards from "./components/StatsCards";
import FailureTimelineChart from "./components/FailureTimelineChart";
import FailureBreakdownCharts from "./components/FailureBreakdownCharts";
import ServiceHealthTable from "./components/ServiceHealthTable";
import AuditLogFeed from "./components/AuditLogFeed";
import AlertsPanel from "./components/AlertsPanel";
import ConfigPanel from "./components/ConfigPanel";
import SimulatePanel from "./components/SimulatePanel";
import RecoveryPanel from "./components/RecoveryPanel";

const Dashboard = ({ activePage = "dashboard", onHealthUpdate }) => {
  // ── Core State ──
  const [health, setHealth] = useState({ score: 100, status: "HEALTHY" });
  const [circuitState, setCircuitState] = useState("CLOSED");
  const [failures, setFailures] = useState(0);
  const [services, setServices] = useState([]);
  const [failuresByType, setFailuresByType] = useState([]);
  const [failuresBySeverity, setFailuresBySeverity] = useState([]);
  const [timeline, setTimeline] = useState([]);
  const [timelinePeriod, setTimelinePeriod] = useState("1h");

  // ── Admin State ──
  const [recoveryMetrics, setRecoveryMetrics] = useState([]);
  const [recoverySummary, setRecoverySummary] = useState({});
  const [auditLogs, setAuditLogs] = useState([]);
  const [auditPage, setAuditPage] = useState(0);
  const [auditTotalPages, setAuditTotalPages] = useState(1);
  const [alerts, setAlerts] = useState([]);
  const [configs, setConfigs] = useState([]);
  const [wsAlert, setWsAlert] = useState(null);

  // ── Data Loaders ──
  const loadDashboardData = useCallback(async () => {
    try {
      const [hRes, sRes, fRes, svcRes, ftRes, fsRes, tlRes] =
        await Promise.allSettled([
          getHealth(),
          getSystemState(),
          getFailures(),
          getFailuresByService(),
          getFailuresByType(),
          getFailuresBySeverity(),
          getFailuresTimeline(timelinePeriod),
        ]);

      if (hRes.status === "fulfilled") {
        const h = hRes.value.data;
        setHealth(h);
        onHealthUpdate?.(h);
      }
      if (sRes.status === "fulfilled") setCircuitState(sRes.value.data);
      if (fRes.status === "fulfilled") setFailures(fRes.value.data);
      if (svcRes.status === "fulfilled") setServices(svcRes.value.data || []);
      if (ftRes.status === "fulfilled") setFailuresByType(ftRes.value.data || []);
      if (fsRes.status === "fulfilled") setFailuresBySeverity(fsRes.value.data || []);
      if (tlRes.status === "fulfilled") setTimeline(tlRes.value.data || []);
    } catch (e) {
      console.error("Dashboard data load error:", e);
    }
  }, [timelinePeriod, onHealthUpdate]);

  const loadRecoveryData = useCallback(async () => {
    try {
      const [mRes, sRes] = await Promise.allSettled([
        getRecoveryMetrics(),
        getRecoverySummary(),
      ]);
      if (mRes.status === "fulfilled") setRecoveryMetrics(mRes.value.data || []);
      if (sRes.status === "fulfilled") setRecoverySummary(sRes.value.data || {});
    } catch (e) {
      console.error("Recovery data load error:", e);
    }
  }, []);

  const loadAuditLogs = useCallback(async (page = 0) => {
    try {
      const res = await getAuditLogs(page, 15);
      setAuditLogs(res.data.content || []);
      setAuditTotalPages(res.data.totalPages || 1);
      setAuditPage(page);
    } catch (e) {
      console.error("Audit logs load error:", e);
    }
  }, []);

  const loadAlerts = useCallback(async () => {
    try {
      const res = await getAlerts();
      setAlerts(res.data || []);
    } catch (e) {
      console.error("Alerts load error:", e);
    }
  }, []);

  const loadConfigs = useCallback(async () => {
    try {
      const res = await getConfig();
      setConfigs(res.data || []);
    } catch (e) {
      console.error("Configs load error:", e);
    }
  }, []);

  // ── Load data based on active page ──
  useEffect(() => {
    loadDashboardData();
    if (activePage === "recovery") loadRecoveryData();
    if (activePage === "audit") loadAuditLogs(0);
    if (activePage === "alerts") loadAlerts();
    if (activePage === "config") loadConfigs();
    if (activePage === "failures") {
      // data already loaded in dashboard
    }
  }, [activePage, loadDashboardData, loadRecoveryData, loadAuditLogs, loadAlerts, loadConfigs]);

  // ── WebSocket ──
  useEffect(() => {
    const client = new Client({
      brokerURL: "ws://localhost:8080/ws",
      reconnectDelay: 5000,
    });

    client.onConnect = () => {
      client.subscribe("/topic/alerts", (message) => {
        setWsAlert(message.body);
        setTimeout(() => setWsAlert(null), 8000);
        loadDashboardData();
        loadAlerts();
      });

      client.subscribe("/topic/metrics", () => {
        loadDashboardData();
      });
    };

    client.activate();
    return () => client.deactivate();
  }, [loadDashboardData, loadAlerts]);

  // Auto-refresh every 10s
  useEffect(() => {
    const interval = setInterval(loadDashboardData, 10000);
    return () => clearInterval(interval);
  }, [loadDashboardData]);

  // ── Handlers ──
  const handleResetCircuit = async () => {
    await resetCircuit();
    loadDashboardData();
  };

  const handleClearMetrics = async () => {
    await clearMetrics();
    loadDashboardData();
  };

  const handleSimulate = async (type, service, count) => {
    const res = await simulateFailure(type, service, count);
    loadDashboardData();
    return res;
  };

  const handleConfigUpdate = async (key, value) => {
    await updateConfig(key, value);
    loadConfigs();
  };

  const handlePeriodChange = (p) => {
    setTimelinePeriod(p);
  };

  // ── Render Pages ──
  const renderDashboard = () => (
    <>
      <div className="page-header">
        <h1>System Overview</h1>
        <p>Real-time monitoring of the Self-Healing Backend System</p>
      </div>

      <StatsCards
        failures={failures}
        recoveryRate={recoverySummary.successRate || 0}
        alertCount={alerts.length}
        circuitState={circuitState}
      />

      <div className="dashboard-grid" style={{ marginTop: "var(--space-lg)" }}>
        <div className="col-span-4">
          <HealthGauge score={health.score} status={health.status} />
        </div>
        <div className="col-span-8">
          <FailureTimelineChart
            data={timeline}
            period={timelinePeriod}
            onPeriodChange={handlePeriodChange}
          />
        </div>

        <div className="col-span-5">
          <CircuitBreakerCard state={circuitState} onReset={handleResetCircuit} />
        </div>
        <div className="col-span-7">
          <ServiceHealthTable services={services} />
        </div>

        <div className="col-span-12">
          <FailureBreakdownCharts
            byType={failuresByType}
            bySeverity={failuresBySeverity}
          />
        </div>
      </div>
    </>
  );

  const renderFailures = () => (
    <>
      <div className="page-header">
        <h1>Failure Analytics</h1>
        <p>Detailed breakdown of system failures by type, severity, and service</p>
      </div>

      <FailureTimelineChart
        data={timeline}
        period={timelinePeriod}
        onPeriodChange={handlePeriodChange}
      />

      <div style={{ marginTop: "var(--space-lg)" }}>
        <FailureBreakdownCharts
          byType={failuresByType}
          bySeverity={failuresBySeverity}
        />
      </div>

      <div style={{ marginTop: "var(--space-lg)" }}>
        <ServiceHealthTable services={services} />
      </div>
    </>
  );

  const renderRecovery = () => (
    <>
      <div className="page-header">
        <h1>Recovery Management</h1>
        <p>Track recovery attempts, success rates, and the staged recovery pipeline</p>
      </div>
      <RecoveryPanel metrics={recoveryMetrics} summary={recoverySummary} />
    </>
  );

  const renderAudit = () => (
    <>
      <div className="page-header">
        <h1>Audit Logs</h1>
        <p>Complete trail of every healing action and system event</p>
      </div>
      <AuditLogFeed
        logs={auditLogs}
        totalPages={auditTotalPages}
        currentPage={auditPage}
        onPageChange={(p) => loadAuditLogs(p)}
      />
    </>
  );

  const renderAlerts = () => (
    <>
      <div className="page-header">
        <h1>Alert Center</h1>
        <p>History of all system alerts and notifications</p>
      </div>
      <AlertsPanel alerts={alerts} />
    </>
  );

  const renderConfig = () => (
    <>
      <div className="page-header">
        <h1>Configuration</h1>
        <p>Manage system thresholds and runtime configuration</p>
      </div>
      <ConfigPanel configs={configs} onUpdate={handleConfigUpdate} />
    </>
  );

  const renderSimulate = () => (
    <>
      <div className="page-header">
        <h1>Simulation Lab</h1>
        <p>Test the self-healing pipeline by injecting simulated failures</p>
      </div>
      <SimulatePanel
        onSimulate={handleSimulate}
        onResetCircuit={handleResetCircuit}
        onClearMetrics={handleClearMetrics}
      />
    </>
  );

  const pages = {
    dashboard: renderDashboard,
    failures: renderFailures,
    recovery: renderRecovery,
    audit: renderAudit,
    alerts: renderAlerts,
    config: renderConfig,
    simulate: renderSimulate,
  };

  const renderPage = pages[activePage] || renderDashboard;

  return (
    <div style={{ position: "relative" }}>
      {/* WebSocket Alert Banner */}
      {wsAlert && wsAlert !== "NORMAL" && (
        <div
          className={`alert-banner ${
            wsAlert === "CRITICAL"
              ? "alert-banner-critical"
              : "alert-banner-warning"
          }`}
          style={{ position: "relative", left: 0, marginBottom: "var(--space-lg)" }}
        >
          <span>{wsAlert === "CRITICAL" ? "🚨" : "⚠️"}</span>
          <span>System Alert: {wsAlert}</span>
          <button className="dismiss-btn" onClick={() => setWsAlert(null)}>
            ✕
          </button>
        </div>
      )}

      {renderPage()}
    </div>
  );
};

export default Dashboard;