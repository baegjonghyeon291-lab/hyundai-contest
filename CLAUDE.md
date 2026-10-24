# 현대자동차 앱 개발 공모전

공모전 제출용 **계획서 + 목업 데이터 + 프로토타입**을 함께 만드는 저장소.

## 작업 환경
- 집 PC와 회사 PC 두 곳에서 작업한다. GitHub 저장소가 원본이다.
- 작업 시작 시 `git pull`, 끝날 때 `git push`. push하면 Vercel이 자동 배포한다.
- Node 버전은 `.nvmrc` 참고.
- 배포 URL: https://hyundai-contest.vercel.app (Vercel 프로젝트: baegjonghyeon291-labs-projects/hyundai-contest)

## 구조
- `docs/` — 계획서 (Markdown). `docs/plan.md`가 메인.
- `src/mock/` — 목업 데이터 (JSON). 프로토타입은 이 데이터만 읽는다.
- `src/` — 프로토타입 (Vite + React + TypeScript, 모바일 화면 형태의 웹앱).

## 명령어
- `npm run dev` — 로컬 실행
- `npm run build` — 빌드 확인 (push 전에 돌려볼 것)

## 공모전 핵심 (상세: docs/contest.md)
- 현대차·기아 차량용 앱 개발 공모전. **Pleos Playground 공개 API** 활용 필수.
- 분야: 차량 내 엔터테인먼트 / 운전자 건강·생활 / 안전 운전 / 가족·동승자 편의
- **마감 2026-11-15(일)**. 접수양식(DOCX)=기획서 필수. 시연 영상·GitHub(소스+APK)는 가산점. 2차 PT(12/3~4) 전까지 실제 동작 앱 필요.
- 심사: 차량특화성 / 창의성 / 기술 활용성(Pleos SDK) / 사업성(MAU·Retention). 공식: https://appdevchallenge.auto-hmg.io/
- 수상작은 실제 차량 출시 검토 → 실현 가능성·Pleos API 활용도가 중요

## 진행 상황 / 결정 사항
- 2026-10-24: **메인 확정: 골목지킴이**(골목 보행자 돌발 진입 예방) + 보조 데이터 블랙박스. 전체 할 일·우선순위는 **docs/roadmap.md** (1단계 11/15, 2단계 12/3). 불가 기능 목록도 거기에 — 다시 검토하지 말 것.
- (주제, 핵심 기능, 결정한 내용을 여기에 계속 기록 — 두 PC의 Claude가 이 파일로 맥락을 공유함)
- 2026-10-24: **데이터 블랙박스 v1.0 (후보 기능 완료)** — 패키지 com.databox.blackbox. 부팅 자동 시작(BootReceiver+START_STICKY), 충격 방향 자동 보정(MountCalibration), 주행 일지 30일(trips/*.json.gz), 안전벨트 음성 안내(Pleos TTS ON_DEVICE)+기록, release APK(apk/), 제출용 README. 남은 것: APK 파일명에 성명 넣기(팀명_앱이름_release.apk 규정).
- 2026-10-24: **데이터 블랙박스 v0.2** — 맡김 모드(발레/정비/대리/가족: 거리·최고속도·급제동/급가속·바뀐 설정·경로, 의심 사유 판단), QR → https://hyundai-contest.vercel.app/#r=… (deflate-raw+base64url, fragment라 서버 미전송) 웹 리포트(지도 포함), 카드형 UI. 에뮬레이터 한계: 시트 위치 콘솔 조작이 VHAL에 미반영, Pleos 위치 미수신(표준 GPS로 보조, adb emu geo fix).
- 2026-10-24: **데이터 블랙박스 v0.1 동작 확인** (android/). 상시 기록 서비스 + 충격(가속도 2.5G)·에어백으로 사고 감지 → 전30초/후10초 저장 → 리포트·그래프. 차속은 표준 Car API PERF_VEHICLE_SPEED(CAR_SPEED 런타임 권한). 에뮬레이터 차량 조작은 Virtual Console(adb forward tcp:8088, http://127.0.0.1:8088, PUT /vehicle/controls?speed_kph=, /vehicle/gear?gear=Drive, /vehicle/turn-signal?mode=BlinkLeft, brake=) — 브라우저 탭이 열려 있어야 동작. 앱 데이터는 user 10 (run-as --user 10). 에어백은 에뮬레이터 미지원.
- 2026-10-24: Pleos 에뮬레이터 + 테스트 앱(android/, 패키지 com.shimpyo.app) 연동 확인. 빌드는 JDK 17(Temurin) 필요 — Android Studio 내장 JBR 25는 Gradle 8.10과 안 맞음. 공조 온도는 에뮬레이터에서 아직 값 없음(Virtual Console 확인 필요).
- 2026-10-24: **일반 부문 · 개인 참가** 확정. 공식 접수양식 다운로드(docs/form/), 기획서 초안 틀을 양식 항목 순서로 재구성(docs/plan.md). 아이디어는 미정 — 후보는 docs/ideas.md. "차량용 데이터 블랙박스"는 후보(메인 아님)로 보류.

## 작업 방식 (사용자 선호)
- 승인 확인 없이 진행하고 결과만 보고. 작업 끝나면 Windows 알림(Stop hook).
- 알림은 PC별 설정: `.claude/notify.ps1`을 `~/.claude/notify.ps1`로 복사하고 `~/.claude/settings.json`에 Stop hook 등록. 새 PC에서 알림이 안 오면 이 작업부터 할 것.
