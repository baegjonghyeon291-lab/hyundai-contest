// 기획서 별지(DOCX) 생성: docs/plan.md 내용 + 그림(docs/img/*.png) + 화면 캡처.
// 실행: node scripts/build-plan-docx.cjs  → docs/out/골목지킴이_기획서_별지.docx
const fs = require('fs')
const path = require('path')
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, ImageRun, Table, TableRow, TableCell, WidthType, AlignmentType,
  PageBreak, ShadingType, BorderStyle,
} = require('docx')

const root = path.join(__dirname, '..')
const md = fs.readFileSync(path.join(root, 'docs/plan.md'), 'utf8')
const out = path.join(root, 'docs/out')
fs.mkdirSync(out, { recursive: true })

const FONT = 'Malgun Gothic'
const run = (text, opts = {}) => new TextRun({ text, font: FONT, size: opts.size ?? 20, bold: opts.bold, color: opts.color })
const para = (text, opts = {}) => new Paragraph({ children: [run(text, opts)], spacing: { after: opts.after ?? 100 }, alignment: opts.align })
const image = (file, widthPx) => {
  const buf = fs.readFileSync(file)
  const dims = pngSize(buf)
  const w = widthPx, h = Math.round((dims.h / dims.w) * widthPx)
  return new Paragraph({ children: [new ImageRun({ type: 'png', data: buf, transformation: { width: w, height: h } })], alignment: AlignmentType.CENTER, spacing: { after: 120 } })
}
function pngSize(buf) { return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) } }

// ── 마크다운을 단순 변환: 제목/표/불릿/본문 ──
function mdToParagraphs(src) {
  const lines = src.split('\n')
  const items = []
  let i = 0
  const inline = (t) => t.replace(/\*\*(.+?)\*\*/g, '$1').replace(/`(.+?)`/g, '$1')
  while (i < lines.length) {
    const l = lines[i]
    if (l.startsWith('> ') || l.startsWith('---') || l.startsWith('# ')) { i++; continue }
    if (l.startsWith('## ')) { items.push(new Paragraph({ children: [run(inline(l.slice(3)), { size: 28, bold: true, color: '0B1A2E' })], spacing: { before: 280, after: 120 }, heading: HeadingLevel.HEADING_1 })); i++; continue }
    if (l.startsWith('### ')) { items.push(new Paragraph({ children: [run(inline(l.slice(4)), { size: 23, bold: true, color: '1F3B63' })], spacing: { before: 180, after: 80 } })); i++; continue }
    if (l.startsWith('|')) {
      const rows = []
      while (i < lines.length && lines[i].startsWith('|')) { rows.push(lines[i]); i++ }
      const cells = rows.filter((r) => !/^\|\s*-+/.test(r)).map((r) => r.split('|').slice(1, -1).map((c) => inline(c.trim())))
      const cols = cells[0].length
      const width = 9000
      items.push(new Table({
        width: { size: width, type: WidthType.DXA },
        columnWidths: Array(cols).fill(Math.floor(width / cols)),
        rows: cells.map((r, ri) => new TableRow({
          children: r.map((c) => new TableCell({
            width: { size: Math.floor(width / cols), type: WidthType.DXA },
            shading: ri === 0 ? { type: ShadingType.CLEAR, fill: 'EEF3FA', color: 'auto' } : undefined,
            children: [new Paragraph({ children: [run(c, { size: 17, bold: ri === 0 })] })],
          })),
        })),
      }))
      items.push(para('', { after: 60 }))
      continue
    }
    if (/^\s*[-*] /.test(l) || /^\s*\d+\. /.test(l)) {
      const text = inline(l.replace(/^\s*([-*]|\d+\.)\s+/, ''))
      items.push(new Paragraph({ children: [run('• ' + text)], indent: { left: 360 }, spacing: { after: 60 } }))
      i++; continue
    }
    if (l.trim() === '') { i++; continue }
    items.push(para(inline(l)))
    i++
  }
  return items
}

const body = mdToParagraphs(md)

// 그림 삽입 위치: 3장(기능) 뒤에 사용 흐름, 5장(개발 계획) 뒤에 아키텍처, 7장에 화면 캡처
const figures = {
  flow: path.join(root, 'docs/img/flow.png'),
  arch: path.join(root, 'docs/img/architecture.png'),
}
const shots = fs.existsSync(path.join(root, 'docs/img/shots')) ? fs.readdirSync(path.join(root, 'docs/img/shots')).filter((f) => f.endsWith('.png')).sort() : []

const children = [
  new Paragraph({ children: [run('골목지킴이 — 기획서 별지', { size: 40, bold: true, color: '0B1A2E' })], alignment: AlignmentType.CENTER, spacing: { after: 120 } }),
  para('현대자동차그룹 차량용 앱 개발 공모전 · 안전 운전 · Pleos Connect SDK 기반', { size: 20, color: '5B6B82', align: AlignmentType.CENTER, after: 60 }),
  para('※ 접수양식 02 항목의 상세 내용과 그림. 참가자 정보는 포함하지 않음.', { size: 17, color: '8A9BB4', align: AlignmentType.CENTER, after: 300 }),
]
for (const p of body) {
  children.push(p)
  const text = p.root?.[1]?.root?.[1]?.root?.[0]?.root?.[1] ?? ''
}
// 간단히: 본문 뒤에 그림 섹션을 붙인다 (양식 7번 '참고자료'에 대응)
children.push(new Paragraph({ children: [new PageBreak()] }))
children.push(new Paragraph({ children: [run('그림 1. 사용 흐름 — 골목 진입부터 사고 리포트까지', { size: 24, bold: true })], spacing: { after: 100 } }))
children.push(image(figures.flow, 620))
children.push(new Paragraph({ children: [run('그림 2. 아키텍처 — Pleos SDK 매핑', { size: 24, bold: true })], spacing: { before: 200, after: 100 } }))
children.push(image(figures.arch, 620))
if (shots.length) {
  children.push(new Paragraph({ children: [new PageBreak()] }))
  children.push(new Paragraph({ children: [run('그림 3. 화면 (Pleos Connect Emulator 실행 캡처)', { size: 24, bold: true })], spacing: { after: 100 } }))
  for (const f of shots) {
    const caption = f.replace(/^\d+_/, '').replace(/\.png$/, '').replace(/_/g, ' ')
    children.push(image(path.join(root, 'docs/img/shots', f), 600))
    children.push(para(caption, { size: 17, color: '5B6B82', align: AlignmentType.CENTER, after: 200 }))
  }
}

const doc = new Document({
  styles: { default: { document: { run: { font: FONT, size: 20 } } } },
  sections: [{ properties: { page: { margin: { top: 1100, bottom: 1100, left: 1200, right: 1200 } } }, children }],
})
Packer.toBuffer(doc).then((buf) => {
  const file = path.join(out, '골목지킴이_기획서_별지.docx')
  fs.writeFileSync(file, buf)
  console.log('wrote', file, buf.length, 'bytes', '| shots:', shots.length)
})
