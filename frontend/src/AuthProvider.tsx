import { type ReactNode, useCallback, useEffect, useState } from 'react'
import { AuthContext, type Me } from './auth'
import i18n from './i18n'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setState] = useState<Me | null>(null)

  // Правапіс з профілю ўключаецца, як толькі сэрвэр сказаў, хто ўвайшоў: пры ўваходзе й пры адкрыцьці сайта.
  const setMe = useCallback((next: Me | null) => {
    setState(next)
    if (next?.spelling) {
      i18n.changeLanguage(next.spelling)
    }
  }, [])

  useEffect(() => {
    fetch('/api/auth/me')
      .then((r) => (r.status === 200 ? r.json() : null))
      .then(setMe)
      .catch(() => setMe(null))
  }, [setMe])

  return <AuthContext.Provider value={{ me, setMe }}>{children}</AuthContext.Provider>
}
