import React, { useEffect, useRef } from 'react';
import { motion, useReducedMotion, AnimatePresence } from 'framer-motion';

const CircuitBreakerDial = ({ state, onReset }) => {
  const prefersReduced = useReducedMotion();
  const prevState = useRef(state);
  const isTransition = prevState.current !== state;

  useEffect(() => {
    prevState.current = state;
  }, [state]);

  const ringClass = state === 'OPEN' ? 'cb-dial__ring--open'
    : state === 'HALF_OPEN' ? 'cb-dial__ring--half'
    : 'cb-dial__ring--closed';

  const stateColor = state === 'OPEN' ? 'var(--signal)'
    : state === 'HALF_OPEN' ? 'var(--amber)'
    : 'var(--green)';

  // Animation variants
  const getAnimation = () => {
    if (prefersReduced || !isTransition) return {};

    if (state === 'OPEN') {
      // CLOSED→OPEN: abrupt shake
      return {
        animate: {
          x: [0, -3, 3, -3, 3, -2, 2, 0],
          transition: { duration: 0.3 }
        }
      };
    }
    if (state === 'HALF_OPEN') {
      // OPEN→HALF_OPEN: gentle pulse
      return {
        animate: {
          scale: [1, 1.02, 1, 1.02, 1],
          transition: { duration: 2, repeat: Infinity, ease: 'easeInOut' }
        }
      };
    }
    // HALF_OPEN→CLOSED: settle
    return {
      animate: {
        scale: [1.02, 1],
        transition: { duration: 0.6, ease: 'easeOut' }
      }
    };
  };

  const anim = getAnimation();

  return (
    <div className="card">
      <div className="cb-dial">
        <span className="cb-dial__label">Circuit Breaker</span>

        <motion.div
          className={`cb-dial__ring ${ringClass}`}
          {...anim}
        >
          {/* Flash overlay for OPEN */}
          <AnimatePresence>
            {state === 'OPEN' && isTransition && !prefersReduced && (
              <motion.div
                key="flash"
                initial={{ opacity: 0.6 }}
                animate={{ opacity: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                style={{
                  position: 'absolute',
                  inset: -4,
                  borderRadius: '50%',
                  background: 'var(--signal)',
                  pointerEvents: 'none',
                }}
              />
            )}
          </AnimatePresence>

          <span className="cb-dial__state mono" style={{ color: stateColor }}>
            {state === 'HALF_OPEN' ? 'HALF' : state}
          </span>
        </motion.div>

        <span style={{ fontSize: 11, color: 'var(--ash)' }}>
          {state === 'CLOSED' && 'All traffic allowed'}
          {state === 'OPEN' && 'Traffic blocked'}
          {state === 'HALF_OPEN' && 'Testing recovery...'}
        </span>

        {state !== 'CLOSED' && (
          <button className="admin-btn" onClick={onReset} style={{ marginTop: 8 }}>
            Reset Circuit
          </button>
        )}
      </div>
    </div>
  );
};

export default CircuitBreakerDial;
