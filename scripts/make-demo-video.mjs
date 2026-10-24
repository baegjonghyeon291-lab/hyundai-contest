// 시연 영상 편집: 녹화본(take1.mp4) + 타임라인·음성 로그 기반 자막 + 타이틀/엔딩 카드 → docs/out/백종현_골목지킴이_시연영상.mp4
// 사용: node scripts/make-demo-video.mjs <take.mp4> <recStartEpoch> [trimEnd]
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const [,, take, recStartArg, trimEndArg] = process.argv
if (!take || !recStartArg) { console.error('usage: make-demo-video.mjs <take.mp4> <recStartEpoch> [trimEnd]'); process.exit(1) }
const t0 = Number(recStartArg)
const trimEnd = Number(trimEndArg || 135)

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..')
const bin = (() => {
  const base = path.join(process.env.LOCALAPPDATA, 'Microsoft/WinGet/Packages')
  const pkg = readdirSync(base).find((d) => d.startsWith('Gyan.FFmpeg'))
  const ver = readdirSync(path.join(base, pkg)).find((d) => d.startsWith('ffmpeg-'))
  return path.join(base, pkg, ver, 'bin', 'ffmpeg.exe')
})()
const work = path.join(root, 'docs/out/_video'); mkdirSync(work, { recursive: true })
const font = 'C\\:/Windows/Fonts/malgunbd.ttf'
const fontR = 'C\\:/Windows/Fonts/malgun.ttf'

// 자막 (녹화 시작 기준 초). 타임라인 마커 + 음성 로그에서 계산.
const rel = (epoch) => +(epoch - t0).toFixed(1)
const caps = [
  { s: 0.0, e: 3.2, top: '골목지킴이 · Pleos Connect Emulator 시연', text: '메인 화면 — 상시 기록 중 · 골목 모드 대기' },
  { s: 3.3, e: 7.6, top: '① 큰길 주행', text: '강남 선릉로 · 40km/h · 일반 도로' },
  { s: rel(1792844178.2), e: 13.7, top: '② 진입 예고 (좌회전 깜빡이 + 핸들 감지)', text: '🔊 "곧 골목길입니다. 브레이크에 발을 올리고 30 이하로 준비하세요."' },
  { s: rel(1792844184.3), e: 21.9, top: '③ 골목 모드 진입 · 이면도로(제한 20)', text: '🔊 "골목길 진입. 20 이하로 서행하세요."   무드등 호박색 · 창문 10% · 볼륨 ↓ · 헤드업 알림' },
  { s: rel(1792844192.5), e: 30.8, top: '④ 과속 35km/h → 위험 경고', text: '🔊 "위험! 버스정류장 56m 앞. 즉시 감속하세요."   무드등 적색' },
  { s: rel(1792844201.6), e: 36.2, top: '⑤ 15km/h로 감속', text: '기준 이내 → 안전 단계' },
  { s: rel(1792844207.6), e: 45.0, top: '⑥ 큰길 복귀', text: '🔊 "골목을 벗어났습니다."   창문·볼륨·조명 원래대로' },
  { s: rel(1792844215.6), e: 52.4, top: '⑦ 다시 골목 — 보행자 돌발 상황 가정', text: '🔊 "골목길 진입. 20 이하로…"' },
  { s: rel(1792844223.0), e: 69.5, top: '💥 충격 2.8G 감지 → 사고로 판단', text: '충돌 전 30초 ~ 후 10초 차량 데이터 자동 저장 (속도·브레이크·깜빡이·조명·골목 모드)' },
  { s: rel(1792844240.2), e: 76.0, top: '⑧ 정차 후 — 사고 감지 카드', text: '🚨 보험사 전화 · 112 신고 · 📍 위치 복사 · 📱 QR로 휴대폰 리포트' },
  { s: rel(1792844246.5), e: 90.3, top: '⑨ 사고 리포트', text: '골목 모드 기준 20 · 충돌 시 21 → 초과 !   충돌 8초 전 음성 안내 수신 ✓   브레이크 시점 · 속도 그래프 · 사고 지점' },
  { s: rel(1792844261.0), e: 120.1, top: '⑩ 맡김 모드 — 발레파킹에 맡김', text: '맡긴 동안 90km/h 운행 · 급가속 · 에어컨 변경 (가상 재현)' },
  { s: rel(1792844290.7), e: 126.5, top: '⑪ 돌려받기', text: '맡긴 동안의 주행 기록을 리포트로' },
  { s: rel(1792844297.1), e: trimEnd, top: '⑫ 맡김 리포트', text: '이동 거리 · 최고 속도 · 급제동/급가속 · 바뀐 설정 · 경로 → 의심 운행 판정' },
]

