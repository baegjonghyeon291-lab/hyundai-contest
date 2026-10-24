# 데이터 블랙박스 (Data BlackBox)

**영상이 못 남기는 내 차의 순간을 숫자로 남기는 Pleos Connect 차량용 앱**

블랙박스 영상에는 내 차의 깜빡이·브레이크·속도가 찍히지 않습니다. 데이터 블랙박스는 차량 신호를 상시 기록하다가 충격을 감지하면 충돌 전 30초 ~ 후 10초를 사고 리포트로 남기고, 차를 남에게 맡길 때(발레·정비·대리·가족)의 운행도 기록해 운전자의 억울함을 막습니다.

> 현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Playground SDK 기반

## 주요 기능

| 기능 | 내용 |
|---|---|
| 상시 기록 | 시동(부팅) 시 자동 시작. 차속·기어·방향지시등·브레이크·전조등·와이퍼·핸들 각도·위치·가속도를 숫자로 기록 (1시간 ≈ 2MB) |
| 사고 자동 감지 | 가속도 충격 2.5G 이상 또는 에어백 전개 시 충돌 전 30초 ~ 후 10초 저장 |
| 사고 리포트 | 충돌 속도, 브레이크 시점·감속량, 깜빡이 시점, 전조등·와이퍼, **충격 방향(전·후·좌·우)**, 안전벨트 상태, 속도 그래프, 사고 지점 |
| 휴대폰 전송 | QR 코드 → 휴대폰 웹 리포트(지도·공유·PDF). 기록은 링크 안에만 담겨 서버에 저장되지 않음 |
| 맡김 모드 | 발레파킹·정비소·대리운전·가족: 맡긴 동안 주행 거리·최고 속도·급가속/급제동·바뀐 설정·경로 + 용도별 의심 운행 판정 |
| 주행 일지 | 주행마다 거리·시간·속도·경로 자동 저장, 30일 보관 후 자동 삭제 |
| 안전벨트 안내 기록 | 주행 중 탑승석 벨트 미착용 시 음성 안내 + 안내 시각 기록 (택시·카풀 운전자 면책 근거) |

주행 중(D)에는 차량 시스템 정책에 따라 앱 화면이 표시되지 않으며, 기록은 백그라운드에서 계속됩니다. 리포트는 정차 후 확인합니다.

## APK

- `apk/데이터블랙박스_release.apk` (release 서명, versionName 1.0.0)
- 패키지: `com.databox.blackbox` · minSdk 28 · targetSdk 35

## 실행 방법

1. Pleos Connect Emulator(v2.1.0, API 34)를 실행합니다.
2. APK를 권한 허용 상태로 설치합니다.
   ```bash
   adb install -g apk/데이터블랙박스_release.apk
   ```
   `-g` 없이 설치한 경우 첫 실행 시 차속·위치·알림 권한을 허용합니다.
3. 앱 목록에서 **데이터 블랙박스**를 실행합니다. 이후에는 부팅 시 자동으로 기록이 시작됩니다.

### 시연 (Pleos Connect Virtual Console)

```bash
adb forward tcp:8088 tcp:8088   # 브라우저에서 http://127.0.0.1:8088 을 열어 둔 상태로 진행
```
- **사고**: 기어 D → 속도 60km/h → 방향지시등 왼쪽 → 브레이크 → 에뮬레이터 확장 컨트롤의 가상 센서에서 가속도를 순간적으로 크게(3G 이상) 변경 → 10초 후 사고 기록 생성
- **맡김 모드**: 앱에서 「발레파킹」 → 주행 → 기어 P → 「돌려받기」
- **안전벨트**: 콘솔에서 뒷좌석 탑승(Seat Occupancy) → 5km/h 이상 주행
- 위치: `adb emu geo fix <경도> <위도>`

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Android 14 / API 34, x86_64), RAM 4GB, cold boot |
| 빌드 | JDK 17, Gradle 8.10.2, Android Gradle Plugin 8.8.2, Kotlin 2.0.0, compileSdk 35 |
| Pleos SDK | Vehicle 2.3.2, FusedLocation 1.1.3, TextToSpeech 3.3.1 |
| 표준 API | Car API `PERF_VEHICLE_SPEED`(차속), SensorManager 가속도, LocationManager(위치 보조) |

**권한**: `pleos.car.permission.CAR_DRIVE / CAR_LIGHTS / CAR_WINDOWS / CAR_SAFETY / CAR_INFO / CAR_SEATS / CAR_CLIMATE / FUSED_LOCATION / TTS_SERVICE`, `android.car.permission.CAR_SPEED`, 위치, 알림, 부팅 완료 수신, 포그라운드 서비스(specialUse). 차량 **제어** 권한은 사용하지 않습니다(읽기 전용).

### 소스에서 빌드

```bash
cd android
./gradlew assembleDebug      # 디버그
./gradlew assembleRelease    # release: android/keystore.properties(서명 정보) 필요, 없으면 미서명 빌드
```

## 구조

```
android/                 Pleos 차량용 앱 (Kotlin)
  app/src/main/java/com/databox/blackbox/
    RecorderService.kt   상시 기록·사고 감지·맡김/주행 일지·벨트 안내
    Signals.kt           링버퍼(최근 60초)
    CrashReport.kt       사고 리포트 사실 추출
    ImpactDirection.kt   충격 방향 자동 보정
    Handover.kt, Trip.kt 맡김 모드·주행 일지
    Share.kt             QR 링크 생성
src/                     휴대폰용 웹 리포트 (React, https://hyundai-contest.vercel.app)
apk/                     제출용 release APK
docs/                    기획·조사 문서
```

## 알려진 사항

- 에어백 전개 신호, 시트 위치 변경은 에뮬레이터에서 미지원/미반영이라 실차에서 확인이 필요합니다.
- Vehicle SDK 2.3.2의 좌석 탑승 값이 표준(VACANT=1, OCCUPIED=2)과 반대로 변환되어 앱에서 보정합니다 (`docs/pleos-research.md`).
- 리포트는 블랙박스 영상을 보완하는 자료이며 법적 판단을 대신하지 않습니다.
