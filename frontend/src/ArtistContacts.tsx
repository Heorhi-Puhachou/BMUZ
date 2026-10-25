import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { type Artist, CONTACT_KINDS, type Contact, type ContactKind } from './api'
import { isAdmin, reason, send, useAuth } from './auth'

const MAX_CONTACTS = 20

// Адрас без https:// і www. — карацей і чытаецца лепей.
function shown(contact: Contact) {
  return contact.kind === 'EMAIL' ? contact.value : contact.value.replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, '')
}

// Кантакты выканаўцы. Піша іх толькі ўліковы запіс самога выканаўцы; адміністратар можа толькі выдаліць.
export default function ArtistContacts({ artist, onChange }: { artist: Artist; onChange: () => void }) {
  const { t } = useTranslation()
  const { me } = useAuth()
  const own = me != null && me.artists.includes(artist.id)
  if (artist.contacts.length === 0 && !own) {
    return null
  }

  async function remove(id: number) {
    const response = await send('DELETE', `/api/admin/artist-contacts/${id}`)
    if (response.ok) {
      onChange()
    }
  }

  return (
    <section>
      <h2>{t('contacts.heading')}</h2>
      {artist.contacts.length > 0 && (
        <ul className="plain">
          {artist.contacts.map((c) => (
            <li key={c.id}>
              <span className="muted">{t(`contacts.kind.${c.kind}`)}: </span>
              {c.kind === 'EMAIL' ? (
                <a href={`mailto:${c.value}`}>{shown(c)}</a>
              ) : (
                <a href={c.value} rel="noopener nofollow ugc" target="_blank">
                  {shown(c)}
                </a>
              )}
              {isAdmin(me) && (
                <>
                  {' · '}
                  <button type="button" className="link-button" onClick={() => remove(c.id)}>
                    {t('contacts.remove')}
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {own && <ContactsForm key={artist.contacts.map((c) => c.id).join()} artist={artist} onChange={onChange} />}
    </section>
  )
}

function ContactsForm({ artist, onChange }: { artist: Artist; onChange: () => void }) {
  const { t } = useTranslation()
  const [rows, setRows] = useState<{ kind: ContactKind; value: string }[]>(() =>
    artist.contacts.map(({ kind, value }) => ({ kind, value })),
  )
  const [message, setMessage] = useState<string | null>(null)

  function change(i: number, row: { kind: ContactKind; value: string }) {
    setRows(rows.map((r, j) => (j === i ? row : r)))
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const body = JSON.stringify({ contacts: rows.filter((r) => r.value.trim() !== '') })
    const response = await send('PUT', `/api/edit/artists/${artist.id}/contacts`, body, true)
    if (response.ok) {
      setMessage(null)
      onChange()
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <form className="admin-form" onSubmit={save}>
      <p className="muted small">{t('contacts.hint')}</p>
      {rows.map((row, i) => (
        <div key={i} className="contact-row">
          <select
            aria-label={t('contacts.kindLabel')}
            value={row.kind}
            onChange={(e) => change(i, { ...row, kind: e.target.value as ContactKind })}
          >
            {CONTACT_KINDS.map((k) => (
              <option key={k} value={k}>
                {t(`contacts.kind.${k}`)}
              </option>
            ))}
          </select>
          <input
            aria-label={t('contacts.valueLabel')}
            type={row.kind === 'EMAIL' ? 'email' : 'url'}
            maxLength={300}
            placeholder={row.kind === 'EMAIL' ? 'name@example.org' : 'https://'}
            value={row.value}
            onChange={(e) => change(i, { ...row, value: e.target.value })}
          />
          <button type="button" onClick={() => setRows(rows.filter((_, j) => j !== i))}>
            {t('contacts.drop')}
          </button>
        </div>
      ))}
      <div>
        <button
          type="button"
          disabled={rows.length >= MAX_CONTACTS}
          onClick={() => setRows([...rows, { kind: 'WEBSITE', value: '' }])}
        >
          {t('contacts.add')}
        </button>{' '}
        <button type="submit">{t('contacts.save')}</button>
      </div>
      {message && <p role="status">{message}</p>}
    </form>
  )
}
