import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Album } from './api'
import { reason, send } from './auth'

interface Permission {
  id: number
  artistId: number
  artistName: string
  kind: 'WRITTEN_PERMISSION' | 'OPEN_LICENCE'
  licence: string | null
  proof: string
  proofUrl: string | null
  grantedOn: string
  scope: string
}

// Панэль адміністратара: без падставы (пісьмовага дазволу або адкрытай ліцэнзіі) вокладку загрузіць нельга.
export default function CoverAdmin({ album, onChange }: { album: Album; onChange: () => void }) {
  const { t } = useTranslation()
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [chosen, setChosen] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    Promise.all(
      album.credit.map((c) =>
        fetch(`/api/admin/artists/${c.artistId}/permissions`).then((r) => (r.ok ? r.json() : [])),
      ),
    ).then((lists: Permission[][]) => setPermissions(lists.flat()))
  }, [album, version])

  async function upload(event: FormEvent) {
    event.preventDefault()
    if (chosen == null || file == null) {
      return
    }
    setError(null)
    const form = new FormData()
    form.append('permissionId', String(chosen))
    form.append('file', file)
    const response = await send('PUT', `/api/admin/albums/${album.id}/cover`, form)
    if (response.ok) {
      setFile(null)
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

  return (
    <section className="admin-panel">
      <h2>{t('admin.coverHeading')}</h2>
      <h3>{t('admin.coverBasis')}</h3>
      {permissions.length === 0 && <p>{t('admin.noBasis')}</p>}
      {permissions.map((p) => (
        <label key={p.id} className="basis">
          <input type="radio" name="basis" checked={chosen === p.id} onChange={() => setChosen(p.id)} />
          <span>
            <strong>{p.artistName}</strong> · {p.kind === 'OPEN_LICENCE' ? p.licence : t('admin.kindWritten')} ·{' '}
            {p.grantedOn} · {p.scope}
            <br />
            <span className="muted">{p.proof}</span>
          </span>
        </label>
      ))}

      {adding ? (
        <PermissionForm
          album={album}
          onSaved={(id) => {
            setAdding(false)
            setChosen(id)
            setVersion((v) => v + 1)
          }}
          onCancel={() => setAdding(false)}
        />
      ) : (
        <button type="button" onClick={() => setAdding(true)}>
          {t('admin.addBasis')}
        </button>
      )}

      {chosen != null && (
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
      )}
      {album.coverUrl && (
        <button type="button" onClick={remove}>
          {t('admin.remove')}
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </section>
  )
}

function PermissionForm({
  album,
  onSaved,
  onCancel,
}: {
  album: Album
  onSaved: (id: number) => void
  onCancel: () => void
}) {
  const { t } = useTranslation()
  const [artistId, setArtistId] = useState(album.credit[0]?.artistId ?? 0)
  const [kind, setKind] = useState<Permission['kind']>('WRITTEN_PERMISSION')
  const [licence, setLicence] = useState('')
  const [proof, setProof] = useState('')
  const [proofUrl, setProofUrl] = useState('')
  const [grantedOn, setGrantedOn] = useState(() => new Date().toISOString().slice(0, 10))
  const [scope, setScope] = useState(t('admin.scopeDefault'))
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent) {
    event.preventDefault()
    const body = JSON.stringify({ artistId, kind, licence, proof, proofUrl, grantedOn, scope })
    const response = await send('POST', '/api/admin/permissions', body, true)
    if (response.ok) {
      onSaved((await response.json()).id)
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  const open = kind === 'OPEN_LICENCE'
  return (
    <form className="admin-form" onSubmit={save}>
      <label>
        {t('admin.artist')}
        <select value={artistId} onChange={(e) => setArtistId(Number(e.target.value))}>
          {album.credit.map((c) => (
            <option key={c.artistId} value={c.artistId}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <fieldset>
        <legend>{t('admin.kind')}</legend>
        <label>
          <input type="radio" checked={!open} onChange={() => setKind('WRITTEN_PERMISSION')} />
          {t('admin.kindWritten')}
        </label>
        <label>
          <input type="radio" checked={open} onChange={() => setKind('OPEN_LICENCE')} />
          {t('admin.kindOpen')}
        </label>
      </fieldset>
      {open && (
        <label>
          {t('admin.licence')}
          <input required value={licence} onChange={(e) => setLicence(e.target.value)} />
        </label>
      )}
      <label>
        {t('admin.proof')}
        <textarea required rows={3} value={proof} onChange={(e) => setProof(e.target.value)} />
      </label>
      <label>
        {t('admin.proofUrl')}
        <input type="url" required={open} value={proofUrl} onChange={(e) => setProofUrl(e.target.value)} />
      </label>
      <label>
        {t('admin.grantedOn')}
        <input type="date" required value={grantedOn} onChange={(e) => setGrantedOn(e.target.value)} />
      </label>
      <label>
        {t('admin.scope')}
        <input required value={scope} onChange={(e) => setScope(e.target.value)} />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">{t('admin.save')}</button>{' '}
      <button type="button" onClick={onCancel}>
        {t('admin.cancel')}
      </button>
    </form>
  )
}
