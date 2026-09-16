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

export interface MenuItem {
  screen: Screen
  path: string
  label: string
}

/** Every screen now has a page behind it — OPERATOR was the last one without endpoints. */
export const MENU: MenuItem[] = [
  { screen: 'BOARD', path: '/board', label: SCREEN_LABEL.BOARD },
  { screen: 'COMMON_CODE', path: '/common-code', label: SCREEN_LABEL.COMMON_CODE },
  { screen: 'STORE', path: '/stores', label: SCREEN_LABEL.STORE },
  { screen: 'TABLE', path: '/tables', label: SCREEN_LABEL.TABLE },
  { screen: 'MENU', path: '/menus', label: SCREEN_LABEL.MENU },
  { screen: 'SOLD_OUT', path: '/sold-out', label: SCREEN_LABEL.SOLD_OUT },
  { screen: 'DEVICE', path: '/devices', label: SCREEN_LABEL.DEVICE },
  { screen: 'TERMINAL', path: '/terminals', label: SCREEN_LABEL.TERMINAL },
  { screen: 'SALES', path: '/sales', label: SCREEN_LABEL.SALES },
  { screen: 'OPERATOR', path: '/operators', label: SCREEN_LABEL.OPERATOR }
]
