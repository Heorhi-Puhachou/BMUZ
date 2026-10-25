import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { shortTime, useApi } from './api'
import { reason, send, useAuth } from './auth'

interface Check {
  email: string
  checkedAt: string
  changedSince: boolean
}

// Адзнака адміністратара «праглядзеў». Альбом, зьменены пасьля прагляду, зноў лічыцца не праглядзеным.
export default function AlbumCheck({ albumId, reload }: { albumId: number; reload: number }) {
  const { t } = useTranslation()
  const { me } = useAuth()
  const [version, setVersion] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const loaded = useApi<Check[]>(`/api/admin/albums/${albumId}/checks`, reload + version)
  if (loaded.state !== 'ok') {
    return null
  }
  const mine = loaded.data.find((c) => c.email === me?.email)
  const others = loaded.data.filter((c) => c !== mine)
  const checked = mine != null && !mine.changedSince

  async function toggle() {
    setError(null)
    const response = await send(checked ? 'DELETE' : 'PUT', `/api/admin/albums/${albumId}/check`)
    if (response.ok) {
      setVersion((v) => v + 1)
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <section className="admin-panel">
      <label>
        <input type="checkbox" checked={checked} onChange={toggle} /> {t('checks.mine')}
        {mine && !mine.changedSince && <span className="muted"> · {shortTime(mine.checkedAt)}</span>}
      </label>
      {mine?.changedSince && <p>{t('checks.changedSince', { date: shortTime(mine.checkedAt) })}</p>}
      {others.length > 0 && (
        <p className="muted small">
          {t('checks.others')}{' '}
          {others
            .map((c) => `${c.email} (${shortTime(c.checkedAt)}${c.changedSince ? `, ${t('checks.otherChanged')}` : ''})`)
            .join(', ')}
        </p>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  )
}
