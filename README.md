# 골목지킴이 (Golmok Guard)

**골목에서 사람이 튀어나오기 전에 차를 '멈출 수 있는 상태'로 만들고, 그 순간을 숫자로 남기는 Pleos Connect 차량용 앱**

차 대 사람 사고는 보행자 과실이어도 운전자가 가해자가 됩니다. 골목지킴이는 골목 진입을 내비·지도·차량 신호로 감지해 서행을 안내하고 전조등·무드등·창문을 자동으로 맞추며, 여러 차의 급제동 지점을 모아 "사고 날 뻔한 곳"을 미리 알려줍니다. 충격이 감지되면 충돌 전후 40초의 차량 데이터를 리포트로 남겨 "운전자가 피할 수 있었나"에 답합니다.

> 현대자동차그룹 차량용 앱 개발 공모전 출품작 · 분야: 안전 운전 · Pleos Playground SDK 기반

## 주요 기능

| 기능 | 내용 |
|---|---|
| **골목 감속 가이드** | 내비 주의지점(좁은 길·스쿨존·사고다발) + 지도 도로 등급·제한속도 + 깜빡이·핸들·다음 회전으로 골목 진입 감지. 50m 전 예고("브레이크에 발 올리세요") → 기준 30/20km/h → 초과 시 단계 경고 |
| **급제동 핫스팟 지도** | 여러 차의 급제동 지점을 익명으로 모아 50m 격자 집계. 접근 시 "최근 N번 급제동" 안내 |
| **골목 자동 제어** | 진입 시 전조등·안개등 점등, 무드등 호박/적색, 운전석 창문 10% 개방, 미디어 볼륨 절반, 야간 실내등 소등, 우천 김서림 제거 — 벗어나면 모두 복원, 설정에서 개별 해제 가능 |
| **구조적 위험 지점** | 횡단보도·버스정류장·학교·유치원 접근 시 과속이면 안내 |
| **데이터 블랙박스** | 차속·기어·방향지시등·브레이크·조명·와이퍼·핸들·위치·가속도 상시 기록(부팅 자동 시작). 충격 2.5G·에어백 시 전 30초/후 10초 저장 |
| **사고 리포트** | 충돌 속도, 브레이크 시점·감속량, 깜빡이, 전조등, **충격 방향(자동 보정)**, 안전벨트, **골목 모드 준수 여부·안내 수신·자동 점등** |
| **사고 직후 대응** | 보험사·112 전화, 위치 복사, QR → 휴대폰 웹 리포트(지도·공유·PDF). 기록은 링크 안에만 담겨 서버 미저장 |
| **하차 측면 안내** | 이면도로 정차 중 뒷문 열림 + 뒷좌석 탑승 시 "뒤 확인 후 내리세요" |
| **안전벨트 안내 기록** | 주행 중 미착용 좌석 음성 안내 + 안내 시각 기록 |
| **맡김 모드** | 발레·정비·대리·가족: 맡긴 동안 거리·최고 속도·급가속/급제동·바뀐 설정·경로, 용도별 의심 운행 판정 |
| **주행 일지** | 주행마다 자동 저장, 30일 보관 |

주행 중(D)에는 차량 정책에 따라 앱 화면이 표시되지 않으며 기록·안내는 백그라운드(음성)로 계속됩니다.

## APK

- `apk/데이터블랙박스_release.apk` → 제출 시 `성명_골목지킴이_release.apk`로 변경 예정 (release 서명)
- 패키지 `com.databox.blackbox` · minSdk 28 · targetSdk 35

## 실행 방법

1. Pleos Connect Emulator(v2.1.0, API 34)를 실행합니다.
2. APK를 권한 허용 상태로 설치합니다.
   ```bash
   adb install -g apk/*.apk
   ```
3. 앱 목록에서 **골목지킴이**를 실행합니다. 이후 부팅 시 자동으로 기록이 시작됩니다.
4. 설정 카드에서 보험사 번호를 등록하면 사고 직후 바로 걸 수 있습니다.

### 시연 (Pleos Connect Virtual Console)

