import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { type Album, creditText, useApi } from '../api'
import Cover from '../Cover'
import Credit from '../Credit'
import LinkList from '../LinkList'
import Status from '../Status'
import { useTitle } from '../useTitle'

function formatLength(seconds: number | null) {
  if (seconds == null) {
    return ''
  }
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

export default function AlbumPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const loaded = useApi<Album>(`/api/albums/${id}`)
  useTitle(loaded.state === 'ok' ? `${loaded.data.title} — ${creditText(loaded.data.credit)}` : undefined)

  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const album = loaded.data

  return (
    <>
      <div className="album-head">
        <Cover url={album.coverUrl} title={album.title} />
        <div>
          <h1>{album.title}</h1>
          <p>
            <Credit credit={album.credit} />
            <span className="muted"> · {album.year}</span>
          </p>
        </div>
      </div>

      <h2>{t('album.where')}</h2>
      <LinkList links={album.links} />

      <h2>{t('album.tracks')}</h2>
      {album.tracks.length === 0 ? (
        <p className="muted">{t('album.noTracks')}</p>
      ) : (
        <ol className="tracks">
          {album.tracks.map((track) => (
            <li key={track.id} value={track.number}>
              {track.title} <span className="muted">{formatLength(track.lengthSeconds)}</span>
              {track.credit.length > 0 && (
                <div className="muted">
                  <Credit credit={track.credit} />
                </div>
              )}
              {track.links.length > 0 && <LinkList links={track.links} />}
            </li>
          ))}
        </ol>
      )}
    </>
  )
}
