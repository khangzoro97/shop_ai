import { createContext, useContext } from 'react'
import type { Account } from '../api.ts'

export interface AuthState {
  account?: Account
  /** True while the stored token is being checked on page load. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, shopName: string) => Promise<void>
  logout: () => void
  refreshAccount: () => Promise<void>
  setAccount: (account: Account) => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}
