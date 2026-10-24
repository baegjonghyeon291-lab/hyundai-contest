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

## 진행 상황 / 결정 사항
- (주제, 핵심 기능, 결정한 내용을 여기에 계속 기록 — 두 PC의 Claude가 이 파일로 맥락을 공유함)
