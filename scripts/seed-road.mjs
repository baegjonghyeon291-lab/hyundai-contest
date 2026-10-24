// 시연 구간의 도로 정보를 Blob 캐시에 직접 넣는다 (외부 지도 서버가 느릴 때도 시연이 흔들리지 않게).
// 값은 OpenStreetMap에서 실제로 조회한 결과를 옮긴 것. 실행: node scripts/seed-road.mjs  (BLOB_READ_WRITE_TOKEN은 .env.local)
import { put } from '@vercel/blob'
import { readFileSync } from 'node:fs'

for (const line of readFileSync('.env.local', 'utf8').split('\n')) {
  const m = line.match(/^([A-Z_]+)="?([^"\n]*)"?$/)
  if (m && !process.env[m[1]]) process.env[m[1]] = m[2]
}

const CELL = 2000
const key = (lat, lon) => `road/${Math.round(lat * CELL)}_${Math.round(lon * CELL)}.json`

const cells = [
  // 강남 테헤란로·선릉로 (큰길, 2차로 이상) — 시연의 '일반 도로'
  ...[[37.5045, 127.0490], [37.5050, 127.0490], [37.5040, 127.0490], [37.5045, 127.0485], [37.5045, 127.0495]].map(([lat, lon]) => ({
    lat, lon,
    road: { roadClass: 'secondary', roadName: '선릉로', maxspeedKph: 50, hazards: [
      { kind: 'crossing', label: '횡단보도', lat: 37.5051, lon: 127.0489 },
      { kind: 'crossing', label: '횡단보도', lat: 37.5039, lon: 127.0491 },
    ], source: 'seed(osm)' },
  })),
  // 연남동 동교로45길 (이면도로, 제한 20) — 시연의 '골목'
  ...[[37.5628, 126.9245], [37.5626, 126.9245], [37.5630, 126.9245], [37.5628, 126.9243], [37.5628, 126.9247]].map(([lat, lon]) => ({
    lat, lon,
    road: { roadClass: 'residential', roadName: '동교로45길', maxspeedKph: 20, hazards: [
      { kind: 'bus_stop', label: '버스정류장 (하모니마트사거리)', lat: 37.5633956, lon: 126.9255206 },
      { kind: 'bus_stop', label: '버스정류장 (대명아파트)', lat: 37.5623, lon: 126.9244 },
    ], source: 'seed(osm)' },
  })),
]

for (const c of cells) {
  const body = JSON.stringify({ ...c.road, fetchedAt: Date.now() })
  const r = await put(key(c.lat, c.lon), body, { access: 'public', addRandomSuffix: false, allowOverwrite: true, contentType: 'application/json' })
  console.log('seeded', key(c.lat, c.lon), c.road.roadClass, r.url.length > 0 ? 'ok' : '')
}
