import React, { useState, useEffect, useRef } from 'react';

const AnimatedValue = ({ value, danger = false }) => {
  const [display, setDisplay] = useState(value);
  const prev = useRef(value);
  const frameRef = useRef(null);

  useEffect(() => {
    const from = prev.current;
    const to = typeof value === 'number' ? value : parseFloat(value) || 0;
    const fromNum = typeof from === 'number' ? from : parseFloat(from) || 0;

    if (fromNum === to) { setDisplay(to); return; }

    const duration = 400;
    const start = performance.now();

    const step = (now) => {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      const current = fromNum + (to - fromNum) * progress;
      setDisplay(Number.isInteger(to) ? Math.round(current) : parseFloat(current.toFixed(1)));
      if (progress < 1) {
        frameRef.current = requestAnimationFrame(step);
      }
    };

    frameRef.current = requestAnimationFrame(step);
    prev.current = to;

    return () => { if (frameRef.current) cancelAnimationFrame(frameRef.current); };
  }, [value]);

  return (
    <span className={`metric-value mono ${danger ? 'metric-value--danger' : ''}`}>
      {display}
    </span>
  );
};

const MetricCard = ({ label, value, unit = '', dangerThreshold }) => {
  const [flash, setFlash] = useState(false);
  const prevVal = useRef(value);
  const isDanger = dangerThreshold !== undefined && typeof value === 'number' && value >= dangerThreshold;

  useEffect(() => {
    if (prevVal.current !== value && isDanger) {
      setFlash(true);
      const t = setTimeout(() => setFlash(false), 300);
      prevVal.current = value;
      return () => clearTimeout(t);
    }
    prevVal.current = value;
  }, [value, isDanger]);

  return (
    <div className={`metric-card ${flash ? 'metric-card--flash' : ''}`}>
      <AnimatedValue value={value} danger={isDanger} />
      {unit && <span className="mono ash" style={{ fontSize: 14, marginLeft: 4 }}>{unit}</span>}
      <div className="metric-label">{label}</div>
    </div>
  );
};

const MetricsPanel = ({ failures, health, recoveryRate, sloData }) => {
  return (
    <div>
      <MetricCard label="Failures" value={failures} dangerThreshold={5} />
      <MetricCard label="Health Score" value={health?.score ?? 100} dangerThreshold={undefined} />
      <MetricCard label="Recovery Rate" value={recoveryRate || 0} unit="%" />

      {/* SLO Section */}
      <div className="card" style={{ marginTop: 4 }}>
        <div className="panel__header" style={{ marginBottom: 12 }}>SLO STATUS</div>
        <div className="slo-gauge">
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <span className="ash" style={{ fontSize: 11 }}>Error Budget</span>
            <span className="mono" style={{ fontSize: 12 }}>
              {sloData?.errorBudgetRemainingPct || '100.0%'}
            </span>
          </div>
          <div className="slo-bar">
            <div
              className="slo-bar__fill"
              style={{
                width: sloData?.errorBudgetRemainingPct || '100%',
                background: parseFloat(sloData?.errorBudgetRemainingPct) < 20 ? 'var(--signal)'
                  : parseFloat(sloData?.errorBudgetRemainingPct) < 50 ? 'var(--amber)'
                  : 'var(--green)',
              }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
            <span className="ash" style={{ fontSize: 11 }}>Burn Rate</span>
            <span className="mono" style={{
              fontSize: 12,
              color: parseFloat(sloData?.burnRate) >= 5 ? 'var(--signal)'
                : parseFloat(sloData?.burnRate) >= 2 ? 'var(--amber)' : 'var(--bone)'
            }}>
              {sloData?.burnRate || '0.00x'}
            </span>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 8 }}>
            <span className="ash" style={{ fontSize: 11 }}>SLO Met</span>
            <span className="mono" style={{
              fontSize: 12,
              color: sloData?.sloMet === false ? 'var(--signal)' : 'var(--green)'
            }}>
              {sloData?.sloMet === false ? 'NO' : 'YES'}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MetricsPanel;
