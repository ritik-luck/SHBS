import React from "react";

const formatTime = (ts) => {
  if (!ts) return "—";
  const d = new Date(ts);
  return d.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
};

const levelConfig = {
  INFO: { icon: "ℹ️", badge: "badge-info", color: "var(--accent-blue)" },
  WARNING: { icon: "⚠️", badge: "badge-warning", color: "var(--accent-amber)" },
  CRITICAL: { icon: "🚨", badge: "badge-critical", color: "var(--accent-rose)" },
};

const AlertsPanel = ({ alerts = [] }) => {
  const sorted = [...alerts].sort(
    (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
  );

  return (
    <div className="card">
      <div className="card-title">
        <span>🔔</span> Alert History
      </div>

      {sorted.length > 0 ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 500, overflowY: "auto" }}>
          {sorted.map((alert, i) => {
            const config = levelConfig[alert.level] || levelConfig.INFO;
            return (
              <div
                key={alert.id || i}
                className="animate-fade-in-up"
                style={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: 12,
                  padding: "14px 16px",
                  borderRadius: "var(--radius-md)",
                  background: "var(--bg-glass)",
                  border: "1px solid var(--border-subtle)",
                  animationDelay: `${i * 40}ms`,
                  transition: "background var(--transition-fast)",
                }}
              >
                <span style={{ fontSize: "1.2rem", flexShrink: 0, marginTop: 2 }}>
                  {config.icon}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 8,
                      marginBottom: 4,
                      flexWrap: "wrap",
                    }}
                  >
                    <span className={`badge ${config.badge}`}>{alert.level}</span>
                    <span
                      className="badge"
                      style={{
                        background: "rgba(99,102,241,0.1)",
                        color: "var(--accent-indigo)",
                        border: "1px solid transparent",
                      }}
                    >
                      {alert.source}
                    </span>
                    <span
                      className="mono"
                      style={{
                        fontSize: "0.72rem",
                        color: "var(--text-muted)",
                        marginLeft: "auto",
                      }}
                    >
                      {formatTime(alert.timestamp)}
                    </span>
                  </div>
                  <div
                    style={{
                      fontSize: "0.85rem",
                      color: "var(--text-secondary)",
                      lineHeight: 1.5,
                    }}
                  >
                    {alert.message}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">🔔</div>
          <div className="empty-state-text">No alerts recorded</div>
        </div>
      )}
    </div>
  );
};

export default AlertsPanel;
