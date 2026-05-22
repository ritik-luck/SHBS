import React from "react";
import { Doughnut } from "react-chartjs-2";
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from "chart.js";

ChartJS.register(ArcElement, Tooltip, Legend);

const typeColors = {
  timeout: "#f43f5e",
  exception: "#f97316",
  database: "#a855f7",
  network: "#3b82f6",
  dependency: "#06b6d4",
  rate_limit: "#ec4899",
  unknown: "#64748b",
};

const severityColors = {
  LOW: "#06b6d4",
  MEDIUM: "#f59e0b",
  HIGH: "#f97316",
  CRITICAL: "#f43f5e",
};

const makeDoughnutData = (entries, colorMap) => {
  const labels = entries.map((e) => e[0] || "Unknown");
  const values = entries.map((e) => e[1] || 0);
  const colors = labels.map(
    (l) => colorMap[l.toLowerCase()] || colorMap[l] || "#64748b"
  );

  return {
    labels,
    datasets: [
      {
        data: values,
        backgroundColor: colors.map((c) => c + "33"),
        borderColor: colors,
        borderWidth: 2,
        hoverOffset: 6,
        spacing: 2,
      },
    ],
  };
};

const doughnutOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: "65%",
  plugins: {
    legend: {
      position: "bottom",
      labels: {
        color: "#94a3b8",
        font: { family: "Inter", size: 11, weight: "500" },
        padding: 16,
        usePointStyle: true,
        pointStyleWidth: 8,
      },
    },
    tooltip: {
      backgroundColor: "rgba(17, 24, 39, 0.95)",
      titleColor: "#f1f5f9",
      bodyColor: "#94a3b8",
      borderColor: "rgba(255,255,255,0.1)",
      borderWidth: 1,
      padding: 12,
      cornerRadius: 8,
      titleFont: { family: "Inter", weight: "600" },
      bodyFont: { family: "Inter" },
    },
  },
};

const FailureBreakdownCharts = ({ byType = [], bySeverity = [] }) => {
  return (
    <div className="grid-2">
      <div className="card">
        <div className="card-title">
          <span>🏷️</span> By Type
        </div>
        <div style={{ height: 240 }}>
          {byType.length > 0 ? (
            <Doughnut
              data={makeDoughnutData(byType, typeColors)}
              options={doughnutOptions}
            />
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">🏷️</div>
              <div className="empty-state-text">No failure type data</div>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <span>📊</span> By Severity
        </div>
        <div style={{ height: 240 }}>
          {bySeverity.length > 0 ? (
            <Doughnut
              data={makeDoughnutData(bySeverity, severityColors)}
              options={doughnutOptions}
            />
          ) : (
            <div className="empty-state">
              <div className="empty-state-icon">📊</div>
              <div className="empty-state-text">No severity data</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default FailureBreakdownCharts;
