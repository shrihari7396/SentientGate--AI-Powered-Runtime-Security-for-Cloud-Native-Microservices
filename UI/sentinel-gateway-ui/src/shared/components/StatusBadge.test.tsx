import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from './StatusBadge';

const RED = ['BLOCKED', 'DOWN', 'BLACKLISTED'];
const GREEN = ['ALLOWED', 'UP'];
const AMBER = ['MONITORING', 'DEGRADED', 'STARTING'];

describe('StatusBadge', () => {
  it.each(RED)('uses red styling for %s', (status) => {
    render(<StatusBadge status={status} />);
    const badge = screen.getByText(status);
    expect(badge).toHaveClass('bg-red/20', 'text-red');
    expect(badge).not.toHaveClass('text-teal', 'text-amber');
  });

  it.each(GREEN)('uses teal styling for %s', (status) => {
    render(<StatusBadge status={status} />);
    const badge = screen.getByText(status);
    expect(badge).toHaveClass('bg-teal/20', 'text-teal');
    expect(badge).not.toHaveClass('text-red', 'text-amber');
  });

  it.each(AMBER)('uses amber styling for %s', (status) => {
    render(<StatusBadge status={status} />);
    const badge = screen.getByText(status);
    expect(badge).toHaveClass('bg-amber/20', 'text-amber');
    expect(badge).not.toHaveClass('text-red', 'text-teal');
  });

  it('applies no color class to an unknown status', () => {
    render(<StatusBadge status="UNKNOWN" />);
    const badge = screen.getByText('UNKNOWN');
    expect(badge).not.toHaveClass('text-red', 'text-teal', 'text-amber');
  });

  it('always renders the base typography classes', () => {
    render(<StatusBadge status="UP" />);
    expect(screen.getByText('UP')).toHaveClass('font-mono', 'font-semibold', 'uppercase');
  });
});
