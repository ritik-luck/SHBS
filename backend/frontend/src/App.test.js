import { render, screen } from '@testing-library/react';
import App from './App';

test('renders SHBS dashboard title', () => {
  render(<App />);
  const titleElement = screen.getByText(/SHBS/i);
  expect(titleElement).toBeInTheDocument();
});
