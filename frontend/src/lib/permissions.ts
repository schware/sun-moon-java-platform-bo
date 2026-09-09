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

export const SCREENS = ['BOARD', 'COMMON_CODE', 'DEVICE', 'MENU', 'OPERATOR'] as const
export type Screen = (typeof SCREENS)[number]

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

/** Screens with a page behind them. OPERATOR is deliberately absent — it has no endpoints yet. */
export const MENU: MenuItem[] = [
  { screen: 'BOARD', path: '/board', label: '공지사항' },
  { screen: 'COMMON_CODE', path: '/common-code', label: '공통코드' },
  { screen: 'DEVICE', path: '/devices', label: '장비' },
  { screen: 'MENU', path: '/menus', label: '메뉴' }
]
