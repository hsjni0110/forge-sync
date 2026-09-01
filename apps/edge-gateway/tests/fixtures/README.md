# Source fixtures

`mazak01-20161005-golden.shdr`는 NIST SMS Test Bed의 pinned commit `968279f14ebe96c03c8877eeb1901cf6b4c8fbab`, `raw/Mazak01/Mazak01-20161005-20161006.txt` 첫 12개 완전한 레코드에서 만든 `DERIVED_FIXTURE`다.

- 원본: NIST Smart Manufacturing Systems Test Bed
- 변형: 첫 12개 완전한 line만 선택, 내용 변경 없음
- 용도: byte 보존과 raw grammar regression test
- NIST endorsement를 의미하지 않으며 NIST logo를 사용하지 않는다.

`Devices-minimal.xml`은 parser 경계 테스트를 위해 필요한 Mazak01 DataItem만 재구성한 synthetic fixture이며 공식 Devices.xml 원본이 아니다.
