import React, { useState } from "react";

const actionTypeColors = {
  CIRCUIT_OPENED: { color: "var(--accent-rose)", bg: "rgba(244,63,94,0.1)" },
  CIRCUIT_CLOSED: { color: "var(--accent-emerald)", bg: "rgba(16,185,129,0.1)" },
  CIRCUIT_HALF_OPEN: { color: "var(--accent-amber)", bg: "rgba(245,158,11,0.1)" },
  FAILURE_RECORDED: { color: "var(--accent-rose)", bg: "rgba(244,63,94,0.1)" },
  RECOVERY_ATTEMPTED: { color: "var(--accent-blue)", bg: "rgba(59,130,246,0.1)" },
  RECOVERY_SUCCESS: { color: "var(--accent-emerald)", bg: "rgba(16,185,129,0.1)" },
  RECOVERY_FAILED: { color: "var(--accent-rose)", bg: "rgba(244,63,94,0.1)" },
  FALLBACK_SERVED: { color: "var(--accent-purple)", bg: "rgba(168,85,247,0.1)" },
  ALERT_SENT: { color: "var(--accent-amber)", bg: "rgba(245,158,11,0.1)" },
  STRATEGY_DECIDED: { color: "var(--accent-indigo)", bg: "rgba(99,102,241,0.1)" },
  CONFIG_UPDATED: { color: "var(--accent-cyan)", bg: "rgba(6,182,212,0.1)" },
};

const formatTimestamp = (ts) => {
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

const AuditLogFeed = ({ logs = [], totalPages = 1, currentPage = 0, onPageChange, loading }) => {
  const [filterAction, setFilterAction] = useState("");

  const content = logs.content || logs;
  const filtered = filterAction
    ? content.filter((l) => l.actionType === filterAction)
    : content;

  const allActionTypes = [...new Set(content.map((l) => l.actionType))].sort();

  return (
    <div className="card">
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 16,
          flexWrap: "wrap",
          gap: 12,
        }}
      >
        <div className="card-title" style={{ marginBottom: 0 }}>
          <span>📋</span> Audit Logs
        </div>
        <select
          className="select"
          style={{ width: "auto", minWidth: 160 }}
          value={filterAction}
          onChange={(e) => setFilterAction(e.target.value)}
        >
          <option value="">All Actions</option>
          {allActionTypes.map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </select>
      </div>

      {filtered.length > 0 ? (
        <>
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Action</th>
                  <th>Details</th>
                  <th>Triggered By</th>
                  <th>Result</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((log, i) => {
                  const typeStyle = actionTypeColors[log.actionType] || {
                    color: "var(--text-muted)",
                    bg: "rgba(255,255,255,0.05)",
                  };
                  return (
                    <tr key={log.id || i}>
                      <td className="mono" style={{ fontSize: "0.78rem", whiteSpace: "nowrap" }}>
                        {formatTimestamp(log.timestamp)}
                      </td>
                      <td>
                        <span
                          className="badge"
                          style={{
                            color: typeStyle.color,
                            background: typeStyle.bg,
                            border: `1px solid ${typeStyle.color}22`,
                            fontSize: "0.68rem",
                          }}
                        >
                          {log.actionType}
                        </span>
                      </td>
                      <td
                        style={{
                          maxWidth: 280,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                          fontSize: "0.82rem",
                        }}
                        title={log.details}
                      >
                        {log.details}
                      </td>
                      <td>
                        <span
                          className="badge"
                          style={{
                            background:
                              log.triggeredBy === "SYSTEM"
                                ? "rgba(99,102,241,0.1)"
                                : "rgba(245,158,11,0.1)",
                            color:
                              log.triggeredBy === "SYSTEM"
                                ? "var(--accent-indigo)"
                                : "var(--accent-amber)",
                            border: "1px solid transparent",
                          }}
                        >
                          {log.triggeredBy}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            log.result === "SUCCESS"
                              ? "badge-success"
                              : "badge-failure"
                          }`}
                        >
                          {log.result}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div
              style={{
                display: "flex",
                justifyContent: "center",
                gap: 8,
                marginTop: 16,
              }}
            >
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => onPageChange(currentPage - 1)}
                disabled={currentPage === 0}
                style={{ opacity: currentPage === 0 ? 0.4 : 1 }}
              >
                ← Prev
              </button>
              <span
                style={{
                  display: "flex",
                  alignItems: "center",
                  fontSize: "0.8rem",
                  color: "var(--text-muted)",
                  fontFamily: "var(--font-mono)",
                }}
              >
                {currentPage + 1} / {totalPages}
              </span>
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => onPageChange(currentPage + 1)}
                disabled={currentPage >= totalPages - 1}
                style={{ opacity: currentPage >= totalPages - 1 ? 0.4 : 1 }}
              >
                Next →
              </button>
            </div>
          )}
        </>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">📋</div>
          <div className="empty-state-text">
            {loading ? "Loading audit logs..." : "No audit logs found"}
          </div>
        </div>
      )}
    </div>
  );
};

export default AuditLogFeed;