```bash
adb forward tcp:8088 tcp:8088     # 브라우저에서 http://127.0.0.1:8088 을 열어 둔 채 진행
adb emu geo fix 127.0490 37.5045   # 큰길(강남 테헤란로)
# 가상 콘솔: 기어 D, 속도 40 → 좌회전 깜빡이 + 핸들 -45 (진입 예고)
adb emu geo fix 126.9245 37.5628   # 이면도로(연남동 동교로45길) → 골목 모드 진입, 전조등·무드등·창문·볼륨 제어
# 속도 35 → 기준 초과 경고 / 속도 15 → 안전 / 큰길 좌표로 복귀 → 복원
# 사고: 에뮬레이터 확장 컨트롤 > 가상 센서에서 가속도 순간 3G 이상 → 10초 후 사고 리포트
```
- 맡김 모드: 「발레파킹」 → 주행 → 기어 P → 「돌려받기」
- 안전벨트: 콘솔 Seat Occupancy로 뒷좌석 탑승 → 5km/h 이상 주행

## 서버 API (Vercel)

| 엔드포인트 | 역할 |
|---|---|
| `GET /api/road?lat&lon` | 도로 등급·이름·제한속도 + 횡단보도·정류장·학교 (OpenStreetMap, 55m 격자 캐시) |
| `GET /api/hotspots?lat&lon&r` · `POST` | 급제동 지점 익명 집계·조회 (식별 정보 없음) |
| `/#r=…` | 휴대폰 웹 리포트 (데이터는 URL fragment 안에만, 서버 미전송) |

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Android 14 / API 34, x86_64), RAM 4GB, cold boot, 인터넷 필요(도로·핫스팟 API) |
| 빌드 | JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compileSdk 35 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1 |
| 표준 API | Car API `PERF_VEHICLE_SPEED`(차속), SensorManager 가속도, LocationManager(위치 보조), AudioManager |

**권한**: `pleos.car.permission.CAR_DRIVE / CAR_LIGHTS / CAR_WINDOWS / CAR_SAFETY / CAR_INFO / CAR_SEATS / CAR_CLIMATE / CAR_DOORS / FUSED_LOCATION / TTS_SERVICE / NAVI_ROUTE / CONTROL_CAR_LIGHTS / CONTROL_CAR_WINDOWS / CONTROL_CAR_CLIMATE`, `android.car.permission.CAR_SPEED`, 위치, 알림, 인터넷, 부팅 완료 수신, 포그라운드 서비스(specialUse). 제동·가속 제어는 사용하지 않습니다.

### 소스에서 빌드

```bash
cd android
./gradlew assembleDebug      # 디버그
./gradlew assembleRelease    # release: android/keystore.properties 필요
npm install && npm run build # 웹 리포트·API (Vercel 배포, BLOB_READ_WRITE_TOKEN 필요)
```

## 구조

```
android/app/src/main/java/com/databox/blackbox/
  AlleyEngine.kt      골목 진입 판단·위험 단계 (지도·내비·핫스팟·차량 신호)
  AlleyGuard.kt       음성 안내, 조명·무드등·창문·볼륨·김서림 제어와 복원, 하차 안내
  RecorderService.kt  상시 기록·사고 감지·맡김/주행 일지·벨트 안내
  CrashReport.kt      사고 리포트 사실 추출 (골목 모드 준수 포함)
  ImpactDirection.kt  충격 방향 자동 보정
  Emergency.kt        보험사·112 전화, 위치 복사
  Osm.kt / Hotspots.kt  서버 API 클라이언트
api/                  road.ts(도로 정보), hotspots.ts(급제동 집계) — Vercel 서버리스 + Blob
src/                  휴대폰용 웹 리포트 (React)
apk/                  제출용 release APK
docs/                 기획서(plan.md)·요강·조사·로드맵
```

## 알려진 사항

- 에어백 전개, 시트 위치 변경, 안개등은 에뮬레이터 미지원/미반영 → 실차 검증 항목
- Vehicle SDK 2.3.2의 좌석 탑승 값이 표준(VACANT=1, OCCUPIED=2)과 반대로 변환되어 앱에서 보정 (`docs/pleos-research.md`)
- 실시간 보행자 감지·제동 제어·외부음·운전자 카메라는 앱에 개방되지 않은 영역 — Pleos ADAS API 개방 시 센서 입력만 교체하면 연동되는 구조
- 리포트는 블랙박스 영상을 보완하는 자료이며 법적 판단을 대신하지 않습니다
