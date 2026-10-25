import { useLayoutEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { type ArtistTile, type Page, useApi } from '../api'
import Cover from '../Cover'
import Pager from '../Pager'
import { useListFilter, withFilter } from '../listFilter'
import RatingSquares from '../RatingSquares'
import Status from '../Status'
import { useTitle } from '../useTitle'

// 48 дзеліцца на 2, 3, 4 і 6, таму радкі плітак запаўняюцца цалкам пры любой шырыні.
const PAGE_SIZE = 48

export default function ArtistList() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const filter = useListFilter()
  const loaded = useApi<Page<ArtistTile>>(withFilter(`/api/artists?page=${page}&size=${PAGE_SIZE}`, filter), 0, true)
  useTitle(undefined)

  // Пасьля пераходу ніжнім радком старонак ён застаецца пад курсорам: новая старонка можа быць іншай вышыні.
  const anchor = useRef<{ pager: HTMLElement; top: number } | null>(null)
  useLayoutEffect(() => {
    const kept = anchor.current
    if (kept && loaded.state === 'ok') {
      anchor.current = null
      window.scrollBy(0, kept.pager.getBoundingClientRect().top - kept.top)
    }
  }, [loaded])

  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const { items, total } = loaded.data
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <>
      <h1>{t('nav.artists')}</h1>
      <Pager path="/" page={page} pages={pages} />
      <ul className="tiles">
        {items.map((artist) => (
          <li key={artist.id}>
            <Link to={`/artists/${artist.id}`} className="tile">
              <Cover url={artist.coverUrl} title={artist.name} />
              <RatingSquares rating={artist.rating} />
              <span className="tile-name">{artist.name}</span>
            </Link>
          </li>
        ))}
      </ul>
      <Pager
        path="/"
        page={page}
        pages={pages}
        onNavigate={(pager) => (anchor.current = { pager, top: pager.getBoundingClientRect().top })}
      />
    </>
  )
}
