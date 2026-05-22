import React, { useState } from "react";

const failureTypes = [
  { value: "timeout", label: "Timeout", icon: "⏳" },
  { value: "exception", label: "Exception", icon: "💣" },
  { value: "database", label: "Database", icon: "🗄️" },
  { value: "network", label: "Network", icon: "🌐" },
  { value: "dependency", label: "Dependency", icon: "🔗" },
];

const SimulatePanel = ({ onSimulate, onResetCircuit, onClearMetrics }) => {
  const [type, setType] = useState("timeout");
  const [service, setService] = useState("api-service");
  const [count, setCount] = useState(1);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const handleSimulate = async () => {
    setLoading(true);
    setResult(null);
    try {
      const res = await onSimulate(type, service, count);
      setResult({ success: true, message: res.data || "Simulation complete!" });
    } catch (e) {
      setResult({ success: false, message: "Simulation failed: " + (e.message || "Unknown error") });
    }
    setLoading(false);
    setTimeout(() => setResult(null), 4000);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-lg)" }}>
      {/* Simulate Form */}
      <div className="card">
        <div className="card-title">
          <span>🧪</span> Failure Simulation
        </div>
        <p style={{ color: "var(--text-muted)", fontSize: "0.85rem", marginBottom: 20 }}>
          Inject simulated failures to test the self-healing pipeline end-to-end.
        </p>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 20 }}>
          <div className="form-group">
            <label className="form-label">Failure Type</label>
            <select className="select" value={type} onChange={(e) => setType(e.target.value)}>
              {failureTypes.map((ft) => (
                <option key={ft.value} value={ft.value}>
                  {ft.icon} {ft.label}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label">Target Service</label>
            <input
              className="input"
              value={service}
              onChange={(e) => setService(e.target.value)}
              placeholder="e.g., auth-service"
            />
          </div>

          <div className="form-group">
            <label className="form-label">Count ({count})</label>
            <input
              type="range"
              min={1}
              max={20}
              value={count}
              onChange={(e) => setCount(parseInt(e.target.value))}
              style={{
                width: "100%",
                accentColor: "var(--accent-rose)",
                height: 6,
                marginTop: 8,
              }}
            />
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: "0.7rem",
                color: "var(--text-muted)",
              }}
            >
              <span>1</span>
              <span>20</span>
            </div>
          </div>

          <div className="form-group" style={{ justifyContent: "flex-end" }}>
            <button
              className="btn btn-danger"
              onClick={handleSimulate}
              disabled={loading || !service.trim()}
              style={{
                opacity: loading || !service.trim() ? 0.6 : 1,
                width: "100%",
              }}
            >
              {loading ? (
                <>
                  <span className="loading-spinner" style={{ width: 14, height: 14, borderTopColor: "white" }} />
                  Simulating...
                </>
              ) : (
                <>💥 Inject {count} Failure{count > 1 ? "s" : ""}</>
              )}
            </button>
          </div>
        </div>

        {/* Result toast */}
        {result && (
          <div
            className={`toast ${result.success ? "toast-success" : "toast-error"}`}
            style={{ position: "relative", bottom: "auto", right: "auto", marginTop: 8 }}
          >
            {result.success ? "✅" : "❌"} {result.message}
          </div>
        )}
      </div>

      {/* Quick Actions */}
      <div className="card">
        <div className="card-title">
          <span>⚡</span> Quick Actions
        </div>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
          <button className="btn btn-success" onClick={onResetCircuit}>
            🔄 Reset Circuit Breaker
          </button>
          <button className="btn btn-warning" onClick={onClearMetrics}>
            🗑️ Clear All Metrics
          </button>
        </div>
      </div>

      {/* Simulation Guide */}
      <div className="card" style={{ borderColor: "var(--border-accent)" }}>
        <div className="card-title">
          <span>📖</span> Demo Scenario
        </div>
        <div
          style={{
            fontSize: "0.85rem",
            color: "var(--text-secondary)",
            lineHeight: 1.8,
          }}
        >
          <ol style={{ paddingLeft: 20, display: "flex", flexDirection: "column", gap: 8 }}>
            <li>
              <strong style={{ color: "var(--status-healthy)" }}>Start fresh</strong> — All indicators should be GREEN
            </li>
            <li>
              <strong style={{ color: "var(--status-degraded)" }}>Inject 3 failures</strong> — Status changes to DEGRADED (yellow)
            </li>
            <li>
              <strong style={{ color: "var(--status-unhealthy)" }}>Inject 2 more</strong> — Circuit breaker OPENS (red), alerts fire
            </li>
            <li>
              <strong style={{ color: "var(--accent-purple)" }}>Observe fallback</strong> — System serves degraded/fallback responses
            </li>
            <li>
              <strong style={{ color: "var(--accent-blue)" }}>Wait for recovery</strong> — Circuit breaker moves to HALF_OPEN
            </li>
            <li>
              <strong style={{ color: "var(--status-healthy)" }}>Auto-recovery</strong> — All indicators return to GREEN
            </li>
            <li>
              <strong style={{ color: "var(--accent-indigo)" }}>Review audit logs</strong> — See complete healing timeline
            </li>
          </ol>
        </div>
      </div>
    </div>
  );
};

export default SimulatePanel;
