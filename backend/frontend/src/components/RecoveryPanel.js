import React from "react";

const stageConfig = {
  HEALTH_CHECK: { icon: "🏥", color: "var(--accent-blue)", label: "Health Check" },
  TEST_REQUEST: { icon: "🧪", color: "var(--accent-purple)", label: "Test Request" },
  GRADUAL_RESTORE: { icon: "📈", color: "var(--accent-amber)", label: "Gradual Restore" },
  FULL_RECOVERY: { icon: "✅", color: "var(--accent-emerald)", label: "Full Recovery" },
};

const RecoveryPanel = ({ metrics = [], summary = {} }) => {
  const sorted = [...metrics].sort(
    (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
  );

  const successRate = summary.successRate ?? 0;
  const totalAttempts = summary.totalAttempts ?? 0;
  const successCount = summary.successCount ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-lg)" }}>
      {/* Summary Stats */}
      <div className="grid-3">
        <div className="card" style={{ textAlign: "center" }}>
          <div className="card-title" style={{ justifyContent: "center" }}>
            <span>🔢</span> Total Attempts
          </div>
          <div
            className="card-value mono"
            style={{ color: "var(--accent-blue)" }}
          >
            {totalAttempts}
          </div>
        </div>
        <div className="card" style={{ textAlign: "center" }}>
          <div className="card-title" style={{ justifyContent: "center" }}>
            <span>✅</span> Successful
          </div>
          <div
            className="card-value mono"
            style={{ color: "var(--accent-emerald)" }}
          >
            {successCount}
          </div>
        </div>
        <div className="card" style={{ textAlign: "center" }}>
          <div className="card-title" style={{ justifyContent: "center" }}>
            <span>📊</span> Success Rate
          </div>
          <div
            className="card-value mono"
            style={{
              color:
                successRate >= 80
                  ? "var(--status-healthy)"
                  : successRate >= 50
                  ? "var(--status-degraded)"
                  : "var(--status-unhealthy)",
            }}
          >
            {Math.round(successRate)}%
          </div>
        </div>
      </div>

      {/* Recovery Pipeline Visualizer */}
      <div className="card">
        <div className="card-title">
          <span>🔄</span> Recovery Pipeline
        </div>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "20px 0",
            gap: 8,
          }}
        >
          {Object.entries(stageConfig).map(([key, stage], i) => (
            <React.Fragment key={key}>
              <div style={{ textAlign: "center", flex: 1 }}>
                <div
                  style={{
                    width: 56,
                    height: 56,
                    borderRadius: "50%",
                    background: `${stage.color}15`,
                    border: `2px solid ${stage.color}40`,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: "1.4rem",
                    margin: "0 auto 8px",
                  }}
                >
                  {stage.icon}
                </div>
                <div
                  style={{
                    fontSize: "0.72rem",
                    fontWeight: 600,
                    color: stage.color,
                    textTransform: "uppercase",
                    letterSpacing: "0.04em",
                  }}
                >
                  {stage.label}
                </div>
              </div>
              {i < Object.entries(stageConfig).length - 1 && (
                <div
                  style={{
                    flex: "0 0 40px",
                    height: 2,
                    background:
                      "linear-gradient(90deg, rgba(255,255,255,0.1), rgba(255,255,255,0.05))",
                    marginBottom: 20,
                  }}
                />
              )}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* Recovery History */}
      <div className="card">
        <div className="card-title">
          <span>📜</span> Recovery History
        </div>
        {sorted.length > 0 ? (
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Stage</th>
                  <th>Result</th>
                  <th>Duration</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {sorted.slice(0, 25).map((m, i) => {
                  const sc = stageConfig[m.stage] || {
                    icon: "❓",
                    color: "var(--text-muted)",
                    label: m.stage,
                  };
                  return (
                    <tr key={m.id || i}>
                      <td className="mono" style={{ fontSize: "0.78rem", whiteSpace: "nowrap" }}>
                        {m.timestamp
                          ? new Date(m.timestamp).toLocaleString([], {
                              month: "short",
                              day: "numeric",
                              hour: "2-digit",
                              minute: "2-digit",
                              second: "2-digit",
                            })
                          : "—"}
                      </td>
                      <td>
                        <span
                          className="badge"
                          style={{
                            color: sc.color,
                            background: `${sc.color}15`,
                            border: `1px solid ${sc.color}30`,
                          }}
                        >
                          {sc.icon} {sc.label}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            m.result === "SUCCESS" ? "badge-success" : "badge-failure"
                          }`}
                        >
                          {m.result}
                        </span>
                      </td>
                      <td className="mono" style={{ fontSize: "0.82rem" }}>
                        {m.durationMs}ms
                      </td>
                      <td
                        style={{
                          fontSize: "0.82rem",
                          maxWidth: 250,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                        title={m.details}
                      >
                        {m.details || "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">
            <div className="empty-state-icon">🔄</div>
            <div className="empty-state-text">No recovery attempts recorded</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default RecoveryPanel;
