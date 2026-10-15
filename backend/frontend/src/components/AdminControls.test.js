import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import AdminControls from './AdminControls';

describe('AdminControls Component', () => {
  test('renders all administrative buttons', () => {
    render(<AdminControls onSimulate={jest.fn()} onReset={jest.fn()} onClear={jest.fn()} />);
    
    expect(screen.getByText(/Inject Failure/i)).toBeInTheDocument();
    expect(screen.getByText(/Reset Circuit/i)).toBeInTheDocument();
    expect(screen.getByText(/Clear Metrics/i)).toBeInTheDocument();
  });

  test('calls onReset when Reset Circuit is clicked', () => {
    const onResetMock = jest.fn();
    render(<AdminControls onSimulate={jest.fn()} onReset={onResetMock} onClear={jest.fn()} />);
    
    const resetButton = screen.getByText(/Reset Circuit/i);
    fireEvent.click(resetButton);
    
    expect(onResetMock).toHaveBeenCalledTimes(1);
  });

  test('calls onSimulate with correct arguments when Inject Failure is clicked', () => {
    const onSimulateMock = jest.fn();
    render(<AdminControls onSimulate={onSimulateMock} onReset={jest.fn()} onClear={jest.fn()} />);
    
    const injectButton = screen.getByText(/Inject Failure/i);
    fireEvent.click(injectButton);
    
    expect(onSimulateMock).toHaveBeenCalledWith('timeout', 'api-service', 1);
  });

  test('calls onClear when Clear Metrics is clicked', () => {
    const onClearMock = jest.fn();
    render(<AdminControls onSimulate={jest.fn()} onReset={jest.fn()} onClear={onClearMock} />);
    
    const clearButton = screen.getByText(/Clear Metrics/i);
    fireEvent.click(clearButton);
    
    expect(onClearMock).toHaveBeenCalledTimes(1);
  });
});