const esc = (s) => s.replace(/\\/g, '\\\\').replace(/'/g, "\u2019").replace(/:/g, '\\:').replace(/%/g, '%%')
const lines = []
caps.forEach((c, i) => {
  const topFile = path.join(work, `top${i}.txt`); writeFileSync(topFile, c.top)
  const txtFile = path.join(work, `txt${i}.txt`); writeFileSync(txtFile, c.text)
  const tf = (p) => p.replace(/\\/g, '/').replace(/^([A-Za-z]):/, '$1\\:')
  lines.push(`drawtext=fontfile=${font}:textfile=${tf(topFile)}:enable='between(t,${c.s},${c.e})':x=40:y=28:fontsize=30:fontcolor=white:box=1:boxcolor=0x0B1A2E@0.85:boxborderw=14`)
  lines.push(`drawtext=fontfile=${fontR}:textfile=${tf(txtFile)}:enable='between(t,${c.s},${c.e})':x=40:y=h-86:fontsize=26:fontcolor=white:box=1:boxcolor=black@0.6:boxborderw=14`)
})
const filterFile = path.join(work, 'filters.txt'); writeFileSync(filterFile, lines.join(',\n'))

const run = (args) => { console.log('ffmpeg', args.slice(0, 6).join(' '), '...'); execFileSync(bin, ['-y', '-loglevel', 'error', ...args], { stdio: 'inherit' }) }

// 1) 본편: 자막 입히고 30fps로 재인코딩
const main = path.join(work, 'main.mp4')
run(['-i', take, '-t', String(trimEnd), '-filter_complex_script', filterFile, '-r', '30', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-an', main])

// 2) 타이틀·엔딩 카드
const card = (file, dur, t1, t2, t3) => {
  const f = [
    `drawtext=fontfile=${font}:text='${esc(t1)}':x=(w-text_w)/2:y=h/2-110:fontsize=64:fontcolor=white`,
    `drawtext=fontfile=${fontR}:text='${esc(t2)}':x=(w-text_w)/2:y=h/2-20:fontsize=30:fontcolor=0xC9D6E6`,
    `drawtext=fontfile=${fontR}:text='${esc(t3)}':x=(w-text_w)/2:y=h/2+50:fontsize=24:fontcolor=0x8A9BB4`,
  ].join(',')
  run(['-f', 'lavfi', '-i', `color=c=0x08111F:s=1280x720:d=${dur}:r=30`, '-vf', f, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', file])
}
const title = path.join(work, 'title.mp4')
card(title, 4, '골목지킴이', '골목에서 사람이 튀어나오기 전에 — 서행 안내 · 급제동 핫스팟 · 데이터 블랙박스', '현대자동차그룹 차량용 앱 개발 공모전 · Pleos Connect SDK · 에뮬레이터 시연')
const ending = path.join(work, 'ending.mp4')
card(ending, 5, '골목지킴이', 'github.com/baegjonghyeon291-lab/hyundai-contest  ·  hyundai-contest.vercel.app', '사고 리포트는 블랙박스 영상의 보조 자료입니다 · 모든 자동 제어는 설정에서 해제 가능')

// 3) 이어 붙이기
const list = path.join(work, 'list.txt')
writeFileSync(list, [title, main, ending].map((f) => `file '${f.replace(/\\/g, '/')}'`).join('\n'))
const out = path.join(root, 'docs/out/백종현_골목지킴이_시연영상.mp4')
run(['-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', out])
console.log('done →', out)
