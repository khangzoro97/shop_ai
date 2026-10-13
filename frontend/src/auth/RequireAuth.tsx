import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './context.ts'

export default function RequireAuth({ children }: { children: ReactNode }) {
  const { account, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex justify-center py-32">
        <span className="h-12 w-12 animate-spin rounded-full border-4 border-ink/10 border-t-pop-pink" />
      </div>
    )
  }
  if (!account) {
    return <Navigate to="/dang-nhap" replace state={{ from: location.pathname }} />
  }
  return children
}
