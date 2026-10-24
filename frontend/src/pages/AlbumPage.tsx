import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { type Album, creditText, useApi } from '../api'
import AlbumEditor from '../AlbumEditor'
import ArtistCover from '../ArtistCover'
import { canEdit, isAdmin, useAuth } from '../auth'
import Cover from '../Cover'
import CoverAdmin from '../CoverAdmin'
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
  const { me } = useAuth()
  const [reload, setReload] = useState(0)
  const loaded = useApi<Album>(`/api/albums/${id}`, reload)
  useTitle(loaded.state === 'ok' ? `${loaded.data.title} — ${creditText(loaded.data.credit)}` : undefined)

  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const album = loaded.data

  return (
    <>
      <div className="album-head">
        <figure className="album-cover">
          <Cover url={album.coverUrl} title={album.title} />
          {album.coverLicence && (
            <figcaption className="muted small">
              {t('album.coverLicence', { licence: album.coverLicence })}
              {album.coverSource && (
                <>
                  {' · '}
                  <a href={album.coverSource} rel="noopener" target="_blank">
                    {t('album.coverSource')}
                  </a>
                </>
              )}
            </figcaption>
          )}
        </figure>
        <div>
          <h1>{album.title}</h1>
          <p>
            <Credit credit={album.credit} />
            <span className="muted"> · {album.year}</span>
          </p>
        </div>
      </div>

      {isAdmin(me) && <CoverAdmin album={album} onChange={() => setReload((r) => r + 1)} />}
      {!isAdmin(me) && canEdit(me, album.credit.map((c) => c.artistId)) && (
        <ArtistCover album={album} onChange={() => setReload((r) => r + 1)} />
      )}
      {canEdit(me, album.credit.map((c) => c.artistId)) && (
        <AlbumEditor key={album.id} album={album} onChange={() => setReload((r) => r + 1)} />
      )}

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
