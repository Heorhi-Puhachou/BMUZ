import { useEffect, useState } from 'react'

export type PriceType = 'FREE' | 'NAME_YOUR_PRICE' | 'PAID'

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface ArtistSummary {
  id: number
  name: string
}

export interface ArtistTile extends ArtistSummary {
  coverUrl: string | null
}

export interface CreditEntry {
  artistId: number
  name: string
  joinPhrase: string
}

export function creditText(credit: CreditEntry[]) {
  return credit.map((entry) => entry.name + entry.joinPhrase).join('')
}

/** Час з сэрвэра (UTC) да хвіліны: 2026-10-25 14:03. */
export function shortTime(instant: string) {
  return instant.slice(0, 16).replace('T', ' ')
}

export interface AlbumSummary {
  id: number
  year: number
  title: string
  coverUrl: string | null
  credit: CreditEntry[]
}

export interface TrackHit {
  id: number
  number: number
  title: string
  credit: CreditEntry[]
  album: AlbumSummary
}

export interface Artist {
  id: number
  name: string
  aliases: string[]
  albums: AlbumSummary[]
  otherTracks: TrackHit[]
}

export interface Link {
  id: number
  store: string
  url: string
  priceType: PriceType
  minPrice: number | null
  currency: string | null
  note: string | null
}

export interface Track {
  id: number
  number: number
  title: string
  lengthSeconds: number | null
  credit: CreditEntry[]
  links: Link[]
}

export interface Album {
  id: number
  year: number
  title: string
  coverUrl: string | null
  coverLicence: string | null
  coverSource: string | null
  credit: CreditEntry[]
  tracks: Track[]
  links: Link[]
}

export interface SearchResult {
  artists: ArtistSummary[]
  albums: AlbumSummary[]
}

export type Loaded<T> =
  | { state: 'loading' }
  | { state: 'error' }
  | { state: 'notFound' }
  | { state: 'ok'; data: T }

/** `reload` — лічыльнік: калі ён мяняецца, дадзеныя загружаюцца нанова (напрыклад, пасьля загрузкі вокладкі). */
export function useApi<T>(path: string, reload = 0): Loaded<T> {
  const [result, setResult] = useState<{ path: string; reload: number; loaded: Loaded<T> } | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch(path, { signal: controller.signal })
      .then(async (response) => {
        if (response.status === 404) {
          return { state: 'notFound' } as const
        }
        if (!response.ok) {
          return { state: 'error' } as const
        }
        return { state: 'ok', data: (await response.json()) as T } as const
      })
      .catch(() => ({ state: 'error' }) as const)
      .then((loaded) => {
        if (!controller.signal.aborted) {
          setResult({ path, reload, loaded })
        }
      })
    return () => controller.abort()
  }, [path, reload])

  return result?.path === path && result.reload === reload ? result.loaded : { state: 'loading' }
}
