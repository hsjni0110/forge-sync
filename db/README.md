# Database

Database migrations and database-specific integration support belong here. Domain models must not
depend on migration or persistence representations.

Flyway migrations live in `migration/` and are copied into the Factory API runtime classpath. The
Inbox and Canonical Observation history are regular PostgreSQL tables; the TimescaleDB runtime does
not imply that every table is a hypertable.

Latest Observation projection is keyed by machine and source DataItem. A separate machine row owns
the monotonic TwinVersion and provides the transaction lock for concurrent projection updates.
