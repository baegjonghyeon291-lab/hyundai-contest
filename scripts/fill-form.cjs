// 공식 접수양식(docs/form/contest-application-consent.docx)의 02 '앱 프로젝트 소개' 8개 박스를 채운다.
// 01 참가신청서·04 서명은 개인정보라 건드리지 않는다(사용자가 직접 기입).
// 실행: node scripts/fill-form.cjs → docs/out/골목지킴이_접수양식_02작성.docx
const fs = require('fs')
const path = require('path')
const JSZip = require('jszip')

const root = path.join(__dirname, '..')
const src = path.join(root, 'docs/form/contest-application-consent.docx')
const hasContact = fs.existsSync(path.join(root, 'submit/contact.json'))
const out = path.join(root, hasContact ? 'submit/골목지킴이_접수양식_제출용.docx' : 'docs/out/골목지킴이_접수양식_02작성.docx')

// 박스별 내용 (양식에 보이는 번호 기준). 분량 제한 없음이지만 박스는 요약, 상세는 별지.
const answers = {
  1: `골목에 들어서기 전에 운전자가 속도를 줄이게 만들고, 사고가 나면 '내 차가 어떻게 달렸는지'를 숫자로 남기는 Pleos 차량용 앱입니다.
내비·지도·차량 신호로 골목 진입을 알아채 서행을 안내하고 전조등·무드등·창문을 자동으로 맞춥니다. 여러 차의 급제동 지점을 모아 '사고 날 뻔한 곳'을 미리 알려주고, 충격이 감지되면 충돌 전후 40초의 차량 데이터를 리포트로 남깁니다.`,
  2: `1) 주목한 순간
- 주택가 골목에서 사람이 갑자기 나오면 차가 천천히 가도 멈추기엔 늦는 경우가 많음. 보행자 과실이 커도 차 대 사람 사고에서는 운전자가 가해자가 됨.
- 어린이보호구역에서는 제한속도(30km/h)를 지켰는지에 따라 형사처벌 수위가 달라짐(특정범죄가중처벌법). 그런데 사고가 난 뒤에 "지켰다"를 증명할 방법이 운전자에게 없음.
- 블랙박스는 앞 영상만 찍음. 내 차의 속도·브레이크·방향지시등·전조등은 기록하지 않음.
2) 왜 차 안에서 풀어야 하나
- 보행자가 어디서 나올지는 알 수 없지만 어디서 자주 나오는지는 뻔함. 골목, 횡단보도 직전, 버스정류장 옆, 학교 앞, 그리고 다른 운전자들이 급제동한 바로 그 지점.
- 휴대폰은 차가 골목에 들어섰는지, 지금 속도·브레이크·전조등 상태가 어떤지 모름. 차는 알고 있고, 전조등·무드등·창문·음성을 직접 다룰 수 있음.
- Pleos Connect가 이 신호(차속·기어·방향지시등·브레이크·조명·와이퍼·핸들·좌석·문)와 제어(조명·창문·공조·음성)를 앱에 열어 주었기 때문에 가능해진 기획임.`,
  3: `1) 골목 감속 가이드 (핵심)
- 진입 감지: 내비 주의지점(좁은 길·어린이보호구역·사고다발·과속방지턱, 남은 거리) + 지도의 도로 등급·제한속도(이면도로, 20·30 구간) + 차량 신호(방향지시등·핸들 각도·다음 회전)를 합쳐 판단
- 진입 50m 전 예고: "곧 골목길입니다. 브레이크에 발을 올리고 30 이하로 준비하세요". 제동을 대신해 주는 것이 아니라 운전자가 미리 준비하게 함
- 기준 속도: 골목 30km/h, 어린이보호구역·등하교 시간·보행자 구역 20km/h. 초과하면 음성 + 무드등 적색, 횡단보도·정류장 앞 과속은 즉시 감속 안내
2) 급제동 핫스팟 (핵심)
- 여러 차의 급제동 지점(위치·시각·직전 속도만, 식별 정보 없음)을 50m 격자로 모음. 접근하면 "이 구간에서 다른 운전자들이 최근 N번 급제동했어요"
- 공공 사고다발지역 데이터는 '사고 난 곳', 이것은 '사고 날 뻔한 곳'. 초기에는 공공 사고다발지역과 내비 주의지점으로 채우고, 차가 늘수록 실제 급제동 데이터로 바뀜
3) 데이터 블랙박스 + 사고 직후 대응 (핵심)
- 차속(차량 신호)·기어·방향지시등·브레이크·조명·와이퍼·핸들·위치·가속도를 상시 기록(1시간 약 2MB). 충격 2.5G 또는 에어백 전개 시 충돌 전 30초~후 10초 저장
- 리포트: 충돌 속도, 브레이크 시점과 감속량, 방향지시등, 충격 방향(자동 보정), 안전벨트, 골목 모드 기준 속도 준수 여부·안내 수신 여부·자동 점등 여부
- 정차하면 보험사·112 전화, 위치 복사, QR로 휴대폰에 리포트 전달(기록은 링크 안에만 담겨 서버에 남지 않음)
4) 차라서 되는 보조 기능
- 골목 진입 시 주간에도 전조등·안개등 점등(사람이 차를 알아채게) / 미디어 볼륨 절반 + 운전석 창문 10%(운전자가 바깥 소리를 듣게, 비 올 땐 창문 제외) / 야간 실내등 소등·우천 김서림 제거(시야) / 뒷좌석 하차 시 "뒤 확인" 안내 / 안전벨트 미착용 음성 안내와 기록 / 맡김 모드(발레·정비·대리·가족 운행 리포트) / 주행 일지 30일
5) 기존과 다른 점
- 내비 제한속도 경고는 법정 속도만 보지만, 골목이면 더 낮게, 진입 전에 알림
- AEB는 튀어나온 뒤에 반응하지만, 튀어나오기 전에 멈출 수 있는 속도를 만듦
- 블랙박스는 영상만 남기지만, 내 차의 행동을 숫자로 남김`,
  4: `시나리오 A. 퇴근길 주택가, 저녁 7시, 비
1. 큰길에서 좌회전 방향지시등을 켜자 "곧 골목길입니다. 브레이크에 발을 올리고 30 이하로 준비하세요"
2. 이면도로 진입. 무드등이 호박색으로 바뀌고 전조등·안개등이 켜지며 음악이 작아짐. 비가 와서 창문은 그대로 두고 앞유리 김서림 제거가 켜짐. "골목길 진입. 30 이하로 서행하세요. 비가 와서 제동거리가 길어요"
3. 버스정류장 59m 전에서 속도 35 → "기준 속도 30을 넘었어요", 무드등 적색. 이어서 "이 구간에서 다른 운전자들이 최근 7번 급제동했어요". 운전자가 20까지 줄임
4. 큰길로 나오면 "골목을 벗어났습니다". 볼륨·조명이 원래대로
시나리오 B. 그래도 사고가 났을 때 (시연 영상과 동일)
1. 이면도로(제한 20)에서 16km/h로 서행 중 보행자 접촉, 충격 2.8G. 앱이 전후 40초를 저장
2. 정차하자 "사고가 감지되었습니다" 카드. 보험사 전화·112·위치 복사 버튼
3. 리포트: 골목 모드 기준 20 · 충돌 시 16 → 준수 / 충돌 6초 전 음성 안내 수신 / 전조등 자동 점등 / 브레이크 충돌 0.9초 전
4. QR을 휴대폰으로 찍어 보험사에 그대로 전달. 블랙박스 영상이 '무슨 일이 있었나'를 보여 준다면, 이 리포트는 '운전자가 할 수 있는 걸 다 했나'를 보여 줌
시나리오 C. 발레파킹
"발레파킹" 버튼을 누르고 맡김 → 돌려받으면 "0.4km 이동, 최고 90km/h, 급가속 4회, 에어컨 24→19℃" 리포트. 분쟁 대신 기록으로 이야기함
대상: 주택가·학교 근처를 매일 지나는 출퇴근 운전자, 택시·대리·배달·카셰어링 운전자, 초보·고령 운전자의 가족`,
  5: `1) 구현 완료 (Pleos Connect Emulator에서 검증)
- 상시 기록 서비스(부팅 자동 시작), 충격·에어백 사고 감지, 사고 리포트·QR·웹 리포트, 맡김 모드, 주행 일지, 안전벨트 음성 안내, 충격 방향 보정, 골목 감지 엔진, 골목 음성 안내·헤드업 알림·조명·무드등·창문·볼륨 제어, 급제동 핫스팟 서버, 사고 직후 대응 화면, 주행 중 DO 화면
2) 사용한 Pleos SDK / API
- 차속: Car API PERF_VEHICLE_SPEED (계기판과 같은 출처)
- 기어·브레이크·방향지시등·핸들·조명·와이퍼: Vehicle SDK 조회 콜백
- 전조등·안개등·실내등·무드등·창문·김서림: Vehicle SDK Light·Window·HVAC 제어 (골목을 벗어나면 복원, 설정에서 개별 해제 가능)
- 좌석 탑승·벨트·문·에어백: Vehicle SDK Seat·Door·Safety
- 내비 주의지점·다음 회전·제한속도: NaviHelper SDK / 위치: FusedLocation SDK (+표준 GPS 보조) / 음성: TextToSpeech SDK (기기 내장, 한국어)
- 도로 등급·제한속도·횡단보도·정류장·학교: 자체 서버 /api/road (OpenStreetMap, 55m 격자 캐시) / 급제동 핫스팟: 자체 서버 /api/hotspots (익명 집계)
3) 주의분산·안전 설계
- 주행 중에는 화면 대신 음성 + 헤드업 알림, 주행용 화면은 Distraction Optimized(큰 글자 몇 개). 모든 자동 제어는 사용자가 끌 수 있음. 제동·가속 제어, 레이더·카메라, 외부음은 사용하지 않음
4) 데이터 소유·공유
- 기록은 운전자 소유. 차량 기기에만 저장되고, 공유는 운전자가 QR로 직접 보낼 때만(서버 미저장). 리포트는 준수·초과 모두 있는 그대로 기록. 급제동 핫스팟은 위치·시각·직전 속도만 익명으로 올림
5) 개발 중 확인한 플랫폼 이슈
- Vehicle SDK 2.3.2의 좌석 탑승 값이 표준(VACANT=1/OCCUPIED=2)과 반대로 변환되는 것을 확인해 앱에서 보정. 에어백·시트 위치·안개등은 에뮬레이터 미지원이라 실차 검증 항목으로 분리
6) 2차 PT까지
- 실차 환경 검증, 핫스팟 실시간 알림(앞 차가 방금 급제동), 등하교 시간 어린이보호구역 우회 제안, 주간 골목 안전 점수`,
  6: `1) 사용자 가치
- 골목 사고의 결정 변수는 속도. 50→30km/h로 들어가면 정지거리가 절반 이하로 줄어 "봤을 때 이미 늦은" 상황 자체가 줄어듦
- 사고가 나도 "서행했고, 안내를 받았고, 조명을 켰다"는 사실이 남아 과실 다툼에서 운전자를 지킴. 어린이보호구역이라면 형사처벌 수위를 가르는 증거가 됨
2) 타깃·규모
- 주택가·학교 근처 출퇴근 운전자, 택시·대리·배달·카셰어링 운전자, 초보·고령 운전자 가족. Pleos Connect 탑재 차량이 늘수록 모수가 커짐(공개된 탑재 대수가 없어 PT에서 보수적 가정으로 산정)
3) MAU·Retention
- 상시 기록 앱이라 설치 후 매 주행이 사용. 부팅 자동 시작이라 설치 유지율이 곧 MAU. 주간 골목 안전 점수·리포트(2차 확장)로 재방문을 만듦
4) 사업성
- B2C: 기본 무료 + 보험사 UBI 연계 할인(골목 서행 준수율)
- B2B: 렌터카·카셰어링·택시 플릿의 맡김 리포트·사고 리포트
- B2G·B2B 데이터: 급제동 핫스팟 지도(지자체 펜스·방지턱·표지 설치 근거, 보험사 위험 구간 데이터)
5) 향후 확장
- Pleos가 ADAS API(추적 물체·충돌 경고·운전자 모니터링·외부음)를 앱에 개방하면 센서 입력만 교체해 즉시 연동(현재 구조 유지). 실시간 급제동 알림, 어린이보호구역 우회, 보행자 앱 연동(V2P), 주간 안전 점수`,
  7: `별지: 골목지킴이_기획서_별지.docx (상세 기획 + 그림: 사용 흐름도, 아키텍처·Pleos API 매핑, 에뮬레이터 화면 캡처 7장)
시연 영상: 백종현_골목지킴이_시연영상.mp4 (Pleos Connect Emulator에서 골목 진입·경고·사고 리포트·맡김 리포트 재현, 2분 20초)
GitHub(소스 + APK): https://github.com/baegjonghyeon291-lab/hyundai-contest (apk/ 폴더, README에 실행 방법과 테스트 환경)
웹 리포트·프로토타입: https://hyundai-contest.vercel.app`,
  8: `(없음)`,
}

