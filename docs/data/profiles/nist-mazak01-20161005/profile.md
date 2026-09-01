# Mazak01 Source Profile

## 출처와 재현성

- Source: NIST Smart Manufacturing Systems Test Bed
- Upstream commit: `968279f14ebe96c03c8877eeb1901cf6b4c8fbab`
- Processing run: `sha256:b0f6d75c12eac620b2b1a278a12fa3b5b31ef57c54290ef4afa55cc1a4179a91`
- Parser/Profile schema: `1.0.0` / `2.0.0`
- Terms: https://github.com/usnistgov/smstestbed#disclaimers
- Acknowledgement: Source data and metadata originate from NIST. No NIST endorsement is implied and the NIST logo is not used.

## Machine

- ID / Name: `Mazak01` / `Mazak01`
- UUID: `mtc_adapter002`
- Description: Mazak Integrex 100-IV
- DataItems: 65

## Raw record 요약

- 전체: 115991
- 상태별: `{"PARSED": 115969, "UNKNOWN_DATA_ITEM": 22}`
- category별: `{"CONDITION": 1052, "EVENT": 4182, "SAMPLE": 110735}`
- UNAVAILABLE: 1600
- Source time: `2016-10-05T05:27:55.740706+00:00` ~ `2016-10-05T19:15:07.025798+00:00`
- Catalog coverage: 115969 / 115991 (99.98%)
- Ordering anomalies: 0

## DataItem catalog와 관찰 수

