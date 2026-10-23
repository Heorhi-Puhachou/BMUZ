import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { type AlbumSummary, type Artist, useApi } from '../api'
import Cover from '../Cover'
import Credit from '../Credit'
import { useFreeOnly, withFree } from '../freeOnly'
import Status from '../Status'
import { useTitle } from '../useTitle'

// Калі альбом супольны, пад назвай відаць усе выканаўцы.
function others(album: AlbumSummary) {
  return album.credit.length > 1
}

export default function ArtistPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const { freeOnly } = useFreeOnly()
  const loaded = useApi<Artist>(withFree(`/api/artists/${id}`, freeOnly))
  useTitle(loaded.state === 'ok' ? loaded.data.name : undefined)

  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const artist = loaded.data

  return (
    <>
      <h1>{artist.name}</h1>
      {artist.aliases.length > 0 && (
        <p className="muted">{t('artist.aliases', { names: artist.aliases.join(', ') })}</p>
      )}

      {artist.albums.length > 0 && (
        <>
          <h2>{t('artist.albums')}</h2>
          <ul className="tiles">
            {artist.albums.map((album) => (
              <li key={album.id} className="tile">
                <Link to={`/albums/${album.id}`} className="tile-link">
                  <Cover url={album.coverUrl} title={album.title} />
                  <span className="tile-name">{album.title}</span>
                </Link>
                {others(album) && (
                  <span className="muted small">
                    <Credit credit={album.credit} />
                  </span>
                )}
                <span className="year">{album.year}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      {artist.otherTracks.length > 0 && (
        <>
          <h2>{t('artist.otherTracks')}</h2>
          <ul className="plain">
            {artist.otherTracks.map((track) => (
              <li key={track.id}>
                {track.title}
                <span className="muted">
                  {' — '}
                  <Link to={`/albums/${track.album.id}`}>{track.album.title}</Link>
                  {' ('}
                  <Credit credit={track.album.credit} />, {track.album.year})
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  )
}
