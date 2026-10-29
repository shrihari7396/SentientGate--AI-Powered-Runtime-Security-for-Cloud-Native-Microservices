import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { CopyableUUID } from './CopyableUUID';

const UUID = '123e4567-e89b-12d3-a456-426614174000';

describe('CopyableUUID', () => {
  const writeText = vi.fn<(text: string) => Promise<void>>();

  beforeEach(() => {
    vi.useFakeTimers();
    writeText.mockReset().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('truncates long UUIDs to 12 characters by default and keeps the full value in the title', () => {
    render(<CopyableUUID uuid={UUID} />);
    const label = screen.getByText(`${UUID.slice(0, 12)}…`);
    expect(label).toHaveAttribute('title', UUID);
  });

  it('respects a custom maxChars', () => {
    render(<CopyableUUID uuid={UUID} maxChars={8} />);
    expect(screen.getByText(`${UUID.slice(0, 8)}…`)).toBeInTheDocument();
  });

  it('shows the whole value when it is not longer than maxChars', () => {
    render(<CopyableUUID uuid="short-id" />);
    expect(screen.getByText('short-id')).toBeInTheDocument();
  });

  it('copies the full UUID (not the truncated one) to the clipboard on click', () => {
    render(<CopyableUUID uuid={UUID} />);
    fireEvent.click(screen.getByTitle('Copy UUID'));
    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText).toHaveBeenCalledWith(UUID);
  });

  it('shows the copied check icon and reverts to the copy icon after 1.5s', () => {
    const { container } = render(<CopyableUUID uuid={UUID} />);
    expect(container.querySelector('.lucide-copy')).toBeInTheDocument();
    expect(container.querySelector('.lucide-check')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTitle('Copy UUID'));
    expect(container.querySelector('.lucide-check')).toBeInTheDocument();
    expect(container.querySelector('.lucide-copy')).not.toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(1499);
    });
    expect(container.querySelector('.lucide-check')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(container.querySelector('.lucide-copy')).toBeInTheDocument();
    expect(container.querySelector('.lucide-check')).not.toBeInTheDocument();
  });

  it('does not propagate the copy click to parent handlers', () => {
    const onRowClick = vi.fn();
    render(
      <div onClick={onRowClick}>
        <CopyableUUID uuid={UUID} />
      </div>,
    );
    fireEvent.click(screen.getByTitle('Copy UUID'));
    expect(onRowClick).not.toHaveBeenCalled();
  });

  it('dispatches open-user-context with the full UUID when the label is clicked', () => {
    const listener = vi.fn<(event: Event) => void>();
    window.addEventListener('open-user-context', listener);
    render(<CopyableUUID uuid={UUID} />);

    fireEvent.click(screen.getByText(`${UUID.slice(0, 12)}…`));

    window.removeEventListener('open-user-context', listener);
    expect(listener).toHaveBeenCalledTimes(1);
    expect((listener.mock.calls[0][0] as CustomEvent<string>).detail).toBe(UUID);
    expect(writeText).not.toHaveBeenCalled();
  });
});
