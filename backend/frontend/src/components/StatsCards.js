import React from "react";

const StatsCards = ({ failures = 0, recoveryRate = 0, alertCount = 0, circuitState = "CLOSED" }) => {
  const cards = [
    {
      title: "Total Failures",
      value: failures,
      icon: "💥",
      iconBg: "rgba(244, 63, 94, 0.12)",
      color: "var(--accent-rose)",
      trend: failures > 5 ? "up" : failures === 0 ? "neutral" : "down",
      trendLabel: failures > 5 ? "High" : failures === 0 ? "None" : "Low",
    },
    {
      title: "Recovery Rate",
      value: `${Math.round(recoveryRate)}%`,
      icon: "🔄",
      iconBg: "rgba(16, 185, 129, 0.12)",
      color: "var(--accent-emerald)",
      trend: recoveryRate >= 80 ? "down" : recoveryRate >= 50 ? "neutral" : "up",
      trendLabel: recoveryRate >= 80 ? "Excellent" : recoveryRate >= 50 ? "Fair" : "Poor",
    },
    {
      title: "Active Alerts",
      value: alertCount,
      icon: "🔔",
      iconBg: "rgba(245, 158, 11, 0.12)",
      color: "var(--accent-amber)",
      trend: alertCount > 3 ? "up" : alertCount === 0 ? "neutral" : "down",
      trendLabel: alertCount > 3 ? "Critical" : alertCount === 0 ? "Clear" : "Monitoring",
    },
    {
      title: "Circuit Breaker",
      value: circuitState,
      icon: "⚡",
      iconBg:
        circuitState === "CLOSED"
          ? "rgba(16, 185, 129, 0.12)"
          : circuitState === "HALF_OPEN"
          ? "rgba(245, 158, 11, 0.12)"
          : "rgba(244, 63, 94, 0.12)",
      color:
        circuitState === "CLOSED"
          ? "var(--status-healthy)"
          : circuitState === "HALF_OPEN"
          ? "var(--status-degraded)"
          : "var(--status-unhealthy)",
      trend:
        circuitState === "CLOSED"
          ? "down"
          : circuitState === "HALF_OPEN"
          ? "neutral"
          : "up",
      trendLabel:
        circuitState === "CLOSED"
          ? "Normal"
          : circuitState === "HALF_OPEN"
          ? "Testing"
          : "Tripped",
    },
  ];

  return (
    <div className="stats-row">
      {cards.map((card, i) => (
        <div
          key={i}
          className="card stat-card animate-fade-in-up"
          style={{ animationDelay: `${i * 80}ms` }}
        >
          <div className="stat-icon" style={{ background: card.iconBg }}>
            {card.icon}
          </div>
          <div className="card-title">{card.title}</div>
          <div className="card-value" style={{ color: card.color }}>
            {card.value}
          </div>
          <div className={`stat-trend ${card.trend}`}>
            {card.trend === "up" ? "▲" : card.trend === "down" ? "▼" : "●"}{" "}
            {card.trendLabel}
          </div>
        </div>
      ))}
    </div>
  );
};

export default StatsCards;
