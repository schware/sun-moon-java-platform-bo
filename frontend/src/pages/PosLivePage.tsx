import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  apiGet,
  AXIS_LABEL,
  LIVE_AXES,
  type AxisState,
  type LiveAxis,
  type Me,
  type PosLiveCheck,
  type PosLiveRow,
  type Store
} from '../lib/api'
import { can } from '../lib/permissions'

/**
 * POS Live Check — 다섯 축을 따로 봅니다 (업무 정의서 §3.5).
 *
 * 이 화면이 존재하는 이유는 문서의 한 문장입니다:
 * **"WebSocket 연결 상태만으로 정상 영업 여부를 판단하지 않는다."**
 *
 * 그래서 축을 합친 신호등을 크게 그리지 않고 **다섯 칸을 나란히** 놓습니다.
 * 종합은 작게 옆에 붙입니다 — 사람이 봐야 하는 것은 "빨간가"가 아니라
 * "어느 축이 빨간가"입니다. 소켓은 멀쩡한데 프로그램이 죽은 단말과
 * 마감을 안 한 단말은 할 일이 전혀 다릅니다.
 */

const REFRESH_MS = 15_000

function seoulToday(): string {
  return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)
}

function axisClass(state: AxisState): string {
  return state === 'OK' ? 'ok' : state === 'WARN' ? 'warn' : state === 'DOWN' ? 'down' : 'unknown'
}

/** 축 칸에 들어갈 짧은 말. 상태 이름을 그대로 쓰면 다섯 칸이 영어로 덮입니다. */
const STATE_MARK: Record<AxisState, string> = {
  OK: '정상',
  WARN: '주의',
  DOWN: '중단',
  UNKNOWN: '모름'
}

function elapsed(iso: string | null): string {
  if (!iso) return '보고 없음'
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return `${seconds}초 전`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전`
  return `${Math.floor(seconds / 3600)}시간 전`
}

export default function PosLivePage({ me }: { me: Me }) {
  const today = useMemo(seoulToday, [])
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [businessDate, setBusinessDate] = useState(today)
  const [rows, setRows] = useState<PosLiveRow[]>([])
  const [checkedAt, setCheckedAt] = useState<Date | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mayView = can(me, 'POS_LIVE', 'VIEW')

  useEffect(() => {
    if (!can(me, 'STORE', 'VIEW')) return
    apiGet<Store[]>('/stores')
      .then((s) => {
        setStores(s)
        setStoreId((current) => (current === '' && s.length > 0 ? s[0]!.storeId : current))
      })
      .catch(() => setStores([]))
  }, [me])

  const load = useCallback(async () => {
    if (!mayView || !storeId) return
    setLoading(true)
    try {
      const q = new URLSearchParams({ storeId, businessDate })
      const answer = await apiGet<PosLiveCheck>(`/pos-ops/live?${q}`)
      setRows(answer.terminals ?? [])
      setCheckedAt(new Date())
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Live Check를 불러오지 못했습니다')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId, businessDate])

  useEffect(() => {
    void load()
  }, [load])

  // 관제 화면은 사람이 보고 있는 동안 스스로 새로고침해야 합니다 —
  // 눌러야 갱신되는 관제는 아무도 안 누릅니다.
  useEffect(() => {
    const timer = setInterval(() => void load(), REFRESH_MS)
    return () => clearInterval(timer)
  }, [load])

  if (!mayView) return <div className="empty">이 화면에 대한 권한이 없습니다.</div>

  const normal = rows.filter((r) => r.normal).length
  const flagged = rows.filter((r) => r.mismatches.length > 0).length

  return (
    <>
      <div className="page-head">
        <h1>POS Live Check</h1>
        <button className="btn" onClick={() => void load()}>새로고침</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: 18 }}>
        연결 하나로 정상 여부를 판단하지 않습니다. 다섯 축은 <strong>출처가 다릅니다</strong> —
        연결은 소켓, 영업은 개점·마감 상태, 프로그램·장비는 POS의 보고, 데이터는 그 보고와
        서버가 본 매출 분해 실패를 함께 봅니다. 보고가 끊기면 마지막 값이 아니라 <strong>모름</strong>
        입니다. {REFRESH_MS / 1000}초마다 다시 물어봅니다.
        {checkedAt && ` (마지막 확인 ${checkedAt.toLocaleTimeString('ko-KR')})`}
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="panel">
        <div className="row" style={{ marginBottom: 14 }}>
          <select value={storeId} onChange={(e) => setStoreId(e.target.value)}>
            {stores.map((s) => (
              <option key={s.storeId} value={s.storeId}>{s.name}</option>
            ))}
          </select>
          <input type="date" value={businessDate} onChange={(e) => setBusinessDate(e.target.value)} />
          <span className="badge on">정상 {normal}</span>
          <span className="badge down">중단 {rows.length - normal}</span>
          {flagged > 0 && <span className="badge warn">어긋남 {flagged}</span>}
        </div>

        {loading && rows.length === 0 ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">
            이 매장에서 오늘 개점했거나, 접속했거나, 상태를 보고한 단말이 없습니다.
          </div>
        ) : (
          rows.map((row) => (
            <div
              key={row.posNo}
              className="panel"
              style={{ background: '#12151c', marginBottom: 12, padding: 14 }}
            >
              <div className="row" style={{ justifyContent: 'space-between', marginBottom: 10 }}>
                <div className="row">
                  <strong style={{ fontSize: 14 }}>{row.posNo}</strong>
                  <span className={`badge ${row.normal ? 'on' : 'down'}`}>
                    {row.normal ? '정상 영업' : '중단'}
                  </span>
                  {row.programVersion && (
                    <span className="badge off">프로그램 {row.programVersion}</span>
                  )}
                  {row.dbVersion && <span className="badge off">DB {row.dbVersion}</span>}
                </div>
                <span className="muted" style={{ fontSize: 12 }}>
                  보고 {elapsed(row.lastReportedAt)}
                  {/* 시계는 축이 아닙니다. 다섯 축이 전부 정상인데 시계만
                      틀어진 단말이 있고, 그 단말의 매출은 다른 날로 갑니다. */}
                  {row.clockDriftSeconds !== null && Math.abs(row.clockDriftSeconds) > 30 && (
                    <span className="badge warn" style={{ marginLeft: 8 }}>
                      시계 {Math.abs(row.clockDriftSeconds)}초 {row.clockDriftSeconds > 0 ? '느림' : '빠름'}
                    </span>
                  )}
                </span>
              </div>

              <div className="axis-grid">
                {LIVE_AXES.map((axis: LiveAxis) => {
                  const state = (row.axes[axis] ?? 'UNKNOWN') as AxisState
                  return (
                    <div key={axis} className={`axis-cell ${axisClass(state)}`}>
                      <span className="axis-name">{AXIS_LABEL[axis]}</span>
                      {STATE_MARK[state]}
                    </div>
                  )
                })}
              </div>

              {row.mismatches.length > 0 && (
                <ul className="mismatch-list">
                  {row.mismatches.map((m) => <li key={m}>{m}</li>)}
                </ul>
              )}
            </div>
          ))
        )}
      </div>

      <p className="muted" style={{ marginTop: 14 }}>
        <strong>영업</strong> 축이 중단인 것은 고장이 아니라 그 단말이 마감했다는 뜻입니다.
        밤에는 모든 단말이 중단으로 보이는 것이 맞습니다 — 묻는 것이 "지금 정상 영업
        중인가"이기 때문입니다.
      </p>
    </>
  )
}
