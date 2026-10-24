import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import type { Artist } from './api'
import { reason, send } from './auth'

// Рэдагаваньне выканаўцы — для адміністратара і для самога выканаўцы. Зьмены бачныя на сайце адразу.
export default function ArtistEditor({ artist, onChange }: { artist: Artist; onChange: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [name, setName] = useState(artist.name)
  const [aliases, setAliases] = useState(artist.aliases.join('\n'))
  const [year, setYear] = useState(() => String(new Date().getFullYear()))
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault()
    const body = JSON.stringify({ name, aliases: aliases.split('\n') })
    const response = await send('PUT', `/api/edit/artists/${artist.id}`, body, true)
    setMessage(response.ok ? t('edit.saved') : t('admin.failed', { reason: await reason(response) }))
    if (response.ok) {
      onChange()
    }
  }

  async function addAlbum(event: FormEvent) {
    event.preventDefault()
    const body = JSON.stringify({ year: Number(year), title, credit: [{ artist: artist.name }] })
    const response = await send('POST', '/api/edit/albums', body, true)
    if (response.ok) {
      navigate(`/albums/${(await response.json()).id}`)
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <section className="admin-panel">
      <h2>{t('edit.artistHeading')}</h2>
      <form className="admin-form" onSubmit={save}>
        <label>
          {t('edit.name')}
          <input required value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          {t('edit.aliases')}
          <textarea rows={3} value={aliases} onChange={(e) => setAliases(e.target.value)} />
        </label>
        <button type="submit">{t('edit.save')}</button>
      </form>

      <h3>{t('edit.addAlbum')}</h3>
      <form className="admin-form" onSubmit={addAlbum}>
        <label>
          {t('edit.year')}
          <input type="number" min={1900} max={2100} required value={year} onChange={(e) => setYear(e.target.value)} />
        </label>
        <label>
          {t('edit.title')}
          <input required value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <button type="submit">{t('edit.create')}</button>
      </form>
      {message && <p role="status">{message}</p>}
    </section>
  )
}
