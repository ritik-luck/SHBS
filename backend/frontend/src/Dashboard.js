import React, { useEffect, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import {
  getHealth,
  getSystemState,
  getFailures,
  getRecoverySummary,
  getAuditLogs,
  getSLOMetrics,
  simulateFailure,
  resetCircuit,
  clearMetrics,
} from "./api";

import StatusBar from "./components/StatusBar";
import CircuitBreakerDial from "./components/CircuitBreakerDial";
import TopologyView from "./components/TopologyView";
import MetricsPanel from "./components/MetricsPanel";
import AuditLogPanel from "./components/AuditLogPanel";
import AdminControls from "./components/AdminControls";

const Dashboard = () => {
  // ── State ──
  const [health, setHealth] = useState({ score: 100, status: "HEALTHY" });
  const [circuitState, setCircuitState] = useState("CLOSED");
  const [failures, setFailures] = useState(0);
  const [recoveryRate, setRecoveryRate] = useState(0);
  const [auditLogs, setAuditLogs] = useState([]);
  const [sloData, setSloData] = useState({});

  // ── Data Loaders ──
  const loadData = useCallback(async () => {
    try {
      const [hRes, sRes, fRes, rRes, aRes, sloRes] = await Promise.allSettled([
        getHealth(),
        getSystemState(),
        getFailures(),
        getRecoverySummary(),
        getAuditLogs(0, 30),
        getSLOMetrics(),
      ]);

      if (hRes.status === "fulfilled") setHealth(hRes.value.data);
      if (sRes.status === "fulfilled") setCircuitState(sRes.value.data);
      if (fRes.status === "fulfilled") setFailures(fRes.value.data);
      if (rRes.status === "fulfilled") setRecoveryRate(rRes.value.data?.successRate || 0);
      if (aRes.status === "fulfilled") setAuditLogs(aRes.value.data?.content || []);
      if (sloRes.status === "fulfilled") setSloData(sloRes.value.data || {});
    } catch (e) {
      console.error("Data load error:", e);
    }
  }, []);

  // ── WebSocket ──
  useEffect(() => {
    const WS_URL = process.env.REACT_APP_WS_URL || `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws`;
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 5000,
    });

    client.onConnect = () => {
      client.subscribe("/topic/alerts", () => loadData());
      client.subscribe("/topic/metrics", () => loadData());
    };

    client.activate();
    return () => client.deactivate();
  }, [loadData]);

  // ── Initial load + polling ──
  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 10000);
    return () => clearInterval(interval);
  }, [loadData]);

  // ── Handlers ──
  const handleSimulate = async (type, service, count) => {
    await simulateFailure(type, service, count);
    loadData();
  };

  const handleReset = async () => {
    await resetCircuit();
    loadData();
  };

  const handleClear = async () => {
    await clearMetrics();
    loadData();
  };

  // ── Render: NOC 3-Column Layout ──
  return (
    <div className="noc-layout">
      {/* ── Top: Status Bar ── */}
      <StatusBar circuitState={circuitState} sloData={sloData} health={health} />

      {/* ── Middle: 3-Column Cockpit ── */}
      <div className="noc-main">
        {/* Left Column: Topology & Circuit Breaker */}
        <div className="panel">
          <div className="panel__header">System Topology</div>
          <CircuitBreakerDial state={circuitState} onReset={handleReset} />
          <TopologyView circuitState={circuitState} health={health} />
        </div>

        {/* Center Column: Live Metrics */}
        <div className="panel">
          <div className="panel__header">Live Metrics</div>
          <MetricsPanel
            failures={failures}
            health={health}
            recoveryRate={recoveryRate}
            sloData={sloData}
          />
        </div>

        {/* Right Column: Audit Log */}
        <div className="panel">
          <div className="panel__header">Audit Log</div>
          <AuditLogPanel logs={auditLogs} />
        </div>
      </div>

      {/* ── Bottom: Admin Controls ── */}
      <AdminControls
        onSimulate={handleSimulate}
        onReset={handleReset}
        onClear={handleClear}
      />
    </div>
  );
};

export default Dashboard;
