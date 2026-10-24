// 도로 정보 API — 차 안의 앱 대신 서버가 OpenStreetMap(Overpass)을 조회하고 55m 격자 단위로 캐시한다.
// 응답: 현재 도로 등급·이름·제한속도 + 주변 구조적 위험 지점(횡단보도·버스정류장·학교)
import type { VercelRequest, VercelResponse } from '@vercel/node'
import { list, put } from '@vercel/blob'

const CELL = 2000
const TTL_MS = 7 * 24 * 3600 * 1000
const MIRRORS = [
  'https://overpass.kumi.systems/api/interpreter',
  'https://overpass-api.de/api/interpreter',
  'https://lz4.overpass-api.de/api/interpreter',
]
const ALLEY_RANK = ['living_street', 'pedestrian', 'service', 'residential', 'unclassified', 'tertiary', 'secondary', 'primary', 'trunk', 'motorway']

interface Hazard { kind: 'crossing' | 'bus_stop' | 'school'; label: string; lat: number; lon: number }
interface Road { roadClass: string | null; roadName: string | null; maxspeedKph: number | null; hazards: Hazard[]; fetchedAt: number; source: string }

function cellKey(lat: number, lon: number) {
  return `${Math.round(lat * CELL)}_${Math.round(lon * CELL)}`
}

function query(lat: number, lon: number) {
  return `[out:json][timeout:15];
(
  way(around:14,${lat},${lon})[highway][highway!~"footway|path|steps|cycleway"];
  node(around:70,${lat},${lon})[highway=crossing];
  node(around:120,${lat},${lon})[highway=bus_stop];
  way(around:180,${lat},${lon})[amenity~"school|kindergarten|childcare"];
  node(around:180,${lat},${lon})[amenity~"school|kindergarten|childcare"];
);
out tags center;`
}

async function overpass(lat: number, lon: number): Promise<{ elements: any[]; source: string }> {
  let lastErr = ''
  for (const url of MIRRORS) {
    try {
      const ctrl = new AbortController()
      const timer = setTimeout(() => ctrl.abort(), 9_000)
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': 'golmok-guard-prototype/0.1' },
        body: 'data=' + encodeURIComponent(query(lat, lon)),
        signal: ctrl.signal,
      })
      clearTimeout(timer)
      if (!res.ok) { lastErr = `${url} ${res.status}`; continue }
      const json = await res.json()
      return { elements: json.elements ?? [], source: new URL(url).host }
    } catch (e) {
      lastErr = `${url} ${(e as Error).message}`
    }
  }
  throw new Error(lastErr || 'overpass unavailable')
}

function parse(elements: any[], source: string): Road {
  let best: any = null
  const hazards: Hazard[] = []
  for (const e of elements) {
    const t = e.tags ?? {}
    const hw = t.highway ?? ''
    const amenity = t.amenity ?? ''
    const plat = e.lat ?? e.center?.lat ?? 0
    const plon = e.lon ?? e.center?.lon ?? 0
    const name = t.name ? ` (${t.name})` : ''
    if (e.type === 'way' && hw && !amenity) {
      // 큰길 옆 골목에 들어선 순간엔 골목 등급을 우선한다
      if (!best || ALLEY_RANK.indexOf(hw) < ALLEY_RANK.indexOf(best.highway)) best = t
    } else if (hw === 'crossing') hazards.push({ kind: 'crossing', label: '횡단보도', lat: plat, lon: plon })
    else if (hw === 'bus_stop') hazards.push({ kind: 'bus_stop', label: '버스정류장' + name, lat: plat, lon: plon })
    else if (amenity) hazards.push({ kind: 'school', label: (amenity === 'school' ? '학교' : amenity === 'kindergarten' ? '유치원' : '어린이집') + name, lat: plat, lon: plon })
  }
  const ms = best?.maxspeed ? parseInt(String(best.maxspeed).replace(/\D/g, ''), 10) : NaN
  return {
    roadClass: best?.highway ?? null,
    roadName: best?.name ?? null,
    maxspeedKph: Number.isFinite(ms) ? ms : null,
    hazards,
    fetchedAt: Date.now(),
    source,
  }
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader('Cache-Control', 'public, max-age=300')
  const lat = Number(req.query.lat), lon = Number(req.query.lon)
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return res.status(400).json({ error: 'lat, lon required' })
  const key = `road/${cellKey(lat, lon)}.json`
  try {
    const { blobs } = await list({ prefix: key, limit: 1 })
    const hit = blobs.find((b) => b.pathname === key)
    if (hit && Date.now() - new Date(hit.uploadedAt).getTime() < TTL_MS) {
      const cached = await (await fetch(hit.url, { cache: 'no-store' })).json()
      return res.status(200).json({ ...cached, cached: true })
    }
  } catch { /* 캐시 실패는 무시하고 새로 조회 */ }
  try {
    const { elements, source } = await overpass(lat, lon)
    const road = parse(elements, source)
    put(key, JSON.stringify(road), { access: 'public', addRandomSuffix: false, allowOverwrite: true, contentType: 'application/json' }).catch(() => {})
    return res.status(200).json({ ...road, cached: false })
  } catch (e) {
    return res.status(502).json({ error: (e as Error).message })
  }
}
