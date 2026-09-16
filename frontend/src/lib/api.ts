/**
 * The BO API client.
 *
 * Everything is same-origin on purpose: the built app is served by the BO
 * service itself, so the session cookie and the XSRF-TOKEN cookie travel
 * without any CORS configuration — which matters, because BO has none.
 */

const BASE = '/bo/api'

export class UnauthorizedError extends Error {
  constructor() {
    super('로그인이 필요합니다')
    this.name = 'UnauthorizedError'
  }
}

export interface Me {
  username: string
  superAdmin: boolean
  authorities: string[]
}

export interface Post {
  id: number
  title: string
  content: string
  author: string
  createdAt: string
  updatedAt: string
}

export interface CommonCode {
  groupCode: string
  code: string
  name: string
  sortOrder: number
  active: boolean
}

export type TerminalType = 'POS' | 'KDS' | 'DID'

export interface Device {
  deviceId: string
  name: string
  deviceType: string
  location: string | null
  /** Which 매장 the terminal stands in. Null for equipment that never connects. */
  storeId: string | null
  terminalType: TerminalType | null
  ipAddress: string | null
  port: number | null
  active: boolean
}

export type ConnectionCheckResult = 'REACHABLE' | 'UNREACHABLE' | 'NO_ADDRESS'

/**
 * One row of 단말 현황 — what BO registered next to what the Device Server
 * can see. `registered && !connected` is a dark screen; `!registered &&
 * connected` is a terminal that connected as an id nobody entered.
 */
export interface TerminalStatus {
  deviceId: string
  terminalType: TerminalType
  storeId: string | null
  storeIds: string[] | null
  name: string | null
  location: string | null
  ipAddress: string | null
  port: number | null
  registered: boolean
  active: boolean
  connected: boolean
  connectedAt: string | null
}

/** 일자별 매출 집계. Counted by Order; BO only adds `storeName`. */
export interface SalesSummary {
  businessDate: string
  storeId: string
  storeName: string | null
  orderCount: number
  acceptedCount: number
  rejectedCount: number
  expiredCount: number
  salesAmount: number
}

export interface SalesOrder {
  id: number
  storeId: string
  customerId: string
  menuName: string
  amount: number
  status: string
  acceptedBy: string | null
  businessDate: string
  placedAt: string
  updatedAt: string
}

export type ScreenAction = 'VIEW' | 'CREATE' | 'SAVE' | 'DELETE'

export interface ScreenPermission {
  screen: string
  canView: boolean
  canCreate: boolean
  canSave: boolean
  canDelete: boolean
}

/** Never carries the password hash — the server's OperatorSummary has no such field. */
export interface Operator {
  id: number
  username: string
  displayName: string
  superAdmin: boolean
  active: boolean
  permissions: ScreenPermission[]
}

export interface OperatorRequest {
  username: string
  /** Blank on 저장 means "leave the existing password alone". */
  password: string
  displayName: string
  superAdmin: boolean
  active: boolean
  permissions: ScreenPermission[]
}

export interface Store {
  storeId: string
  name: string
  phone: string | null
  address: string | null
  active: boolean
}

/**
 * 매장의 테이블 한 자리 — 배송지입니다(ADR-0007). qrCode는 서버가 발급하고
 * 절대 바뀌지 않습니다: 이미 인쇄해서 붙인 스티커가 계속 이 테이블을
 * 가리켜야 하기 때문입니다. 그래서 수정 폼에도 없습니다.
 */
export interface StoreTable {
  id: number | null
  storeId: string
  floor: number
  tableNo: string
  displayName: string | null
  posX: number | null
  posY: number | null
  qrCode: string
  active: boolean
}

export interface Menu {
  menuCode: string
  name: string
  imageUrl: string | null
  description: string | null
  kdsDeviceId: string | null
  active: boolean
  /**
   * 기준 조리시간(분). 실측 평균이 아니라 운영자가 정하는 값이고,
   * 예상 대기시간을 "기준 조리시간 x 대기열"로 뽑을 때 앞의 항입니다.
   * null은 "아직 안 정했다"이며 0으로 읽으면 안 됩니다.
   */
  cookMinutes: number | null
}

export interface MenuSummary extends Menu {
  currentPrice: number | null
}

/** 결품 화면 한 줄 — 메뉴판 전체에 오늘 결품 표시를 단 것. */
export interface SoldOutRow {
  menuCode: string
  name: string
  active: boolean
  currentPrice: number | null
  cookMinutes: number | null
  soldOut: boolean
}

export interface SoldOutBoard {
  storeId: string
  businessDate: string
  menus: SoldOutRow[]
}

export interface MenuPrice {
  id: number
  menuCode: string
  price: number
  startDate: string
  endDate: string | null
}

/**
 * Spring rotates the CSRF token on login, so it is read per write rather
 * than cached. One extra round trip on a screen used by a handful of
 * operators is cheaper than the class of bug caching it invites.
 */
async function csrfToken(): Promise<string> {
  const res = await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
  if (!res.ok) throw new Error('CSRF 토큰을 받지 못했습니다')
  const body = (await res.json()) as { token: string }
  return body.token
}

/** Prefers the server's own message over a bare status code. */
async function errorFrom(res: Response): Promise<Error> {
  try {
    const body = (await res.json()) as { error?: string; message?: string }
    if (body.error) return new Error(body.error)
    if (body.message) return new Error(body.message)
  } catch {
    // not JSON — fall through
  }
  if (res.status === 403) return new Error('이 작업에 대한 권한이 없습니다')
  if (res.status === 400) return new Error('입력값을 확인해주세요')
  return new Error(`요청이 실패했습니다 (HTTP ${res.status})`)
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { credentials: 'same-origin' })
  if (res.status === 401) throw new UnauthorizedError()
  if (!res.ok) throw await errorFrom(res)
  return (await res.json()) as T
}

type Method = 'POST' | 'PUT' | 'DELETE'

export async function apiWrite<T>(method: Method, path: string, body?: unknown): Promise<T | null> {
  const headers: Record<string, string> = { 'X-XSRF-TOKEN': await csrfToken() }
  let payload: BodyInit | undefined

  if (body instanceof URLSearchParams) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=UTF-8'
    payload = JSON.stringify(body)
  }

  const res = await fetch(BASE + path, { method, headers, body: payload, credentials: 'same-origin' })
  if (res.status === 401) throw new UnauthorizedError()
  if (!res.ok) throw await errorFrom(res)
  return res.status === 204 ? null : ((await res.json()) as T)
}

/** Spring Security's own filter handles this, which is why it is form-encoded rather than JSON. */
export async function login(username: string, password: string): Promise<void> {
  const form = new URLSearchParams({ username, password })
  await apiWrite('POST', '/auth/login', form)
}

export async function logout(): Promise<void> {
  await apiWrite('POST', '/auth/logout')
}

export function fetchMe(): Promise<Me> {
  return apiGet<Me>('/auth/me')
}
