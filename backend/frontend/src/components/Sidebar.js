import React from "react";

const navItems = [
  { id: "dashboard", icon: "📊", label: "Dashboard" },
  { id: "failures", icon: "⚠️", label: "Failures" },
  { id: "recovery", icon: "🔄", label: "Recovery" },
  { id: "audit", icon: "📋", label: "Audit Logs" },
  { id: "alerts", icon: "🔔", label: "Alerts" },
  { id: "config", icon: "⚙️", label: "Configuration" },
  { id: "simulate", icon: "🧪", label: "Simulate" },
];

const Sidebar = ({ activePage, onNavigate, healthData, alertCount }) => {
  const healthStatus = healthData?.status || "HEALTHY";
  const healthScore = healthData?.score ?? 100;

  const dotClass =
    healthStatus === "HEALTHY"
      ? "healthy"
      : healthStatus === "DEGRADED"
      ? "degraded"
      : "unhealthy";

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-brand-logo">
          <div className="sidebar-brand-icon">🛡️</div>
          <div className="sidebar-brand-text">
            <h2>SHBS</h2>
            <span>Self-Healing System</span>
          </div>
        </div>
      </div>

      <nav className="sidebar-nav">
        <div className="nav-section-label">Monitor</div>
        {navItems.slice(0, 3).map((item) => (
          <button
            key={item.id}
            className={`nav-item ${activePage === item.id ? "active" : ""}`}
            onClick={() => onNavigate(item.id)}
          >
            <span className="nav-item-icon">{item.icon}</span>
            <span className="nav-item-label">{item.label}</span>
            {item.id === "alerts" && alertCount > 0 && (
              <span className="nav-item-badge">{alertCount}</span>
            )}
          </button>
        ))}

        <div className="nav-section-label">Admin</div>
        {navItems.slice(3).map((item) => (
          <button
            key={item.id}
            className={`nav-item ${activePage === item.id ? "active" : ""}`}
            onClick={() => onNavigate(item.id)}
          >
            <span className="nav-item-icon">{item.icon}</span>
            <span className="nav-item-label">{item.label}</span>
            {item.id === "alerts" && alertCount > 0 && (
              <span className="nav-item-badge">{alertCount}</span>
            )}
          </button>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="sidebar-status">
          <div className={`status-dot ${dotClass}`} />
          <span className="sidebar-status-label">System</span>
          <span
            className="sidebar-status-value"
            style={{
              color:
                dotClass === "healthy"
                  ? "var(--status-healthy)"
                  : dotClass === "degraded"
                  ? "var(--status-degraded)"
                  : "var(--status-unhealthy)",
            }}
          >
            {healthScore}%
          </span>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
