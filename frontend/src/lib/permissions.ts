import type { Me } from './api'

/**
 * The UI half of the three-tier permission model.
 *
 * The server is the only thing that enforces these — every controller
 * method carries its own {@code @PreAuthorize}. What happens here is
 * presentation: not showing a screen an operator cannot open, and
 * disabling a button that would only ever come back 403. Never read this
 * as security.
 */

export const SCREENS = [
  'BOARD', 'COMMON_CODE', 'DEVICE', 'MENU', 'SOLD_OUT', 'STORE', 'TABLE', 'SALES', 'TERMINAL', 'OPERATOR'
] as const
export type Screen = (typeof SCREENS)[number]

/** For the 운영자 permission matrix, which otherwise shows enum names. */
export const SCREEN_LABEL: Record<Screen, string> = {
  BOARD: '공지사항',
  COMMON_CODE: '공통코드',
  DEVICE: '장비',
  MENU: '메뉴',
  SOLD_OUT: '결품',
  STORE: '매장',
  TABLE: '매장 배치도',
  SALES: '매출 조회',
  TERMINAL: '단말 현황',
  OPERATOR: '운영자'
}

/**
 * Screens with nothing to grant beyond 조회. Both read another service and
 * neither has a write endpoint at all — see the two controllers. The
 * matrix greys the other three columns rather than offering permissions
 * that could only ever be inert.
 */
export const VIEW_ONLY_SCREENS: readonly Screen[] = ['SALES', 'TERMINAL']

/**
 * 화면마다 실제로 뜻이 있는 행위. 안 적힌 화면은 네 가지 모두입니다.
 *
 * 결품에 저장(SAVE)이 없는 이유: 결품은 고치는 값이 아니라 걸거나 푸는
 * 것이고, 표에도 걸면 행이 생기고 풀면 행이 사라집니다. 그대로
 * CREATE(선언)와 DELETE(해제)입니다 — 가운데에 둘 것이 없습니다.
 */
export const SCREEN_ACTIONS: Partial<Record<Screen, readonly Action[]>> = {
  SALES: ['VIEW'],
  TERMINAL: ['VIEW'],
  SOLD_OUT: ['VIEW', 'CREATE', 'DELETE']
}

/** 이 화면에서 줄 수 있는 권한인가. 못 주는 칸은 체크박스를 죽입니다. */
export function actionApplies(screen: Screen, action: Action): boolean {
  const allowed = SCREEN_ACTIONS[screen]
  return allowed === undefined || allowed.includes(action)
}

export const ACTIONS = ['VIEW', 'CREATE', 'SAVE', 'DELETE'] as const
export type Action = (typeof ACTIONS)[number]

/** Mirrors OperatorScreenPermission.authorities(): SCREEN_ACTION, super admin passing everything. */
export function can(me: Me, screen: Screen, action: Action): boolean {
  return me.superAdmin || me.authorities.includes(`${screen}_${action}`)
}

/** Any action on a screen makes it worth putting in the menu. */
export function canSeeScreen(me: Me, screen: Screen): boolean {
  return ACTIONS.some((action) => can(me, screen, action))
}

/**
 * 화면이 속한 묶음.
 *
 * 열 개가 한 줄로 늘어서 있으면 "지금 고치려는 게 어디에 있더라"를 매번
 * 다시 찾습니다. 나누는 기준은 화면의 종류가 아니라 **누가 그 값을
 * 쓰는가**입니다 — 이 계열이 이미 그렇게 갈려 있기 때문입니다
 * (umbrella docs/adr/0008: 제어면은 BO, 실행면은 POS 쪽과 APP 쪽).
 *
 * - `MASTER`  기준정보. 양쪽이 다 읽는 것들.
 * - `POS`     매장·단말 쪽. 결품이 여기 있는 이유는 사장님 말씀 그대로
 *             입니다 — "BO에서 결품을 하면 그것을 POS가 가져가는 것".
 * - `APP`     손님 쪽 창구(APP-SERVER)가 읽는 것들. 지금은 테이블 QR
 *             하나뿐이고, App·주문 QR 마스터가 여기로 들어옵니다.
 * - `SYSTEM`  BO 자신을 운영하는 것들.
 */
export const GROUPS = ['MASTER', 'POS', 'APP', 'SYSTEM'] as const
export type Group = (typeof GROUPS)[number]

export const GROUP_LABEL: Record<Group, string> = {
  MASTER: '기준정보',
  POS: 'POS',
  APP: 'APP',
  SYSTEM: '시스템'
}

export interface MenuItem {
  screen: Screen
  path: string
  label: string
  group: Group
}

/** Every screen now has a page behind it — OPERATOR was the last one without endpoints. */
export const MENU: MenuItem[] = [
  { group: 'MASTER', screen: 'STORE', path: '/stores', label: SCREEN_LABEL.STORE },
  { group: 'MASTER', screen: 'MENU', path: '/menus', label: SCREEN_LABEL.MENU },
  { group: 'MASTER', screen: 'COMMON_CODE', path: '/common-code', label: SCREEN_LABEL.COMMON_CODE },

  { group: 'POS', screen: 'SOLD_OUT', path: '/sold-out', label: SCREEN_LABEL.SOLD_OUT },
  { group: 'POS', screen: 'DEVICE', path: '/devices', label: SCREEN_LABEL.DEVICE },
  { group: 'POS', screen: 'TERMINAL', path: '/terminals', label: SCREEN_LABEL.TERMINAL },
  { group: 'POS', screen: 'SALES', path: '/sales', label: SCREEN_LABEL.SALES },

  { group: 'APP', screen: 'TABLE', path: '/tables', label: SCREEN_LABEL.TABLE },

  { group: 'SYSTEM', screen: 'BOARD', path: '/board', label: SCREEN_LABEL.BOARD },
  { group: 'SYSTEM', screen: 'OPERATOR', path: '/operators', label: SCREEN_LABEL.OPERATOR }
]
