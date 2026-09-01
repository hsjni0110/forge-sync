# Mazak01 Source Profile

## 출처와 재현성

- Source: NIST Smart Manufacturing Systems Test Bed
- Upstream commit: `968279f14ebe96c03c8877eeb1901cf6b4c8fbab`
- Processing run: `sha256:213d97c586e95d85369b10aa4ad063d078ec2007e4cc21252d8e72cd82ab1c5c`
- Parser/Profile schema: `1.0.0` / `1.0.0`
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

| Name | Category | Type | Unit | Records | Unavailable | Ambiguous |
|---|---|---|---|---:|---:|---|
| `Bdeg` | SAMPLE | `ANGLE` | `DEGREE` | 943 | 25 | NO |
| `Bfrt` | SAMPLE | `ANGULAR_VELOCITY` | `DEGREE/SECOND` | 913 | 25 | NO |
| `Bload` | SAMPLE | `LOAD` | `PERCENT` | 2077 | 25 | NO |
| `Btravel` | CONDITION | `ANGLE` | `-` | 49 | 25 | NO |
| `Cdeg` | SAMPLE | `ANGLE` | `DEGREE` | 424 | 25 | NO |
| `Cfrt` | SAMPLE | `ANGULAR_VELOCITY` | `DEGREE/SECOND` | 331 | 25 | NO |
| `Cload` | SAMPLE | `LOAD` | `PERCENT` | 85 | 25 | NO |
| `Ctravel` | CONDITION | `ANGLE` | `-` | 49 | 25 | NO |
| `Fact` | SAMPLE | `PATH_FEEDRATE` | `MILLIMETER/SECOND` | 7633 | 25 | NO |
| `Fovr` | EVENT | `PATH_FEEDRATE_OVERRIDE` | `PERCENT` | 49 | 25 | NO |
| `Frapidovr` | EVENT | `PATH_FEEDRATE_OVERRIDE` | `PERCENT` | 86 | 25 | NO |
| `PartCountAct` | EVENT | `PART_COUNT` | `-` | 49 | 25 | NO |
| `S2load` | SAMPLE | `LOAD` | `PERCENT` | 1040 | 25 | NO |
| `S2load_cond` | CONDITION | `LOAD` | `-` | 49 | 25 | NO |
| `S2rpm` | SAMPLE | `ROTARY_VELOCITY` | `REVOLUTION/MINUTE` | 1107 | 25 | NO |
| `S2temp` | SAMPLE | `TEMPERATURE` | `CELSIUS` | 7448 | 25 | NO |
| `S2temp_cond` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `Sload` | SAMPLE | `LOAD` | `PERCENT` | 1055 | 25 | NO |
| `Sload_cond` | CONDITION | `LOAD` | `-` | 49 | 25 | NO |
| `Sovr` | EVENT | `ROTARY_VELOCITY_OVERRIDE` | `PERCENT` | 49 | 25 | NO |
| `Srpm` | SAMPLE | `ROTARY_VELOCITY` | `REVOLUTION/MINUTE` | 1593 | 25 | NO |
| `Stemp` | SAMPLE | `TEMPERATURE` | `CELSIUS` | 2755 | 25 | NO |
| `Stemp_cond` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `Tool_group` | EVENT | `x:TOOL_GROUP` | `-` | 49 | 25 | NO |
| `Tool_number` | EVENT | `TOOL_NUMBER` | `-` | 593 | 25 | NO |
| `Tool_suffix` | EVENT | `x:TOOL_SUFFIX` | `-` | 595 | 25 | NO |
| `Xabs` | SAMPLE | `POSITION` | `MILLIMETER` | 6868 | 25 | NO |
| `Xfrt` | SAMPLE | `AXIS_FEEDRATE` | `MILLIMETER/SECOND` | 4575 | 25 | NO |
| `Xload` | SAMPLE | `LOAD` | `PERCENT` | 4306 | 25 | NO |
| `Xtravel` | CONDITION | `POSITION` | `-` | 49 | 25 | NO |
| `Yabs` | SAMPLE | `POSITION` | `MILLIMETER` | 1538 | 25 | NO |
| `Yfrt` | SAMPLE | `AXIS_FEEDRATE` | `MILLIMETER/SECOND` | 1167 | 25 | NO |
| `Yload` | SAMPLE | `LOAD` | `PERCENT` | 1260 | 25 | NO |
| `Ytravel` | CONDITION | `POSITION` | `-` | 49 | 25 | NO |
| `Zabs` | SAMPLE | `POSITION` | `MILLIMETER` | 7380 | 25 | NO |
| `Zfrt` | SAMPLE | `AXIS_FEEDRATE` | `MILLIMETER/SECOND` | 5404 | 25 | NO |
| `Zload` | SAMPLE | `LOAD` | `PERCENT` | 4735 | 25 | NO |
| `Ztravel` | CONDITION | `POSITION` | `-` | 49 | 25 | NO |
| `auto_time` | SAMPLE | `ACCUMULATED_TIME` | `-` | 10111 | 25 | NO |
| `avail` | EVENT | `AVAILABILITY` | `-` | 0 | 0 | NO |
| `comms_cond` | CONDITION | `COMMUNICATIONS` | `-` | 49 | 25 | NO |
| `coolant_level` | CONDITION | `LEVEL` | `-` | 49 | 25 | NO |
| `coolant_pres` | CONDITION | `PRESSURE` | `-` | 49 | 25 | NO |
| `coolant_temp` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `cut_time` | SAMPLE | `ACCUMULATED_TIME` | `-` | 3516 | 25 | NO |
| `electric_temp` | CONDITION | `TEMPERATURE` | `-` | 49 | 25 | NO |
| `estop` | EVENT | `EMERGENCY_STOP` | `-` | 51 | 25 | NO |
| `execution` | EVENT | `EXECUTION` | `-` | 329 | 25 | NO |
| `hydra_cond` | CONDITION | `PRESSURE` | `-` | 49 | 25 | NO |
| `line` | EVENT | `LINE` | `-` | 686 | 25 | NO |
| `logic_cond` | CONDITION | `LOGIC_PROGRAM` | `-` | 56 | 25 | NO |
| `mode` | EVENT | `CONTROLLER_MODE` | `-` | 79 | 25 | NO |
| `motion_cond` | CONDITION | `MOTION_PROGRAM` | `-` | 49 | 25 | NO |
| `path_system` | CONDITION | `SYSTEM` | `-` | 49 | 25 | NO |
| `pneu_cond` | CONDITION | `PRESSURE` | `-` | 49 | 25 | NO |
| `power` | EVENT | `POWER_STATE` | `-` | 49 | 25 | NO |
| `program` | EVENT | `PROGRAM` | `-` | 50 | 25 | NO |
| `program_cmt` | EVENT | `x:PROGRAM_COMMENT` | `-` | 50 | 25 | NO |
| `sequenceNum` | EVENT | `x:SEQUENCE_NUMBER` | `-` | 686 | 25 | NO |
| `servo_cond` | CONDITION | `ACTUATOR` | `-` | 49 | 25 | NO |
| `subprogram` | EVENT | `PROGRAM` | `-` | 49 | 25 | NO |
| `subprogram_cmt` | EVENT | `x:PROGRAM_COMMENT` | `-` | 49 | 25 | NO |
| `system_cond` | CONDITION | `SYSTEM` | `-` | 65 | 25 | NO |
| `total_time` | SAMPLE | `ACCUMULATED_TIME` | `-` | 32471 | 25 | NO |
| `unitNum` | EVENT | `x:UNIT` | `-` | 634 | 25 | NO |

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
