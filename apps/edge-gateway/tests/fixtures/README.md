# Source fixtures

`mazak01-20161005-golden.shdr`는 NIST SMS Test Bed의 pinned commit `968279f14ebe96c03c8877eeb1901cf6b4c8fbab`, `raw/Mazak01/Mazak01-20161005-20161006.txt` 첫 12개 완전한 레코드에서 만든 `DERIVED_FIXTURE`다.

- 원본: NIST Smart Manufacturing Systems Test Bed
- 변형: 첫 12개 완전한 line만 선택, 내용 변경 없음
- 용도: byte 보존과 raw grammar regression test
- NIST endorsement를 의미하지 않으며 NIST logo를 사용하지 않는다.

`Devices-minimal.xml`은 parser 경계 테스트를 위해 필요한 Mazak01 DataItem만 재구성한 synthetic fixture이며 공식 Devices.xml 원본이 아니다.

`Devices-mapping.xml`은 component-aware Canonical mapping golden test에 필요한 DataItem metadata를
고정 Devices.xml에서 선택한 `DERIVED_FIXTURE`다. `mazak01-canonical-mapping-golden.shdr`는 같은 pinned
raw artifact에서 component가 다른 RPM·위치·부하·온도와 Condition 예제를 byte 변경 없이 선택했다.

- Devices fixture SHA-256: `d6de222ad4703430aaae5aa77be03cc14fca7318ce1852b2d4cc5c3fdc149878`
- Raw fixture SHA-256: `60a9891de3a98a8dcd139c38f469cd9126d5f87974f4177b427f782c44fc1664`
- 원본 Devices artifact: `bb54e4eca7b65e50b026d02b785c3f905b66bd38493456569272540296f68166`
- 원본 raw artifact: `6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf`
- 용도: 대표 mapping, category 보존, unavailable, unsupported와 provenance regression test
