# 아이디어 후보

평가 기준(추정): Pleos API 활용도 · 실제 출시 가능성 · 차별성 · 시연 임팩트

| # | 이름(가칭) | 분야 | 한 줄 설명 | 쓰는 API |
|---|---|---|---|---|
| 1 | **쉼표** – 졸음·피로 케어 | 안전 운전 + 건강 | 연속 주행 시간·방향지시등 미사용 차선 변경·경고 정보로 피로도를 추정하고, 단계별로 찬바람·창문 환기·무드등·음성 대화로 깨운 뒤 휴게소로 안내 | Odometer, TurnSignal, Safety, Brake, HVAC, Window, Light, Seat, TTS/STT, NaviHelper |
| 2 | **펫 케어 모드** | 가족·동승자 | 반려동물을 차에 잠시 두고 내릴 때 실내 온도 유지·창문 살짝 열기·문 잠금 유지, 차 화면에 "주인이 곧 돌아와요" 표시, 상태를 휴대폰 알림 | HVAC, Window, Door, Light, EvBattery/Fuel |
| 3 | **탑승 루틴** – 개인화 자동 세팅 | 생활 관리 | 운전자·시간·날씨별로 시트·공조·핸들 열선·무드등을 한 번에 세팅 ("출근 모드", "퇴근 힐링 모드"), 음성으로 실행 | Seat, HVAC, Steeringwheel, SideMirror, Light, STT/TTS |
| 4 | **EV 전비 코치** | 생활 관리 | 주행 습관과 배터리·전비 데이터로 남은 주행거리 코칭, 충전 계획을 경로에 반영 | EvBattery, DrivingMode, Brake, Odometer, NaviHelper, FusedLocation |
| 5 | **안전운전 리포트 & 게임화** | 안전 운전 | 방향지시등 사용률·급제동 등으로 안전 점수를 매기고 배지·가족 랭킹 | TurnSignal, Brake, Safety, Odometer, FusedLocation |

## 추천: 1번 '쉼표'
- 졸음운전은 사회 문제라 심사 설득력이 높고, **안전 + 건강 두 분야**에 걸침
- Vehicle SDK의 **조회 + 제어**를 둘 다 많이 씀 → API 활용도 점수에 유리
- 에뮬레이터에서 주행 시간·지시등 값을 바꿔 가며 **단계별 개입을 시연하기 좋음**
- 카메라(DMS) 없이 차량 데이터만으로 추정 → 모든 Pleos 차량에 바로 적용 가능 (출시 가능성)
