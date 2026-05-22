import React from "react";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Filler,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Filler, Tooltip, Legend);

const FailureTimelineChart = ({ data = [], period, onPeriodChange }) => {
  const labels = data.map((d) => {
    if (d[0]) {
      const date = new Date(d[0]);
      return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    }
    return "";
  });
  const values = data.map((d) => d[1] || 0);

  const chartData = {
    labels,
    datasets: [
      {
        label: "Failures",
        data: values,
        borderColor: "#f43f5e",
        backgroundColor: (ctx) => {
          const gradient = ctx.chart.ctx.createLinearGradient(0, 0, 0, 300);
          gradient.addColorStop(0, "rgba(244, 63, 94, 0.25)");
          gradient.addColorStop(1, "rgba(244, 63, 94, 0.0)");
          return gradient;
        },
        borderWidth: 2,
        pointRadius: 3,
        pointBackgroundColor: "#f43f5e",
        pointBorderColor: "#0a0e1a",
        pointBorderWidth: 2,
        pointHoverRadius: 6,
        fill: true,
        tension: 0.4,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: "index",
      intersect: false,
    },
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: "rgba(17, 24, 39, 0.95)",
        titleColor: "#f1f5f9",
        bodyColor: "#94a3b8",
        borderColor: "rgba(255,255,255,0.1)",
        borderWidth: 1,
        padding: 12,
        cornerRadius: 8,
        displayColors: false,
        titleFont: { family: "Inter", weight: "600" },
        bodyFont: { family: "Inter" },
      },
    },
    scales: {
      x: {
        grid: { color: "rgba(255,255,255,0.04)", drawBorder: false },
        ticks: {
          color: "#64748b",
          font: { size: 10, family: "Inter" },
          maxTicksLimit: 8,
        },
      },
      y: {
        beginAtZero: true,
        grid: { color: "rgba(255,255,255,0.04)", drawBorder: false },
        ticks: {
          color: "#64748b",
          font: { size: 10, family: "Inter" },
          stepSize: 1,
        },
      },
    },
  };

  return (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div className="card-title" style={{ marginBottom: 0 }}>
          <span>📈</span> Failure Timeline
        </div>
        <div className="period-tabs">
          {["30m", "1h", "24h"].map((p) => (
            <button
              key={p}
              className={`period-tab ${period === p ? "active" : ""}`}
              onClick={() => onPeriodChange(p)}
            >
              {p}
            </button>
          ))}
        </div>
      </div>
      <div style={{ height: 260 }}>
        {values.length > 0 ? (
          <Line data={chartData} options={chartOptions} />
        ) : (
          <div className="empty-state">
            <div className="empty-state-icon">📊</div>
            <div className="empty-state-text">No failure data for this period</div>
          </div>
        )}
      </div>
    </div>
  );
};

export default FailureTimelineChart;
