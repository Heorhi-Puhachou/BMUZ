import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { type AlbumSummary, type Artist, useApi } from '../api'
import ArtistContacts from '../ArtistContacts'
import ArtistEditor from '../ArtistEditor'
import ArtistImage from '../ArtistImage'
import ArtistMembers from '../ArtistMembers'
import { canEdit, useAuth } from '../auth'
import Cover from '../Cover'
import Credit from '../Credit'
import { useListFilter, withFilter } from '../listFilter'
import RatingSquares from '../RatingSquares'
import Status from '../Status'
import { useUnchecked } from '../unchecked'
import { useTitle } from '../useTitle'

// Калі альбом супольны, пад назвай відаць усе выканаўцы.
function others(album: AlbumSummary) {
  return album.credit.length > 1
}

export default function ArtistPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const filter = useListFilter()
  const { me } = useAuth()
  const [reload, setReload] = useState(0)
  const loaded = useApi<Artist>(withFilter(`/api/artists/${id}`, filter), reload)
  const unchecked = useUnchecked()
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

      <ArtistContacts artist={artist} onChange={() => setReload((r) => r + 1)} />
      <ArtistImage artist={artist} onChange={() => setReload((r) => r + 1)} />

      {canEdit(me, [artist.id]) && <ArtistEditor key={artist.id} artist={artist} onChange={() => setReload((r) => r + 1)} />}
      {me?.role === 'SUPER_ADMIN' && <ArtistMembers artistId={artist.id} />}

      {artist.albums.length > 0 && (
        <>
          <h2>{t('artist.albums')}</h2>
          <ul className="tiles">
            {artist.albums.map((album) => (
              <li key={album.id} className="tile">
                <Link to={`/albums/${album.id}`} className="tile-link">
                  <Cover
                    url={album.coverUrl}
                    title={album.title}
                    mark={unchecked.has(album.id) ? t('checks.albumMark') : undefined}
                  />
                  <RatingSquares rating={album.rating} />
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
