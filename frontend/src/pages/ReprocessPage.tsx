import { useCallback, useEffect, useState } from 'react'
import { apiGet, apiWrite, type Me, type Store } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 재처리 — 실패한 것을 보고 다시 하게 만듭니다 (업무 정의서 §5.8, §10).
 *
 * 매출 분해 · 마스터 적용 · 프로그램 설치(POS-SERVER), 주문 이벤트 발행(Order),
 * 손님 주문(APP-SERVER)의 실패가 **한 화면에** 모입니다 (2026-10-03, umbrella
 * docs/design/reprocess-library.md). 운영자가 묻는 것은 하나라서입니다: 지금
 * 손봐야 할 것이 있나. 그 답이 세 군데면 아무도 세 군데를 다 열어보지 않습니다.
 *
 * **읽지 못한 서비스는 따로 띄웁니다.** 빈 칸으로 두면 "실패 0건" 과 구분이 안
 * 되고, 위의 "손볼 것 없음" 이 거짓말이 됩니다.
 *
 * 화면이 지키는 사실 둘:
 *
 * 1. **"다시 한다" 가 종류마다 다릅니다.** 매출은 POS-Server 가 직접 다시
 *    풉니다(다음 배치, 30초 이내). 마스터·프로그램은 **장비가 가져가는**
 *    구조라, 여기서 할 수 있는 것은 실패 표시를 지워 "다시 받아야 할 것"
 *    으로 되돌리는 것까지입니다. 그래서 누른 뒤의 문장을 서버가 준 그대로
 *    보여줍니다 — "눌렀는데 왜 안 바뀌지" 를 겪지 않게.
 *
 * 2. **실패는 멈추고 사람을 기다립니다.** 2026-09-18 전에는 배치가 실패한
 *    매출을 30초마다 계속 다시 풀었고(한 건을 이틀 동안 388번), 그래서 이
 *    버튼은 아무 뜻이 없었습니다. 지금은 여기가 되돌리는 **유일한** 길이고,
 *    시도 횟수가 "사람이 봐야 한다" 는 신호입니다.
 */

interface FailedEntry {
  id: string
  storeId: string
  summary: string
  error: string | null
  attempts: number
  failedAt: string | null
}

interface ReprocessKind {
  service: string
  kind: string
  label: string
  count: number
  items: FailedEntry[]
}

interface RetryOutcome {
  kind: string
  id: string
  accepted: boolean
  done: boolean
  message: string
}

interface Board {
  kinds: ReprocessKind[]
  unreachable: string[]
}

/** 화면에 쓰는 서비스 이름 — 주소의 이름(service)은 그대로 보내고, 보이는 것만 바꿉니다. */
const SERVICE_LABEL: Record<string, string> = {
  'POS-SERVER': 'POS-SERVER',
  ORDER: 'Order',
  'APP-SERVER': 'APP-SERVER',
}

function serviceLabel(service: string): string {
  return SERVICE_LABEL[service] ?? service
}

/** 종류 이름은 서비스 안에서만 유일합니다. */
function keyOf(kind: ReprocessKind): string {
  return `${kind.service}/${kind.kind}`
}

function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }) : ''
}

/**
 * 세 번째부터는 표시합니다. 한두 번은 일시적인 것일 수 있지만, 같은 건을
 * 세 번째 되돌린다면 규칙이나 원본을 고쳐야 하는 건입니다 — 다시 누르는
 * 것으로는 안 풀립니다.
 */
const LOOK_AT_ME = 3

