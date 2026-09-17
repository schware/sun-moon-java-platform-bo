import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  apiGet,
  type Me,
  type PosBusinessHistory,
  type PosBusinessState,
  type PosBusinessStates,
  type Store
} from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 개점 현황 — 단말 단위 개점·마감 (업무 정의서 §3.4).
 *
 * 매장의 개점/마감과 **다른 화면**입니다. 저쪽은 가게가 주문을 받는가이고
 * 여기는 그 단말이 매출을 올릴 수 있는가입니다. POS 셋 중 하나만 마감한
 * 매장은 여전히 영업 중이고, 그 하나를 찾는 것이 이 화면의 일입니다.
 *
 * 현재 상태와 이력을 한 화면에 둔 이유: 상태만 보면 "왜 이런가"에 답이
 * 없습니다. 이력에는 **무시된 전문까지** 남아 있어서, 마감으로 안 바뀐
 * POS의 사연이 거기 있습니다.
 */

function seoulToday(): string {
  return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)
}

function time(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const EVENT_LABEL: Record<string, string> = { OPEN: '개점', CLOSE: '마감', REOPEN: '재개점' }

/** 무시했다는 사실이 화면에서 사라지면 안 됩니다 — 그게 답이어야 할 때가 있습니다. */
const RESULT_LABEL: Record<string, string> = {
  APPLIED: '반영',
  DUPLICATE: '중복',
  IGNORED_STALE: '지나간 전문'
}

export default function PosBusinessPage({ me }: { me: Me }) {
  const today = useMemo(seoulToday, [])
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [businessDate, setBusinessDate] = useState(today)
  const [states, setStates] = useState<PosBusinessState[]>([])
  const [openPos, setOpenPos] = useState<string | null>(null)
  const [history, setHistory] = useState<PosBusinessHistory | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const mayView = can(me, 'POS_BUSINESS', 'VIEW')

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
      const answer = await apiGet<PosBusinessStates>(`/pos-ops/business?${q}`)
      setStates(answer.terminals ?? [])
      setError(null)
    } catch (e) {
      // 빈 목록으로 뭉개지 않습니다 — "개점한 단말이 없다"와 "POS-Server가
      // 안 보인다"는 전화를 걸 곳이 다릅니다.
      setError(e instanceof Error ? e.message : '개점 현황을 불러오지 못했습니다')
      setStates([])
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId, businessDate])

  useEffect(() => {
    void load()
    setOpenPos(null)
    setHistory(null)
  }, [load])

  async function showHistory(posNo: string) {
    if (openPos === posNo) {
      setOpenPos(null)
      setHistory(null)
      return
    }
    setOpenPos(posNo)
    setHistory(null)
    try {
      const q = new URLSearchParams({ storeId, posNo, businessDate })
      setHistory(await apiGet<PosBusinessHistory>(`/pos-ops/business/history?${q}`))
    } catch (e) {
      setError(e instanceof Error ? e.message : '이력을 불러오지 못했습니다')
    }
  }

  if (!mayView) return <div className="empty">이 화면에 대한 권한이 없습니다.</div>

  const open = states.filter((s) => s.status === 'OPEN').length
  const reopened = states.filter((s) => s.openCount > 1).length

  return (
    <>
      <div className="page-head">
        <h1>개점 현황</h1>
        <button className="btn" onClick={() => void load()}>새로고침</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: 18 }}>
        <strong>단말 단위</strong>입니다 — 매장이 주문을 받는가(Order의 영업일)와 다릅니다.
        POS 셋 중 하나만 마감한 매장은 여전히 영업 중이고, 여기서 그 하나를 찾습니다.
        재개점은 POS가 아니라 <strong>서버가 판단합니다</strong>: 재부팅한 POS는 오늘 이미
        열었다는 것을 모르기 때문입니다.
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
          <span className="badge on">영업 중 {open}</span>
          <span className="badge off">마감 {states.length - open}</span>
          {reopened > 0 && <span className="badge warn">재개점 있음 {reopened}</span>}
        </div>

        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : states.length === 0 ? (
          <div className="empty">이 날 이 매장에서 개점·마감 전문을 보낸 단말이 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>상태</th><th>POS</th><th>마지막 사건</th>
                  <th>개점 횟수</th><th>사건 시각</th><th>수신 시각</th><th></th>
                </tr>
              </thead>
              <tbody>
                {states.map((row) => (
                  <tr
                    key={row.posNo}
                    className={openPos === row.posNo ? 'active clickable' : 'clickable'}
                    onClick={() => void showHistory(row.posNo)}
                  >
                    <td>
                      {row.status === 'OPEN'
                        ? <span className="badge on">영업 중</span>
                        : <span className="badge off">마감</span>}
                    </td>
                    <td>{row.posNo}</td>
                    <td>
                      {EVENT_LABEL[row.lastEvent] ?? row.lastEvent}
                      {row.lastEvent === 'REOPEN' && <span className="badge warn" style={{ marginLeft: 6 }}>재개점</span>}
                    </td>
                    <td>{row.openCount}</td>
                    {/* 사건 시각과 수신 시각이 크게 벌어진 단말은 통신이 밀리고 있습니다. */}
                    <td className="muted">{time(row.eventAt)}</td>
                    <td className="muted">{time(row.receivedAt)}</td>
                    <td className="muted">{openPos === row.posNo ? '접기' : '이력'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {openPos && (
        <div className="panel" style={{ marginTop: 16 }}>
          <h2 style={{ fontSize: 15, margin: '0 0 4px' }}>{openPos} 이력</h2>
          <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
            받은 전문이 <strong>전부</strong> 있습니다. 반영되지 않은 것도 남습니다 —
            "이 POS는 왜 마감으로 안 바뀌었나"에 답할 수 있는 유일한 자리입니다.
          </p>
          {history === null ? (
            <div className="empty">불러오는 중…</div>
          ) : history.history.length === 0 ? (
            <div className="empty">이력이 없습니다.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>순번</th><th>사건</th><th>처리</th>
                    <th>사건 시각</th><th>수신 시각</th><th>사유</th><th>IRT</th>
                  </tr>
                </thead>
                <tbody>
                  {history.history.map((entry) => (
                    <tr key={entry.seq}>
                      <td>{entry.seq}</td>
                      <td>{EVENT_LABEL[entry.event] ?? entry.event}</td>
                      <td>
                        {entry.result === 'APPLIED' ? (
                          <span className="badge on">{RESULT_LABEL.APPLIED}</span>
                        ) : entry.result === 'DUPLICATE' ? (
                          <span className="badge off">{RESULT_LABEL.DUPLICATE}</span>
                        ) : (
                          <span className="badge warn">{RESULT_LABEL.IGNORED_STALE}</span>
                        )}
                      </td>
                      <td className="muted">{time(entry.eventAt)}</td>
                      <td className="muted">{time(entry.receivedAt)}</td>
                      <td className="muted">{entry.message ?? ''}</td>
                      <td className="muted">{entry.irtId ?? ''}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  )
}
