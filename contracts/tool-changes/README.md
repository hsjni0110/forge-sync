# Tool Change Timeline contract

`v1` exposes observed `TOOL_NUMBER` transitions for one Replay session through an explicit
Replay sequence. The first available value is a baseline, duplicate values are ignored, and an
unavailable observation breaks continuity so a transition is never inferred across a gap.

Media type: `application/vnd.forgesync.tool-changes.v1+json`.