// 박스 아래에 넣을 그림. 입력 박스(순수 텍스트 컨트롤) 안에는 그림이 못 들어가므로 같은 표 칸에 문단을 덧붙인다.
// [[그림, 그림]] 한 묶음 = 한 줄 (2개면 반 폭씩). 캡션은 그림 아래 작은 글씨.
const img = (file, caption) => ({ file: path.join(root, 'docs/img', file), caption })
const figures = {
  3: [[img('architecture.png', '그림 1. 아키텍처 — Pleos SDK·Car API·자체 서버 매핑')]],
  4: [
    [img('flow.png', '그림 2. 사용 흐름 — 큰길 → 진입 예고 → 골목 모드 → 경고 → 복귀 / 사고 → 리포트')],
    [img('shots/02_주행중_골목진입_헤드업안내.png', '화면 1. 골목 진입 — 음성 + 헤드업 안내'), img('shots/03_주행중_위험경고_정류장앞과속.png', '화면 2. 정류장 앞 과속 — 위험 경고')],
    [img('shots/04_사고리포트_골목모드준수.png', '화면 3. 사고 리포트 — 골목 모드 기준·안내 수신·브레이크 시점'), img('shots/05_맡김리포트_발레파킹.png', '화면 4. 맡김 리포트 — 발레파킹')],
  ],
  5: [[img('shots/01_메인_사고감지_골목모드.png', '화면 5. 메인 — 사고 감지 카드 · 골목 모드 · 설정'), img('shots/07_QR_휴대폰전송.png', '화면 6. QR → 휴대폰 웹 리포트')]],
}
const CELL_W = 9866 - 2 * 110 - 40 // 표 폭(dxa) − 셀 여백 − 테두리 여유
const EMU_PER_DXA = 635
let drawingId = 100
function drawingXml(rId, cx, cy, name) {
  return `<w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" distT="0" distB="0" distL="0" distR="0"><wp:extent cx="${cx}" cy="${cy}"/><wp:docPr id="${drawingId}" name="${name}"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="${drawingId++}" name="${name}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="${rId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>`
}
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
function runsFor(text) {
  return text.split('\n').map((line, i) =>
    (i > 0 ? '<w:r><w:br/></w:r>' : '') + `<w:r><w:rPr><w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic"/><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve">${esc(line)}</w:t></w:r>`,
  ).join('')
}

