import { createContext, useContext } from 'react'

export interface Me {
  email: string
  role: 'SUPER_ADMIN' | 'ADMIN' | 'ARTIST' | 'USER'
  /** Выканаўцы, якіх гэты запіс рэдагуе. Адміністратар рэдагуе ўсіх. */
  artists: number[]
}

/** Адміністратар або суперадміністратар: рэдагуе ўвесь каталёг. */
export function isAdmin(me: Me | null) {
  return me?.role === 'ADMIN' || me?.role === 'SUPER_ADMIN'
}

/** Ці можа гэты запіс рэдагаваць выканаўцу або альбом, дзе названы гэтыя выканаўцы. */
export function canEdit(me: Me | null, artistIds: number[]) {
  return me != null && (isAdmin(me) || artistIds.some((id) => me.artists.includes(id)))
}

export interface Auth {
  me: Me | null
  setMe: (me: Me | null) => void
  /** Ці адкрытая публічная рэгістрацыя на гэтым сэрвэры. */
  registration: boolean
}

export const AuthContext = createContext<Auth>({ me: null, setMe: () => {}, registration: false })

export function useAuth() {
  return useContext(AuthContext)
}

function cookie(name: string) {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/**
 * Запыт, які нешта мяняе. Токен CSRF бярэцца з кукі XSRF-TOKEN; калі яго яшчэ няма, спачатку пытаемся /api/auth/csrf —
 * толькі тады браўзэр і атрымлівае гэты кукі.
 */
export async function send(method: string, path: string, body?: BodyInit, json = false) {
  if (!cookie('XSRF-TOKEN')) {
    await fetch('/api/auth/csrf')
  }
  const headers: Record<string, string> = { 'X-XSRF-TOKEN': decodeURIComponent(cookie('XSRF-TOKEN') ?? '') }
  if (json) {
    headers['Content-Type'] = 'application/json'
  }
  return fetch(path, { method, headers, body })
}

/** Чалавечае тлумачэньне адмовы сэрвэра, калі яно ёсьць. */
export async function reason(response: Response) {
  try {
    const data = await response.json()
    return data.detail ?? data.message ?? data.error ?? String(response.status)
  } catch {
    return String(response.status)
  }
}
