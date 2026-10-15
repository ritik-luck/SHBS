import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const getEntryClass = (actionType) => {
  if (!actionType) return 'audit-entry--system';
  const upper = actionType.toUpperCase();
  if (upper.includes('FAIL') || upper.includes('OPEN') || upper.includes('VIOLATION')) return 'audit-entry--failure';
  if (upper.includes('RECOVER') || upper.includes('CLOSED') || upper.includes('RESET') || upper.includes('SUCCESS')) return 'audit-entry--recovery';
  if (upper.includes('WARN') || upper.includes('HALF') || upper.includes('ATTEMPT')) return 'audit-entry--warning';
  return 'audit-entry--system';
};

const formatTime = (timestamp) => {
  if (!timestamp) return '--:--';
  const d = new Date(timestamp);
  return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

// Group entries by incident (consecutive failure→recovery sequences)
const groupByIncident = (logs) => {
  const groups = [];
  let current = null;

  for (const log of logs) {
    const cls = getEntryClass(log.actionType);
    if (cls === 'audit-entry--failure' && (!current || current.resolved)) {
      current = { id: log.id || groups.length, entries: [log], resolved: false };
      groups.push(current);
    } else if (current && !current.resolved) {
      current.entries.push(log);
      if (cls === 'audit-entry--recovery') {
        current.resolved = true;
      }
    } else {
      // Standalone entry
      groups.push({ id: log.id || groups.length, entries: [log], resolved: cls === 'audit-entry--recovery' });
    }
  }

  return groups;
};

const AuditLogPanel = ({ logs }) => {
  const groups = groupByIncident(logs);

  return (
    <div style={{ overflow: 'auto', maxHeight: '100%' }}>
      <AnimatePresence initial={false}>
        {groups.map((group) => (
          <motion.div
            key={group.id}
            className="audit-incident-group"
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: 'easeOut' }}
          >
            <div className="audit-incident-group__header">
              {group.resolved ? '✓ Resolved Incident' : group.entries[0]?.actionType || 'Event'}
            </div>
            {group.entries.map((entry, i) => (
              <div key={i} className={`audit-entry ${getEntryClass(entry.actionType)}`}>
                <span className="audit-entry__time">{formatTime(entry.timestamp)}</span>
                <span style={{ color: 'var(--bone)' }}>{entry.actionType}</span>
                {entry.details && (
                  <span className="ash" style={{ marginLeft: 8 }}>— {entry.details}</span>
                )}
              </div>
            ))}
          </motion.div>
        ))}
      </AnimatePresence>

      {(!logs || logs.length === 0) && (
        <div className="ash mono" style={{ textAlign: 'center', padding: 32, fontSize: 12 }}>
          No audit events recorded
        </div>
      )}
    </div>
  );
};

export default AuditLogPanel;
