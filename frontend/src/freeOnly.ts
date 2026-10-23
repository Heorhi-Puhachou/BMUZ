import { createContext, useContext } from 'react'

// «Паказваць толькі бясплатнае»: сьпісы паказваюць толькі альбомы са спасылкай FREE або NAME_YOUR_PRICE.
// Старонка альбома паказвае ўсе спасылкі заўсёды.
export interface FreeOnly {
  freeOnly: boolean
  setFreeOnly: (value: boolean) => void
}

export const FreeOnlyContext = createContext<FreeOnly>({ freeOnly: false, setFreeOnly: () => {} })

export function useFreeOnly() {
  return useContext(FreeOnlyContext)
}

/** Дадае `free=true` да адраса API, калі фільтар уключаны. */
export function withFree(path: string, freeOnly: boolean) {
  if (!freeOnly) {
    return path
  }
  return `${path}${path.includes('?') ? '&' : '?'}free=true`
}
