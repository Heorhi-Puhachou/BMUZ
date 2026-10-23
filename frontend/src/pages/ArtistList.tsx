import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { type ArtistTile, type Page, useApi } from '../api'
import Cover from '../Cover'
import Status from '../Status'
import { useTitle } from '../useTitle'

// 48 дзеліцца на 2, 3, 4 і 6, таму радкі плітак запаўняюцца цалкам пры любой шырыні.
const PAGE_SIZE = 48

export default function ArtistList() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const loaded = useApi<Page<ArtistTile>>(`/api/artists?page=${page}&size=${PAGE_SIZE}`)
  useTitle(undefined)

  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const { items, total } = loaded.data
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <>
      <h1>{t('nav.artists')}</h1>
      <ul className="tiles">
        {items.map((artist) => (
          <li key={artist.id}>
            <Link to={`/artists/${artist.id}`} className="tile">
              <Cover url={artist.coverUrl} title={artist.name} />
              <span className="tile-name">{artist.name}</span>
            </Link>
          </li>
        ))}
      </ul>
      <nav className="pager">
        {page > 0 && <Link to={`/?page=${page - 1}`}>{t('artists.prev')}</Link>}
        <span className="muted">{t('artists.page', { page: page + 1, pages })}</span>
        {page + 1 < pages && <Link to={`/?page=${page + 1}`}>{t('artists.next')}</Link>}
      </nav>
    </>
  )
}
