import { useEffect, useState } from 'react'
import { formatTime, readPayloadFromHash, type CrashPayload, type HandoverPayload, type Payload } from './report'
import './App.css'

function App() {
  const [payload, setPayload] = useState<Payload | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const load = () => {
      setError(false)
      readPayloadFromHash(window.location.hash)
        .then(setPayload)
        .catch(() => setError(true))
    }
    load()
    window.addEventListener('hashchange', load)
    return () => window.removeEventListener('hashchange', load)
  }, [])

  if (error) return <Shell><p className="muted">리포트를 열 수 없습니다. QR을 다시 찍어 주세요.</p></Shell>
  if (!payload) return <Landing />
  return <Shell>{payload.k === 'c' ? <CrashView p={payload} /> : <HandoverView p={payload} />}</Shell>
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <main className="page">
      <header className="brand">
        <span className="logo">●</span> 데이터 블랙박스
      </header>
      {children}
      <footer className="foot">
        차량 데이터 기록 · 블랙박스 영상의 보조 자료입니다. 이 리포트는 링크 안에만 담겨 있으며 서버에 저장되지 않습니다.
      </footer>
    </main>
  )
}

function Actions() {
  const share = () => {
    if (navigator.share) navigator.share({ title: '데이터 블랙박스 리포트', url: window.location.href }).catch(() => {})
    else navigator.clipboard?.writeText(window.location.href)
  }
  return (
    <div className="actions">
      <button onClick={share}>공유 · 링크 복사</button>
      <button className="ghost" onClick={() => window.print()}>PDF로 저장</button>
    </div>
  )
}

