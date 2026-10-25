import type { ReactNode } from 'react'

export const MARK = '\u0000'

// Устаўляе спасылку туды, дзе ў перакладзе стаіць {{email}}: пераклад перадаецца з MARK на гэтым месцы.
export function withLink(text: string, link: ReactNode) {
  const [before, after] = text.split(MARK)
  return (
    <>
      {before}
      {link}
      {after}
    </>
  )
}
