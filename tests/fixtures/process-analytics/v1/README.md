# Machining Run v1 fixture

`mazak01-machining-runs.json` is a `DERIVED_FIXTURE` for the public response contract. Its reviewed
boundary uses unchanged Canonical observations from the pinned NIST Mazak01 artifact:

- start: `EXECUTION=ACTIVE` at `2016-10-05T09:18:30.447Z`, raw bytes `131385-131426`
- support: `SPINDLE_SPEED=2000` at `2016-10-05T09:18:43.075Z`, raw bytes `134775-134810`
- end: `EXECUTION=READY` at `2016-10-05T09:20:59.181Z`, raw bytes `170237-170277`

Replay sequence values are the zero-based order of the checked-in Canonical output. Processing and
result hashes use contract-only fixed values; they are not claims about a production processing run.
The source artifact is `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf`.
The fixture SHA-256 is `a0ebee05e598a8ed252f21bb8c580af2a9500af3a94501ea6b7451239e143192`.
