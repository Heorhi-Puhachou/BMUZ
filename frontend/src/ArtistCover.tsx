import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Album } from './api'
import { reason, send } from './auth'

// Вокладка, якую выканаўца загружае сам. Адміністратар загружае праз сваю панэль, дзе выбірае падставу.
export default function ArtistCover({ album, onChange }: { album: Album; onChange: () => void }) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)

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
    await done(await send('PUT', `/api/edit/albums/${album.id}/cover`, form))
  }

  return (
    <section className="admin-panel">
      <h2>{t('admin.coverHeading')}</h2>
      <p className="muted small">{t('edit.coverConsent')}</p>
      <form className="admin-form" onSubmit={upload}>
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
          {t('admin.upload')}
        </button>
      </form>
      {album.coverUrl && (
        <button type="button" onClick={async () => done(await send('DELETE', `/api/edit/albums/${album.id}/cover`))}>
          {t('admin.remove')}
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  )
}