;(async () => {
  const zip = await JSZip.loadAsync(fs.readFileSync(src))
  let xml = await zip.file('word/document.xml').async('string')
  // 제목 텍스트가 여러 run으로 쪼개져 있을 수 있어, 확실히 한 run에 있는 '1. 앱 한 줄 소개'를 기준으로 잡는다
  const start = xml.lastIndexOf('<w:p', xml.indexOf('1. 앱 한 줄 소개'))
  const end = xml.indexOf('개인정보 수집') // 03 동의서 제목 (가운뎃점 글자가 달라 앞부분만 매칭)
  if (start < 0 || end < 0) throw new Error('양식에서 02 섹션을 찾지 못함')
  let section = xml.slice(start, end)

  // 각 sdt 앞에서 가장 가까운 "N. " 제목을 찾아 번호로 매핑한다
  let count = 0
  // 02 섹션의 입력 박스는 보이는 번호 순서(1~8)대로 놓여 있으므로 순서로 매핑한다 (제목 텍스트는 run이 쪼개져 신뢰 불가)
  // 그림 파일을 word/media에 넣고 관계 ID를 만든다 (가로 1600px로 줄여 용량 절약)
  const sharp = require('sharp')
  let rels = await zip.file('word/_rels/document.xml.rels').async('string')
  let relN = 900
  const figParagraphs = {}
  for (const [n, rows] of Object.entries(figures)) {
    let out = ''
    for (const row of rows) {
      const w = row.length > 1 ? Math.floor(CELL_W * 0.47) : CELL_W
      let runs = '', caps = ''
      for (const f of row) {
        const buf = await sharp(f.file).resize({ width: 1600, withoutEnlargement: true }).png().toBuffer()
        const meta = await sharp(buf).metadata()
        const name = `fig${relN}.png`
        zip.file(`word/media/${name}`, buf)
        const rId = `rId${relN++}`
        rels = rels.replace('</Relationships>', `<Relationship Id="${rId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${name}"/></Relationships>`)
        const cx = w * EMU_PER_DXA, cy = Math.round(cx * meta.height / meta.width)
        runs += (runs ? '<w:r><w:t xml:space="preserve">  </w:t></w:r>' : '') + drawingXml(rId, cx, cy, f.caption)
        caps += (caps ? '<w:r><w:tab/></w:r>' : '') + `<w:r><w:rPr><w:rFonts w:ascii="Malgun Gothic" w:eastAsia="Malgun Gothic" w:hAnsi="Malgun Gothic"/><w:color w:val="555555"/><w:sz w:val="16"/></w:rPr><w:t xml:space="preserve">${esc(f.caption)}</w:t></w:r>`
      }
      out += `<w:p><w:pPr><w:keepNext/><w:spacing w:before="120" w:after="0"/><w:jc w:val="center"/></w:pPr>${runs}</w:p>`
      out += `<w:p><w:pPr><w:spacing w:before="0" w:after="60"/><w:jc w:val="center"/></w:pPr>${caps}</w:p>`
    }
    figParagraphs[n] = out
  }
  zip.file('word/_rels/document.xml.rels', rels)
  let ct = await zip.file('[Content_Types].xml').async('string')
  if (!ct.includes('Extension="png"')) ct = ct.replace('<Default ', '<Default Extension="png" ContentType="image/png"/><Default ')
  zip.file('[Content_Types].xml', ct)

  section = section.replace(/<w:sdt>([\s\S]*?)<w:sdtContent>[\s\S]*?<\/w:sdtContent><\/w:sdt><\/w:p>/g, (m, props) => {
    const n = ++count
    if (!answers[n]) return m
    return `<w:sdt>${props}<w:sdtContent>${runsFor(answers[n])}</w:sdtContent></w:sdt></w:p>${figParagraphs[n] || ''}`
  })
  // 양식의 박스 행은 '페이지를 넘어 나뉘지 않음'이라 내용이 길면 행 전체가 다음 쪽으로 밀려 빈 쪽이 생긴다 → 나뉘게 허용
  section = section.split('<w:cantSplit/>').join('')
  xml = xml.slice(0, start) + section + xml.slice(end)

  // ── 01 참가신청서: 확정된 사실만 채운다 (이메일·휴대폰·소속·작성일·서명은 사용자가 직접) ──
  const applicant = { name: '백종현', app: '골목지킴이', role: '기획·개발', members: '1' }
  const head = xml.slice(0, start)
  const tail = xml.slice(start)
  let part01 = head
  const check = (label, nth) => { // 라벨 뒤 nth번째 "□" → "■" (선택지 글자가 다른 run에 쪼개져 있어 순서로 고른다)
    let i = part01.indexOf(label)
    if (i < 0) return
    for (let k = 0; k < nth; k++) { i = part01.indexOf('□', i + 1); if (i < 0) return }
    part01 = part01.slice(0, i) + '■' + part01.slice(i + 1)
  }
  check('참가 단위', 1) // 개인
  check('참가 부문', 2) // 일반(스타트업 포함)
  // 라벨 뒤 밑줄(여러 run에 걸침)을 지우고 값을 넣는다. 같은 문단의 다음 라벨 앞까지만.
  const fill = (label, value, stopLabel) => {
    const idx = part01.indexOf(label)
    if (idx < 0) { console.warn('라벨 없음:', label); return }
    const li = part01.lastIndexOf('<w:t', idx) // 라벨이 든 run의 시작부터 (라벨 run 자체도 바꿔야 함)
    const pEnd = part01.indexOf('</w:p>', li)
    let region = part01.slice(li, pEnd)
    const stop = stopLabel ? region.indexOf(stopLabel) : -1
    let target = stop > 0 ? region.slice(0, stop) : region
    const rest = stop > 0 ? region.slice(stop) : ''
    let inserted = false
    target = target.replace(/(<w:t[^>]*>)([^<]*)(<\/w:t>)/g, (m, a, t, b) => {
      let s = t.replace(/_+/g, '')
      if (!inserted && s.includes(label)) { s = s.replace(label, label + ' ' + value); inserted = true }
      return a + s + b
    })
    part01 = part01.slice(0, li) + target + rest + part01.slice(pEnd)
  }
  fill('팀명(개인 참가 시 성명):', applicant.name)
  fill('프로젝트명:', applicant.app)
  fill('대표자 성명:', applicant.name, '역할:')
  fill('역할:', applicant.role)
  fill('참가 인원:', applicant.members, '작성일:')
  // 연락처·작성일 (사용자가 채팅으로 알려 준 값)
  const contactFile = path.join(root, 'submit/contact.json') // 개인정보라 gitignore, 있을 때만 채움
  const contact = fs.existsSync(contactFile) ? JSON.parse(fs.readFileSync(contactFile, 'utf8')) : null
  if (contact) {
  fill('대표자 이메일:', contact.email)
  fill('대표자 휴대폰번호', '')
  {
    // 라벨과 ':'가 다른 run이라 값이 콜론 앞에 들어가지 않게 ': ' run 뒤에 넣는다
    const i = part01.indexOf('대표자 휴대폰번호')
    const j = part01.indexOf(': ', i)
    part01 = part01.slice(0, j + 2) + contact.phone + part01.slice(j + 2)
  }
  {
    const idx = part01.indexOf('작성일: ')
    const li = part01.lastIndexOf('<w:t', idx)
    const pEnd = part01.indexOf('</w:p>', li)
    let seg = part01.slice(li, pEnd), first = true
    seg = seg.replace(/(<w:t[^>]*>)([^<]*)(<\/w:t>)/g, (m, a, t, b) => {
      if (first) { first = false; return a + '작성일: ' + contact.y + '년 ' + contact.m + '월 ' + contact.d + '일' + b }
      return a + b
    })
    part01 = part01.slice(0, li) + seg + part01.slice(pEnd)
  }
  }
  xml = part01 + tail
  // 04 서명 페이지 하단의 앱명도 채운다
  const sigLabel = xml.lastIndexOf('프로젝트명:')
  if (sigLabel > end) {
    const sigIdx = xml.lastIndexOf('<w:t', sigLabel)
    const pEnd = xml.indexOf('</w:p>', sigIdx)
    let p = xml.slice(sigIdx, pEnd)
    const stop = p.indexOf('팀명(')
    let target = stop > 0 ? p.slice(0, stop) : p
    const rest = stop > 0 ? p.slice(stop) : ''
    let done = false
    target = target.replace(/(<w:t[^>]*>)([^<]*)(<\/w:t>)/g, (m, a, t, b) => {
      let s = t.replace(/_+/g, '')
      if (!done && s.includes('프로젝트명:')) { s = s.replace('프로젝트명:', '프로젝트명: ' + applicant.app); done = true }
      return a + s + b
    })
    xml = xml.slice(0, sigIdx) + target + rest + xml.slice(pEnd)
  }
  // 04 서명 표 '대표자' 줄: 성명·동의 체크·서명일자 (사용자가 채팅에서 동의 체크를 직접 지시한 경우에만, 본인 서명은 비워 둠)
  if (contact && contact.consent) {
    const si = xml.indexOf('서명일자')
    const tb = xml.lastIndexOf('<w:tbl>', si), te = xml.indexOf('</w:tbl>', si)
    let tbl = xml.slice(tb, te)
    const rows = tbl.split('<w:tr ')
    const r = rows[2] // [0]=표 머리, [1]=헤더 행, [2]=대표자 행
    if (!r || !r.includes('□ 동의')) throw new Error('04 표의 대표자 행을 찾지 못함')
    const cells = r.split('<w:tc>')
    const run = (t) => '<w:r><w:t>' + t + '</w:t></w:r>'
    cells[2] = cells[2].replace('</w:pPr></w:p>', '</w:pPr>' + run(contact.signName) + '</w:p>')
    cells[3] = cells[3].replace('□ 동의', '■ 동의')
    cells[5] = cells[5].replace('</w:pPr></w:p>', '</w:pPr>' + run(contact.signDate) + '</w:p>')
    rows[2] = cells.join('<w:tc>')
    tbl = rows.join('<w:tr ')
    xml = xml.slice(0, tb) + tbl + xml.slice(te)
  }
  zip.file('word/document.xml', xml)
  fs.mkdirSync(path.dirname(out), { recursive: true })
  fs.writeFileSync(out, await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' }))
  console.log('filled boxes:', count, '→', out)
})()
