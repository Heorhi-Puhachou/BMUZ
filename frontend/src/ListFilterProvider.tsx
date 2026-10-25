import { type ReactNode, useState } from 'react'
import { isAdmin, useAuth } from './auth'
import { ListFilterContext } from './listFilter'

const STORAGE_KEYS = { free: 'bmuz.freeOnly', unchecked: 'bmuz.uncheckedOnly' }

function saved(key: string) {
  try {
    return localStorage.getItem(key) === 'true'
  } catch {
    return false
  }
}

// Абодва выбары памятаюцца паасобку: адміністратар, які выйшаў, зноў бачыць свой «толькі бясплатнае».
export default function ListFilterProvider({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  const kind = isAdmin(me) ? 'unchecked' : 'free'
  const [on, setState] = useState(() => ({ free: saved(STORAGE_KEYS.free), unchecked: saved(STORAGE_KEYS.unchecked) }))

  function setOn(value: boolean) {
    setState((current) => ({ ...current, [kind]: value }))
    try {
      localStorage.setItem(STORAGE_KEYS[kind], String(value))
    } catch {
      // Браўзэр не дазваляе захоўваць: выбар проста не запомніцца.
    }
  }

  return <ListFilterContext.Provider value={{ kind, on: on[kind], setOn }}>{children}</ListFilterContext.Provider>
}
