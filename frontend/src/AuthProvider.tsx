import { type ReactNode, useEffect, useState } from 'react'
import { AuthContext, type Me } from './auth'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [registration, setRegistration] = useState(false)

  useEffect(() => {
    fetch('/api/auth/me')
      .then((r) => (r.status === 200 ? r.json() : null))
      .then(setMe)
      .catch(() => setMe(null))
    fetch('/api/auth/config')
      .then((r) => (r.ok ? r.json() : { registration: false }))
      .then((c) => setRegistration(Boolean(c.registration)))
      .catch(() => setRegistration(false))
  }, [])

  return <AuthContext.Provider value={{ me, setMe, registration }}>{children}</AuthContext.Provider>
}
