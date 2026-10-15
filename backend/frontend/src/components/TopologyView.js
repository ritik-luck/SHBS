import React from 'react';
import { motion, useReducedMotion } from 'framer-motion';

const services = [
  { id: 'api', name: 'API Gateway', type: 'primary' },
  { id: 'backend', name: 'Backend Service', type: 'primary' },
  { id: 'db', name: 'MySQL Database', type: 'dependency' },
  { id: 'redis', name: 'Redis Cache', type: 'dependency' },
];

const TopologyView = ({ circuitState, health }) => {
  const prefersReduced = useReducedMotion();
  const isOpen = circuitState === 'OPEN';
  const isHalf = circuitState === 'HALF_OPEN';
  const score = health?.score ?? 100;

  const getNodeClass = (service) => {
    if (isOpen && service.type === 'primary') return 'topology__node--failed';
    if (isHalf && service.type === 'primary') return 'topology__node--degraded';
    if (score < 40) return 'topology__node--degraded';
    return 'topology__node--healthy';
  };

  const getConnectorClass = () => {
    if (isOpen) return 'topology__connector--broken';
    return '';
  };

  return (
    <div className="topology">
      {services.map((service, i) => (
        <React.Fragment key={service.id}>
          <motion.div
            className={`topology__node ${getNodeClass(service)}`}
            // Shake on OPEN for primary services
            animate={
              isOpen && service.type === 'primary' && !prefersReduced
                ? { x: [0, -2, 2, -2, 0] }
                : { x: 0 }
            }
            transition={{ duration: 0.3 }}
          >
            <span className={`state-dot ${
              isOpen && service.type === 'primary' ? 'state-dot--open' 
              : isHalf && service.type === 'primary' ? 'state-dot--half'
              : 'state-dot--closed'
            }`} />
            <span>{service.name}</span>
            <span className="ash mono" style={{ marginLeft: 'auto', fontSize: 11 }}>
              {service.type === 'primary' ? circuitState : 'CONNECTED'}
            </span>
          </motion.div>

          {/* Connector lines between services */}
          {i < services.length - 1 && (
            <motion.div
              className={`topology__connector ${getConnectorClass()}`}
              animate={
                isHalf && !prefersReduced
                  ? { opacity: [0.3, 0.8, 0.3] }
                  : { opacity: 1 }
              }
              transition={isHalf ? { duration: 2, repeat: Infinity } : {}}
            />
          )}
        </React.Fragment>
      ))}
    </div>
  );
};

export default TopologyView;
