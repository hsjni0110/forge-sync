# Mazak01 Canonical Mapping Report

## Reproducibility

- Processing run: `sha256:bf5a3342dcfcc896153c5b69059de66479e8e19d9f225caa72477ec1dc6a2416`
- Mapping / Mapper / Parser: `2.0.0` / `2.0.0` / `1.0.0`
- Raw artifact: `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf`
- Mapping table SHA-256: `179fe21fbec5c7dd88de27c307e97b23c8a80143f8a4e0799d525c1828980212`

## Semantic coverage

- Mapped / parsed: 52996 / 115991 (45.69%)
- Status counts: `{'MAPPED': 52996, 'UNKNOWN_DATA_ITEM': 22, 'UNSUPPORTED_DATA_ITEM': 62973}`

## Explicit mappings

| DataItem | Component | Category | Source type | Target | Mapped | Unavailable | Invalid |
|---|---|---|---|---|---:|---:|---:|
| `Bload` | `Mazak01-B` | SAMPLE | `LOAD` | `LOAD` | 2077 | 25 | 0 |
| `Btravel` | `Mazak01-B` | CONDITION | `ANGLE` | `ANGLE` | 49 | 0 | 0 |
| `S2load` | `Mazak01-C2` | SAMPLE | `LOAD` | `LOAD` | 1040 | 25 | 0 |
| `S2rpm` | `Mazak01-C2` | SAMPLE | `ROTARY_VELOCITY` | `SPINDLE_SPEED` | 1107 | 25 | 0 |
| `S2temp` | `Mazak01-C2` | SAMPLE | `TEMPERATURE` | `TEMPERATURE` | 7448 | 25 | 0 |
| `S2load_cond` | `Mazak01-C2` | CONDITION | `LOAD` | `LOAD` | 49 | 0 | 0 |
| `S2temp_cond` | `Mazak01-C2` | CONDITION | `TEMPERATURE` | `TEMPERATURE` | 49 | 0 | 0 |
| `Stemp_cond` | `Mazak01-C` | CONDITION | `TEMPERATURE` | `TEMPERATURE` | 49 | 0 | 0 |
| `Sload` | `Mazak01-C` | SAMPLE | `LOAD` | `LOAD` | 1055 | 25 | 0 |
| `Ctravel` | `Mazak01-C` | CONDITION | `ANGLE` | `ANGLE` | 49 | 0 | 0 |
| `Srpm` | `Mazak01-C` | SAMPLE | `ROTARY_VELOCITY` | `SPINDLE_SPEED` | 1593 | 25 | 0 |
| `Stemp` | `Mazak01-C` | SAMPLE | `TEMPERATURE` | `TEMPERATURE` | 2755 | 25 | 0 |
| `Sload_cond` | `Mazak01-C` | CONDITION | `LOAD` | `LOAD` | 49 | 0 | 0 |
| `Xabs` | `Mazak01-X` | SAMPLE | `POSITION` | `POSITION` | 6868 | 25 | 0 |
| `Xtravel` | `Mazak01-X` | CONDITION | `POSITION` | `POSITION` | 49 | 0 | 0 |
| `Xload` | `Mazak01-X` | SAMPLE | `LOAD` | `LOAD` | 4306 | 25 | 0 |
| `Yabs` | `Mazak01-Y` | SAMPLE | `POSITION` | `POSITION` | 1538 | 25 | 0 |
| `Ytravel` | `Mazak01-Y` | CONDITION | `POSITION` | `POSITION` | 49 | 0 | 0 |
| `Yload` | `Mazak01-Y` | SAMPLE | `LOAD` | `LOAD` | 1260 | 25 | 0 |
| `Zabs` | `Mazak01-Z` | SAMPLE | `POSITION` | `POSITION` | 7380 | 25 | 0 |
| `Ztravel` | `Mazak01-Z` | CONDITION | `POSITION` | `POSITION` | 49 | 0 | 0 |
| `Zload` | `Mazak01-Z` | SAMPLE | `LOAD` | `LOAD` | 4735 | 25 | 0 |
| `servo_cond` | `Mazak01-base` | CONDITION | `ACTUATOR` | `ACTUATOR` | 49 | 0 | 0 |
| `comms_cond` | `Mazak01-controller` | CONDITION | `COMMUNICATIONS` | `COMMUNICATIONS` | 49 | 0 | 0 |
| `logic_cond` | `Mazak01-controller` | CONDITION | `LOGIC_PROGRAM` | `LOGIC_PROGRAM` | 56 | 0 | 0 |
| `system_cond` | `Mazak01-controller` | CONDITION | `SYSTEM` | `SYSTEM` | 65 | 0 | 0 |
| `coolant_pres` | `Mazak01-coolant` | CONDITION | `PRESSURE` | `PRESSURE` | 49 | 0 | 0 |
| `coolant_temp` | `Mazak01-coolant` | CONDITION | `TEMPERATURE` | `TEMPERATURE` | 49 | 0 | 0 |
| `coolant_level` | `Mazak01-coolant` | CONDITION | `LEVEL` | `LEVEL` | 49 | 0 | 0 |
| `power` | `Mazak01-electric` | EVENT | `POWER_STATE` | `POWER_STATE` | 49 | 25 | 0 |
| `electric_temp` | `Mazak01-electric` | CONDITION | `TEMPERATURE` | `TEMPERATURE` | 49 | 0 | 0 |
| `hydra_cond` | `Mazak01-hydraulic` | CONDITION | `PRESSURE` | `PRESSURE` | 49 | 0 | 0 |
| `program` | `Mazak01-path` | EVENT | `PROGRAM` | `PROGRAM` | 50 | 25 | 0 |
| `Tool_number` | `Mazak01-path` | EVENT | `TOOL_NUMBER` | `TOOL_NUMBER` | 593 | 25 | 0 |
| `execution` | `Mazak01-path` | EVENT | `EXECUTION` | `EXECUTION` | 329 | 25 | 0 |
| `mode` | `Mazak01-path` | EVENT | `CONTROLLER_MODE` | `CONTROLLER_MODE` | 79 | 25 | 0 |
| `motion_cond` | `Mazak01-path` | CONDITION | `MOTION_PROGRAM` | `MOTION_PROGRAM` | 49 | 0 | 0 |
| `path_system` | `Mazak01-path` | CONDITION | `SYSTEM` | `SYSTEM` | 49 | 0 | 0 |
| `PartCountAct` | `Mazak01-path` | EVENT | `PART_COUNT` | `PART_COUNT` | 49 | 25 | 0 |
| `Fact` | `Mazak01-path` | SAMPLE | `PATH_FEEDRATE` | `PATH_FEEDRATE` | 7633 | 25 | 0 |
| `pneu_cond` | `Mazak01-pneumatic` | CONDITION | `PRESSURE` | `PRESSURE` | 49 | 0 | 0 |

