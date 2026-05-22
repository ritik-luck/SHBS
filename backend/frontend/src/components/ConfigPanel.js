import React, { useState } from "react";

const ConfigPanel = ({ configs = [], onUpdate }) => {
  const [editingKey, setEditingKey] = useState(null);
  const [editValue, setEditValue] = useState("");
  const [saving, setSaving] = useState(false);

  const handleEdit = (config) => {
    setEditingKey(config.configKey);
    setEditValue(config.configValue);
  };

  const handleSave = async () => {
    if (!editingKey) return;
    setSaving(true);
    try {
      await onUpdate(editingKey, editValue);
      setEditingKey(null);
    } catch (e) {
      console.error("Config save failed:", e);
    }
    setSaving(false);
  };

  const handleCancel = () => {
    setEditingKey(null);
    setEditValue("");
  };

  const formatDate = (ts) => {
    if (!ts) return "—";
    return new Date(ts).toLocaleString([], {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  return (
    <div className="card">
      <div className="card-title">
        <span>⚙️</span> System Configuration
      </div>

      {configs.length > 0 ? (
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>Key</th>
                <th>Value</th>
                <th>Description</th>
                <th>Updated</th>
                <th style={{ width: 100 }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {configs.map((config, i) => (
                <tr key={config.configKey || i}>
                  <td>
                    <code
                      className="mono"
                      style={{
                        fontSize: "0.8rem",
                        color: "var(--accent-cyan)",
                        background: "rgba(6,182,212,0.08)",
                        padding: "2px 8px",
                        borderRadius: "var(--radius-sm)",
                      }}
                    >
                      {config.configKey}
                    </code>
                  </td>
                  <td>
                    {editingKey === config.configKey ? (
                      <input
                        className="input"
                        value={editValue}
                        onChange={(e) => setEditValue(e.target.value)}
                        style={{ padding: "6px 10px", fontSize: "0.82rem" }}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === "Enter") handleSave();
                          if (e.key === "Escape") handleCancel();
                        }}
                      />
                    ) : (
                      <span
                        className="mono"
                        style={{
                          fontSize: "0.85rem",
                          fontWeight: 600,
                          color: "var(--text-primary)",
                        }}
                      >
                        {config.configValue}
                      </span>
                    )}
                  </td>
                  <td style={{ fontSize: "0.8rem", maxWidth: 200 }}>
                    {config.description || "—"}
                  </td>
                  <td
                    className="mono"
                    style={{ fontSize: "0.75rem", whiteSpace: "nowrap" }}
                  >
                    {formatDate(config.updatedAt)}
                  </td>
                  <td>
                    {editingKey === config.configKey ? (
                      <div style={{ display: "flex", gap: 6 }}>
                        <button
                          className="btn btn-success btn-sm"
                          onClick={handleSave}
                          disabled={saving}
                          style={{ padding: "4px 10px", fontSize: "0.75rem" }}
                        >
                          {saving ? "..." : "Save"}
                        </button>
                        <button
                          className="btn btn-ghost btn-sm"
                          onClick={handleCancel}
                          style={{ padding: "4px 10px", fontSize: "0.75rem" }}
                        >
                          ✕
                        </button>
                      </div>
                    ) : (
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => handleEdit(config)}
                        style={{ padding: "4px 10px", fontSize: "0.75rem" }}
                      >
                        ✏️ Edit
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-state-icon">⚙️</div>
          <div className="empty-state-text">No configuration entries found</div>
        </div>
      )}
    </div>
  );
};

export default ConfigPanel;
