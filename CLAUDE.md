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
- 2026-11-03: 양식 02 전면 재작성(개조식, 대괄호 소제목·장식 대시 제거 → AI 문체 완화), 어린이보호구역 형사처벌(특가법) 근거 추가, 핫스팟 콜드스타트(공공 사고다발지역으로 시드) 문구, 시나리오 숫자를 시연 영상과 일치. 비 올 때 창문 안 여는 코드 수정(AlleyGuard) + release APK 재빌드. 심사용 GitHub 계정 ID는 사이트 어디에도 없음 → 주최 측 문의 필요.
- 2026-11-01: **제출 전 점검** — 사고 리포트 데모가 "→ 초과"로 찍혀 "운전자를 지킨다" 주장과 모순 → 충돌 속도 16km/h(기준 20)로 재녹화해 "준수 ✓"로 교체(영상·양식 스크린샷). /api/road 시드 캐시 TTL 만료로 장애 → 만료 캐시 폴백 추가. RecorderService.onStartCommand에서 startForeground 멱등 호출 보강(ANR 방지). 양식 02에 데이터 소유·공유 문구 추가. 저장소는 아직 PRIVATE(제출 시 Public 전환 또는 심사 계정 Collaborator 초대 필요).
- 2026-10-24 저녁: **골목지킴이 v1.1 구현·검증 완료** — AlleyEngine/AlleyGuard(내비·/api/road·핫스팟·차량신호 → 음성+헤드업 알림+조명/무드등/창문/볼륨/김서림 제어), /api/road(서버 측 OSM 조회+Blob 캐시+시연 구간 시드 scripts/seed-road.mjs), /api/hotspots(Blob), 사고 직후 대응(Emergency), DriveActivity(DO), 기획서 초안 docs/plan.md + 별지 DOCX(scripts/build-plan-docx.cjs → docs/out/), 시연 영상 초안 docs/out/*.mp4(gitignore), release APK apk/골목지킴이_release.apk. **검증 방법**: Virtual Console은 브라우저 탭 대신 `curl -X POST http://127.0.0.1:8088/browser/session?client=x`를 1초마다 보내는 하트비트 루프로 활성 유지(탭이 뒤로 가면 emulator_inactive 됨). 주행 중(D)에는 플랫폼 주행뷰가 앱을 가림 → 헤드업 알림으로 안내(동작 확인). 에뮬레이터 한계: 전조등 set 오류 50001, 안개등 미지원. 참가자: 백종현(개인·일반). 남은 것: 공식 양식 02 박스 채우기, 공공데이터 사고다발지역(API 키 필요), 시연 영상 자막.
- 2026-10-24: **메인 확정: 골목지킴이**(골목 보행자 돌발 진입 예방) + 보조 데이터 블랙박스. 전체 할 일·우선순위는 **docs/roadmap.md** (1단계 11/15, 2단계 12/3). 불가 기능 목록도 거기에 — 다시 검토하지 말 것.
- (주제, 핵심 기능, 결정한 내용을 여기에 계속 기록 — 두 PC의 Claude가 이 파일로 맥락을 공유함)
- 2026-10-24: **데이터 블랙박스 v1.0 (후보 기능 완료)** — 패키지 com.databox.blackbox. 부팅 자동 시작(BootReceiver+START_STICKY), 충격 방향 자동 보정(MountCalibration), 주행 일지 30일(trips/*.json.gz), 안전벨트 음성 안내(Pleos TTS ON_DEVICE)+기록, release APK(apk/), 제출용 README. 남은 것: APK 파일명에 성명 넣기(팀명_앱이름_release.apk 규정).
- 2026-10-24: **데이터 블랙박스 v0.2** — 맡김 모드(발레/정비/대리/가족: 거리·최고속도·급제동/급가속·바뀐 설정·경로, 의심 사유 판단), QR → https://hyundai-contest.vercel.app/#r=… (deflate-raw+base64url, fragment라 서버 미전송) 웹 리포트(지도 포함), 카드형 UI. 에뮬레이터 한계: 시트 위치 콘솔 조작이 VHAL에 미반영, Pleos 위치 미수신(표준 GPS로 보조, adb emu geo fix).
- 2026-10-24: **데이터 블랙박스 v0.1 동작 확인** (android/). 상시 기록 서비스 + 충격(가속도 2.5G)·에어백으로 사고 감지 → 전30초/후10초 저장 → 리포트·그래프. 차속은 표준 Car API PERF_VEHICLE_SPEED(CAR_SPEED 런타임 권한). 에뮬레이터 차량 조작은 Virtual Console(adb forward tcp:8088, http://127.0.0.1:8088, PUT /vehicle/controls?speed_kph=, /vehicle/gear?gear=Drive, /vehicle/turn-signal?mode=BlinkLeft, brake=) — 브라우저 탭이 열려 있어야 동작. 앱 데이터는 user 10 (run-as --user 10). 에어백은 에뮬레이터 미지원.
- 2026-10-24: Pleos 에뮬레이터 + 테스트 앱(android/, 패키지 com.shimpyo.app) 연동 확인. 빌드는 JDK 17(Temurin) 필요 — Android Studio 내장 JBR 25는 Gradle 8.10과 안 맞음. 공조 온도는 에뮬레이터에서 아직 값 없음(Virtual Console 확인 필요).
- 2026-10-24: **일반 부문 · 개인 참가** 확정. 공식 접수양식 다운로드(docs/form/), 기획서 초안 틀을 양식 항목 순서로 재구성(docs/plan.md). 아이디어는 미정 — 후보는 docs/ideas.md. "차량용 데이터 블랙박스"는 후보(메인 아님)로 보류.

## 작업 방식 (사용자 선호)
- **사용자 화면에 창을 띄우지 말 것**(사용자가 다른 작업 중). 에뮬레이터는 `-qt-hide-window -no-audio`로 띄운다(`-no-window`는 앱 화면이 안 그려져 부적합). 크롬 탭·팝업도 필요할 때만.
- 시연 녹화 시 에뮬레이터가 메모리 부족이면 ANR·시스템 재시작이 난다 → 긴 녹화는 구간으로 나눠 찍고 ffmpeg로 이어 붙인다(scratchpad splice 방식). 부팅 직후 Wi-Fi가 안 붙으면 `adb shell svc wifi disable/enable`.
- 승인 확인 없이 진행하고 결과만 보고. 작업 끝나면 Windows 알림(Stop hook).
- 알림은 PC별 설정: `.claude/notify.ps1`을 `~/.claude/notify.ps1`로 복사하고 `~/.claude/settings.json`에 Stop hook 등록. 새 PC에서 알림이 안 오면 이 작업부터 할 것.
