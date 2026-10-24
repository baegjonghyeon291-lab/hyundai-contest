# Pleos Playground 조사 (2026-10-24)

출처: https://document.pleos.ai (Pleos Connect SDK 문서)

## 플랫폼
- **Pleos Connect**: AAOS(Android Automotive OS) 기반 차량 인포테인먼트 시스템
- 앱 = Android 앱 (Kotlin). **Pleos Connect Emulator**(Android Studio AVD, Pleos_Connect_v2.1 / Android 14)로 실차 없이 개발·테스트
- 기준 환경: JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, compile/targetSdk 35, 배포 앱은 minSdk 28 이상
- 앱 마켓(Pleos App Market) 심사 → 승인된 권한만 사용 가능 (미승인 권한 선언/호출 시 심사 탈락)

## SDK 5종
| SDK | 기능 |
|---|---|
| Vehicle SDK | 차량 상태 조회 + 제어 |
| NaviHelper SDK | 경로 안내 제어, 내비 정보 |
| SpeechToText SDK | 음성 인식 |
| TextToSpeech SDK | 음성 합성 |
| FusedLocation SDK | 차량 위치 |

별도: **Vehicle Data API** (클라우드 API, Playground 프로젝트에서 접근 신청 + OAuth 필요)

## Vehicle SDK 도메인 (v2.3.2)
| 도메인 | 조회 | 제어 |
|---|---|---|
| Brake | 브레이크 페달, 주차 브레이크 | |
| CarInfo | 차체 크기, 제조사, 연식 | |
| Display | 온도 단위 등 표시 설정 | O |
| Door | 문 상태 | 열기/닫기, 잠금 |
| DrivingMode | 주행 모드, 크루즈 컨트롤 | |
| EvBattery | 충전 상태, 전비 | |
| Fuel | 연비 등 | |
| HVAC | 온도, 풍량, 습도 | O |
| Light | 조명 상태 | 앰비언트(무드) 조명 |
| Odometer | 총 주행거리, 트립 거리·시간 | |
| Safety | 차량 경고 정보 | |
| Seat | 위치, 각도, 기능 | O |
| SideMirror | 열선 상태 | O |
| Steeringwheel | 상태 | 열선 등 |
| Tire | 타이어 상태 | |
| Toll | 하이패스 음성 볼륨 등 | O |
| TurnSignal | 방향지시등 상태 | |
| Window | 창문 상태 | O |
| Wiper | 와이퍼 상태 | O |

- API 형태: `checkXxxCapabilities`(지원 여부·범위) / `get` / `set` / `register`(변경 알림 구독). 모두 비동기 콜백.
- 권한: `pleos.car.permission.CAR_*`(조회), `CONTROL_CAR_*`(제어). 필요한 최소 권한만 선언.
- 속도는 Vehicle SDK 도메인에 없음 → FusedLocation 위치/속도로 대체 검토.

## 참고
- 2025년 Pleos Connect 앱 아이디어 공모전 대상: **'창밖동화'** (위치 + 생성형 AI로 창밖 풍경 기반 동화) → 비슷한 아이디어는 피할 것
- 샘플 앱: Vehicle SDK Sample App (도메인별 조회/제어/구독 체험)

## SDK 검증 중 발견한 이슈 (2026-10-24)
- **Vehicle SDK 2.3.2 `getCurrentSeatOccupancy` / `registerSeatOccupancy` 값 반전**: SDK가 VHAL 정수 값 `1`을 `true`로 변환하는데, Android 표준 `VehicleSeatOccupancyState`는 `1 = VACANT`, `2 = OCCUPIED`. 에뮬레이터 Virtual Console에서 탑승시킨 좌석이 `false`로 수신됨을 확인 → 앱에서 반전 처리 (`RecorderService.subscribeSeats`). SDK 업데이트 시 재확인 필요.
- **Virtual Console 시트 위치(slide 등) 조작이 VHAL에 반영되지 않음** (콘솔 카탈로그에서도 해당 항목 visible:false).
- **에어백 전개(SEAT_AIRBAGS_DEPLOYED)**: 에뮬레이터 미지원(ERROR_NOT_SUPPORTED_PROPERTY).
- **Pleos FusedLocation**: 에뮬레이터에서 위치 미수신 → 표준 LocationManager(GPS) 병행.
- 차속은 Vehicle SDK에 없음 → 표준 Car API `PERF_VEHICLE_SPEED` (`android.car.permission.CAR_SPEED`, 런타임 권한).
