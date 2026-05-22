import React from "react";

const ServiceHealthTable = ({ services = [] }) => {
  const getStatusBadge = (count) => {
    if (count === 0 || count === undefined) return <span className="badge badge-healthy">Healthy</span>;
    if (count <= 3) return <span className="badge badge-warning">Warning</span>;
    return <span className="badge badge-critical">Critical</span>;
  };

  return (
    <div className="card">
      <div className="card-title">
        <span>🖥️</span> Service Health
      </div>
      {services.length > 0 ? (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Service</th>
                <th>Failures</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {services.map((s, i) => (
                <tr key={i} style={{ animation: `fadeInUp 0.3s ease ${i * 50}ms both` }}>
                  <td style={{ color: "var(--text-primary)", fontWeight: 500 }}>
                    <span style={{ marginRight: 8 }}>🔹</span>
                    {s[0]}
                  </td>
                  <td>
                    <span className="mono" style={{ fontWeight: 600, color: s[1] > 3 ? "var(--accent-rose)" : "var(--text-secondary)" }}>
                      {s[1]}
                    </span>
                  </td>
                  <td>{getStatusBadge(s[1])}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">🖥️</div>
          <div className="empty-state-text">No service data available</div>
        </div>
      )}
    </div>
  );
};

export default ServiceHealthTable;
