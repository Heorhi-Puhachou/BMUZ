import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import beTarask from './messages/be-tarask.json'

i18n.use(initReactI18next).init({
  resources: {
    'be-tarask': { translation: beTarask },
  },
  lng: 'be-tarask',
  fallbackLng: 'be-tarask',
  interpolation: { escapeValue: false },
})

export default i18n