| Name | Component | Category | Type | Unit | Records | Unavailable | Ambiguous |
|---|---|---|---|---|---:|---:|---|
| `Bdeg` | `Mazak01-B` | SAMPLE | `ANGLE` | `DEGREE` | 943 | 25 | NO |
| `Bfrt` | `Mazak01-B` | SAMPLE | `ANGULAR_VELOCITY` | `DEGREE/SECOND` | 913 | 25 | NO |
| `Bload` | `Mazak01-B` | SAMPLE | `LOAD` | `PERCENT` | 2077 | 25 | NO |
| `Btravel` | `Mazak01-B` | CONDITION | `ANGLE` | `-` | 49 | 25 | NO |
| `Cdeg` | `Mazak01-C` | SAMPLE | `ANGLE` | `DEGREE` | 424 | 25 | NO |
| `Cfrt` | `Mazak01-C` | SAMPLE | `ANGULAR_VELOCITY` | `DEGREE/SECOND` | 331 | 25 | NO |
| `Cload` | `Mazak01-C` | SAMPLE | `LOAD` | `PERCENT` | 85 | 25 | NO |
| `Ctravel` | `Mazak01-C` | CONDITION | `ANGLE` | `-` | 49 | 25 | NO |
| `Fact` | `Mazak01-path` | SAMPLE | `PATH_FEEDRATE` | `MILLIMETER/SECOND` | 7633 | 25 | NO |
| `Fovr` | `Mazak01-path` | EVENT | `PATH_FEEDRATE_OVERRIDE` | `PERCENT` | 49 | 25 | NO |
| `Frapidovr` | `Mazak01-path` | EVENT | `PATH_FEEDRATE_OVERRIDE` | `PERCENT` | 86 | 25 | NO |
| `PartCountAct` | `Mazak01-path` | EVENT | `PART_COUNT` | `-` | 49 | 25 | NO |
| `S2load` | `Mazak01-C2` | SAMPLE | `LOAD` | `PERCENT` | 1040 | 25 | NO |
| `S2load_cond` | `Mazak01-C2` | CONDITION | `LOAD` | `-` | 49 | 25 | NO |
| `S2rpm` | `Mazak01-C2` | SAMPLE | `ROTARY_VELOCITY` | `REVOLUTION/MINUTE` | 1107 | 25 | NO |
| `S2temp` | `Mazak01-C2` | SAMPLE | `TEMPERATURE` | `CELSIUS` | 7448 | 25 | NO |
| `S2temp_cond` | `Mazak01-C2` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `Sload` | `Mazak01-C` | SAMPLE | `LOAD` | `PERCENT` | 1055 | 25 | NO |
| `Sload_cond` | `Mazak01-C` | CONDITION | `LOAD` | `-` | 49 | 25 | NO |
| `Sovr` | `Mazak01-C` | EVENT | `ROTARY_VELOCITY_OVERRIDE` | `PERCENT` | 49 | 25 | NO |
| `Srpm` | `Mazak01-C` | SAMPLE | `ROTARY_VELOCITY` | `REVOLUTION/MINUTE` | 1593 | 25 | NO |
| `Stemp` | `Mazak01-C` | SAMPLE | `TEMPERATURE` | `CELSIUS` | 2755 | 25 | NO |
| `Stemp_cond` | `Mazak01-C` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `Tool_group` | `Mazak01-path` | EVENT | `x:TOOL_GROUP` | `-` | 49 | 25 | NO |
| `Tool_number` | `Mazak01-path` | EVENT | `TOOL_NUMBER` | `-` | 593 | 25 | NO |
| `Tool_suffix` | `Mazak01-path` | EVENT | `x:TOOL_SUFFIX` | `-` | 595 | 25 | NO |
| `Xabs` | `Mazak01-X` | SAMPLE | `POSITION` | `MILLIMETER` | 6868 | 25 | NO |
| `Xfrt` | `Mazak01-X` | SAMPLE | `AXIS_FEEDRATE` | `MILLIMETER/SECOND` | 4575 | 25 | NO |
| `Xload` | `Mazak01-X` | SAMPLE | `LOAD` | `PERCENT` | 4306 | 25 | NO |
| `Xtravel` | `Mazak01-X` | CONDITION | `POSITION` | `-` | 49 | 25 | NO |
| `Yabs` | `Mazak01-Y` | SAMPLE | `POSITION` | `MILLIMETER` | 1538 | 25 | NO |
| `Yfrt` | `Mazak01-Y` | SAMPLE | `AXIS_FEEDRATE` | `MILLIMETER/SECOND` | 1167 | 25 | NO |
| `Yload` | `Mazak01-Y` | SAMPLE | `LOAD` | `PERCENT` | 1260 | 25 | NO |
| `Ytravel` | `Mazak01-Y` | CONDITION | `POSITION` | `-` | 49 | 25 | NO |
| `Zabs` | `Mazak01-Z` | SAMPLE | `POSITION` | `MILLIMETER` | 7380 | 25 | NO |
| `Zfrt` | `Mazak01-Z` | SAMPLE | `AXIS_FEEDRATE` | `MILLIMETER/SECOND` | 5404 | 25 | NO |
| `Zload` | `Mazak01-Z` | SAMPLE | `LOAD` | `PERCENT` | 4735 | 25 | NO |
| `Ztravel` | `Mazak01-Z` | CONDITION | `POSITION` | `-` | 49 | 25 | NO |
| `auto_time` | `Mazak01-path` | SAMPLE | `ACCUMULATED_TIME` | `-` | 10111 | 25 | NO |
| `avail` | `Mazak01` | EVENT | `AVAILABILITY` | `-` | 0 | 0 | NO |
| `comms_cond` | `Mazak01-controller` | CONDITION | `COMMUNICATIONS` | `-` | 49 | 25 | NO |
| `coolant_level` | `Mazak01-coolant` | CONDITION | `LEVEL` | `-` | 49 | 25 | NO |
| `coolant_pres` | `Mazak01-coolant` | CONDITION | `PRESSURE` | `-` | 49 | 25 | NO |
| `coolant_temp` | `Mazak01-coolant` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `cut_time` | `Mazak01-path` | SAMPLE | `ACCUMULATED_TIME` | `-` | 3516 | 25 | NO |
| `electric_temp` | `Mazak01-electric` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `estop` | `Mazak01-controller` | EVENT | `EMERGENCY_STOP` | `-` | 51 | 25 | NO |
| `execution` | `Mazak01-path` | EVENT | `EXECUTION` | `-` | 329 | 25 | NO |
| `hydra_cond` | `Mazak01-hydraulic` | CONDITION | `PRESSURE` | `-` | 49 | 25 | NO |
| `line` | `Mazak01-path` | EVENT | `LINE` | `-` | 686 | 25 | NO |
| `logic_cond` | `Mazak01-controller` | CONDITION | `LOGIC_PROGRAM` | `-` | 56 | 25 | NO |
| `mode` | `Mazak01-path` | EVENT | `CONTROLLER_MODE` | `-` | 79 | 25 | NO |
| `motion_cond` | `Mazak01-path` | CONDITION | `MOTION_PROGRAM` | `-` | 49 | 25 | NO |
| `path_system` | `Mazak01-path` | CONDITION | `SYSTEM` | `-` | 49 | 25 | NO |
| `pneu_cond` | `Mazak01-pneumatic` | CONDITION | `PRESSURE` | `-` | 49 | 25 | NO |
| `power` | `Mazak01-electric` | EVENT | `POWER_STATE` | `-` | 49 | 25 | NO |
| `program` | `Mazak01-path` | EVENT | `PROGRAM` | `-` | 50 | 25 | NO |
| `program_cmt` | `Mazak01-path` | EVENT | `x:PROGRAM_COMMENT` | `-` | 50 | 25 | NO |
| `sequenceNum` | `Mazak01-path` | EVENT | `x:SEQUENCE_NUMBER` | `-` | 686 | 25 | NO |
| `servo_cond` | `Mazak01-base` | CONDITION | `ACTUATOR` | `-` | 49 | 25 | NO |
| `subprogram` | `Mazak01-path` | EVENT | `PROGRAM` | `-` | 49 | 25 | NO |
| `subprogram_cmt` | `Mazak01-path` | EVENT | `x:PROGRAM_COMMENT` | `-` | 49 | 25 | NO |
| `system_cond` | `Mazak01-controller` | CONDITION | `SYSTEM` | `-` | 65 | 25 | NO |
| `total_time` | `Mazak01-path` | SAMPLE | `ACCUMULATED_TIME` | `-` | 32471 | 25 | NO |
| `unitNum` | `Mazak01-path` | EVENT | `x:UNIT` | `-` | 634 | 25 | NO |

