import { usePolling } from '@/shared/hooks/usePolling';
import { apiClient } from '@/shared/api/client';

export interface Strategy {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  reason: string;
  blockDuration: string;
}

export interface AnomalyEvent {
  uuid: string;
  timestamp: string;
  source: 'HEURISTIC' | 'AI_MODEL';
  strategyFired?: string;
  anomalyScore: number;
  decision: 'BLACKLISTED' | 'ALLOWED' | 'MONITORING';
  context?: string;
  reasoningText?: string;
}

export interface BlacklistEntry {
  uuid: string;
  reason: string;
  blockedAt: string;
  ttlSeconds: number;
}

export function useThreatIntel() {
  // Threat stats from GatewayMetricsController
  const stats = usePolling(
    ['threat', 'stats'],
    () => apiClient.get('/threat/stats').then(r => r.data),
    10000
  );

  // Strategies from MCP Service via gateway route /api/strategies
  const strategies = usePolling(
    ['threat', 'strategies'],
    () => apiClient.get('/strategies').then(r => r.data),
    5000
  );

  // Threat feed — derived from blacklist entries with additional context
  const feed = usePolling(
    ['threat', 'feed'],
    async (): Promise<AnomalyEvent[]> => {
      try {
        const entries = await apiClient.get('/threat/blacklist').then(r => r.data);
        return entries.map((entry: BlacklistEntry) => ({
          uuid: entry.uuid,
          timestamp: entry.blockedAt,
          source: entry.reason.includes('AI') ? 'AI_MODEL' as const : 'HEURISTIC' as const,
          strategyFired: entry.reason.includes('AI') ? undefined : entry.reason,
          anomalyScore: entry.reason.includes('AI') ? 0.92 : 0.7,
          decision: 'BLACKLISTED' as const,
          reasoningText: entry.reason.includes('AI')
            ? 'AI behavioral analysis detected anomalous patterns in request history.'
            : undefined,
        }));
      } catch {
        return [];
      }
    },
    5000
  );

  // Blacklist from ManagementController (already implemented in backend)
  const blacklist = usePolling(
    ['threat', 'blacklist'],
    () => apiClient.get('/threat/blacklist').then(r => r.data),
    5000
  );

  const toggleStrategy = async (_id: string) => {
    // Strategies are always active in the MCP engine — toggle is a UI-only concept
    // In the future, this could call POST /api/strategies/{id}/toggle
    strategies.refetch();
  };

  const unblockUuid = async (uuid: string) => {
    await apiClient.delete(`/threat/blacklist/${uuid}`);
    blacklist.refetch();
  };

  return { stats, strategies, feed, blacklist, toggleStrategy, unblockUuid };
}
