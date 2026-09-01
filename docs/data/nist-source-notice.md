# NIST Source Notice

ForgeSync의 operational source 검증에는 [NIST Smart Manufacturing Systems Test Bed](https://github.com/usnistgov/smstestbed)의 공개 metadata와 raw data를 사용한다.

## 선택한 원천

- Repository commit: `968279f14ebe96c03c8877eeb1901cf6b4c8fbab`
- `mtconnect/agent/Devices.xml`
  - 39,450 bytes
  - SHA-256 `bb54e4eca7b65e50b026d02b785c3f905b66bd38493456569272540296f68166`
- `raw/Mazak01/Mazak01-20161005-20161006.txt`
  - 4,609,711 bytes
  - SHA-256 `6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf`

정확한 URI와 무결성 값은 `config/sources/nist-mazak01-20161005.lock.json`에 고정한다.

## 사용 조건과 표시

NIST repository의 공식 disclaimer는 별도로 저작권이 표시된 자료를 제외한 페이지 정보를 public information으로 설명하고 배포 또는 복사를 허용하며 적절한 출처 표시를 요청한다. NIST가 개발한 software에는 원 notice의 유지와 NIST 출처의 명시적 acknowledgement를 요청한다.

ForgeSync는 다음을 지킨다.

- NIST를 source로 명시한다.
- NIST의 보증, 추천 또는 제품 endorsement를 주장하지 않는다.
- NIST logo를 사용하지 않는다.
- 원천은 정확성과 가용성이 보장되지 않는 `AS IS` 자료로 취급한다.
- 실제 전체 raw payload는 Git에 커밋하지 않고 pinned URI와 checksum으로 재현한다.
- 커밋하는 작은 fixture에는 `DERIVED_FIXTURE`와 변형 방식을 기록한다.

이 문서는 법률 자문이 아니라 프로젝트에서 확인하고 적용한 source handling 근거다. 외부 사용 조건이 변경될 수 있으므로 재검증 날짜는 Verification Ledger에서 관리한다.
