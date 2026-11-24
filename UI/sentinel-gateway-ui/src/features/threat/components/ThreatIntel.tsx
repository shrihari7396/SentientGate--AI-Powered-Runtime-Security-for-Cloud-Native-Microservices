
import { motion, AnimatePresence } from 'framer-motion';
import { useThreatIntel, AnomalyEvent } from '../hooks/useThreatIntel';
import { CopyableUUID } from '@/shared/components/CopyableUUID';
import { TTLCountdown } from '@/shared/components/TTLCountdown';
import { StatusBadge } from '@/shared/components/StatusBadge';
import { Shield, Brain, Activity, Target } from 'lucide-react';
import clsx from 'clsx';
import { useEffect, useRef, useState, type FormEvent } from 'react';

export interface BlacklistEntry {
  uuid: string;
  reason: string;
  score: number;
  blockedAt: string;
  ttlSeconds: number;
}

export default function ThreatIntel() {
  const { stats, strategies, feed, blacklist, toggleStrategy, unblockUuid, blockUuid } = useThreatIntel();
  const [blockModalOpen, setBlockModalOpen] = useState(false);
  const [targetUuid, setTargetUuid] = useState('');
  const [reason, setReason] = useState('MANUAL_BLOCK');
  const [customReason, setCustomReason] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [isBlocking, setIsBlocking] = useState(false);
  const blockDialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = blockDialogRef.current;
    if (!dialog) return;

    if (blockModalOpen && !dialog.open) {
      dialog.showModal();
    } else if (!blockModalOpen && dialog.open) {
      dialog.close();
    }
  }, [blockModalOpen]);

  const submitBlock = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const resolvedReason = reason === 'CUSTOM' ? customReason.trim() : reason;
    if (!targetUuid.trim() || !resolvedReason) return;
    setIsBlocking(true);
    try {
      await blockUuid(targetUuid.trim(), resolvedReason, durationMinutes);
      setBlockModalOpen(false);
      setTargetUuid('');
      setReason('MANUAL_BLOCK');
      setCustomReason('');
      setDurationMinutes(60);
    } catch {
      // The hook reports the failure through a toast and keeps the dialog open.
    } finally {
      setIsBlocking(false);
    }
  };

  const sData = stats.data;
  const strategiesData = strategies.data || [];
  const feedData = feed.data || [];
  const blacklistData = blacklist.data || [];

  return (
    <motion.div 
      className="p-6 max-w-[1800px] mx-auto h-full flex flex-col"
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
    >
      <header className="mb-6 flex justify-between items-end">
        <div>
          <h1 className="text-2xl font-sans font-semibold text-teal flex items-center gap-3">
            <Brain size={28} /> AI Security Brain
          </h1>
          <p className="text-sm text-text-muted mt-1">Real-time threat evaluation and dynamic orchestration.</p>
        </div>
        
        {/* Global Stats Bar */}
        <div className="flex gap-6 bg-surface border border-border rounded px-6 py-3">
          <div className="flex flex-col">
            <span className="text-[10px] uppercase tracking-wider text-text-muted">Blocked Today</span>
            <span className="text-xl font-mono text-text-primary">{sData?.blockedToday ?? 0}</span>
          </div>
          <div className="w-px bg-border my-1"></div>
          <div className="flex flex-col">
            <span className="text-[10px] uppercase tracking-wider text-text-muted">AI Escalations</span>
            <span className="text-xl font-mono text-purple-400">{sData?.aiEscalations ?? 0}</span>
          </div>
          <div className="w-px bg-border my-1"></div>
          <div className="flex flex-col">
            <span className="text-[10px] uppercase tracking-wider text-text-muted">Avg Anomaly</span>
            <span className="text-xl font-mono text-amber">{sData?.avgAnomalyScore?.toFixed(2) ?? '0.00'}</span>
          </div>
        </div>
      </header>

      <div className="flex-1 grid grid-cols-1 lg:grid-cols-12 gap-6 min-h-0">
        
        {/* Left: Strategy Console (30% -> col-span-3) */}
        <div className="lg:col-span-3 bg-surface border border-border rounded flex flex-col overflow-hidden">
          <div className="p-4 border-b border-border bg-elevated/30 flex items-center justify-between">
            <h3 className="text-xs font-semibold tracking-widest uppercase text-text-muted flex items-center gap-2">
              <Target size={14} /> Strategy Console
            </h3>
            <span className="text-xs bg-elevated px-2 py-0.5 rounded text-text-muted font-mono">{strategiesData.filter((s: any) => s.enabled).length}/{strategiesData.length} Active</span>
          </div>
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {strategiesData.map((strategy: any) => (
              <div key={strategy.id} className="bg-elevated/50 border border-border/50 rounded p-3 transition-colors hover:border-border">
                <div className="flex items-start justify-between">
                  <div className="font-mono text-sm text-text-primary">{strategy.name}</div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" className="sr-only peer" checked={strategy.enabled} onChange={() => toggleStrategy(strategy.id)} />
                    <div className="w-8 h-4 bg-background peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-text-muted peer-checked:after:bg-black after:rounded-full after:h-3 after:w-3 after:transition-all peer-checked:bg-teal"></div>
                  </label>
                </div>
                <p className="text-xs text-text-muted mt-2 line-clamp-2">{strategy.description}</p>
                <div className="mt-3 flex justify-between items-end text-[10px] uppercase tracking-wider text-text-muted font-mono">
                  <span>Last fired: {strategy.lastFired}</span>
                  <span className={clsx(strategy.firesToday > 0 && "text-teal")}>{strategy.firesToday} today</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Center: Live Anomaly Feed (40% -> col-span-5) */}
        <div className="lg:col-span-5 bg-surface border border-border rounded flex flex-col overflow-hidden">
          <div className="p-4 border-b border-border bg-elevated/30 flex items-center gap-2">
            <Activity size={14} className="text-amber" />
            <h3 className="text-xs font-semibold tracking-widest uppercase text-text-muted">Live Anomaly Feed</h3>
            <div className="ml-auto w-2 h-2 rounded-full bg-teal animate-ping"></div>
          </div>
          <div className="flex flex-col gap-3 p-4 overflow-y-auto h-full">
            <AnimatePresence>
              {feedData.map((event: AnomalyEvent) => (
                <motion.div
                  key={`${event.uuid}-${event.timestamp}`}
                  initial={{ opacity: 0, x: -20, height: 0 }}
                  animate={{ opacity: 1, x: 0, height: 'auto' }}
                  className={clsx(
                    "border rounded p-3 overflow-hidden",
                    event.decision === 'BLACKLISTED' ? 'bg-red/5 border-red/20' : 'bg-elevated/30 border-border/50'
                  )}
                >
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex items-center gap-2">
                       <CopyableUUID uuid={event.uuid} />
                       <span className="text-[10px] text-text-muted font-mono">{new Date(event.timestamp).toLocaleTimeString()}</span>
                    </div>
                    <StatusBadge status={event.decision} />
                  </div>
                  
                  <div className="grid grid-cols-2 gap-2 mt-3 mb-2">
                    <div>
                      <span className="text-[10px] uppercase text-text-muted block mb-1">Source</span>
                      <span className={clsx("text-xs font-mono px-1.5 py-0.5 rounded", event.source === 'AI_MODEL' ? 'bg-purple-500/20 text-purple-400' : 'bg-amber/20 text-amber')}>
                        {event.source}
                      </span>
                    </div>
                    <div>
                      <span className="text-[10px] uppercase text-text-muted block mb-1">Trigger</span>
                      <span className="text-xs font-mono text-text-primary truncate block" title={event.strategyFired}>
                        {event.strategyFired || 'MCP Escalation'}
                      </span>
                    </div>
                  </div>

                  <div className="mt-3">
                    <div className="flex justify-between mb-1">
                      <span className="text-[10px] uppercase text-text-muted">Anomaly Score</span>
                      <span className="text-[10px] font-mono text-text-primary">{(event.anomalyScore * 100).toFixed(0)}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-background rounded-full overflow-hidden">
                      <div 
                        className={clsx("h-full", event.anomalyScore > 0.8 ? "bg-red" : event.anomalyScore > 0.5 ? "bg-amber" : "bg-teal")}
                        style={{ width: `${event.anomalyScore * 100}%` }}
                      ></div>
                    </div>
                  </div>

                  {event.reasoningText && (
                    <div className="mt-3 bg-background/50 rounded p-2 border border-border/50">
                      <span className="text-[10px] text-purple-400 font-mono flex items-center gap-1 mb-1">
                        <Brain size={10} /> AI Analysis
                      </span>
                      <p className="text-xs text-text-muted leading-relaxed font-sans">{event.reasoningText}</p>
                    </div>
                  )}

                </motion.div>
              ))}
            </AnimatePresence>
          </div>
        </div>

        {/* Right: Blacklist Manager (30% -> col-span-4) */}
        <div className="lg:col-span-4 bg-surface border border-border rounded flex flex-col overflow-hidden">
          <div className="p-4 border-b border-border bg-elevated/30 flex items-center gap-2">
            <Shield size={14} className="text-red" />
            <h3 className="text-xs font-semibold tracking-widest uppercase text-text-muted">Active Blacklist</h3>
            <span className="ml-auto text-xs font-mono text-text-muted">{blacklistData.length} records</span>
            <button type="button" onClick={() => setBlockModalOpen(true)} className="ml-3 px-3 py-1.5 rounded bg-red/10 border border-red/30 text-red hover:bg-red/20 text-xs font-semibold">Block Target</button>
          </div>
          <div className="flex-1 overflow-x-auto">
             <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-border/50 bg-background/50">
                  <th className="p-3 text-[10px] tracking-wider uppercase text-text-muted w-[35%]">Target</th>
                  <th className="p-3 text-[10px] tracking-wider uppercase text-text-muted w-[25%]">Reason</th>
                  <th className="p-3 text-[10px] tracking-wider uppercase text-text-muted w-[25%]">TTL</th>
                  <th className="p-3 text-[10px] tracking-wider uppercase text-text-muted w-[15%]"></th>
                </tr>
              </thead>
              <tbody className="text-xs font-mono text-text-primary divide-y divide-border/50">
                {blacklistData.map((entry: BlacklistEntry) => (
                  <motion.tr key={entry.uuid} layout className="hover:bg-elevated/50 transition-colors">
                    <td className="p-3"><CopyableUUID uuid={entry.uuid} maxChars={8} /></td>
                    <td className="p-3 text-text-muted truncate max-w-[80px]" title={entry.reason}>{entry.reason}</td>
                    <td className="p-3"><TTLCountdown ttlSeconds={entry.ttlSeconds} /></td>
                    <td className="p-3 text-right">
                      <button 
                        onClick={() => unblockUuid(entry.uuid)}
                        className="px-2 py-1 bg-elevated hover:bg-border rounded text-text-muted hover:text-text-primary transition-colors text-[10px] uppercase font-sans"
                      >
                        Unblock
                      </button>
                    </td>
                  </motion.tr>
                ))}
                {blacklistData.length === 0 && (
                  <tr>
                    <td colSpan={4} className="p-4 text-center text-text-muted font-sans text-sm py-10">
                      Blacklist is empty.
                    </td>
                  </tr>
                )}
              </tbody>
             </table>
          </div>
        </div>

      </div>
      <dialog
        ref={blockDialogRef}
        onClose={() => setBlockModalOpen(false)}
        aria-labelledby="block-target-title"
        className="w-[calc(100%-2rem)] max-w-md rounded-lg border border-border bg-surface p-6 text-text-primary shadow-xl backdrop:bg-black/60"
      >
        <div className="mb-5 flex items-center justify-between">
          <h2 id="block-target-title" className="text-lg font-semibold text-text-primary">Block target</h2>
          <button type="button" aria-label="Close dialog" onClick={() => setBlockModalOpen(false)} className="text-text-muted hover:text-text-primary">×</button>
        </div>
        <form onSubmit={submitBlock} className="space-y-4">
          <div className="space-y-1">
            <label htmlFor="block-target-uuid" className="block text-sm text-text-muted">UUID / Client Identifier</label>
            <input id="block-target-uuid" autoFocus={blockModalOpen} required value={targetUuid} onChange={(event) => setTargetUuid(event.target.value)} className="w-full rounded border border-border bg-background px-3 py-2 text-text-primary" placeholder="Enter UUID or client identifier" />
          </div>
          <div className="space-y-1">
            <label htmlFor="block-target-reason" className="block text-sm text-text-muted">Reason</label>
            <select id="block-target-reason" value={reason} onChange={(event) => setReason(event.target.value)} className="w-full rounded border border-border bg-background px-3 py-2 text-text-primary">
              <option value="MANUAL_BLOCK">Manual block</option>
              <option value="SUSPICIOUS_ACTIVITY">Suspicious activity</option>
              <option value="ABUSE">Abuse</option>
              <option value="CUSTOM">Custom reason</option>
            </select>
          </div>
          {reason === 'CUSTOM' && (
            <div className="space-y-1">
              <label htmlFor="block-target-custom-reason" className="block text-sm text-text-muted">Custom reason</label>
              <input id="block-target-custom-reason" required value={customReason} onChange={(event) => setCustomReason(event.target.value)} maxLength={200} className="w-full rounded border border-border bg-background px-3 py-2 text-text-primary" placeholder="Describe why this target is blocked" />
            </div>
          )}
          <div className="space-y-1">
            <label htmlFor="block-target-duration" className="block text-sm text-text-muted">Block duration</label>
            <select id="block-target-duration" value={durationMinutes} onChange={(event) => setDurationMinutes(Number(event.target.value))} className="w-full rounded border border-border bg-background px-3 py-2 text-text-primary">
              <option value={15}>15 minutes</option>
              <option value={60}>1 hour</option>
              <option value={1440}>24 hours</option>
            </select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={() => setBlockModalOpen(false)} className="rounded border border-border px-4 py-2 text-sm text-text-muted">Cancel</button>
            <button type="submit" disabled={isBlocking || !targetUuid.trim() || (reason === 'CUSTOM' && !customReason.trim())} className="rounded bg-red px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{isBlocking ? 'Blocking…' : 'Block target'}</button>
          </div>
        </form>
      </dialog>
    </motion.div>
  );
}
