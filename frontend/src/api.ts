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

export function useApi<T>(path: string): Loaded<T> {
  const [result, setResult] = useState<{ path: string; loaded: Loaded<T> } | null>(null)

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
          setResult({ path, loaded })
        }
      })
    return () => controller.abort()
  }, [path])

  return result?.path === path ? result.loaded : { state: 'loading' }
}
