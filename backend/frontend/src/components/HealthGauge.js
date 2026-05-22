import React, { useEffect, useRef } from "react";

const HealthGauge = ({ score = 100, status = "HEALTHY" }) => {
  const canvasRef = useRef(null);

  const getColor = (s) => {
    if (s >= 80) return { main: "#10b981", glow: "rgba(16,185,129,0.3)" };
    if (s >= 40) return { main: "#f59e0b", glow: "rgba(245,158,11,0.3)" };
    return { main: "#f43f5e", glow: "rgba(244,63,94,0.3)" };
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    const dpr = window.devicePixelRatio || 1;
    const size = 200;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    canvas.style.width = size + "px";
    canvas.style.height = size + "px";
    ctx.scale(dpr, dpr);

    const cx = size / 2;
    const cy = size / 2;
    const radius = 80;
    const lineWidth = 10;
    const startAngle = 0.75 * Math.PI;
    const endAngle = 2.25 * Math.PI;
    const totalAngle = endAngle - startAngle;

    let animatedScore = 0;
    const target = Math.min(Math.max(score, 0), 100);
    const { main, glow } = getColor(target);

    const animate = () => {
      animatedScore += (target - animatedScore) * 0.08;
      if (Math.abs(animatedScore - target) < 0.5) animatedScore = target;

      ctx.clearRect(0, 0, size, size);

      // Track background
      ctx.beginPath();
      ctx.arc(cx, cy, radius, startAngle, endAngle);
      ctx.strokeStyle = "rgba(255,255,255,0.06)";
      ctx.lineWidth = lineWidth;
      ctx.lineCap = "round";
      ctx.stroke();

      // Value arc
      const valueAngle = startAngle + (animatedScore / 100) * totalAngle;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, startAngle, valueAngle);
      ctx.strokeStyle = main;
      ctx.lineWidth = lineWidth;
      ctx.lineCap = "round";
      ctx.shadowColor = glow;
      ctx.shadowBlur = 16;
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Score text
      ctx.fillStyle = "#f1f5f9";
      ctx.font = "bold 44px Inter, sans-serif";
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(Math.round(animatedScore), cx, cy - 6);

      // Label
      ctx.fillStyle = "#64748b";
      ctx.font = "600 11px Inter, sans-serif";
      ctx.fillText("HEALTH SCORE", cx, cy + 28);

      if (animatedScore !== target) {
        requestAnimationFrame(animate);
      }
    };

    animate();
  }, [score]);

  const statusClass =
    status === "HEALTHY"
      ? "badge-healthy"
      : status === "DEGRADED"
      ? "badge-degraded"
      : "badge-unhealthy";

  return (
    <div className="card" style={{ textAlign: "center" }}>
      <div className="card-title">
        <span>💚</span> System Health
      </div>
      <canvas
        ref={canvasRef}
        style={{ display: "block", margin: "0 auto 12px" }}
      />
      <span className={`badge ${statusClass}`}>{status}</span>
    </div>
  );
};

export default HealthGauge;