## 검토가 필요한 항목

- Unknown DataItems: 22종
- Ambiguous DataItems: 0종
- Invalid records: 0건
- Canonical observation mapping: 아직 평가하지 않음

### Unknown DataItems

| Name | Records | First locator |
|---|---:|---|
| `Brfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=5;bytes=188-235` |
| `C2deg` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=15;bytes=679-725` |
| `C2frt` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=11;bytes=494-540` |
| `C2load` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=6;bytes=235-282` |
| `C2travel` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=10;bytes=441-494` |
| `C3deg` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=26;bytes=1217-1263` |
| `C3load` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=17;bytes=773-820` |
| `C3travel` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=21;bytes=979-1032` |
| `Mazak01-dtop_1` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=59;bytes=2841-2896` |
| `S2ovr` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=13;bytes=586-632` |
| `S3frt` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=22;bytes=1032-1078` |
| `S3load` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=20;bytes=932-979` |
| `S3load_cond` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=18;bytes=820-876` |
| `S3ovr` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=24;bytes=1124-1170` |
| `S3rpm` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=23;bytes=1078-1124` |
| `S3temp` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=25;bytes=1170-1217` |
| `S3temp_cond` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=19;bytes=876-932` |
| `c2rfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=16;bytes=725-773` |
| `c3rfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=27;bytes=1263-1311` |
| `crfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=38;bytes=1791-1838` |
| `d1_asset_chg` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=85;bytes=4165-4218` |
| `d1_asset_rem` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#line=86;bytes=4218-4271` |

## 제한사항

- Raw values are preserved as strings; canonical numeric and enum mapping is not evaluated.
- Coverage means catalog matching only, not canonical observation coverage.
- The report covers one selected Mazak01 daily raw artifact, not the full corpus.

## 재생성

```bash
uv run forgesync-source profile \
  --lock config/sources/nist-mazak01-20161005.lock.json \
  --store datasets/raw \
  --machine Mazak01 \
  --output docs/data/profiles/nist-mazak01-20161005
```
