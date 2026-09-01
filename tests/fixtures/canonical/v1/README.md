# Canonical Observation v1 fixtures

These are `DERIVED_FIXTURE` contract examples based on the pinned NIST Mazak01 artifact
`sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf` and its generated source
profile. They do not copy the complete source dataset.

The valid fixtures use profile-confirmed Raw Record locators and values:

- spindle SAMPLE: line 125, `2016-10-05T08:43:49.514Z`, `0 REVOLUTION/MINUTE`
- execution EVENT: line 1526, `2016-10-05T09:01:37.891Z`, `ACTIVE`
- system CONDITION: line 1708, `2016-10-05T09:03:57.872Z`, `Warning|406|||MEMORY PROTECT`
- unavailable spindle SAMPLE and execution EVENT: lines 34 and 67

`eventId`, `sourceEventKey`, Replay identity, and `mappingVersion` are deterministic contract-fixture
values, not claims that the production mapping or replay algorithms already exist. Invalid fixtures
are deliberate mutations used to prove rejection behavior.

## SHA-256

```text
b62396ac5b57e59506a908d0f9ef256a495301f9d880c2798639b63423cf2416  valid/condition-warning.json
10f53aa4b955830b06dd84c1dab30102f620f4aaff9b9696ec88cde58a2864a1  valid/event-execution.json
9baffb3bade7b76ec8877e5be8b35465fb5a8eabdaa9fc3a1633baeced9cb8c0  valid/event-unavailable.json
0337e8f2be81914f987b752efc01663793482c4b09e0dab4e6baf83ab497020b  valid/sample-spindle-speed.json
b23f2b084d7096a419eb9f981fada50145a628893eaeb5389191d0ada5dd93fe  valid/sample-unavailable.json
97da791bf9abd1c5b6dff1457c3b5f38437b36cb24dba778175bfe9eba55e494  invalid/condition-missing-provenance.json
51331a441e185bcfcc631beb60e81c4934ab06152bba0521035cc9986278a0bf  invalid/event-value-type.json
990077fa2d62d9b4f572993890f9750b09f659a252d0d5a4cae41bab8b47cadb  invalid/replay-partial.json
075fc24621795e334b656953f31968a80ee97635558faf7d808209855c17a3f3  invalid/sample-unit-mismatch.json
1362034c8d05fe95839aa27e3d669d45a7a247291d5b38808c1bf35cb6d1b4ea  invalid/schema-version-mismatch.json
6208339ffc868d8c63c3e4d161670780da1ea596eb6aba4d642d3caff2ec3fa5  invalid/source-timezone-missing.json
890131dda196a7b95051f630ec3306c8e33a770def6d31fac7d8d237ac2fbf6f  invalid/unavailable-sample-with-value.json
e3cc5230138289c894d4131eb6617198442a8d385ea96a7adea15ae4e8df5f5f  invalid/unknown-field.json
```
