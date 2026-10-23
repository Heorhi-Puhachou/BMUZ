import { type ReactNode, useState } from 'react'
import { FreeOnlyContext } from './freeOnly'

const STORAGE_KEY = 'bmuz.freeOnly'

function saved() {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

export default function FreeOnlyProvider({ children }: { children: ReactNode }) {
  const [freeOnly, setState] = useState(saved)

  function setFreeOnly(value: boolean) {
    setState(value)
    try {
      localStorage.setItem(STORAGE_KEY, String(value))
    } catch {
      // Браўзэр не дазваляе захоўваць: выбар проста не запомніцца.
    }
  }

  return <FreeOnlyContext.Provider value={{ freeOnly, setFreeOnly }}>{children}</FreeOnlyContext.Provider>
}
