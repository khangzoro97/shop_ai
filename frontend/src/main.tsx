import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './index.css'
import Layout from './components/Layout.tsx'
import HomePage from './pages/HomePage.tsx'
import StudioPage from './pages/StudioPage.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="studio" element={<StudioPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
