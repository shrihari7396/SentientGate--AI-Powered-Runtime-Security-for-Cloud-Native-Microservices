import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { TTLCountdown } from './TTLCountdown';

describe('TTLCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('formats the initial TTL as minutes and seconds', () => {
    render(<TTLCountdown ttlSeconds={125} />);
    expect(screen.getByText('2m 5s')).toBeInTheDocument();
  });

  it('counts down one second per tick', () => {
    render(<TTLCountdown ttlSeconds={125} />);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText('2m 4s')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(4000);
    });
    expect(screen.getByText('2m 0s')).toBeInTheDocument();
  });

  it('rolls over from minutes to seconds', () => {
    render(<TTLCountdown ttlSeconds={61} />);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText('1m 0s')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText('0m 59s')).toBeInTheDocument();
  });

  it('uses the expiring (red) style under one minute', () => {
    render(<TTLCountdown ttlSeconds={59} />);
    expect(screen.getByText('0m 59s')).toHaveClass('text-red');
  });

  it('uses the normal style at one minute or more', () => {
    render(<TTLCountdown ttlSeconds={60} />);
    const label = screen.getByText('1m 0s');
    expect(label).toHaveClass('text-text-primary');
    expect(label).not.toHaveClass('text-red');
  });

  it('renders the ALLOWED badge once the countdown reaches zero', () => {
    render(<TTLCountdown ttlSeconds={2} />);
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(screen.getByText('ALLOWED')).toBeInTheDocument();
    expect(screen.queryByText(/\dm \d+s/)).not.toBeInTheDocument();
  });

  it('renders the ALLOWED badge immediately when the TTL is zero', () => {
    render(<TTLCountdown ttlSeconds={0} />);
    expect(screen.getByText('ALLOWED')).toBeInTheDocument();
  });

  it('stays at zero and stops ticking after expiring', () => {
    render(<TTLCountdown ttlSeconds={1} />);
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText('ALLOWED')).toBeInTheDocument();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('resets the countdown when the ttlSeconds prop changes', () => {
    const { rerender } = render(<TTLCountdown ttlSeconds={90} />);
    act(() => {
      vi.advanceTimersByTime(10000);
    });
    expect(screen.getByText('1m 20s')).toBeInTheDocument();
    rerender(<TTLCountdown ttlSeconds={300} />);
    expect(screen.getByText('5m 0s')).toBeInTheDocument();
  });

  it('clears the interval on unmount', () => {
    const { unmount } = render(<TTLCountdown ttlSeconds={30} />);
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
