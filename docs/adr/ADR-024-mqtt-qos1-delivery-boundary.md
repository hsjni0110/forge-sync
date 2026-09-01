# ADR-024: MQTT QoS1 Delivery Boundary

- Status: Accepted
- Date: 2026-09-01

## Context

Replay produces Observation v2 envelopes and advances only after its publisher port succeeds. The
transport must expose MQTT's at-least-once behavior without changing source identity or claiming
that broker acceptance equals database processing.

## Decision

- Use MQTT 5 with QoS1 and no retained messages. The Observation v2 JSON is the payload without a
  transport wrapper.
- Use `forgesync/observations/{machineId}`. Contract version and message identity are MQTT 5
  properties, not topic segments.
- Limit payloads to 65,536 bytes at both boundaries. The selected NIST L2 run's largest generated
  Observation was measured at 834 bytes on 2026-09-01.
- Edge publisher success requires PUBACK and uses three total attempts with bounded 100 ms and
  500 ms delays. A publisher reuses its broker connection and resets it only after transport
  failure. Exhaustion leaves Step 5's replay position unchanged.
- Factory API uses manual acknowledgments. Permanent contract rejection is acknowledged;
  application failure is not acknowledged and causes reconnect. A valid message is acknowledged
  only after `ObservationIngress` returns. Acknowledgment failure is tracked separately from
  application handoff and also causes reconnect.
- Step 6 intentionally forwards duplicate deliveries. Step 7 owns Inbox persistence and the only
  declared effectively-once business boundary.

## Consequences

- QoS1 duplicates remain observable and testable; global exactly-once is not claimed.
- Broker PUBACK is a transport guarantee, not proof of consumer or database acceptance.
- MQTT consumer activation remains disabled until an `ObservationIngress` implementation is
  provided. Enabling it without that port fails application startup rather than dropping messages.
- Local integration uses anonymous Mosquitto only on a loopback-bound port. Remote authentication,
  TLS deployment, and durable replay-session recovery remain separate work.
