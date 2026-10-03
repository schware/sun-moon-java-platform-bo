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

export type TerminalType = 'POS' | 'KDS' | 'DID' | 'KIOSK'

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
  /** KDS 만: 'MAIN' 은 주방의 중심(매장에 하나), 비어 있으면 SUB. 페어링할 때 기기에 묶입니다. */
  kdsRole?: 'MAIN' | 'SUB' | null
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

/**
 * 한 건의 주문. 매출 조회의 펼침과 APP 매출 조회가 같이 씁니다 — 서버가
 * 주는 것이 하나라서, 화면마다 다르게 부르지 않습니다.
 */
/** 주문 한 줄. Order 가 만든 값을 그대로 받습니다. */
export interface SalesOrderLine {
  seq: number
  menuCode: string | null
  menuName: string
  quantity: number
  unitPrice: number
  lineAmount: number
}

export interface SalesOrder {
  id: number
  storeId: string
  customerId: string
  menuName: string
  amount: number
  status: string
  acceptedBy: string | null
  /**
   * 도착지 — "이 메뉴를 저 자리로". 테이블 QR로 들어온 주문에만 있고,
   * 매장 선택 화면이나 포장·배달은 null 입니다. null 이 "모른다"가
   * 아니라 "테이블이 아니다"라는 뜻입니다.
   */
  floor: number | null
  tableNo: string | null
  /** 부르는 번호. DID에 뜨고 손님이 보고 온 값입니다. */
  pickupNo: string | null
  /** 이 주문이 들어온 테이블 QR의 코드. 자리 번호를 바꿔도 그대로입니다. */
  qrCode: string | null
  /** 거래번호 — 매출 키의 마지막 자리. 수락 전에는 null 입니다. */
  tranNo: string | null
  /** 어느 단말이 내줬나. 2026-09-17 이전 주문에는 없습니다. */
  pickupBy: string | null
  pickupAt: string | null
  /**
   * 수령번호를 확인하지 않고 내준 건인가.
   *
   * 기록만 하고 볼 곳이 없으면 기록하지 않은 것과 같아서 이 화면까지
   * 끌고 옵니다. 늘어나면 둘 중 하나입니다 — 손님이 정말 번호를 못
   * 대거나(보여주는 방식을 고쳐야 합니다), 매장이 묻기를 그만뒀거나
   * (그러면 수령번호가 이미 장식입니다).
   */
  pickupOverride: boolean
  /** KDS-MAIN 이 남은 줄을 강제로 끝냈으면 그 기기 (store-app.md §4.1). */
  forceProducedBy?: string | null
  /** MAIN 이 끊긴 동안 SUB 가 내준 수령 (§4.2). */
  pickupEmergency?: boolean
  /**
   * 주문의 줄들 (§4.4.2).
   *
   * `menuName` 은 이제 메뉴 이름이 아니라 **주문의 이름**입니다 —
   * "짜장면 외 2건". 무엇이 얼마나 팔렸는지는 여기에만 있습니다.
   * 2026-09-18 이전 주문은 비어 있습니다.
   */
  lines: SalesOrderLine[]
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

/**
 * APP-SERVER 원장에서 읽어 온 주문 한 건.
 *
 * `channel` 이 이 화면의 요점입니다 — 전에는 "테이블 번호가 있는가"로
 * QR 주문을 짐작했는데, 그건 도착지이지 유입 경로가 아닙니다.
 *
 * `status` 는 OMS의 상태가 아니라 **손님의 요청이 어떻게 됐는가**입니다.
 * 그래서 OMS가 거절한 주문(REJECTED)과 넘기지도 못한 주문(SEND_FAILED)도
 * 여기 나옵니다 — 매출 조회에는 아예 없는 것들입니다.
 */
export interface AppOrderRow {
  id: number
  orderNo: string
  storeId: string
  businessDate: string
  channel: 'APP' | 'QR' | 'EXTERNAL'
  type: 'IN_STORE' | 'DELIVERY'
  status: 'CREATED' | 'SENT' | 'REJECTED' | 'SEND_FAILED' | 'CANCELLED'
  floor: number | null
  tableNo: string | null
  totalAmount: number
  pickupNo: string | null
  lines: { detailSeq: number; menuCode: string; menuName: string; quantity: number }[]
  placedAt: string
}

/* ---- POS 운영 관제 (업무 정의서 §3.4~3.6) ------------------------------
 *
 * 전부 POS-Server 가 갖고 있는 값입니다. BO는 읽어서 그릴 뿐이고,
 * 사람이 시작하는 셋(마스터 버전, 프로그램 배포, 공지)만 대신 보냅니다.
 */

export type AxisState = 'OK' | 'WARN' | 'DOWN' | 'UNKNOWN'

/** 다섯 축. 순서가 화면의 열 순서입니다 — 밖에서 안으로: 연결 → 데이터. */
export const LIVE_AXES = ['CONNECTION', 'APPLICATION', 'BUSINESS', 'DEVICE', 'DATA'] as const
export type LiveAxis = (typeof LIVE_AXES)[number]

export const AXIS_LABEL: Record<LiveAxis, string> = {
  CONNECTION: '연결',
  APPLICATION: '프로그램',
  BUSINESS: '영업',
  DEVICE: '장비',
  DATA: '데이터'
}

export interface PosBusinessState {
  posNo: string
  status: 'OPEN' | 'CLOSED'
  /** OPEN / CLOSE / REOPEN. 상태와 다릅니다 — 재개점한 POS도 상태는 OPEN 입니다. */
  lastEvent: 'OPEN' | 'CLOSE' | 'REOPEN'
  openCount: number
  eventAt: string
  receivedAt: string
}

export interface PosBusinessStates {
  storeId: string
  businessDate: string
  terminals: PosBusinessState[]
}

export interface PosBusinessHistoryEntry {
  seq: number
  event: 'OPEN' | 'CLOSE' | 'REOPEN'
  /** APPLIED / DUPLICATE / IGNORED_STALE — 무시된 전문도 남습니다. */
  result: 'APPLIED' | 'DUPLICATE' | 'IGNORED_STALE'
  message: string | null
  eventAt: string
  receivedAt: string
  irtId: string | null
}

export interface PosBusinessHistory {
  storeId: string
  posNo: string
  businessDate: string
  history: PosBusinessHistoryEntry[]
}

export interface PosLiveRow {
  posNo: string
  axes: Record<string, AxisState>
  overall: AxisState
  normal: boolean
  programVersion: string | null
  dbVersion: string | null
  /** 서버 - POS, 초. 양수면 POS가 느립니다. */
  clockDriftSeconds: number | null
  lastReportedAt: string | null
  mismatches: string[]
}

export interface PosLiveCheck {
  storeId: string
  businessDate: string
  terminals: PosLiveRow[]
}

export interface MasterPlan {
  masterType: string
  label: string
  currentVersion: number
  appliedVersion: number | null
  mode: 'FULL' | 'DELTA' | 'UP_TO_DATE'
  lastError: string | null
}

export interface MasterPlans {
  storeId: string
  deviceType: string
  deviceNo: string
  masters: MasterPlan[]
}

export interface MasterPending {
  deviceType: string
  deviceNo: string
  masterType: string
  appliedVersion: number | null
  status: 'SENT' | 'APPLIED' | 'FAILED'
  attempts: number
  lastError: string | null
  sentAt: string | null
}

export interface ProgramInstall {
  deviceType: string
  deviceNo: string
  program: string
  installedVersion: string | null
  targetVersion: string | null
  status: 'OFFERED' | 'DOWNLOADED' | 'INSTALLED' | 'FAILED'
  attempts: number
  lastError: string | null
  offeredAt: string | null
  installedAt: string | null
}

export interface ProgramRelease {
  id: number
  /** 'ALL_STORES' 또는 매장 코드. 매장 지정이 전 매장을 이깁니다. */
  scope: string
  deviceType: string
  program: string
  version: string
  mandatory: boolean
  note: string | null
  releasedAt: string
}

export interface ProgramStatus {
  storeId: string
  installs: ProgramInstall[]
  releases: ProgramRelease[]
}

export interface PosNotice {
  id: number
  scope: string
  deviceType: string | null
  title: string
  body: string
  urgent: boolean
  startsAt: string
  endsAt: string | null
  createdBy: string | null
  active: boolean
  /** 긴급일 때만 옵니다 — 일반 공지는 아무도 확인하지 않습니다. */
  acknowledged?: number
  acknowledgedBy?: string[]
}

/* ---- 기준정보 공통 CRUD (umbrella docs/adr/0014) --------------------
 *
 * 화면이 자기 모양을 코드로 갖고 있지 않습니다. 서버가 정의를 주고
 * MasterPage 가 그대로 그립니다 — 기준정보를 하나 더 붙여도 프런트엔드는
 * 한 줄도 안 바뀝니다.
 */

export type MasterFieldType = 'TEXT' | 'LONG_TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'CODE'

export interface MasterField {
  name: string
  label: string
  type: MasterFieldType
  required: boolean
  /** 조회 조건으로 쓸 수 있는가. 색인 없는 칸으로 거르면 느려집니다. */
  searchable: boolean
  /** 고칠 수 있는가. 키는 false 입니다 — 키를 바꾸는 것은 다른 행을 만드는 일입니다. */
  editable: boolean
  /** type === 'CODE' 일 때 어느 공통코드 그룹인가. */
  codeGroup: string | null
}

export interface MasterDefinition {
  code: string
  label: string
  idColumn: string
  fields: MasterField[]
}

/**
 * 기준정보 한 행.
 *
 * 타입이 느슨한 것이 이 구조의 요점이자 대가입니다. 표마다 interface 를
 * 만들면 타입이 지켜 주지만, 그러면 표마다 파일이 하나씩 늘어 **공통화의
 * 의미가 사라집니다**. 대신 무엇이 들어 있는지는 정의가 말해 줍니다.
 */
export type MasterRow = Record<string, unknown>

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
/**
 * 로그인이 끊겼다는 신호 — 어느 화면에서 401 을 받든 App 이 로그인 화면으로 돌립니다.
 *
 * 화면마다 401 을 다루게 두면, 끊긴 세션이 화면마다 다른 오류 문구로만 보이고 운영자는 무엇을 해야 하는지
 * 모릅니다. 주소는 그대로 두므로 다시 로그인하면 보던 화면으로 돌아갑니다.
 */
const SESSION_LOST = 'bo:session-lost'

function sessionLost(): UnauthorizedError {
  window.dispatchEvent(new Event(SESSION_LOST))
  return new UnauthorizedError()
}

/** @returns 듣기를 그만두는 함수 */
export function onSessionLost(listener: () => void): () => void {
  window.addEventListener(SESSION_LOST, listener)
  return () => window.removeEventListener(SESSION_LOST, listener)
}

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
  if (res.status === 401) throw sessionLost()
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
  if (res.status === 401) throw sessionLost()
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
