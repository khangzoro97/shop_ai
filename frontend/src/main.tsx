import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './index.css'
import AuthProvider from './auth/AuthProvider.tsx'
import RequireAuth from './auth/RequireAuth.tsx'
import Layout from './components/Layout.tsx'
import AdminPage from './pages/AdminPage.tsx'
import HomePage from './pages/HomePage.tsx'
import PasswordResetPage from './pages/PasswordResetPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import PolicyPage from './pages/PolicyPage.tsx'
import StudioPage from './pages/StudioPage.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<HomePage />} />
            <Route path="dang-nhap" element={<LoginPage />} />
            <Route path="quen-mat-khau" element={<PasswordResetPage />} />
            <Route path="dat-lai-mat-khau" element={<PasswordResetPage />} />
            <Route
              path="quan-tri"
              element={
                <RequireAuth>
                  <AdminPage />
                </RequireAuth>
              }
            />
            <Route path="dieu-khoan" element={<PolicyPage policy="terms" />} />
            <Route path="bao-mat" element={<PolicyPage policy="privacy" />} />
            <Route path="hoan-tien" element={<PolicyPage policy="refund" />} />
            <Route
              path="studio"
              element={
                <RequireAuth>
                  <StudioPage />
                </RequireAuth>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
