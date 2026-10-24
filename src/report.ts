// 차량 앱이 QR 링크의 # 뒤에 담아 보낸 리포트를 푼다.
// fragment는 서버로 전송되지 않으므로 기록은 휴대폰 브라우저 안에서만 열린다.

export type Fact = [title: string, value: string, favorable: 1 | 0 | -1]
export type Interval = [startDs: number, endDs: number, value: number] // 충돌 기준 0.1초 단위

export interface CrashPayload {
  k: 'c'
  t: number
  c: string
  g: number
  f: Fact[]
  sp: number[] // 0.5초 간격, -30초 ~ +10초
  br: Interval[]
  ts: Interval[]
  loc?: [number, number]
  path: [number, number][]
}

export interface HandoverPayload {
  k: 'h'
  m: string
  s: number
  e: number
  d: number
  mx: number
  hb: number
  ha: number
  sus: boolean
  ch: [string, string][]
  path: [number, number][]
}

export type Payload = CrashPayload | HandoverPayload

export async function readPayloadFromHash(hash: string): Promise<Payload | null> {
  const match = hash.match(/r=([A-Za-z0-9_-]+)/)
  if (!match) return null
  const b64 = match[1].replace(/-/g, '+').replace(/_/g, '/')
  const bytes = Uint8Array.from(atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4)), (ch) => ch.charCodeAt(0))
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'))
  const text = await new Response(stream).text()
  return JSON.parse(text) as Payload
}

export function formatTime(ms: number) {
  return new Date(ms).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}
