import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Album } from './api'
import { reason, send } from './auth'

// Панэль адміністратара. Для вокладкі пад адкрытай ліцэнзіяй — назва ліцэнзіі і старонка зь ёю: іх паказваюць пад
// вокладкай, як патрабуюць ліцэнзіі тыпу CC BY.
export default function CoverAdmin({ album, onChange }: { album: Album; onChange: () => void }) {
  const { t } = useTranslation()
  const [file, setFile] = useState<File | null>(null)
  const [licence, setLicence] = useState('')
  const [licenceUrl, setLicenceUrl] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function upload(event: FormEvent) {
    event.preventDefault()
    if (file == null) {
      return
    }
    setError(null)
    const form = new FormData()
    form.append('file', file)
    if (licence.trim() !== '' || licenceUrl.trim() !== '') {
      form.append('licence', licence)
      form.append('licenceUrl', licenceUrl)
    }
    const response = await send('PUT', `/api/admin/albums/${album.id}/cover`, form)
    if (response.ok) {
      setFile(null)
      setLicence('')
      setLicenceUrl('')
      onChange()
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  async function remove() {
    const response = await send('DELETE', `/api/admin/albums/${album.id}/cover`)
    if (response.ok) {
      onChange()
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  const open = licence.trim() !== '' || licenceUrl.trim() !== ''
  return (
    <section className="admin-panel">
      <h2>{t('admin.coverHeading')}</h2>
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
        <p className="muted small">{t('admin.licenceHint')}</p>
        <label>
          {t('admin.licence')}
          <input required={open} value={licence} onChange={(e) => setLicence(e.target.value)} />
        </label>
        <label>
          {t('admin.licenceUrl')}
          <input type="url" required={open} value={licenceUrl} onChange={(e) => setLicenceUrl(e.target.value)} />
        </label>
        <button type="submit" disabled={file == null}>
          {t('admin.upload')}
        </button>
      </form>
      {album.coverUrl && (
        <button type="button" onClick={remove}>
          {t('admin.remove')}
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  )
}
