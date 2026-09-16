import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiGet, apiWrite, type Device, type Me, type Menu, type MenuPrice, type MenuSummary } from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit' }

const BLANK: Menu = {
  menuCode: '', name: '', imageUrl: '', description: '', kdsDeviceId: '', active: true,
  cookMinutes: null
}
const BLANK_PRICE = { price: '', startDate: '', endDate: '' }

function formatPrice(amount: number | null): string {
  return amount === null ? '가격 미등록' : new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount)
}

export default function MenuPage({ me }: { me: Me }) {
  const [rows, setRows] = useState<MenuSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<Menu>(BLANK)
  const [busy, setBusy] = useState(false)

  const [selected, setSelected] = useState<string | null>(null)
  const [priceRows, setPriceRows] = useState<MenuPrice[]>([])
  const [priceForm, setPriceForm] = useState(BLANK_PRICE)
  const [priceError, setPriceError] = useState<string | null>(null)
  const [priceBusy, setPriceBusy] = useState(false)

  const [kdsDevices, setKdsDevices] = useState<Device[]>([])

  const mayView = can(me, 'MENU', 'VIEW')
  const mayCreate = can(me, 'MENU', 'CREATE')
  const maySave = can(me, 'MENU', 'SAVE')
  const mayDelete = can(me, 'MENU', 'DELETE')
  const mayReadDevices = can(me, 'DEVICE', 'VIEW')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setRows(await apiGet<MenuSummary[]>('/menus'))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [mayView])

  // A convenience list for the select, not a constraint — same reasoning
  // as DevicesPage's device-type datalist. Not enforced as a foreign key
  // in the database either.
  const loadKdsDevices = useCallback(async () => {
    if (!mayReadDevices) return
    try {
      const devices = await apiGet<Device[]>('/devices')
      setKdsDevices(devices.filter((d) => d.terminalType === 'KDS'))
    } catch {
      setKdsDevices([])
    }
  }, [mayReadDevices])

  useEffect(() => {
    void load()
    void loadKdsDevices()
  }, [load, loadKdsDevices])

  const loadPrices = useCallback(async (menuCode: string) => {
    setPriceError(null)
    try {
      setPriceRows(await apiGet<MenuPrice[]>(`/menus/${encodeURIComponent(menuCode)}/prices`))
    } catch (e) {
      setPriceError(e instanceof Error ? e.message : '가격 이력을 불러오지 못했습니다')
    }
  }, [])

  function select(menuCode: string) {
    setSelected(menuCode)
    setPriceForm(BLANK_PRICE)
    void loadPrices(menuCode)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiWrite(editing.mode === 'new' ? 'POST' : 'PUT', '/menus', form)
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(row: MenuSummary) {
    if (!window.confirm(`${row.menuCode} 을(를) 삭제할까요? 가격 이력도 함께 삭제됩니다.`)) return
    setError(null)
    try {
      const q = new URLSearchParams({ menuCode: row.menuCode })
      await apiWrite('DELETE', `/menus?${q}`)
      if (selected === row.menuCode) setSelected(null)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  async function addPrice(event: FormEvent) {
    event.preventDefault()
    if (!selected) return
    setPriceBusy(true)
    setPriceError(null)
    try {
      await apiWrite('POST', `/menus/${encodeURIComponent(selected)}/prices`, {
        price: Number(priceForm.price),
        startDate: priceForm.startDate,
        endDate: priceForm.endDate || null
      })
      setPriceForm(BLANK_PRICE)
      await loadPrices(selected)
      await load()
    } catch (e) {
      setPriceError(e instanceof Error ? e.message : '가격 등록에 실패했습니다')
    } finally {
      setPriceBusy(false)
    }
  }

  async function removePrice(row: MenuPrice) {
    if (!selected) return
    if (!window.confirm(`${row.startDate} ~ ${row.endDate ?? '(진행중)'} 가격을 삭제할까요?`)) return
    setPriceError(null)
    try {
      await apiWrite('DELETE', `/menus/${encodeURIComponent(selected)}/prices/${row.id}`)
      await loadPrices(selected)
      await load()
    } catch (e) {
      setPriceError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>메뉴</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>메뉴</h1>
        <button className="btn primary" disabled={!mayCreate}
                onClick={() => { setForm(BLANK); setEditing({ mode: 'new' }) }}>
          신규
        </button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        대/중/소 옵션은 아직 없습니다. 가격은 이력으로 관리되며, 메뉴 연동 시 조회 일자에 맞는 가격이 사용됩니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '신규 메뉴' : `${form.menuCode} 수정`}</h2>
          <div className="field">
            <label htmlFor="menuCode">메뉴코드</label>
            <input id="menuCode" type="text" value={form.menuCode} required
                   disabled={editing.mode === 'edit'}
                   onChange={(e) => setForm({ ...form, menuCode: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="name">메뉴 이름</label>
            <input id="name" type="text" value={form.name} required
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="imageUrl">메뉴 이미지 URL</label>
            <input id="imageUrl" type="text" value={form.imageUrl ?? ''}
                   onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="description">설명</label>
            <input id="description" type="text" value={form.description ?? ''}
                   onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="kdsDeviceId">담당 KDS</label>
            <select id="kdsDeviceId" value={form.kdsDeviceId ?? ''}
                    onChange={(e) => setForm({ ...form, kdsDeviceId: e.target.value || null })}>
              <option value="">지정 안 함 (모든 KDS로 전달)</option>
              {kdsDevices.map((d) => (
                <option key={d.deviceId} value={d.deviceId}>{d.name} ({d.deviceId})</option>
              ))}
            </select>
            <p className="muted" style={{ margin: '4px 0 0' }}>
              지금은 표시용입니다 — 실제 주문은 주문 채널 자신의 카탈로그가 담당 KDS를 정합니다.
            </p>
          </div>
          <div className="field">
            <label htmlFor="cookMinutes">기준 조리시간 (분)</label>
            <input id="cookMinutes" type="number" min={1} value={form.cookMinutes ?? ''}
                   onChange={(e) => setForm({
                     ...form,
                     cookMinutes: e.target.value === '' ? null : Number(e.target.value)
                   })} />
            <p className="muted" style={{ margin: '4px 0 0' }}>
              손님 화면의 예상 대기시간을 "기준 조리시간 &times; 대기열"로 뽑습니다. 실측 평균이
              아니라 운영자가 정하는 값이고, 비워두면 그 메뉴는 예상시간을 안 보여줍니다 —
              0분이라고 말하는 것보다 모른다고 두는 편이 낫습니다.
            </p>
          </div>
          <div className="field">
            <label htmlFor="active">사용여부</label>
            <select id="active" value={form.active ? 'Y' : 'N'}
                    onChange={(e) => setForm({ ...form, active: e.target.value === 'Y' })}>
              <option value="Y">사용</option>
              <option value="N">미사용</option>
            </select>
          </div>
          <div className="row">
            <button className="btn primary" type="submit" disabled={busy}>{busy ? '저장 중…' : '저장'}</button>
            <button className="btn" type="button" onClick={() => setEditing({ mode: 'none' })}>취소</button>
          </div>
        </form>
      )}

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">등록된 메뉴가 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>메뉴코드</th><th>이름</th><th>오늘의 가격</th><th className="num">조리시간</th><th>사용</th><th /></tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.menuCode} className={selected === row.menuCode ? 'active' : ''}>
                    <td className="muted clickable" onClick={() => select(row.menuCode)}>
                      {row.menuCode}
                    </td>
                    <td className="clickable" onClick={() => select(row.menuCode)}>{row.name}</td>
                    <td className="muted">{formatPrice(row.currentPrice)}</td>
                    <td className="num muted">{row.cookMinutes === null ? '' : `${row.cookMinutes}분`}</td>
                    <td>{row.active ? <span className="badge on">사용</span> : <span className="badge off">미사용</span>}</td>
                    <td className="actions">
                      <div className="row" style={{ justifyContent: 'flex-end' }}>
                        <button className="btn" onClick={() => select(row.menuCode)}>가격 이력</button>
                        <button className="btn" disabled={!maySave}
                                onClick={() => { setForm(row); setEditing({ mode: 'edit' }) }}>수정</button>
                        <button className="btn danger" disabled={!mayDelete}
                                onClick={() => void remove(row)}>삭제</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selected && (
        <div className="panel">
          <h2>{selected} 가격 이력</h2>

          {priceError && <div className="error" role="alert">{priceError}</div>}

          {maySave && (
            <form className="row" style={{ alignItems: 'flex-end', flexWrap: 'wrap', marginBottom: '16px' }}
                  onSubmit={(e) => void addPrice(e)}>
              <div className="field">
                <label htmlFor="priceAmount">가격</label>
                <input id="priceAmount" type="number" min={0} required
                       value={priceForm.price}
                       onChange={(e) => setPriceForm({ ...priceForm, price: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor="priceStart">시작일</label>
                <input id="priceStart" type="date" required
                       value={priceForm.startDate}
                       onChange={(e) => setPriceForm({ ...priceForm, startDate: e.target.value })} />
              </div>
              <div className="field">
                <label htmlFor="priceEnd">종료일 (선택)</label>
                <input id="priceEnd" type="date"
                       value={priceForm.endDate}
                       onChange={(e) => setPriceForm({ ...priceForm, endDate: e.target.value })} />
              </div>
              <button className="btn primary" type="submit" disabled={priceBusy}>
                {priceBusy ? '등록 중…' : '가격 추가'}
              </button>
            </form>
          )}

          {priceRows.length === 0 ? (
            <div className="empty">등록된 가격 이력이 없습니다.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>가격</th><th>시작일</th><th>종료일</th><th /></tr>
                </thead>
                <tbody>
                  {priceRows.map((row) => (
                    <tr key={row.id}>
                      <td>{formatPrice(row.price)}</td>
                      <td className="muted">{row.startDate}</td>
                      <td className="muted">{row.endDate ?? '진행중'}</td>
                      <td className="actions">
                        <button className="btn danger" disabled={!maySave}
                                onClick={() => void removePrice(row)}>삭제</button>
                      </td>
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