export default function ReprocessPage({ me }: { me: Me }) {
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [kinds, setKinds] = useState<ReprocessKind[]>([])
  const [unreachable, setUnreachable] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const mayView = can(me, 'REPROCESS', 'VIEW')
  const mayRetry = can(me, 'REPROCESS', 'SAVE')

  useEffect(() => {
    if (!can(me, 'STORE', 'VIEW')) return
    apiGet<Store[]>('/stores').then(setStores).catch(() => setStores([]))
  }, [me])

  const load = useCallback(async () => {
    if (!mayView) return
    setLoading(true)
    try {
      const q = storeId ? `?${new URLSearchParams({ storeId })}` : ''
      const answer = await apiGet<Board>(`/reprocess${q}`)
      setKinds(answer.kinds ?? [])
      setUnreachable(answer.unreachable ?? [])
      setError(null)
    } catch (e) {
      // 못 불러온 것을 "실패 없음" 으로 그리지 않습니다. 목록을 비우고
      // 이유를 띄웁니다 — 빈 표는 "전부 정상" 으로 읽힙니다.
      setError(e instanceof Error ? e.message : '재처리 목록을 불러오지 못했습니다')
      setKinds([])
      setUnreachable([])
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId])

  useEffect(() => {
    void load()
  }, [load])

  async function retryOne(kind: ReprocessKind, item: FailedEntry) {
    if (!mayRetry) return
    setBusy(`${keyOf(kind)}:${item.id}`)
    setError(null)
    try {
      const params = new URLSearchParams({ service: kind.service, kind: kind.kind, id: item.id })
      if (item.storeId) params.set('storeId', item.storeId)
      const outcome = await apiWrite<RetryOutcome>('POST', `/reprocess/one?${params}`)
      // 서버가 준 문장을 그대로 씁니다. "다시 했다" 와 "다시 하게 걸었다" 는
      // 다른 일이고, 그 차이를 아는 것은 저쪽입니다.
      setNotice(outcome?.message ?? '다시 하게 걸었습니다')
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '재처리를 걸지 못했습니다')
    } finally {
      setBusy(null)
    }
  }

  async function retryAll(kind: ReprocessKind) {
    if (!mayRetry) return
    if (!window.confirm(
      `${kind.label} 실패 ${kind.count}건을 전부 다시 하게 걸까요?\n\n` +
      '규칙이나 원본을 고친 뒤에 누르는 버튼입니다. 고치지 않고 누르면 같은 이유로 다시 실패합니다.'
    )) return
    setBusy(`${keyOf(kind)}:*`)
    setError(null)
    try {
      const params = new URLSearchParams({ service: kind.service, kind: kind.kind })
      if (storeId) params.set('storeId', storeId)
      const answer = await apiWrite<{ queued: number; failed: number }>(
        'POST', `/reprocess/all?${params}`)
      setNotice(`${kind.label}: ${answer?.queued ?? 0}건을 다시 하게 걸었습니다` +
        ((answer?.failed ?? 0) > 0 ? ` (${answer?.failed}건은 걸지 못했습니다)` : ''))
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '재처리를 걸지 못했습니다')
    } finally {
      setBusy(null)
    }
  }

  if (!mayView) return <div className="empty">이 화면에 대한 권한이 없습니다.</div>

  const total = kinds.reduce((sum, k) => sum + k.count, 0)

  return (
    <>
      <div className="page-head">
        <h1>재처리</h1>
        <button className="btn" onClick={() => void load()}>새로고침</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: 18 }}>
        실패한 것은 <strong>멈추고 사람을 기다립니다</strong> — 저절로 다시 시도하지 않습니다.
        되돌리는 길은 여기 하나이고, 원본은 지우지 않으므로 규칙을 고친 뒤 다시 풀 수 있습니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div className="panel">
        <div className="row" style={{ marginBottom: 14 }}>
          <select value={storeId} onChange={(e) => setStoreId(e.target.value)}>
            <option value="">전 매장</option>
            {stores.map((s) => <option key={s.storeId} value={s.storeId}>{s.name}</option>)}
          </select>
          {/* 못 불러왔을 때는 "정상" 배지를 띄우지 않습니다 — 한 서비스라도 못 읽었으면 마찬가지. */}
          {!error && !loading && (total > 0
            ? <span className="badge down">실패 {total}건</span>
            : unreachable.length === 0 && <span className="badge on">손볼 것 없음</span>)}
        </div>

        {unreachable.length > 0 && (
          <div className="error" role="alert" style={{ marginBottom: 14 }}>
            읽지 못함: {unreachable.map(serviceLabel).join(', ')} — 이 서비스의 실패는 지금 보이지 않습니다.
            0건이라는 뜻이 아닙니다.
          </div>
        )}

        {loading && kinds.length === 0 ? (
          <div className="empty">불러오는 중…</div>
        ) : kinds.map((kind) => (
          <section key={keyOf(kind)} style={{ marginBottom: 22 }}>
            <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
              <h2 style={{ fontSize: 15, margin: 0 }}>
                <span className="badge off" title="이 실패를 들고 있고, 다시 하는 서비스">{serviceLabel(kind.service)}</span>{' '}
                {kind.label}{' '}
                <span className={kind.count > 0 ? 'badge down' : 'badge on'}>{kind.count}</span>
              </h2>
              {mayRetry && kind.count > 1 && (
                <button className="btn" disabled={busy !== null} onClick={() => void retryAll(kind)}>
                  {busy === `${keyOf(kind)}:*` ? '거는 중…' : '전부 다시'}
                </button>
              )}
            </div>

            {kind.count === 0 ? (
              <div className="muted" style={{ fontSize: 13 }}>실패 없음</div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>매장</th>
                      <th>무엇</th>
                      <th>사유</th>
                      <th style={{ textAlign: 'right' }}>시도</th>
                      <th>언제</th>
                      {mayRetry && <th />}
                    </tr>
                  </thead>
                  <tbody>
                    {kind.items.map((item) => (
                      <tr key={item.id}>
                        <td>{item.storeId}</td>
                        <td className="mono">{item.summary}</td>
                        {/* 사유는 줄이지 않습니다. 요약하면 다시 돌려도 되는지
                            판단할 근거가 사라집니다. */}
                        <td style={{ maxWidth: 380, whiteSpace: 'normal' }}>{item.error ?? ''}</td>
                        <td style={{ textAlign: 'right' }}>
                          {item.attempts >= LOOK_AT_ME
                            ? <span className="badge down" title="같은 건을 여러 번 되돌렸습니다 — 규칙이나 원본을 봐야 합니다">{item.attempts}</span>
                            : item.attempts}
                        </td>
                        <td>{when(item.failedAt)}</td>
                        {mayRetry && (
                          <td>
                            <button
                              className="btn"
                              disabled={busy !== null}
                              onClick={() => void retryOne(kind, item)}
                            >
                              {busy === `${keyOf(kind)}:${item.id}` ? '거는 중…' : '다시'}
                            </button>
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ))}
      </div>

      <p className="muted" style={{ marginTop: 14, fontSize: 12 }}>
        시도가 {LOOK_AT_ME}번 이상인 건은 붉게 표시됩니다. 다시 누르는 것으로는 안 풀리는
        건일 가능성이 높습니다 — 사유를 보고 규칙이나 원본을 고친 뒤 되돌리십시오.
      </p>
    </>
  )
}
