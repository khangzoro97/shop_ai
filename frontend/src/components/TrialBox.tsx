import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, fileToDataUrl, pendingTrial, type Trial, type TrialScene } from '../api.ts'
import { useAuth } from '../auth/context.ts'
import { track } from '../track.ts'
import { UploadSlot } from './StudioControls.tsx'

const SCENES: { id: TrialScene; label: string; swatch: string }[] = [
  { id: 'MARBLE', label: 'Bàn đá, nắng sớm', swatch: 'bg-[linear-gradient(135deg,#f4f1ec,#d9d4cc)]' },
  { id: 'PASTEL', label: 'Pastel tối giản', swatch: 'bg-[linear-gradient(135deg,#ffd6e5,#e7d9ff)]' },
  { id: 'NATURE', label: 'Rêu, lá ngoài trời', swatch: 'bg-[linear-gradient(135deg,#b9d99a,#5f8f4e)]' },
]
const POLL_MS = 3000

/** "Thử ngay, không cần đăng ký": one watermarked image from the visitor's own product photo. */
export default function TrialBox() {
  const { account } = useAuth()
  const [image, setImage] = useState<string>()
  const [scene, setScene] = useState<TrialScene>('MARBLE')
  const [trial, setTrial] = useState<Trial>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  const running = trial?.status === 'RUNNING'

  useEffect(() => {
    if (!running || !trial) return
    const timer = setInterval(() => {
      api
        .trial(trial.id)
        .then(setTrial)
        .catch(() => {})
    }, POLL_MS)
    return () => clearInterval(timer)
  }, [running, trial])

  async function handleFile(file?: File) {
    if (!file) return
    try {
      setImage(await fileToDataUrl(file))
      setError(undefined)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function start() {
    if (!image) return
    setSubmitting(true)
    setError(undefined)
    try {
      const started = await api.startTrial(image, scene)
      track('TRIAL')
      pendingTrial.set(started.id)
      setTrial(started)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  function reset() {
    setTrial(undefined)
    setImage(undefined)
  }

  if (trial?.status === 'SUCCEEDED') {
    return (
      <div className="grid gap-6 md:grid-cols-2 md:items-center">
        <img
          src={trial.previews[0]}
          alt="Ảnh thử do AI tạo, có watermark"
          className="w-full animate-pop-in rounded-3xl border-2 border-ink shadow-[6px_6px_0_0_var(--color-ink)]"
        />
        <div>
          <span className="sticker bg-pop-lime">✦ Xong rồi!</span>
          <h3 className="mt-4 text-3xl font-black">Ảnh của bạn đã sẵn sàng</h3>
          <p className="mt-3 text-ink/70">
            Đây là bản xem trước có watermark. Đăng ký miễn phí để tải ảnh gốc sắc nét, không watermark, và nhận thêm
            30 credit để tạo tiếp.
          </p>
          <Link
            to={account ? '/studio' : '/dang-nhap'}
            className="shine bg-gradient-pop relative mt-6 flex min-h-12 items-center justify-center overflow-hidden rounded-full px-6 py-3 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02]"
          >
            {account ? 'Mở AI Studio để tải ảnh gốc →' : 'Đăng ký để tải ảnh gốc →'}
          </Link>
          <button type="button" onClick={reset} className="mt-3 min-h-11 w-full text-sm font-bold hover:underline">
            Thử ảnh khác
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="grid gap-6 md:grid-cols-[minmax(0,240px)_minmax(0,1fr)] md:items-start">
      <div className="mx-auto w-full max-w-60">
        <UploadSlot
          label="Ảnh sản phẩm"
          required
          image={image}
          onFile={handleFile}
          onClear={() => setImage(undefined)}
        />
      </div>
      <div>
        <p className="text-sm font-black uppercase">Chọn bối cảnh</p>
        <div className="mt-3 grid grid-cols-3 gap-2">
          {SCENES.map((s) => (
            <button
              key={s.id}
              type="button"
              onClick={() => setScene(s.id)}
              disabled={running}
              aria-pressed={scene === s.id}
              className={`rounded-2xl border-2 p-2 text-left text-xs font-bold transition ${
                scene === s.id ? 'border-ink bg-pop-lime/30' : 'border-ink/10 hover:border-ink'
              }`}
            >
              <span className={`mb-2 block aspect-[4/3] rounded-xl ${s.swatch}`} />
              {s.label}
            </button>
          ))}
        </div>

        {trial?.status === 'FAILED' && (
          <p className="mt-4 rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
            AI chưa tạo được ảnh này. Hãy thử ảnh sản phẩm rõ nét, chụp chính diện trên nền đơn giản.
          </p>
        )}
        {error && (
          <p className="mt-4 animate-pop-in rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
            {error}
          </p>
        )}

        <button
          type="button"
          onClick={running ? undefined : trial?.status === 'FAILED' ? reset : start}
          disabled={(!image && !trial) || submitting || running}
          className="shine bg-gradient-pop relative mt-5 flex min-h-12 w-full items-center justify-center gap-3 overflow-hidden rounded-full px-6 py-3 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02] disabled:opacity-60 disabled:hover:scale-100"
        >
          {running || submitting ? (
            <>
              <span className="h-5 w-5 animate-spin rounded-full border-2 border-white/40 border-t-white" />
              AI đang dựng ảnh, khoảng 1 phút...
            </>
          ) : trial?.status === 'FAILED' ? (
            'Thử ảnh khác'
          ) : (
            'Tạo ảnh thử miễn phí ✦'
          )}
        </button>
        <p className="mt-3 text-xs text-ink/60">
          Không cần đăng ký. Mỗi người được thử 2 ảnh mỗi ngày, ảnh xem trước có watermark. Nhãn và chữ trên sản phẩm
          được giữ nguyên.
        </p>
      </div>
    </div>
  )
}
