import React, { useEffect, useState, useRef } from 'react';

const StatusBar = ({ circuitState, sloData, health }) => {
  const [uptime, setUptime] = useState('');
  const [sweepActive, setSweepActive] = useState(false);
  const prevState = useRef(circuitState);

  // Calculate uptime
  useEffect(() => {
    const start = Date.now();
    const interval = setInterval(() => {
      const diff = Date.now() - start;
      const h = Math.floor(diff / 3600000);
      const m = Math.floor((diff % 3600000) / 60000);
      setUptime(`${h}h ${m}m`);
    }, 60000);
    setUptime('0h 0m');
    return () => clearInterval(interval);
  }, []);

  // Trigger sweep animation on CLOSED→OPEN transition
  useEffect(() => {
    if (prevState.current !== 'OPEN' && circuitState === 'OPEN') {
      setSweepActive(true);
      setTimeout(() => setSweepActive(false), 500);
    }
    prevState.current = circuitState;
  }, [circuitState]);

  const stateLabel = circuitState || 'CLOSED';
  const dotClass = stateLabel === 'OPEN' ? 'state-dot--open'
    : stateLabel === 'HALF_OPEN' ? 'state-dot--half'
    : 'state-dot--closed';

  const sli = sloData?.currentSLI || '100.000%';
  const score = health?.score ?? 100;

  return (
    <div className="status-bar">
      <div className={`status-bar__sweep ${sweepActive ? 'status-bar__sweep--active' : ''}`} />

      <div className="status-bar__brand">
        <span style={{ color: 'var(--bone)' }}>▪ SHBS</span>
        <span className={dotClass} style={{ width: 6, height: 6, borderRadius: '50%', display: 'inline-block' }} />
        <span className="mono" style={{ color: stateLabel === 'OPEN' ? 'var(--signal)' : stateLabel === 'HALF_OPEN' ? 'var(--amber)' : 'var(--ash)', fontSize: 11 }}>
          {stateLabel}
        </span>
      </div>

      <div className="status-bar__metrics">
        <div className="status-bar__item">
          <span>SLI</span>
          <span className="value">{sli}</span>
        </div>
        <div className="status-bar__item">
          <span>HEALTH</span>
          <span className="value" style={{ color: score < 40 ? 'var(--signal)' : score < 70 ? 'var(--amber)' : 'var(--bone)' }}>{score}</span>
        </div>
        <div className="status-bar__item">
          <span>UPTIME</span>
          <span className="value">↑ {uptime}</span>
        </div>
      </div>
    </div>
  );
};

export default StatusBar;
