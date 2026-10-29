---
name: Threat Strategy Proposal
about: Propose a new runtime security heuristic or threat detection strategy
title: "[Threat Strategy]: "
labels: ["enhancement", "security", "area/mcp"]
assignees: ""

---

## Strategy Name
(e.g., `SsrfDetectionStrategy`, `LdapInjectionStrategy`, `GeoAnomalyStrategy`)

## Threat Classification
- **Attack Vector:**
- **CWE / OWASP Category:**
- **Target Signals:** (e.g., URI path, query parameters, response status, header signatures)

## Proposed Heuristic Logic
Explain the signals, conditions, thresholds, or scoring logic the strategy would use.
- What patterns trigger a match?
- How does it minimize false positives?
- What legitimate traffic could match?
- What bypasses or limitations should be considered?

## Required Context and Data
- **Request/event fields required:**
- **Historical context required (if any):**
- **Dependencies on LoggingService, Kafka, gRPC, Redis, or other components:**

## Recommended Enforcement Action
- **Expected decision or response:**
- **Suggested block duration / TTL (if applicable):**
- **Reason code:**
- **Severity level:** (LOW / MEDIUM / HIGH / CRITICAL)

## Validation Plan
List representative positive and negative examples, test scenarios, or datasets that could validate the heuristic.

## Architectural Impact
Identify affected services, performance considerations, and configuration or documentation changes.

## Additional Context
Link relevant references or related issues. Do not include live attack targets, secrets, or sensitive production data.
