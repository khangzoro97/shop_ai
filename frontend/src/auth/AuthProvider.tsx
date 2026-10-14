import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, SESSION_EXPIRED_EVENT, tokenStore, type Account, type AuthResponse } from '../api.ts'
import { AuthContext, type AuthState } from './context.ts'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [account, setAccount] = useState<Account>()
  const [loading, setLoading] = useState(() => tokenStore.get() !== null)

  useEffect(() => {
    if (!tokenStore.get()) return
    api
      .me()
      .then(setAccount)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const onExpired = () => setAccount(undefined)
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [])

  const signIn = useCallback(({ token, account }: AuthResponse) => {
    tokenStore.set(token)
    setAccount(account)
  }, [])

  const refreshAccount = useCallback(() => api.me().then(setAccount), [])

  const value = useMemo<AuthState>(
    () => ({
      account,
      loading,
      login: (email, password) => api.login({ email, password }).then(signIn),
      register: (email, password, shopName) => api.register({ email, password, shopName }).then(signIn),
      signIn,
      logout: () => {
        tokenStore.clear()
        setAccount(undefined)
      },
      refreshAccount,
      setAccount,
    }),
    [account, loading, signIn, refreshAccount],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
