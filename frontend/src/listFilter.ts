import { createContext, useContext } from 'react'

// Сьцяжок у загалоўку, які звужае сьпісы. Наведнік бачыць «Паказваць толькі бясплатнае» — альбомы са спасылкай FREE
// або NAME_YOUR_PRICE. Адміністратар замест яго бачыць «Паказваць неправераныя» — альбомы, якіх ён не праглядзеў
// або што зьмянілі пасьля ягонага прагляду. Старонка альбома паказвае ўсё заўсёды.
export interface ListFilter {
  kind: 'free' | 'unchecked'
  on: boolean
  setOn: (value: boolean) => void
}

export const ListFilterContext = createContext<ListFilter>({ kind: 'free', on: false, setOn: () => {} })

export function useListFilter() {
  return useContext(ListFilterContext)
}

/** Дадае `free=true` або `unchecked=true` да адраса API, калі фільтар уключаны. */
export function withFilter(path: string, filter: ListFilter) {
  if (!filter.on) {
    return path
  }
  return `${path}${path.includes('?') ? '&' : '?'}${filter.kind}=true`
}