function CrashView({ p }: { p: CrashPayload }) {
  const point = p.loc ?? p.path.at(-1)
  return (
    <>
      <section className="card hero bad">
        <p className="eyebrow">사고 리포트</p>
        <h1>{formatTime(p.t)}</h1>
        <p className="muted">충돌 전 30초 ~ 후 10초 차량 기록</p>
      </section>

      <section className="card">
        <ul className="facts">
          {p.f.map(([title, value, fav]) => (
            <li key={title}>
              <span className={`mark ${fav === 1 ? 'good' : fav === -1 ? 'bad' : ''}`}>{fav === 1 ? '✓' : fav === -1 ? '!' : '·'}</span>
              <span className="title">{title}</span>
              <span className="value">{value}</span>
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <p className="eyebrow">속도 · 브레이크 · 방향지시등</p>
        <SpeedChart p={p} />
      </section>

      {point && <MapCard lat={point[0]} lon={point[1]} label="사고 지점" />}
      <Actions />
    </>
  )
}

function SpeedChart({ p }: { p: CrashPayload }) {
  const W = 640, H = 260, top = 16, bottom = 190
  const max = Math.max(20, Math.ceil(Math.max(...p.sp) / 20) * 20)
  const padX = 24
  const x = (ds: number) => padX + ((ds + 300) / 400) * (W - padX * 2) // -30초~+10초, 0.1초 단위
  const y = (v: number) => bottom - ((bottom - top) * v) / max
  const line = p.sp.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(-300 + i * 5).toFixed(1)},${y(v).toFixed(1)}`).join(' ')
  const band = (items: CrashPayload['br'], y0: number, cls: string) =>
    items.map(([s, e], i) => (
      <rect key={`${cls}${i}`} className={cls} x={x(Math.max(s, -300))} y={y0} width={Math.max(2, x(e) - x(Math.max(s, -300)))} height={14} rx={3} />
    ))
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="chart" role="img" aria-label="충돌 전후 속도 그래프">
      <line x1={0} y1={bottom} x2={W} y2={bottom} className="axis" />
      <text x={4} y={top + 10} className="tick">{max} km/h</text>
      <path d={line} className="speed" />
      {band(p.br, bottom + 10, 'brake')}
      {band(p.ts, bottom + 30, 'signal')}
      <line x1={x(0)} y1={top} x2={x(0)} y2={bottom + 44} className="crash" />
      {[-30, -20, -10, 0, 10].map((s) => (
        <text key={s} x={x(s * 10)} y={H - 4} className="tick" textAnchor="middle">{s === 0 ? '충돌' : `${s}초`}</text>
      ))}
      <text x={padX} y={bottom + 22} className="tick">브레이크</text>
      <text x={padX} y={bottom + 42} className="tick">깜빡이</text>
    </svg>
  )
}

function HandoverView({ p }: { p: HandoverPayload }) {
  const minutes = Math.round((p.e - p.s) / 60000)
  const last = p.path.at(-1)
  return (
    <>
      <section className={`card hero ${p.sus ? 'warn' : 'good'}`}>
        <p className="eyebrow">맡김 리포트 · {p.m}</p>
        <h1>{formatTime(p.s)} ~</h1>
        {p.sus ? (
          <>
            <p className="warnText">⚠️ 맡긴 동안 의심스러운 운행이 있었습니다</p>
            <ul className="reasons">{(p.why ?? []).map((w) => <li key={w}>{w}</li>)}</ul>
          </>
        ) : <p className="goodText">✓ 맡긴 동안 특이한 운행은 없었습니다.</p>}
      </section>
      <section className="tiles">
        <Tile title="주행 거리" value={`${p.d.toFixed(1)} km`} warn={p.sus} />
        <Tile title="맡긴 시간" value={`${minutes}분`} />
        <Tile title="최고 속도" value={`${Math.round(p.mx)} km/h`} warn={(p.why ?? []).some((w) => w.startsWith('최고 속도'))} />
        <Tile title="급제동" value={`${p.hb}회`} warn={p.hb > 0} />
        <Tile title="급가속" value={`${p.ha}회`} warn={p.ha > 0} />
        <Tile title="바뀐 설정" value={`${p.ch.length}개`} warn={p.ch.length > 0} />
      </section>
      <section className="card">
        <p className="eyebrow">바뀐 설정</p>
        {p.ch.length === 0 ? <p className="muted">없음</p> : (
          <ul className="facts">
            {p.ch.map(([k, v]) => <li key={k}><span className="mark">·</span><span className="title">{k}</span><span className="value">{v}</span></li>)}
          </ul>
        )}
      </section>
      {last && <MapCard lat={last[0]} lon={last[1]} label="마지막 위치" path={p.path} />}
      <Actions />
    </>
  )
}

function Tile({ title, value, warn }: { title: string; value: string; warn?: boolean }) {
  return (
    <div className="tile">
      <p className="muted">{title}</p>
      <p className={`big ${warn ? 'warnText' : ''}`}>{value}</p>
    </div>
  )
}

function MapCard({ lat, lon, label, path }: { lat: number; lon: number; label: string; path?: [number, number][] }) {
  const pts = path && path.length > 1 ? path : [[lat, lon] as [number, number]]
  const lats = pts.map((q) => q[0]), lons = pts.map((q) => q[1])
  const pad = 0.003
  const bbox = [Math.min(...lons) - pad, Math.min(...lats) - pad, Math.max(...lons) + pad, Math.max(...lats) + pad].join(',')
  return (
    <section className="card">
      <p className="eyebrow">{label}</p>
      <iframe
        className="map"
        title={label}
        loading="lazy"
        src={`https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${lat},${lon}`}
      />
      <p className="muted small">
        {lat.toFixed(5)}, {lon.toFixed(5)} ·{' '}
        <a href={`https://map.kakao.com/link/map/${encodeURIComponent(label)},${lat},${lon}`} target="_blank" rel="noreferrer">카카오맵</a>{' '}
        · <a href={`https://www.google.com/maps?q=${lat},${lon}`} target="_blank" rel="noreferrer">구글 지도</a>
      </p>
    </section>
  )
}

function Landing() {
  return (
    <Shell>
      <section className="card hero">
        <p className="eyebrow">Pleos 차량용 앱 · 현대자동차그룹 차량용 앱 개발 공모전</p>
        <h1>영상이 못 남기는<br />내 차의 순간을 기록합니다</h1>
        <p className="muted">
          블랙박스는 앞만 찍습니다. 사고가 나면 "깜빡이 켰나요?", "브레이크 밟았나요?"를 증명할 길이 없죠.
          데이터 블랙박스는 차의 속도·브레이크·방향지시등·조명을 상시 기록하고, 충격이 감지되면 전후 40초를 리포트로 남깁니다.
        </p>
      </section>
      <section className="tiles">
        <Tile title="사고 감지" value="충격·에어백" />
        <Tile title="속도" value="차량 차속 신호" />
        <Tile title="용량" value="1시간 ≈ 2MB" />
        <Tile title="맡김 모드" value="발레·대리·정비" />
      </section>
    </Shell>
  )
}

export default App
