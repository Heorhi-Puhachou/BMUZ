import { useTranslation } from 'react-i18next'
import type { Link, PriceType } from './api'

// Спасылка «заплаці, колькі хочаш» ідзе першай: наведнік павінен адразу бачыць, дзе можна заплаціць выканаўцу.
const ORDER: Record<PriceType, number> = { NAME_YOUR_PRICE: 0, FREE: 1, PAID: 2 }

export default function LinkList({ links }: { links: Link[] }) {
  const { t } = useTranslation()
  const sorted = [...links].sort((a, b) => ORDER[a.priceType] - ORDER[b.priceType])

  return (
    <ul className="links">
      {sorted.map((link) => (
        <li key={link.url} className={`link price-${link.priceType}`}>
          <span className="badge">{t(`price.${link.priceType}`)}</span>{' '}
          <a href={link.url} rel="noopener" target="_blank">{link.store}</a>
          {link.note && <span className="muted"> ({link.note})</span>}
          {link.priceType === 'NAME_YOUR_PRICE' && <p className="nyp">{t('nameYourPrice')}</p>}
        </li>
      ))}
    </ul>
  )
}
