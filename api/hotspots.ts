// 급제동 핫스팟 API — 여러 차의 급제동 지점을 50m 격자로 모은다.
// 저장되는 것: 격자 좌표·건수·마지막 시각뿐. 차량·사용자 식별 정보는 받지 않는다.
import type { VercelRequest, VercelResponse } from '@vercel/node'
import { list, put } from '@vercel/blob'

const FILE = 'hotspots.json'
const CELL = 2000 // 1/2000도 ≈ 위도 55m

interface Cell { lat: number; lon: number; count: number; last: number; alley: number }
interface Store { cells: Record<string, Cell>; events: number }

// 시연용 초기 데이터: 다른 차들이 급제동한 적 있는 골목 (연남동 동교로45길 일대)
const SEED: Record<string, Cell> = {
  '75256,252849': { lat: 37.5628, lon: 126.92445, count: 7, last: 1792700000000, alley: 7 },
  '75258,252846': { lat: 37.5629, lon: 126.9243, count: 3, last: 1792600000000, alley: 3 },
  '75010,254098': { lat: 37.5045, lon: 127.049, count: 4, last: 1792650000000, alley: 2 },
}

function key(lat: number, lon: number) {
  return `${Math.round(lat * CELL)},${Math.round(lon * CELL)}`
}

function distanceM(a: [number, number], b: [number, number]) {
  const r = 6371000
  const dLat = ((b[0] - a[0]) * Math.PI) / 180
  const dLon = ((b[1] - a[1]) * Math.PI) / 180
  const s = Math.sin(dLat / 2) ** 2 + Math.cos((a[0] * Math.PI) / 180) * Math.cos((b[0] * Math.PI) / 180) * Math.sin(dLon / 2) ** 2
  return 2 * r * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s))
}

async function load(): Promise<Store> {
  const { blobs } = await list({ prefix: FILE, limit: 1 })
  const blob = blobs.find((b) => b.pathname === FILE)
  if (!blob) return { cells: { ...SEED }, events: 0 }
  const res = await fetch(`${blob.url}?t=${Date.now()}`, { cache: 'no-store' })
  return (await res.json()) as Store
}

async function save(store: Store) {
  await put(FILE, JSON.stringify(store), { access: 'public', addRandomSuffix: false, allowOverwrite: true, contentType: 'application/json' })
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader('Cache-Control', 'no-store')
  try {
    if (req.method === 'GET') {
      const lat = Number(req.query.lat), lon = Number(req.query.lon), r = Math.min(Number(req.query.r) || 400, 3000)
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return res.status(400).json({ error: 'lat, lon required' })
      const store = await load()
      const cells = Object.values(store.cells)
        .filter((c) => distanceM([lat, lon], [c.lat, c.lon]) <= r)
        .map(({ lat, lon, count, last }) => ({ lat, lon, count, last }))
      return res.status(200).json({ cells })
    }
    if (req.method === 'POST') {
      const body = typeof req.body === 'string' ? JSON.parse(req.body) : req.body ?? {}
      const lat = Number(body.lat), lon = Number(body.lon)
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) return res.status(400).json({ error: 'lat, lon required' })
      const store = await load()
      const k = key(lat, lon)
      const cell = store.cells[k] ?? { lat: Math.round(lat * CELL) / CELL, lon: Math.round(lon * CELL) / CELL, count: 0, last: 0, alley: 0 }
      cell.count += 1
      cell.last = Date.now()
      if (body.alley) cell.alley += 1
      store.cells[k] = cell
      store.events += 1
      await save(store)
      return res.status(200).json({ ok: true, cell: { lat: cell.lat, lon: cell.lon, count: cell.count } })
    }
    return res.status(405).json({ error: 'method not allowed' })
  } catch (e) {
    return res.status(500).json({ error: (e as Error).message })
  }
}
