import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiGet, apiWrite, type Me, type Store, type StoreTable } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 매장 배치도 — 테이블 기준 정보와 QR (umbrella docs/adr/0007).
 *
 * 테이블은 신원도 계산서도 아니고 배송지입니다. 그래서 번호만이 아니라
 * 위치를 갖고, 화면도 목록이 아니라 격자를 먼저 보여줍니다 — 언젠가 로봇이
 * 갈 곳이니까요.
 */

/** 그리는 격자의 크기. 좌표 입력 범위(0~999)와는 별개입니다. */
const GRID = 12

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit'; id: number }

const BLANK: StoreTable = {
  id: null, storeId: '', floor: 1, tableNo: '', displayName: '',
  posX: null, posY: null, qrCode: '', active: true
}

export default function TablesPage({ me }: { me: Me }) {
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [rows, setRows] = useState<StoreTable[]>([])
  const [floor, setFloor] = useState(1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<StoreTable>(BLANK)
  const [busy, setBusy] = useState(false)
  const [qrFor, setQrFor] = useState<StoreTable | null>(null)
  const [qrUrl, setQrUrl] = useState<string | null>(null)

  const mayView = can(me, 'TABLE', 'VIEW')
  const mayCreate = can(me, 'TABLE', 'CREATE')
  const maySave = can(me, 'TABLE', 'SAVE')
  const mayDelete = can(me, 'TABLE', 'DELETE')

  useEffect(() => {
    if (!can(me, 'STORE', 'VIEW')) return
    apiGet<Store[]>('/stores')
      .then((s) => {
        setStores(s)
        // 최초 한 번만 고릅니다. 매번 넣으면 고른 매장이 되돌아갑니다.
        setStoreId((current) => (current === '' && s.length > 0 ? s[0]!.storeId : current))
      })
      .catch(() => setStores([]))
  }, [me])

  const load = useCallback(async () => {
    if (!mayView || !storeId) return
    setLoading(true)
    try {
      setRows(await apiGet<StoreTable[]>('/tables?storeId=' + encodeURIComponent(storeId)))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '배치도를 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId])

  useEffect(() => { void load() }, [load])

  const floors = [...new Set(rows.map((r) => r.floor))].sort((a, b) => a - b)
  const onThisFloor = rows.filter((r) => r.floor === floor)
  const placed = new Map(
    onThisFloor
      .filter((r) => r.posX != null && r.posY != null)
      .map((r) => [r.posX + ',' + r.posY, r]))

  function startNew(x?: number, y?: number) {
    setForm({ ...BLANK, storeId, floor, posX: x ?? null, posY: y ?? null })
    setEditing({ mode: 'new' })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (editing.mode === 'new') await apiWrite('POST', '/tables', form)
      else if (editing.mode === 'edit') await apiWrite('PUT', '/tables/' + editing.id, form)
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(row: StoreTable) {
    const warn = row.floor + '층 ' + row.tableNo + ' 을(를) 삭제할까요?\n\n'
      + '붙여둔 QR이 있다면 같이 떼야 합니다 — 지운 뒤에는 그 코드가 아무것도 가리키지 않습니다.'
    if (!window.confirm(warn)) return
    try {
      await apiWrite('DELETE', '/tables/' + row.id)
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  async function showQr(row: StoreTable) {
    setQrFor(row)
    setQrUrl(null)
    try {
      const target = await apiGet<{ url: string }>('/tables/' + row.id + '/qr-target')
      setQrUrl(target.url)
    } catch {
      setQrUrl(null)
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>매장 배치도</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>매장 배치도</h1>
        <button className="btn primary" disabled={!mayCreate || !storeId}
                onClick={() => startNew()}>신규</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        테이블은 <strong>배송지</strong>입니다 — 이 메뉴를 저 자리로. 그래서 번호만이 아니라
        위치를 갖습니다. 빈 칸을 누르면 그 자리에 새 테이블을 만듭니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="panel">
        <div className="row">
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="store">매장</label>
            <select id="store" value={storeId} onChange={(e) => setStoreId(e.target.value)}>
              {stores.map((s) => <option key={s.storeId} value={s.storeId}>{s.name}</option>)}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="floor">층</label>
            <select id="floor" value={floor} onChange={(e) => setFloor(Number(e.target.value))}>
              {[...new Set([...floors, 1, 2, 3, 4, 5, floor])].sort((a, b) => a - b).map((f) => (
                <option key={f} value={f}>{f}층{floors.includes(f) ? '' : ' (비어 있음)'}</option>
              ))}
            </select>
          </div>
          <span className="muted">이 층 {onThisFloor.length}개 · 매장 전체 {rows.length}개</span>
        </div>
      </div>

      <div className="panel">
        {loading ? <div className="empty">불러오는 중…</div> : (
          <div className="floorplan" style={{ gridTemplateColumns: 'repeat(' + GRID + ', 1fr)' }}>
            {Array.from({ length: GRID * GRID }, (_, i) => {
              const x = i % GRID
              const y = Math.floor(i / GRID)
              const here = placed.get(x + ',' + y)
              return here ? (
                <button key={i} className={'cell taken' + (here.active ? '' : ' off')}
                        title={here.displayName ?? here.tableNo}
                        onClick={() => { setForm(here); setEditing({ mode: 'edit', id: here.id! }) }}>
                  {here.tableNo}
                </button>
              ) : (
                <button key={i} className="cell" disabled={!mayCreate}
                        onClick={() => startNew(x, y)} aria-label={x + ',' + y + ' 빈 자리'} />
              )
            })}
          </div>
        )}
      </div>

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '신규 테이블' : form.tableNo + ' 수정'}</h2>
          <div className="row">
            <div className="field">
              <label htmlFor="tableNo">테이블 번호</label>
              <input id="tableNo" type="text" value={form.tableNo} required
                     onChange={(e) => setForm({ ...form, tableNo: e.target.value })} />
            </div>
            <div className="field">
              <label htmlFor="fl">층</label>
              <input id="fl" type="number" min={1} max={200} value={form.floor}
                     onChange={(e) => setForm({ ...form, floor: Number(e.target.value) })} />
            </div>
          </div>
          <div className="field">
            <label htmlFor="dn">표시명 <span className="muted">(비우면 번호가 이름입니다)</span></label>
            <input id="dn" type="text" value={form.displayName ?? ''}
                   onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          </div>
          <div className="row">
            <div className="field">
              <label htmlFor="px">X</label>
              <input id="px" type="number" min={0} max={999} value={form.posX ?? ''}
                     onChange={(e) => setForm({ ...form, posX: e.target.value === '' ? null : Number(e.target.value) })} />
            </div>
            <div className="field">
              <label htmlFor="py">Y</label>
              <input id="py" type="number" min={0} max={999} value={form.posY ?? ''}
                     onChange={(e) => setForm({ ...form, posY: e.target.value === '' ? null : Number(e.target.value) })} />
            </div>
            <div className="field">
              <label htmlFor="act">사용여부</label>
              <select id="act" value={form.active ? 'Y' : 'N'}
                      onChange={(e) => setForm({ ...form, active: e.target.value === 'Y' })}>
                <option value="Y">사용</option>
                <option value="N">미사용</option>
              </select>
            </div>
          </div>
          {editing.mode === 'edit' && (
            <p className="muted">
              QR 코드 <code>{form.qrCode}</code> 는 바뀌지 않습니다 — 번호를 고치거나 자리를
              옮겨도 이미 붙여둔 스티커가 계속 이 테이블을 가리킵니다.
            </p>
          )}
          <div className="row">
            <button className="btn primary" type="submit"
                    disabled={busy || (editing.mode === 'new' ? !mayCreate : !maySave)}>
              {busy ? '저장 중…' : '저장'}
            </button>
            <button className="btn" type="button" onClick={() => setEditing({ mode: 'none' })}>취소</button>
            {editing.mode === 'edit' && (
              <>
                <button className="btn" type="button" onClick={() => void showQr(form)}>QR 보기</button>
                <button className="btn danger" type="button" disabled={!mayDelete}
                        onClick={() => void remove(form)}>삭제</button>
              </>
            )}
          </div>
        </form>
      )}

      {qrFor && (
        <div className="panel">
          <h2>{qrFor.floor}층 {qrFor.tableNo} — QR</h2>
          <div className="row" style={{ alignItems: 'flex-start' }}>
            <img className="qr" alt="테이블 QR" src={'/bo/api/tables/' + qrFor.id + '/qr.png'} />
            <div style={{ flex: 1, minWidth: 260 }}>
              <p className="muted">코드 <code>{qrFor.qrCode}</code></p>
              {qrUrl && <p className="muted">담고 있는 주소<br /><code>{qrUrl}</code></p>}
              <div className="notice">
                인쇄 전에 위 주소를 확인하세요. <strong>손님 휴대폰은 LTE로 들어옵니다</strong> —
                여기가 <code>192.168.x</code> 이면 매장 안에서만 열리고, 한 번 붙인 스티커는
                회수가 안 됩니다.
              </div>
              <div className="row">
                <button className="btn" onClick={() => window.print()}>인쇄</button>
                <button className="btn" onClick={() => { setQrFor(null); setQrUrl(null) }}>닫기</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
