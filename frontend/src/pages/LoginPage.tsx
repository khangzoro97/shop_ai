import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/context.ts'

type Mode = 'register' | 'login'

const INPUT_CLASS =
  'w-full rounded-2xl border-2 border-ink/10 bg-cream px-4 py-3 transition focus:border-ink focus:outline-none'

export default function LoginPage() {
  const { account, login, register } = useAuth()
  const location = useLocation()
  const [mode, setMode] = useState<Mode>('register')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [shopName, setShopName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  if (account) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? '/studio'} replace />
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      if (mode === 'register') await register(email, password, shopName)
      else await login(email, password)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  function switchMode(next: Mode) {
    setMode(next)
    setError(undefined)
  }

  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <div className="rounded-[2rem] border-2 border-ink bg-white p-8 shadow-[6px_6px_0_0_var(--color-ink)]">
        <span className="sticker bg-pop-lime">✦ Fortfio</span>
        <h1 className="mt-4 text-3xl font-black">
          {mode === 'register' ? 'Tạo tài khoản shop' : 'Chào mừng quay lại'}
        </h1>
        <p className="mt-2 text-ink/60">
          {mode === 'register'
            ? 'Xác minh số điện thoại sau khi đăng ký để nhận 30 credit dùng thử.'
            : 'Đăng nhập để vào AI Studio.'}
        </p>

        <div className="mt-6 grid grid-cols-2 rounded-full border-2 border-ink p-1 text-sm font-bold">
          {(['register', 'login'] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => switchMode(m)}
              className={`rounded-full py-2 transition ${mode === m ? 'bg-ink text-white' : 'hover:bg-ink/5'}`}
            >
              {m === 'register' ? 'Đăng ký' : 'Đăng nhập'}
            </button>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          {mode === 'register' && (
            <label className="block text-sm font-bold">
              Tên shop
              <input
                value={shopName}
                onChange={(e) => setShopName(e.target.value)}
                required
                maxLength={80}
                autoComplete="organization"
                placeholder="Ví dụ: Mộc Skincare"
                className={`mt-1.5 font-normal ${INPUT_CLASS}`}
              />
            </label>
          )}
          <label className="block text-sm font-bold">
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              className={`mt-1.5 font-normal ${INPUT_CLASS}`}
            />
          </label>
          <label className="block text-sm font-bold">
            Mật khẩu
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={mode === 'register' ? 8 : undefined}
              maxLength={72}
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              placeholder={mode === 'register' ? 'Ít nhất 8 ký tự' : undefined}
              className={`mt-1.5 font-normal ${INPUT_CLASS}`}
            />
          </label>

          {error && (
            <p className="animate-pop-in rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="shine bg-gradient-pop relative w-full overflow-hidden rounded-full py-4 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02] disabled:opacity-50 disabled:hover:scale-100"
          >
            {submitting ? 'Đang xử lý...' : mode === 'register' ? 'Tạo tài khoản ✦' : 'Đăng nhập'}
          </button>
        </form>
      </div>
    </div>
  )
}
