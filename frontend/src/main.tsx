import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import FreeOnlyProvider from './FreeOnlyProvider'
import './i18n'
import './index.css'
import Layout from './Layout'
import AboutPage from './pages/AboutPage'
import AlbumPage from './pages/AlbumPage'
import ArtistList from './pages/ArtistList'
import ArtistPage from './pages/ArtistPage'
import NotFound from './pages/NotFound'
import SearchPage from './pages/SearchPage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <FreeOnlyProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<ArtistList />} />
            <Route path="artists/:id" element={<ArtistPage />} />
            <Route path="albums/:id" element={<AlbumPage />} />
            <Route path="search" element={<SearchPage />} />
            <Route path="about" element={<AboutPage />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </FreeOnlyProvider>
  </StrictMode>,
)
