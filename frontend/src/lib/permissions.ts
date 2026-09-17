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
  'BOARD', 'COMMON_CODE', 'DEVICE', 'MENU', 'SOLD_OUT', 'STORE', 'TABLE',
  'SALES', 'APP_SALES', 'TERMINAL',
  'POS_BUSINESS', 'POS_LIVE', 'POS_DEPLOY', 'POS_NOTICE',
  'OPERATOR'
] as const
export type Screen = (typeof SCREENS)[number]

/**
 * For the 운영자 permission matrix, which otherwise shows enum names.
 *
 * 매출 조회가 둘이라 여기서는 앞에 POS/APP를 붙입니다 — 행렬에는 묶음
 * 머리글이 없어서 이름만으로 갈려야 합니다. 왼쪽 트리에서는 묶음이
 * 이미 말해주므로 그냥 "매출 조회"입니다 (MENU 의 label).
 */
export const SCREEN_LABEL: Record<Screen, string> = {
  BOARD: '공지사항',
  COMMON_CODE: '공통코드',
  DEVICE: '장비',
  MENU: '메뉴',
  SOLD_OUT: '결품',
  STORE: '매장',
  TABLE: '매장 배치도',
  SALES: 'POS 매출 조회',
  APP_SALES: 'APP 매출 조회',
  TERMINAL: '단말 현황',
  POS_BUSINESS: '개점 현황',
  POS_LIVE: 'Live Check',
  POS_DEPLOY: '배포 현황',
  POS_NOTICE: '단말 공지',
  OPERATOR: '운영자'
}

/**
 * Screens with nothing to grant beyond 조회. Both read another service and
 * neither has a write endpoint at all — see the two controllers. The
 * matrix greys the other three columns rather than offering permissions
 * that could only ever be inert.
 */
export const VIEW_ONLY_SCREENS: readonly Screen[] =
  ['SALES', 'APP_SALES', 'TERMINAL', 'POS_BUSINESS', 'POS_LIVE']

/**
 * 화면마다 실제로 뜻이 있는 행위. 안 적힌 화면은 네 가지 모두입니다.
 *
 * 결품에 저장(SAVE)이 없는 이유: 결품은 고치는 값이 아니라 걸거나 푸는
 * 것이고, 표에도 걸면 행이 생기고 풀면 행이 사라집니다. 그대로
 * CREATE(선언)와 DELETE(해제)입니다 — 가운데에 둘 것이 없습니다.
 */
export const SCREEN_ACTIONS: Partial<Record<Screen, readonly Action[]>> = {
  SALES: ['VIEW'],
  APP_SALES: ['VIEW'],
  TERMINAL: ['VIEW'],
  SOLD_OUT: ['VIEW', 'CREATE', 'DELETE'],

  // 개점·마감은 POS가 누르는 것이고 BO는 결과를 봅니다. Live Check 도
  // 전부 장비가 만든 사실이라 BO에서 쓸 것이 없습니다.
  POS_BUSINESS: ['VIEW'],
  POS_LIVE: ['VIEW'],

  // 배포는 SAVE 가 마스터 버전 올리기, CREATE 가 프로그램 배포 등록
  // 입니다. 앞은 이미 있는 것을 다시 내리는 일이고, 뒤는 **매장 장비에
  // 깔릴 파일을 새로 지정하는** 일이라 무게가 다릅니다. DELETE 는
  // 없습니다 — 배포는 지우는 것이 아니라 새로 내는 것입니다.
  POS_DEPLOY: ['VIEW', 'CREATE', 'SAVE'],

  // 공지는 걸거나 안 겁니다. 고치는 것이 없는 이유: 이미 단말에 뜬
  // 글을 고치면 본 사람과 안 본 사람이 서로 다른 글을 봅니다.
  POS_NOTICE: ['VIEW', 'CREATE']
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
 * - `MASTER`  기준정보. 양쪽이 다 읽는 것들. 결품이 여기 있는 것은
 *             POS도 손님 화면도 같은 값을 읽기 때문입니다 — 거는 곳은
 *             하나고, 읽는 곳이 둘입니다.
 * - `POS`     매장·단말 쪽. 단말과 그 단말이 만든 매출.
 * - `APP`     손님 쪽 창구(APP-SERVER)의 것들. 테이블 QR을 만들고,
 *             그 QR과 앱으로 들어온 주문을 되짚어 봅니다. App·주문 QR
 *             마스터가 여기로 들어옵니다.
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
  { group: 'MASTER', screen: 'SOLD_OUT', path: '/sold-out', label: SCREEN_LABEL.SOLD_OUT },
  { group: 'MASTER', screen: 'COMMON_CODE', path: '/common-code', label: SCREEN_LABEL.COMMON_CODE },

  { group: 'POS', screen: 'DEVICE', path: '/devices', label: SCREEN_LABEL.DEVICE },
  { group: 'POS', screen: 'TERMINAL', path: '/terminals', label: SCREEN_LABEL.TERMINAL },
  { group: 'POS', screen: 'SALES', path: '/sales', label: '매출 조회' },
  { group: 'POS', screen: 'POS_BUSINESS', path: '/pos-business', label: SCREEN_LABEL.POS_BUSINESS },
  { group: 'POS', screen: 'POS_LIVE', path: '/pos-live', label: SCREEN_LABEL.POS_LIVE },
  { group: 'POS', screen: 'POS_DEPLOY', path: '/pos-deploy', label: SCREEN_LABEL.POS_DEPLOY },
  { group: 'POS', screen: 'POS_NOTICE', path: '/pos-notices', label: SCREEN_LABEL.POS_NOTICE },

  { group: 'APP', screen: 'TABLE', path: '/tables', label: SCREEN_LABEL.TABLE },
  { group: 'APP', screen: 'APP_SALES', path: '/app-sales', label: '매출 조회' },

  { group: 'SYSTEM', screen: 'BOARD', path: '/board', label: SCREEN_LABEL.BOARD },
  { group: 'SYSTEM', screen: 'OPERATOR', path: '/operators', label: SCREEN_LABEL.OPERATOR }
]
