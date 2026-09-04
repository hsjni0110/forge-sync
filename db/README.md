# Database

Database migrations and database-specific integration support belong here. Domain models must not
depend on migration or persistence representations.

Flyway migrations live in `migration/` and are copied into the Factory API runtime classpath. The
Inbox and Canonical Observation history are regular PostgreSQL tables; the TimescaleDB runtime does
not imply that every table is a hypertable.

Latest Observation projection is keyed by machine and source DataItem. A separate machine row owns
the monotonic TwinVersion and provides the transaction lock for concurrent projection updates.
Equipment State is rebuilt from the machine's current Latest Observations and its base connectivity
is stored with the same TwinVersion and projected wall-clock time. Freshness and effective STALE
connectivity are derived at read time so a state can age without a database write; historical source
time is not used for that calculation.

Process Analytics processing runs and Machining Run projections are append-only derived data. One
transaction preserves the processing identity and all of its projections. Their foreign key does
not connect to Production, Equipment Twin, or the Ingestion Inbox transaction boundary.
`V006__create_cycle_feature_projection.sql` adds immutable Cycle Feature processing metadata and
JSONB feature-set projections. The metadata row and every feature set are inserted in one
transaction; their foreign key references the immutable Machining Run processing result without
modifying it or Canonical Observation history.
