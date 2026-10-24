// 시연 영상 편집: 녹화본 + 타임라인(timeline.txt) 기반 자막 + 타이틀/엔딩 카드
// 사용: node scripts/make-demo-video.mjs <take.mp4> <timeline.txt> [끝나는 마커=REC_END]
import { execFileSync } from 'node:child_process'
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const [,, take, timelineFile, endMarker = 'T_END'] = process.argv
if (!take || !timelineFile) { console.error('usage: make-demo-video.mjs <take.mp4> <timeline.txt> [endMarker]'); process.exit(1) }

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..')
const bin = (() => {
  const base = path.join(process.env.LOCALAPPDATA, 'Microsoft/WinGet/Packages')
  const pkg = readdirSync(base).find((d) => d.startsWith('Gyan.FFmpeg'))
  const ver = readdirSync(path.join(base, pkg)).find((d) => d.startsWith('ffmpeg-'))
  return path.join(base, pkg, ver, 'bin', 'ffmpeg.exe')
})()
const work = path.join(root, 'docs/out/_video'); mkdirSync(work, { recursive: true })
const font = '/Windows/Fonts/malgunbd.ttf' // 드라이브 상대 경로 (cwd가 C:)
const fontR = '/Windows/Fonts/malgun.ttf'

// 타임라인: "<epoch> <MARKER>" → 녹화 시작 기준 초
const marks = {}
for (const line of readFileSync(timelineFile, 'utf8').split('\n')) {
  const [t, name] = line.trim().split(/\s+/); if (name) marks[name] = Number(t)
}
const t0 = marks.REC_START
const at = (m, d = 0) => +((marks[m] ?? t0) - t0 + d).toFixed(1)
const trimEnd = at(endMarker)

// 자막: 마커 사이 구간. 이모지는 폰트에 없어 쓰지 않는다. 긴 문장은 \n으로 두 줄.
const seq = [
  ['REC_START', '골목지킴이 · Pleos Connect Emulator 시연', '메인 화면 — 상시 기록 중 · 골목 모드 대기'],
  ['T_DRIVE', '1. 큰길 주행', '강남 선릉로 · 40km/h · 일반 도로'],
  ['T_TURN', '2. 진입 예고 — 좌회전 깜빡이 + 핸들 감지', '음성 "곧 골목길입니다. 브레이크에 발을 올리고\n30 이하로 준비하세요"'],
  ['T_ALLEY', '3. 골목 모드 진입 · 이면도로(제한 20)', '음성 "골목길 진입. 20 이하로 서행하세요"\n무드등 호박색 · 창문 10% 개방 · 볼륨 절반 · 헤드업 알림'],
  ['T_OVERSPEED', '4. 과속 35km/h → 위험 경고', '음성 "위험! 버스정류장 56m 앞. 즉시 감속하세요"\n무드등 적색'],
  ['T_SLOW', '5. 15km/h로 감속', '기준 이내 → 안전 단계'],
  ['T_EXIT', '6. 큰길 복귀', '음성 "골목을 벗어났습니다"\n창문 · 볼륨 · 조명 원래대로'],
  ['T_ALLEY2', '7. 다시 골목 — 보행자 돌발 상황 가정', '음성 "골목길 진입. 20 이하로…"'],
  ['T_CRASH', '충격 2.8G 감지 → 사고로 판단', '충돌 전 30초 ~ 후 10초 차량 데이터 자동 저장\n(속도 · 브레이크 · 깜빡이 · 조명 · 골목 모드)'],
  ['T_CRASH_CARD', '8. 정차 후 — 사고 감지 카드', '보험사 전화 · 112 신고 · 위치 복사 · QR로 휴대폰 리포트'],
  ['T_REPORT', '9. 사고 리포트', '골목 모드 기준 20 · 충돌 시 21 → 초과 / 충돌 8초 전 음성 안내 수신\n브레이크 시점 · 속도 그래프 · 사고 지점'],
  ['T_VALET_START', '10. 맡김 모드 — 발레파킹에 맡김', '맡긴 동안 90km/h 운행 · 급가속 · 에어컨 변경 (가상 재현)'],
  ['T_VALET_RETURN', '11. 돌려받기', '맡긴 동안의 주행 기록을 리포트로'],
  ['T_VALET_REPORT', '12. 맡김 리포트', '이동 거리 · 최고 속도 · 급제동/급가속 · 바뀐 설정 · 경로\n→ 용도별 의심 운행 판정'],
]
const caps = seq.map(([m, top, text], i) => ({ s: at(m), e: i + 1 < seq.length ? at(seq[i + 1][0], -0.1) : trimEnd, top, text })).filter((c) => c.e > c.s)

const lines = []
const tf = (p) => path.relative(root, p).replace(/\\/g, '/')
caps.forEach((c, i) => {
  const topFile = path.join(work, `top${i}.txt`); writeFileSync(topFile, c.top)
  const txtFile = path.join(work, `txt${i}.txt`); writeFileSync(txtFile, c.text)
  const en = `enable='between(t,${c.s},${c.e})'`
  lines.push(`drawtext=fontfile=${font}:textfile=${tf(topFile)}:expansion=none:${en}:x=40:y=26:fontsize=30:fontcolor=white:box=1:boxcolor=0x0B1A2E@0.85:boxborderw=14`)
  lines.push(`drawtext=fontfile=${fontR}:textfile=${tf(txtFile)}:expansion=none:${en}:x=40:y=h-118:fontsize=24:line_spacing=8:fontcolor=white:box=1:boxcolor=black@0.62:boxborderw=14`)
})
const filterFile = path.join(work, 'filters.txt'); writeFileSync(filterFile, lines.join(','))

const run = (args) => { console.log('ffmpeg', args.slice(0, 4).join(' '), '...'); execFileSync(bin, ['-y', '-loglevel', 'error', ...args], { stdio: 'inherit', cwd: root }) }
const esc = (s) => s.replace(/\\/g, '\\\\').replace(/'/g, '\u2019').replace(/:/g, '\\:').replace(/%/g, '%%')

const main = path.join(work, 'main.mp4')
run(['-i', take, '-t', String(trimEnd), '-/vf', tf(filterFile), '-r', '30', '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-an', main])

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

const list = path.join(work, 'list.txt')
writeFileSync(list, [title, main, ending].map((f) => `file '${f.replace(/\\/g, '/')}'`).join('\n'))
const out = path.join(root, 'docs/out/백종현_골목지킴이_시연영상.mp4')
run(['-f', 'concat', '-safe', '0', '-i', list, '-c', 'copy', out])
console.log('done →', out, '| 본편', trimEnd, '초, 자막', caps.length, '개')
