import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiGet, apiWrite, type Me, type SoldOutBoard, type SoldOutRow, type Store } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 결품 — 오늘 이 매장에 없는 메뉴.
 *
 * 메뉴 관리와 갈라 둔 화면입니다. 저쪽은 기준 정보를 고치는 드문 일이고
 * 이쪽은 장사 중에 몇 번씩 누르는 일이라, 목록이 아니라 **누르면 바로
 * 바뀌는 타일**로 그립니다.
 *
 * 판매일자가 키에 들어갑니다. 그래서 개점과 함께 저절로 풀리고, 어제
 * 걸어둔 결품이 오늘 손님 화면에 남는 사고가 구조적으로 안 생깁니다.
 */

const MONEY = new Intl.NumberFormat('ko-KR')

/** 매장의 달력, 브라우저의 달력이 아닙니다 — 판매일자는 Asia/Seoul 날짜입니다. */
function seoulToday(): string {
  return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)
}

export default function SoldOutPage({ me }: { me: Me }) {
  const today = useMemo(seoulToday, [])
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [businessDate, setBusinessDate] = useState(today)
  const [rows, setRows] = useState<SoldOutRow[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyCode, setBusyCode] = useState<string | null>(null)

  const mayView = can(me, 'SOLD_OUT', 'VIEW')
  const mayDeclare = can(me, 'SOLD_OUT', 'CREATE')
  const mayRelease = can(me, 'SOLD_OUT', 'DELETE')

  useEffect(() => {
    if (!can(me, 'STORE', 'VIEW')) return
    apiGet<Store[]>('/stores')
      .then((s) => {
        setStores(s)
        // 최초 한 번만 고릅니다 — 매번 넣으면 고른 매장이 되돌아갑니다.
        setStoreId((current) => (current === '' && s.length > 0 ? s[0]!.storeId : current))
      })
      .catch(() => setStores([]))
  }, [me])

  const load = useCallback(async () => {
    if (!mayView || !storeId) return
    setLoading(true)
    try {
      const q = new URLSearchParams({ storeId, businessDate })
      const board = await apiGet<SoldOutBoard>(`/sold-out?${q}`)
      setRows(board.menus)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '결품 현황을 불러오지 못했습니다')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId, businessDate])

  useEffect(() => {
    void load()
  }, [load])

  async function toggle(row: SoldOutRow) {
    const turningOn = !row.soldOut
    if (turningOn ? !mayDeclare : !mayRelease) return

    setBusyCode(row.menuCode)
    setError(null)
    // 낙관적으로 먼저 뒤집습니다. 장사 중에 누르는 버튼이라 왕복을
    // 기다리는 느낌이 나면 두 번 누르게 됩니다.
    setRows((current) => current.map((r) =>
      r.menuCode === row.menuCode ? { ...r, soldOut: turningOn } : r))
    try {
      const q = new URLSearchParams({ storeId, businessDate })
      const path = `/sold-out/${encodeURIComponent(row.menuCode)}?${q}`
      await apiWrite(turningOn ? 'POST' : 'DELETE', path)
    } catch (e) {
      setError(e instanceof Error ? e.message : '결품 상태를 바꾸지 못했습니다')
      // 서버가 거절했으면 화면이 거짓말을 하고 있는 것이므로 되읽습니다.
      await load()
    } finally {
      setBusyCode(null)
    }
  }

  const soldOutCount = rows.filter((r) => r.soldOut).length

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>결품</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head"><h1>결품</h1></div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        판매일자 기준입니다 — 개점하면 저절로 풀리므로 마감 때 지울 것이 없습니다. 결품으로
        걸면 손님 화면에서 그 메뉴를 고를 수 없게 되고, 매장 단말에도 바로 알려집니다.
        메뉴 자체를 내리는 것은 <strong>메뉴</strong> 화면입니다 — 이쪽은 오늘만입니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="panel">
        <div className="row">
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="store">매장</label>
            <select id="store" value={storeId} onChange={(e) => setStoreId(e.target.value)}>
              {stores.length === 0 && <option value="">매장 없음</option>}
              {stores.map((s) => (
                <option key={s.storeId} value={s.storeId}>{s.name}</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="date">판매일자</label>
            <input id="date" type="date" value={businessDate}
                   onChange={(e) => setBusinessDate(e.target.value)} />
          </div>
          <button className="btn" onClick={() => void load()} disabled={loading}>
            {loading ? '조회 중…' : '새로고침'}
          </button>
          {businessDate !== today && (
            <span className="badge off">오늘이 아닙니다</span>
          )}
        </div>
      </div>

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">등록된 메뉴가 없습니다. 메뉴 화면에서 먼저 등록하세요.</div>
        ) : (
          <>
            <div className="row" style={{ marginBottom: 14 }}>
              <span className="badge">메뉴 {rows.length}개</span>
              {soldOutCount > 0
                ? <span className="badge off">결품 {soldOutCount}개</span>
                : <span className="badge on">결품 없음</span>}
            </div>

            <div className="soldout-grid">
              {rows.map((row) => {
                const locked = row.soldOut ? !mayRelease : !mayDeclare
                return (
                  <button
                    key={row.menuCode}
                    type="button"
                    className={`soldout-tile${row.soldOut ? ' out' : ''}`}
                    disabled={locked || busyCode === row.menuCode}
                    onClick={() => void toggle(row)}
                    title={locked ? '이 행위의 권한이 없습니다' : row.soldOut ? '눌러서 판매 재개' : '눌러서 결품'}
                  >
                    <span className="tile-name">{row.name}</span>
                    <span className="tile-meta">
                      {row.currentPrice === null
                        ? '가격 미등록'
                        : `${MONEY.format(row.currentPrice)}원`}
                      {row.cookMinutes !== null && ` · ${row.cookMinutes}분`}
                    </span>
                    <span className="tile-state">{row.soldOut ? '결품' : '판매'}</span>
                    {!row.active && <span className="tile-flag">내린 메뉴</span>}
                  </button>
                )
              })}
            </div>
          </>
        )}
      </div>
    </>
  )
}
