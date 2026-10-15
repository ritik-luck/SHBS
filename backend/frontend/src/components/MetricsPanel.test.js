import React from 'react';
import { render, screen } from '@testing-library/react';
import MetricsPanel from './MetricsPanel';

describe('MetricsPanel Component', () => {
  const mockSloData = {
    errorBudgetRemainingPct: '98.5%',
    burnRate: '1.50x',
    sloMet: true
  };

  test('renders metric cards correctly', () => {
    render(<MetricsPanel failures={3} health={{ score: 95 }} recoveryRate={100} sloData={mockSloData} />);
    
    // Check for failures
    expect(screen.getByText('3')).toBeInTheDocument();
    
    // Check for health score
    expect(screen.getByText('95')).toBeInTheDocument();
    
    // Check for recovery rate
    expect(screen.getByText('100')).toBeInTheDocument();
  });

  test('renders SLO section correctly', () => {
    render(<MetricsPanel failures={3} health={{ score: 95 }} recoveryRate={100} sloData={mockSloData} />);
    
    // Check SLO data
    expect(screen.getByText('98.5%')).toBeInTheDocument();
    expect(screen.getByText('1.50x')).toBeInTheDocument();
    expect(screen.getByText('YES')).toBeInTheDocument();
  });

  test('applies danger threshold styling to failures', () => {
    const { rerender } = render(<MetricsPanel failures={0} />);
    
    // Failures < 5 is not danger
    const valueEl = screen.getAllByText('0')[0];
    expect(valueEl).not.toHaveClass('metric-value--danger');
    
    // Rerender with 5 failures (dangerThreshold is 5)
    rerender(<MetricsPanel failures={5} />);
    // The value animates, so the text might still be '0' initially, but the danger class applies immediately
    const dangerValueEl = document.querySelector('.metric-value--danger');
    expect(dangerValueEl).toBeInTheDocument();
  });
});
