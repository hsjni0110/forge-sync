# ADR-038: Seek Delivery Backpressure

- Status: Accepted
- Date: 2026-09-05
- Related: ADR-024, ADR-033, ADR-037

## Observed blocker

Step 21's real browser seek rebuild publishes through sequence 1509 (1,510 observations).
During `./scripts/verify-e2e`, the stopped publisher can be ahead of the final Twin cursor and
persisted history contains gaps. In diagnostic run `forgesync-e2e-mqtt-86059`, the broker's
`$SYS/broker/publish/messages/dropped` counter increased from 0 to 170 during seek. A concurrent
read of the new session `c42e0d9a-04d3-4068-9394-5c2664931998` found 1,341 history rows, with maximum
sequence 1506. This read is a diagnostic snapshot, not a claim that ingestion had fully drained.
An earlier run had 1,337 rows through sequence 1509: equality of final cursors alone does not prove
that the intermediate input was delivered.

The current broker configuration does not specify a queue bound. Mosquitto documents a default
per-client queued-message bound of 1,000 and exposes the dropped-message counter for queue/inflight
limits. Sources: [configuration](https://mosquitto.org/man/mosquitto-conf-5.html),
[broker counters](https://mosquitto.org/man/mosquitto-8.html).
The counter establishes broker-side drops in this run; it is not a general throughput benchmark.

## Decision

The user authorized resolving the E2E blocker. Stop a replaced Replay worker with a generation
token before a new seek worker may publish. This prevents the old paused worker from waking and
advancing the replacement session concurrently.

Configure a finite broker queue whose message bound exceeds the pinned MVP source's observation
count, together with a 128 MiB byte bound that prevents unbounded memory growth. This is an MVP
operational bound, not a general slow-consumer solution.

The bound follows the pinned source. It was 60,000 messages for the 52,996 observations of mapping
`2.1.0`, and is 110,000 for the 101,644 observations of mapping `2.2.0`
([ADR-049](./ADR-049-accumulated-time-and-operating-signal-mapping.md)). The raised bound has not
been re-verified against a running broker; see Verification Ledger V-047.

E2E must verify that the broker dropped-message counter does not increase and that every sequence
from zero through the final active Twin cursor exists in Canonical Observation history. PUBACK is
still broker acceptance, not database acceptance, and no exactly-once guarantee is introduced.

## Reproduction and acceptance candidates

1. Run `./scripts/verify-e2e`; its final test seeks to the reviewed READY boundary via the browser.
2. While the temporary broker is alive, read `$SYS/broker/publish/messages/dropped` using
   `mosquitto_sub`, and compare active-session history sequences with the stopped publisher.
3. Preserve the failing browser assertion; do not reduce the replay range to avoid saturation.
4. The deterministic replacement regression holds publication long enough for the old paused worker
   to wake; the former behavior publishes replacement sequence zero twice.

Temporary runtime services are cleaned up by the verification script. Playwright failure evidence
is retained under `apps/factory-web/test-results/playwright/` and is not committed.
