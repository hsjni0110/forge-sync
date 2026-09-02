# MQTT Observation Delivery Contract

This contract transports an unchanged Observation Envelope v2 JSON document over MQTT 5. It does
not wrap the Observation or claim exactly-once delivery.

## Packet contract

| Field | Required value |
|---|---|
| Topic | `forgesync/observations/{machineId}` |
| Topic `machineId` | `[A-Za-z0-9._-]{1,64}`, equal to payload `machineId` |
| QoS | `1` |
| Retained | `false` |
| Maximum payload | 65,536 bytes |
| Content Type | `application/vnd.forgesync.observation+json` |
| Payload Format Indicator | UTF-8 (`1`) |
| User Property `schema-version` | exactly one `2.0.0`, equal to payload `schemaVersion` |
| User Property `message-key` | exactly one `{replaySessionId}:{sourceEventKey}` |

MQTT-delivered Observations require the complete `replay` group. The base Observation contract
continues to allow the group to be absent before replay.

## Acknowledgment and failures

- Publisher success means the broker completed the QoS1 PUBACK handshake. It does not mean that a
  consumer or database accepted the Observation.
- The publisher reuses its broker connection and makes at most three attempts per message. It waits
  100 ms and 500 ms before the two retries.
- Invalid contract or oversized payload errors are permanent and are not retried.
- The API acknowledges a valid packet only after its application ingress port returns successfully.
- The API acknowledges rejected permanent packets so malformed poison messages are not redelivered
  forever. Application and acknowledgment failures remain unacknowledged and force reconnect so
  the broker may deliver them again. They are reported as distinct failure causes.
- Duplicate delivery is expected. The Ingestion transaction owns Inbox identity
  `(replaySessionId, sourceEventKey)` and returns `SKIPPED_DUPLICATE` without another Observation
  insert. Both accepted and skipped-duplicate deliveries are acknowledged only after that database
  transaction returns. Database failure remains unacknowledged.

Counters use bounded reasons and never use machine, event, or replay identity as tags:
`forgesync.mqtt.observations.received`, `forwarded`, `handoff.failures`,
`acknowledgment.failures`, and `rejected{reason}`. Database outcomes use
`forgesync.ingestion.observations{result=accepted|accepted_late|skipped_duplicate}`. Accepted-late
means history was committed while Latest Observation, Equipment State, and TwinVersion were kept
unchanged.
