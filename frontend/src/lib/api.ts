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

export interface Device {
  deviceId: string
  name: string
  deviceType: string
  location: string | null
  active: boolean
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
