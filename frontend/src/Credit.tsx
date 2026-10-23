import { Fragment } from 'react'
import { Link } from 'react-router'
import type { CreditEntry } from './api'

// Імёны з тым самым злучальным тэкстам, што напісаў выканаўца; кожнае імя вядзе на старонку выканаўцы.
export default function Credit({ credit }: { credit: CreditEntry[] }) {
  return (
    <>
      {credit.map((entry) => (
        <Fragment key={entry.artistId}>
          <Link to={`/artists/${entry.artistId}`}>{entry.name}</Link>
          {entry.joinPhrase}
        </Fragment>
      ))}
    </>
  )
}