## Unsupported DataItems

| DataItem | Records | First Raw Record |
|---|---:|---|
| `Bfrt` | 913 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=46-90` |
| `Bdeg` | 943 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=143-187` |
| `Cload` | 85 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1311-1356` |
| `Cfrt` | 331 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1565-1609` |
| `Sovr` | 49 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1655-1699` |
| `Cdeg` | 424 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1746-1790` |
| `Xfrt` | 4575 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1981-2025` |
| `Yfrt` | 1167 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=2169-2213` |
| `Zfrt` | 5404 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=2357-2401` |
| `estop` | 51 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=2623-2668` |
| `Tool_group` | 49 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3155-3205` |
| `Tool_suffix` | 595 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3206-3257` |
| `program_cmt` | 50 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3353-3404` |
| `subprogram_cmt` | 49 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3405-3459` |
| `auto_time` | 10111 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3460-3509` |
| `total_time` | 32471 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3510-3560` |
| `cut_time` | 3516 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3561-3609` |
| `subprogram` | 49 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3610-3660` |
| `line` | 686 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3773-3817` |
| `unitNum` | 634 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3818-3865` |
| `sequenceNum` | 686 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=3866-3917` |
| `Frapidovr` | 86 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=4016-4065` |
| `Fovr` | 49 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=4066-4110` |

## Unknown DataItems

| DataItem | Records | First Raw Record |
|---|---:|---|
| `Brfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=188-234` |
| `C2deg` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=679-724` |
| `C2frt` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=494-539` |
| `C2load` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=235-281` |
| `C2travel` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=441-493` |
| `C3deg` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1217-1262` |
| `C3load` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=773-819` |
| `C3travel` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=979-1031` |
| `Mazak01-dtop_1` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=2841-2895` |
| `S2ovr` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=586-631` |
| `S3frt` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1032-1077` |
| `S3load` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=932-978` |
| `S3load_cond` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=820-875` |
| `S3ovr` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1124-1169` |
| `S3rpm` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1078-1123` |
| `S3temp` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1170-1216` |
| `S3temp_cond` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=876-931` |
| `c2rfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=725-772` |
| `c3rfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1263-1310` |
| `crfunc` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=1791-1837` |
| `d1_asset_chg` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=4165-4217` |
| `d1_asset_rem` | 1 | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf#bytes=4218-4270` |

## Limitations

- Unsupported DataItems remain traceable Raw Records and are not Canonical metrics.
- Telemetry PART_COUNT is not a ProductionResult.
- Cload is deferred because it collides with Sload on the same component LOAD channel.
