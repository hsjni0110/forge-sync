# Raw artifact store

이 디렉터리는 외부 원천 byte를 content-addressed 방식으로 불변 보존하는 로컬 저장소다. payload와 acquisition receipt는 Git에 포함하지 않는다.

실제 경로:

```text
nist/sha256/<content-hash>/payload
nist/sha256/<content-hash>/manifest.json
nist/receipts/<receipt-id>.json
```

재현 가능한 source URI, commit, byte length와 SHA-256은 `config/sources`의 lock 파일을 기준으로 한다.

