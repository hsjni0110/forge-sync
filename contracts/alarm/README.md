# Alarm API contract

`v1/alarm-timeline.schema.json` defines the replay-cursor-scoped Business Alarm list returned with
media type `application/vnd.forgesync.alarms.v1+json`.

An Alarm records that it came from an Equipment Condition, but it is not the Condition itself.
`WARNING` and `CRITICAL` are business severities; the source level remains preserved separately in
the Condition projection. Nullable acknowledgement and resolution fields are explicit so clients
do not invent missing lifecycle evidence.
