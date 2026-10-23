import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { type SearchResult, useApi } from '../api'
import Credit from '../Credit'
import Status from '../Status'
import { useTitle } from '../useTitle'

export default function SearchPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const q = params.get('q') ?? ''
  const loaded = useApi<SearchResult>(`/api/search?q=${encodeURIComponent(q)}`)
  useTitle(t('search.heading', { q }))

  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const { artists, albums } = loaded.data

  return (
    <>
      <h1>{t('search.heading', { q })}</h1>
      {artists.length === 0 && albums.length === 0 && <p>{t('search.nothing')}</p>}
      {artists.length > 0 && (
        <>
          <h2>{t('nav.artists')}</h2>
          <ul className="plain">
            {artists.map((artist) => (
              <li key={artist.id}>
                <Link to={`/artists/${artist.id}`}>{artist.name}</Link>
              </li>
            ))}
          </ul>
        </>
      )}
      {albums.length > 0 && (
        <>
          <h2>{t('artist.albums')}</h2>
          <ul className="plain">
            {albums.map((album) => (
              <li key={album.id}>
                <span className="year">{album.year}</span> <Link to={`/albums/${album.id}`}>{album.title}</Link>
                <span className="muted">
                  {' — '}
                  <Credit credit={album.credit} />
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  )
}
