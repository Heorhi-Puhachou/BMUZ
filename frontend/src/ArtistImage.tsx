import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Artist } from './api'
import { isAdmin, reason, send, useAuth } from './auth'
import Cover from './Cover'

// Выява выканаўцы на ягонай плітцы. Загружае толькі ўліковы запіс самога выканаўцы; адміністратар можа толькі
// зьняць. Пакуль выявы няма, на плітцы — вокладка апошняга альбома.
export default function ArtistImage({ artist, onChange }: { artist: Artist; onChange: () => void }) {
  const { t } = useTranslation()
  const { me } = useAuth()
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const own = me != null && me.artists.includes(artist.id)
  if (!own && !(isAdmin(me) && artist.imageUrl)) {
    return null
  }

  async function done(response: Response) {
    if (response.ok) {
      setError(null)
      setFile(null)
      onChange()
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  async function upload(event: FormEvent) {
    event.preventDefault()
    if (file == null) {
      return
    }
    const form = new FormData()
    form.append('file', file)
    await done(await send('PUT', `/api/edit/artists/${artist.id}/image`, form))
  }

  return (
    <section className="admin-panel">
      <h2>{t('artistImage.heading')}</h2>
      <p className="muted small">{t('artistImage.hint')}</p>
      {artist.imageUrl && (
        <div className="artist-image">
          <Cover url={artist.imageUrl} title={artist.name} />
        </div>
      )}
      {own && (
        <form className="admin-form" onSubmit={upload}>
          <p className="muted small">{t('artistImage.consent')}</p>
          <label>
            {t('admin.file')}
            <input
              type="file"
              accept="image/jpeg,image/png,image/gif,image/bmp"
              required
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <button type="submit" disabled={file == null}>
            {t('artistImage.upload')}
          </button>
        </form>
      )}
      {artist.imageUrl && (
        <button
          type="button"
          onClick={async () => done(await send('DELETE', `/api/edit/artists/${artist.id}/image`))}
        >
          {t('artistImage.remove')}
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  )
}
