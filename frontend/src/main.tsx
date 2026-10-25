import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import AuthProvider from './AuthProvider'
import './i18n'
import './index.css'
import Layout from './Layout'
import ListFilterProvider from './ListFilterProvider'
import AboutPage from './pages/AboutPage'
import AccountPage from './pages/AccountPage'
import AlbumPage from './pages/AlbumPage'
import ArtistList from './pages/ArtistList'
import ArtistPage from './pages/ArtistPage'
import ChecksPage from './pages/ChecksPage'
import ConfirmPage from './pages/ConfirmPage'
import EditsPage from './pages/EditsPage'
import EmailPage from './pages/EmailPage'
import ForgotPage from './pages/ForgotPage'
import InvitePage from './pages/InvitePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ResetPage from './pages/ResetPage'
import NotFound from './pages/NotFound'
import PrivacyPage from './pages/PrivacyPage'
import SearchPage from './pages/SearchPage'
import TeamPage from './pages/TeamPage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <ListFilterProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<ArtistList />} />
              <Route path="artists/:id" element={<ArtistPage />} />
              <Route path="albums/:id" element={<AlbumPage />} />
              <Route path="search" element={<SearchPage />} />
              <Route path="about" element={<AboutPage />} />
              <Route path="privacy" element={<PrivacyPage />} />
              <Route path="login" element={<LoginPage />} />
              <Route path="admin" element={<LoginPage />} />
              <Route path="register" element={<RegisterPage />} />
              <Route path="confirm" element={<ConfirmPage />} />
              <Route path="account" element={<AccountPage />} />
              <Route path="email" element={<EmailPage />} />
              <Route path="forgot" element={<ForgotPage />} />
              <Route path="reset" element={<ResetPage />} />
              <Route path="invite" element={<InvitePage />} />
              <Route path="edits" element={<EditsPage />} />
              <Route path="team" element={<TeamPage />} />
              <Route path="checks" element={<ChecksPage />} />
              <Route path="*" element={<NotFound />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </ListFilterProvider>
    </AuthProvider>
  </StrictMode>,
)
