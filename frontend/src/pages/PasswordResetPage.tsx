import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom'
import { api } from '../api.ts'
import { useAuth } from '../auth/context.ts'

const INPUT_CLASS =
  'w-full rounded-2xl border-2 border-ink/10 bg-cream px-4 py-3 transition focus:border-ink focus:outline-none'
const SUBMIT_CLASS =
  'shine bg-gradient-pop relative w-full overflow-hidden rounded-full py-4 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02] disabled:opacity-50 disabled:hover:scale-100'

/** "/quen-mat-khau" asks for the email; the emailed "/dat-lai-mat-khau?token=..." sets the new password. */
export default function PasswordResetPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  return (
    <div className="mx-auto max-w-md px-6 py-12">
      <div className="rounded-[2rem] border-2 border-ink bg-white p-8 shadow-[6px_6px_0_0_var(--color-ink)]">
        {token ? <NewPasswordForm token={token} /> : <RequestForm />}
      </div>
    </div>
  )
}

function RequestForm() {
  const location = useLocation()
  const [email, setEmail] = useState((location.state as { email?: string } | null)?.email ?? '')
  const [sentTo, setSentTo] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      await api.forgotPassword(email)
      setSentTo(email)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  if (sentTo) {
    return (
      <>
        <span className="sticker bg-pop-lime">✉ Đã gửi</span>
        <h1 className="mt-4 text-3xl font-black">Kiểm tra email của bạn</h1>
        <p className="mt-3 text-ink/70">
          Nếu <strong>{sentTo}</strong> đã đăng ký Chụp Xịn, bạn sẽ nhận được đường dẫn đặt lại mật khẩu trong vài
          phút. Đường dẫn dùng được trong 30 phút.
        </p>
        <p className="mt-3 text-sm text-ink/60">Không thấy email? Hãy xem thư mục Spam hoặc Quảng cáo.</p>
        <button
          type="button"
          onClick={() => setSentTo(undefined)}
          className="mt-6 min-h-11 text-sm font-bold text-pop-pink hover:underline"
        >
          Gửi lại hoặc dùng email khác
        </button>
      </>
    )
  }

  return (
    <>
      <span className="sticker bg-pop-lime">✦ Chụp Xịn</span>
      <h1 className="mt-4 text-3xl font-black">Quên mật khẩu</h1>
      <p className="mt-2 text-ink/60">Nhập email đã đăng ký, chúng tôi sẽ gửi đường dẫn để bạn đặt mật khẩu mới.</p>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <label className="block text-sm font-bold">
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            maxLength={120}
            autoComplete="email"
            className={`mt-1.5 font-normal ${INPUT_CLASS}`}
          />
        </label>
        {error && <ErrorBox message={error} />}
        <button type="submit" disabled={submitting} className={SUBMIT_CLASS}>
          {submitting ? 'Đang gửi...' : 'Gửi đường dẫn'}
        </button>
      </form>
      <BackToLogin />
    </>
  )
}

function NewPasswordForm({ token }: { token: string }) {
  const { account, signIn } = useAuth()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  if (account) return <Navigate to="/studio" replace />

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (password !== confirm) {
      setError('Hai mật khẩu chưa giống nhau')
      return
    }
    setSubmitting(true)
    setError(undefined)
    try {
      signIn(await api.resetPassword(token, password))
    } catch (err) {
      setError((err as Error).message)
      setSubmitting(false)
    }
  }

  return (
    <>
      <span className="sticker bg-pop-lime">✦ Chụp Xịn</span>
      <h1 className="mt-4 text-3xl font-black">Đặt mật khẩu mới</h1>
      <p className="mt-2 text-ink/60">Sau khi lưu, bạn sẽ được đăng nhập vào AI Studio ngay.</p>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <label className="block text-sm font-bold">
          Mật khẩu mới
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            placeholder="Ít nhất 8 ký tự"
            className={`mt-1.5 font-normal ${INPUT_CLASS}`}
          />
        </label>
        <label className="block text-sm font-bold">
          Nhập lại mật khẩu mới
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            className={`mt-1.5 font-normal ${INPUT_CLASS}`}
          />
        </label>
        {error && <ErrorBox message={error} />}
        <button type="submit" disabled={submitting} className={SUBMIT_CLASS}>
          {submitting ? 'Đang lưu...' : 'Lưu và đăng nhập'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-ink/60">
        Đường dẫn hết hạn?{' '}
        <Link to="/quen-mat-khau" className="font-bold text-pop-pink hover:underline">
          Gửi đường dẫn mới
        </Link>
      </p>
    </>
  )
}

function ErrorBox({ message }: { message: string }) {
  return (
    <p className="animate-pop-in rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
      {message}
    </p>
  )
}

function BackToLogin() {
  return (
    <Link to="/dang-nhap?tab=dang-nhap" className="mt-4 flex min-h-11 items-center justify-center text-sm font-bold hover:underline">
      ← Quay lại đăng nhập
    </Link>
  )
}
