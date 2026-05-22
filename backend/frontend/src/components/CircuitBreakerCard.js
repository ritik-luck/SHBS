import React from "react";

const CircuitBreakerCard = ({ state = "CLOSED", onReset }) => {
  const stateConfig = {
    CLOSED: {
      color: "var(--status-healthy)",
      bg: "var(--status-healthy-bg)",
      border: "var(--status-healthy-border)",
      glow: "var(--shadow-glow-emerald)",
      label: "Closed",
      desc: "All systems operational. Requests flowing normally.",
      icon: "✅",
    },
    HALF_OPEN: {
      color: "var(--status-degraded)",
      bg: "var(--status-degraded-bg)",
      border: "var(--status-degraded-border)",
      glow: "var(--shadow-glow-amber)",
      label: "Half-Open",
      desc: "Testing recovery. Limited traffic allowed.",
      icon: "🔶",
    },
    OPEN: {
      color: "var(--status-unhealthy)",
      bg: "var(--status-unhealthy-bg)",
      border: "var(--status-unhealthy-border)",
      glow: "var(--shadow-glow-rose)",
      label: "Open",
      desc: "Circuit tripped. Requests blocked. Auto-recovery in progress.",
      icon: "🔴",
    },
  };

  const config = stateConfig[state] || stateConfig.CLOSED;

  return (
    <div
      className="card"
      style={{
        borderColor: config.border,
        boxShadow: state === "OPEN" ? config.glow : undefined,
        animation: state === "OPEN" ? "pulse-glow 2s infinite" : undefined,
      }}
    >
      <div className="card-title">
        <span>⚡</span> Circuit Breaker
      </div>

      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: "16px",
          padding: "12px 0",
        }}
      >
        {/* State visual */}
        <div
          style={{
            width: 72,
            height: 72,
            borderRadius: "50%",
            background: config.bg,
            border: `2px solid ${config.border}`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "1.8rem",
            boxShadow: `0 0 24px ${config.bg}`,
          }}
        >
          {config.icon}
        </div>

        <div style={{ textAlign: "center" }}>
          <div
            style={{
              fontSize: "1.25rem",
              fontWeight: 700,
              color: config.color,
              marginBottom: 4,
            }}
          >
            {config.label}
          </div>
          <div
            style={{
              fontSize: "0.78rem",
              color: "var(--text-muted)",
              maxWidth: 200,
              lineHeight: 1.5,
            }}
          >
            {config.desc}
          </div>
        </div>

        {/* Three-dot state indicator */}
        <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
          {["CLOSED", "HALF_OPEN", "OPEN"].map((s) => (
            <div
              key={s}
              style={{
                width: 10,
                height: 10,
                borderRadius: "50%",
                background:
                  state === s
                    ? stateConfig[s].color
                    : "rgba(255,255,255,0.08)",
                transition: "all 0.3s ease",
                boxShadow: state === s ? `0 0 8px ${stateConfig[s].bg}` : "none",
              }}
            />
          ))}
        </div>

        {state !== "CLOSED" && (
          <button
            className="btn btn-success btn-sm"
            onClick={onReset}
            style={{ marginTop: 4 }}
          >
            ↻ Reset Circuit
          </button>
        )}
      </div>
    </div>
  );
};

export default CircuitBreakerCard;
