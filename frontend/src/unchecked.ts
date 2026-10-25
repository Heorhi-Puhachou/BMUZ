import { useEffect, useState } from 'react'
import { isAdmin, useAuth } from './auth'

const NONE = new Set<number>()

/** Альбомы, якіх гэты адміністратар яшчэ не праглядзеў, — для адзнак на вокладках. Астатнім нічога не запытваецца. */
export function useUnchecked(): Set<number> {
  const { me } = useAuth()
  const admin = isAdmin(me)
  const [unchecked, setUnchecked] = useState<Set<number>>(NONE)

  useEffect(() => {
    if (!admin) {
      return
    }
    const controller = new AbortController()
    fetch('/api/admin/albums/unchecked', { signal: controller.signal })
      .then((r) => (r.ok ? r.json() : null))
      .then((data: { albumIds: number[] } | null) => {
        if (data && !controller.signal.aborted) {
          setUnchecked(new Set(data.albumIds))
        }
      })
      .catch(() => {})
    return () => controller.abort()
  }, [admin])

  return admin ? unchecked : NONE
}
