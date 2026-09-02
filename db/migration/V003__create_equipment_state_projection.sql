CREATE TABLE equipment_state_projection (
    machine_id TEXT PRIMARY KEY,
    connectivity_state TEXT NOT NULL CHECK (
        connectivity_state IN ('UNKNOWN', 'ONLINE', 'OFFLINE')
    ),
    execution_state TEXT NOT NULL CHECK (
        execution_state IN ('UNKNOWN', 'READY', 'ACTIVE', 'IDLE', 'HOLD', 'STOPPED')
    ),
    health_state TEXT NOT NULL CHECK (
        health_state IN ('UNKNOWN', 'NORMAL', 'WARNING', 'FAULT')
    ),
    twin_version BIGINT NOT NULL CHECK (twin_version > 0),
    projected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT equipment_state_machine_fk
        FOREIGN KEY (machine_id) REFERENCES equipment_twin_version (machine_id)
);
