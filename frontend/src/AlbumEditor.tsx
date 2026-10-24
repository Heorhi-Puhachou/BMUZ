import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import type { Album, Link, PriceType, Track } from './api'
import { reason, send } from './auth'

const PRICE_TYPES: PriceType[] = ['FREE', 'NAME_YOUR_PRICE', 'PAID']

function formatLength(seconds: number | null) {
  return seconds == null ? '' : ` ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

/** Радок «1. Назва 3:45»: нумар, назва і, калі ёсьць, даўжыня ў канцы. */
const TRACK_LINE = /^(\d+)[.)]?\s+(.+?)(?:\s+(?:(\d+):)?(\d{1,2}):(\d{2}))?$/

function parseTracks(text: string) {
  const tracks: { number: number; title: string; lengthSeconds: number | null }[] = []
  const lines = text.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    if (line === '') {
      continue
    }
    const m = TRACK_LINE.exec(line)
    if (!m) {
      return { error: i + 1 }
    }
    const length = m[5] == null ? null : Number(m[3] ?? 0) * 3600 + Number(m[4]) * 60 + Number(m[5])
    tracks.push({ number: Number(m[1]), title: m[2], lengthSeconds: length })
  }
  return { tracks }
}

function trackLines(tracks: Track[]) {
  return tracks.map((t) => `${t.number}. ${t.title}${formatLength(t.lengthSeconds)}`).join('\n')
}

// Рэдагаваньне альбома — для адміністратара і для выканаўцаў, названых у ягоным крэдыце. Зьмены бачныя адразу.
export default function AlbumEditor({ album, onChange }: { album: Album; onChange: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [year, setYear] = useState(String(album.year))
  const [title, setTitle] = useState(album.title)
  const [credit, setCredit] = useState(album.credit.map((c) => ({ artist: c.name, join: c.joinPhrase })))
  const [tracks, setTracks] = useState(trackLines(album.tracks))
  const [message, setMessage] = useState<string | null>(null)

  async function done(response: Response) {
    setMessage(response.ok ? t('edit.saved') : t('admin.failed', { reason: await reason(response) }))
    if (response.ok) {
      onChange()
    }
  }

  async function saveAlbum(event: FormEvent) {
    event.preventDefault()
    const body = JSON.stringify({ year: Number(year), title, credit })
    await done(await send('PUT', `/api/edit/albums/${album.id}`, body, true))
  }

  async function saveTracks(event: FormEvent) {
    event.preventDefault()
    const parsed = parseTracks(tracks)
    if ('error' in parsed) {
      setMessage(t('edit.tracksBad', { line: parsed.error }))
      return
    }
    await done(await send('PUT', `/api/edit/albums/${album.id}/tracks`, JSON.stringify(parsed), true))
  }

  async function deleteAlbum() {
    if (!window.confirm(t('edit.confirmDelete', { title: album.title }))) {
      return
    }
    const response = await send('DELETE', `/api/edit/albums/${album.id}`)
    if (response.ok) {
      navigate(credit.length > 0 ? `/artists/${album.credit[0].artistId}` : '/')
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  function setCreditAt(i: number, field: 'artist' | 'join', value: string) {
    setCredit(credit.map((c, j) => (j === i ? { ...c, [field]: value } : c)))
  }

  return (
    <section className="admin-panel">
      <h2>{t('edit.albumHeading')}</h2>
      <form className="admin-form" onSubmit={saveAlbum}>
        <label>
          {t('edit.year')}
          <input type="number" min={1900} max={2100} required value={year} onChange={(e) => setYear(e.target.value)} />
        </label>
        <label>
          {t('edit.title')}
          <input required value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <fieldset>
          <legend>{t('edit.credit')}</legend>
          {credit.map((c, i) => (
            <div key={i} className="credit-row">
              <input
                aria-label={t('edit.creditArtist')}
                placeholder={t('edit.creditArtist')}
                required
                value={c.artist}
                onChange={(e) => setCreditAt(i, 'artist', e.target.value)}
              />
              <input
                aria-label={t('edit.creditJoin')}
                placeholder={t('edit.creditJoin')}
                value={c.join}
                onChange={(e) => setCreditAt(i, 'join', e.target.value)}
              />
              {credit.length > 1 && (
                <button type="button" onClick={() => setCredit(credit.filter((_, j) => j !== i))}>
                  {t('edit.remove')}
                </button>
              )}
            </div>
          ))}
          <button type="button" onClick={() => setCredit([...credit, { artist: '', join: '' }])}>
            {t('edit.addArtist')}
          </button>
        </fieldset>
        <button type="submit">{t('edit.save')}</button>
      </form>

      <h3>{t('edit.tracksHeading')}</h3>
      <form className="admin-form" onSubmit={saveTracks}>
        <label>
          <span className="muted small">{t('edit.tracksHint')}</span>
          <textarea rows={Math.max(4, album.tracks.length + 2)} value={tracks} onChange={(e) => setTracks(e.target.value)} />
        </label>
        <button type="submit">{t('edit.save')}</button>
      </form>

      <h3>{t('edit.linksHeading')}</h3>
      <LinksEditor album={album} onChange={onChange} setMessage={setMessage} />

      {message && <p role="status">{message}</p>}
      <p>
        <button type="button" onClick={deleteAlbum}>
          {t('edit.deleteAlbum')}
        </button>
      </p>
    </section>
  )
}

type Target = { link: Link; trackNumber: number | null }

function LinksEditor({
  album,
  onChange,
  setMessage,
}: {
  album: Album
  onChange: () => void
  setMessage: (m: string | null) => void
}) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState<Target | 'new' | null>(null)
  const all: Target[] = [
    ...album.links.map((link) => ({ link, trackNumber: null })),
    ...album.tracks.flatMap((track) => track.links.map((link) => ({ link, trackNumber: track.number }))),
  ]

  async function remove(link: Link) {
    const response = await send('DELETE', `/api/edit/links/${link.id}`)
    setMessage(response.ok ? t('edit.saved') : t('admin.failed', { reason: await reason(response) }))
    if (response.ok) {
      onChange()
    }
  }

  return (
    <>
      <ul className="plain">
        {all.map(({ link, trackNumber }) => (
          <li key={link.id}>
            {trackNumber == null ? t('edit.wholeAlbum') : t('edit.trackShort', { number: trackNumber })} ·{' '}
            {link.store} · {t(`price.${link.priceType}`)} · <span className="muted">{link.url}</span> ·{' '}
            <button type="button" className="link-button" onClick={() => setEditing({ link, trackNumber })}>
              {t('edit.editLink')}
            </button>{' '}
            ·{' '}
            <button type="button" className="link-button" onClick={() => remove(link)}>
              {t('edit.deleteLink')}
            </button>
          </li>
        ))}
      </ul>
      {editing ? (
        <LinkForm
          album={album}
          target={editing === 'new' ? null : editing}
          onDone={(response) => {
            setEditing(null)
            if (response) {
              setMessage(t('edit.saved'))
              onChange()
            }
          }}
        />
      ) : (
        <button type="button" onClick={() => setEditing('new')}>
          {t('edit.addLink')}
        </button>
      )}
    </>
  )
}

function LinkForm({
  album,
  target,
  onDone,
}: {
  album: Album
  target: Target | null
  onDone: (saved: boolean) => void
}) {
  const { t } = useTranslation()
  const link = target?.link
  const [stores, setStores] = useState<string[]>([])
  const [store, setStore] = useState(link?.store ?? '')
  const [url, setUrl] = useState(link?.url ?? '')
  const [priceType, setPriceType] = useState<PriceType>(link?.priceType ?? 'FREE')
  const [minPrice, setMinPrice] = useState(link?.minPrice == null ? '' : String(link.minPrice))
  const [currency, setCurrency] = useState(link?.currency ?? '')
  const [note, setNote] = useState(link?.note ?? '')
  const [trackNumber, setTrackNumber] = useState(target?.trackNumber == null ? '' : String(target.trackNumber))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/edit/stores')
      .then((r) => (r.ok ? r.json() : []))
      .then(setStores)
  }, [])

  async function save(event: FormEvent) {
    event.preventDefault()
    const body = JSON.stringify({
      store,
      url,
      priceType,
      minPrice: minPrice === '' ? null : Number(minPrice),
      currency,
      note,
      trackNumber: trackNumber === '' ? null : Number(trackNumber),
    })
    const response = link
      ? await send('PUT', `/api/edit/links/${link.id}`, body, true)
      : await send('POST', `/api/edit/albums/${album.id}/links`, body, true)
    if (response.ok) {
      onDone(true)
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <form className="admin-form" onSubmit={save}>
      <label>
        {t('edit.store')}
        <input required list="stores" value={store} onChange={(e) => setStore(e.target.value)} />
        <datalist id="stores">
          {stores.map((s) => (
            <option key={s} value={s} />
          ))}
        </datalist>
      </label>
      <label>
        {t('edit.url')}
        <input type="url" required value={url} onChange={(e) => setUrl(e.target.value)} />
      </label>
      <label>
        {t('edit.priceType')}
        <select value={priceType} onChange={(e) => setPriceType(e.target.value as PriceType)}>
          {PRICE_TYPES.map((p) => (
            <option key={p} value={p}>
              {t(`price.${p}`)}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t('edit.minPrice')}
        <input type="number" min={0} step="0.01" value={minPrice} onChange={(e) => setMinPrice(e.target.value)} />
      </label>
      <label>
        {t('edit.currency')}
        <input maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value)} />
      </label>
      <label>
        {t('edit.note')}
        <input value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      {!link && (
        <label>
          {t('edit.target')}
          <select value={trackNumber} onChange={(e) => setTrackNumber(e.target.value)}>
            <option value="">{t('edit.wholeAlbum')}</option>
            {album.tracks.map((track) => (
              <option key={track.id} value={track.number}>
                {t('edit.track', { number: track.number, title: track.title })}
              </option>
            ))}
          </select>
        </label>
      )}
      {error && <p role="alert">{error}</p>}
      <button type="submit">{t('edit.save')}</button>{' '}
      <button type="button" onClick={() => onDone(false)}>
        {t('admin.cancel')}
      </button>
    </form>
  )
}
