# Canonical observation store

이 디렉터리는 immutable Raw Artifact에서 versioned Semantic Mapping으로 생성한 L2 Canonical
Observation을 보관하는 로컬 파생 저장소다. Raw byte를 대체하거나 수정하지 않는다.

```text
<sourceSetId>/<processing-run-hash>/
  observations.ndjson
  mapping-report.json
  mapping-report.md
  manifest.json
```

대용량 NDJSON과 실행별 파생물은 Git에 포함하지 않는다. mapping table, 작은 golden fixture,
실제 실행의 검토용 report와 재현 명령만 버전 관리한다.
