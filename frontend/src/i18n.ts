import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import be from './messages/be.json'
import beLatn from './messages/be-Latn.json'
import beTarask from './messages/be-tarask.json'

// Тарашкевіца — асноўны правапіс; астатнія два робіць з яе `npm run messages`.
// Назва кожнага варыянта напісаная ім самім, таму яе не перакладаюць.
export const SPELLINGS = [
  { tag: 'be-tarask', label: 'Тарашкевіца' },
  { tag: 'be', label: 'Афіцыйны правапіс' },
  { tag: 'be-Latn', label: 'Łacinka' },
] as const

const STORAGE_KEY = 'bmuz.spelling'
const DEFAULT = 'be-tarask'

function savedSpelling() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return SPELLINGS.some((s) => s.tag === saved) ? saved! : DEFAULT
  } catch {
    return DEFAULT
  }
}

i18n.use(initReactI18next).init({
  resources: {
    'be-tarask': { translation: beTarask },
    be: { translation: be },
    'be-Latn': { translation: beLatn },
  },
  lng: savedSpelling(),
  fallbackLng: DEFAULT,
  interpolation: { escapeValue: false },
})

document.documentElement.lang = i18n.language

i18n.on('languageChanged', (tag) => {
  document.documentElement.lang = tag
  try {
    localStorage.setItem(STORAGE_KEY, tag)
  } catch {
    // Браўзэр не дазваляе захоўваць: выбар проста не запомніцца.
  }
})

export default i18n
