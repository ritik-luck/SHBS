import React, { useState } from 'react';

const AdminControls = ({ onSimulate, onReset, onClear }) => {
  const [simType, setSimType] = useState('timeout');
  const [loading, setLoading] = useState(null);

  const handleSimulate = async () => {
    setLoading('simulate');
    await onSimulate(simType, 'api-service', 1);
    setLoading(null);
  };

  const handleReset = async () => {
    setLoading('reset');
    await onReset();
    setLoading(null);
  };

  const handleClear = async () => {
    setLoading('clear');
    await onClear();
    setLoading(null);
  };

  return (
    <div className="admin-bar">
      <select
        value={simType}
        onChange={(e) => setSimType(e.target.value)}
        style={{
          background: 'var(--void)',
          border: '1px solid var(--iron)',
          color: 'var(--bone)',
          fontFamily: 'var(--font-mono)',
          fontSize: 11,
          padding: '4px 8px',
          borderRadius: 'var(--radius)',
          cursor: 'pointer',
        }}
      >
        <option value="timeout">timeout</option>
        <option value="database">database</option>
        <option value="network">network</option>
        <option value="rate_limit">rate_limit</option>
      </select>

      <button className="admin-btn admin-btn--danger" onClick={handleSimulate} disabled={loading === 'simulate'}>
        {loading === 'simulate' ? '...' : 'Inject Failure'}
      </button>

      <button className="admin-btn" onClick={handleReset} disabled={loading === 'reset'}>
        {loading === 'reset' ? '...' : 'Reset Circuit'}
      </button>

      <button className="admin-btn" onClick={handleClear} disabled={loading === 'clear'}>
        {loading === 'clear' ? '...' : 'Clear Metrics'}
      </button>

      <span className="ash mono" style={{ marginLeft: 'auto', fontSize: 10 }}>
        ADMIN CONTROLS — JWT AUTHENTICATED
      </span>
    </div>
  );
};

export default AdminControls;
