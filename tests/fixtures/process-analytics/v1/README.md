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

`mazak01-anomaly-assessments.json` is a compact reviewed insufficient-data contract fixture. It
preserves `DERIVED -> DERIVED CycleFeature -> REAL:NIST` lineage without claiming an anomaly when
fewer than five earlier same-program runs exist.
Its schema SHA-256 is `e8f832f61d181030e19e159b12439dc813c5fa653dfadbd026a697018c158174`;
the fixture SHA-256 is `57bccf072162226660efb0ba6ab2727a663a9da3cf5060f93ed87496f5778fbc`.
