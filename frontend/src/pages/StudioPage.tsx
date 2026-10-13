import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, fileToDataUrl, type Generation, type OutputFormat, type Workflow } from '../api.ts'
import { useAuth } from '../auth/context.ts'
import PhoneVerifyCard from '../components/PhoneVerifyCard.tsx'

const POLL_INTERVAL_MS = 3000

const ASPECT_CLASS: Record<OutputFormat, string> = {
  SQUARE: 'aspect-square',
  PORTRAIT: 'aspect-[3/4]',
  STORY: 'aspect-[9/16]',
}

const RUNNING_MESSAGES = ['AI đang xem sản phẩm...', 'Đang dựng bối cảnh...', 'Đang chỉnh ánh sáng...', 'Sắp xong rồi nè...']

export default function StudioPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [selected, setSelected] = useState<Workflow>()
  const { account, refreshAccount } = useAuth()
  const [history, setHistory] = useState<Generation[]>([])
  const [images, setImages] = useState<Record<string, string>>({})
  const [style, setStyle] = useState<string>()
  const [format, setFormat] = useState<OutputFormat>()
  const [prompt, setPrompt] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()
  const [dragKey, setDragKey] = useState<string>()

  const refresh = useCallback(() => {
    refreshAccount().catch(() => {})
    api.generations().then(setHistory).catch(() => {})
  }, [refreshAccount])

  useEffect(() => {
    api.workflows().then((list) => {
      setWorkflows(list)
      selectWorkflow(list[0])
    })
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const hasRunning = history.some((g) => g.status === 'RUNNING')
  useEffect(() => {
    if (!hasRunning) return
    const timer = setInterval(refresh, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [hasRunning, refresh])

  function selectWorkflow(workflow?: Workflow) {
    setSelected(workflow)
    setImages({})
    setStyle(workflow?.styles[0]?.id)
    setFormat(workflow?.formats[0]?.id)
    setError(undefined)
  }

  async function handleFile(key: string, file?: File) {
    if (!file) return
    try {
      const dataUrl = await fileToDataUrl(file)
      setImages((prev) => ({ ...prev, [key]: dataUrl }))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!selected) return
    setSubmitting(true)
    setError(undefined)
    try {
      await api.createGeneration({ workflow: selected.id, style, format, prompt: prompt || undefined, images })
      refresh()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  const ready = selected?.imageSlots.every((slot) => images[slot.key]) ?? false
  const label = (id: string) => workflows.find((w) => w.id === id)?.label ?? id

  return (
    <div className="mx-auto max-w-6xl px-6 py-8">
      <div className="bg-gradient-pop relative mb-8 overflow-hidden rounded-[2rem] border-2 border-ink p-8 text-white shadow-[6px_6px_0_0_var(--color-ink)]">
        <div className="pointer-events-none absolute -right-10 -bottom-16 h-56 w-56 animate-blob rounded-full bg-pop-lime/50 blur-3xl" />
        <div className="relative flex flex-wrap items-end justify-between gap-4">
          <div>
            <span className="sticker bg-white text-ink">✦ AI Studio</span>
            <h1 className="mt-4 text-3xl font-black md:text-4xl">Hôm nay mình làm ảnh gì nè?</h1>
            <p className="mt-2 text-white/85">Tải ảnh lên, chọn vibe, bấm tạo. Lỗi AI thì tự hoàn credit.</p>
          </div>
          {account && (
            <div className="rounded-2xl border-2 border-ink bg-white px-5 py-3 text-ink shadow-[4px_4px_0_0_var(--color-ink)]">
              <p className="text-xs font-bold text-ink/50 uppercase">Số dư</p>
              <p key={account.credits} className="animate-pop-in text-3xl font-black">
                {account.credits} <span className="text-base font-bold text-pop-pink">credit</span>
              </p>
            </div>
          )}
        </div>
      </div>

      {account && !account.phoneVerified && <PhoneVerifyCard />}

      <div className="grid gap-8 lg:grid-cols-[440px_1fr]">
        <form
          onSubmit={handleSubmit}
          className="h-fit space-y-6 rounded-3xl border-2 border-ink bg-white p-6 shadow-[6px_6px_0_0_var(--color-ink)]"
        >
          <div>
            <StepLabel n={1}>Chọn tính năng</StepLabel>
            <div className="space-y-2.5">
              {workflows.map((w) => {
                const active = selected?.id === w.id
                return (
                  <button
                    type="button"
                    key={w.id}
                    onClick={() => selectWorkflow(w)}
                    className={`w-full rounded-2xl border-2 p-4 text-left transition ${
                      active
                        ? 'border-ink bg-pop-lime/30 shadow-[4px_4px_0_0_var(--color-ink)]'
                        : 'border-ink/10 hover:-translate-y-0.5 hover:border-ink'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2 font-black">
                      <span>{w.label}</span>
                      <span className="shrink-0 rounded-full bg-ink px-2.5 py-0.5 text-xs text-white">
                        {w.credits} credit
                      </span>
                    </div>
                    <p className="mt-1 text-sm text-ink/60">{w.description}</p>
                    {w.preservesProduct && (
                      <span className="mt-2 inline-block rounded-full bg-pop-pink px-2.5 py-0.5 text-xs font-bold text-white">
                        ✦ Giữ nguyên 100% sản phẩm · Khuyên dùng
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          </div>

          {selected && (
            <>
              <div>
                <StepLabel n={2}>Tải ảnh lên</StepLabel>
                <div className="grid grid-cols-2 gap-3">
                  {selected.imageSlots.map((slot) => (
                    <label
                      key={slot.key}
                      onDragOver={(e) => {
                        e.preventDefault()
                        setDragKey(slot.key)
                      }}
                      onDragLeave={() => setDragKey(undefined)}
                      onDrop={(e) => {
                        e.preventDefault()
                        setDragKey(undefined)
                        handleFile(slot.key, e.dataTransfer.files[0])
                      }}
                      className={`group flex aspect-square cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed text-sm font-semibold transition ${
                        dragKey === slot.key
                          ? 'scale-105 border-pop-pink bg-pop-pink/10'
                          : 'border-ink/30 bg-cream hover:border-pop-pink hover:bg-pop-pink/5'
                      }`}
                    >
                      {images[slot.key] ? (
                        <img src={images[slot.key]} alt={slot.label} className="h-full w-full animate-pop-in object-cover" />
                      ) : (
                        <>
                          <span className="mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-pop-lime text-2xl font-black transition group-hover:rotate-90">
                            +
                          </span>
                          <span>{slot.label}</span>
                          <span className="mt-1 text-xs font-normal text-ink/50">Bấm hoặc kéo thả</span>
                        </>
                      )}
                      <input
                        type="file"
                        accept="image/*"
                        className="hidden"
                        onChange={(e) => handleFile(slot.key, e.target.files?.[0])}
                      />
                    </label>
                  ))}
                </div>
              </div>

              {selected.styles.length > 0 && (
                <div>
                  <StepLabel n={3}>Chọn vibe</StepLabel>
                  <div className="flex flex-wrap gap-2">
                    {selected.styles.map((s) => (
                      <button
                        type="button"
                        key={s.id}
                        onClick={() => setStyle(s.id)}
                        className={`rounded-full border-2 px-4 py-1.5 text-sm font-bold transition ${
                          style === s.id
                            ? 'border-ink bg-ink text-white'
                            : 'border-ink/10 bg-white hover:-translate-y-0.5 hover:border-ink'
                        }`}
                      >
                        {s.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {selected.formats.length > 0 && (
                <div>
                  <StepLabel n={4}>Tỉ lệ theo sàn</StepLabel>
                  <div className="grid grid-cols-3 gap-2">
                    {selected.formats.map((f) => (
                      <button
                        type="button"
                        key={f.id}
                        onClick={() => setFormat(f.id)}
                        className={`flex flex-col items-center gap-2 rounded-2xl border-2 p-3 text-xs font-bold transition ${
                          format === f.id ? 'border-ink bg-pop-lime/30' : 'border-ink/10 hover:border-ink'
                        }`}
                      >
                        <span
                          className="rounded border-2 border-ink bg-white"
                          style={{ width: 28, height: Math.round((28 * f.height) / f.width) }}
                        />
                        {f.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {selected.styles.length > 0 && (
                <textarea
                  value={prompt}
                  onChange={(e) => setPrompt(e.target.value)}
                  maxLength={500}
                  rows={3}
                  placeholder="Yêu cầu thêm (không bắt buộc), ví dụ: tông hồng pastel, thêm hoa hồng"
                  className="w-full rounded-2xl border-2 border-ink/10 bg-cream p-3 text-sm transition focus:border-ink focus:outline-none"
                />
              )}

              {error && (
                <p className="animate-pop-in rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={!ready || submitting}
                className="shine bg-gradient-pop relative w-full overflow-hidden rounded-full py-4 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:scale-100"
              >
                {submitting ? 'Đang gửi...' : `Tạo ngay ✦ ${selected.credits} credit`}
              </button>
            </>
          )}
        </form>

        <section>
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-2xl font-black">Kết quả của bạn</h2>
            {hasRunning && (
              <span className="flex items-center gap-2 rounded-full bg-pop-lime px-3 py-1 text-xs font-bold">
                <span className="h-2 w-2 animate-ping rounded-full bg-ink" /> AI đang làm việc
              </span>
            )}
          </div>

          {history.length === 0 && (
            <div className="flex flex-col items-center rounded-3xl border-2 border-dashed border-ink/20 bg-white p-12 text-center">
              <span className="animate-float text-5xl font-black text-pop-pink">✦</span>
              <p className="mt-4 text-lg font-black">Chưa có ảnh nào</p>
              <p className="mt-1 text-ink/60">Tải ảnh sản phẩm lên và bấm "Tạo ngay" để bắt đầu.</p>
            </div>
          )}

          <div className="grid items-start gap-5 sm:grid-cols-2">
            {history.map((g) => (
              <article
                key={g.id}
                className="group animate-pop-in overflow-hidden rounded-3xl border-2 border-ink bg-white transition hover:-translate-y-1 hover:shadow-[6px_6px_0_0_var(--color-ink)]"
              >
                <div className={`relative overflow-hidden bg-cream ${ASPECT_CLASS[g.format ?? 'SQUARE']}`}>
                  {g.status === 'RUNNING' && <RunningState />}
                  {g.status === 'FAILED' && (
                    <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center">
                      <span className="text-3xl font-black text-red-400">!</span>
                      <p className="text-sm font-medium text-red-600">{g.errorMessage}</p>
                    </div>
                  )}
                  {g.status === 'SUCCEEDED' &&
                    (g.outputType === 'VIDEO' ? (
                      <video src={g.outputUrls[0]} controls loop className="h-full w-full animate-pop-in object-cover" />
                    ) : (
                      <img
                        src={g.outputUrls[0]}
                        alt={label(g.workflow)}
                        className="h-full w-full animate-pop-in object-cover transition duration-500 group-hover:scale-105"
                      />
                    ))}
                  {g.status === 'SUCCEEDED' && (
                    <span className="sticker absolute top-3 left-3 bg-pop-lime">Xong ✓</span>
                  )}
                </div>
                <div className="flex items-center justify-between gap-2 p-4 text-sm">
                  <div className="min-w-0">
                    <p className="truncate font-black">{label(g.workflow)}</p>
                    <p className="text-ink/50">{new Date(g.createdAt).toLocaleString('vi-VN')}</p>
                  </div>
                  {g.status === 'SUCCEEDED' && (
                    <a
                      href={g.outputUrls[0]}
                      target="_blank"
                      rel="noreferrer"
                      className="shrink-0 rounded-full bg-ink px-4 py-2 font-bold text-white transition hover:bg-pop-pink"
                    >
                      Tải về ↓
                    </a>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}

function StepLabel({ n, children }: { n: number; children: string }) {
  return (
    <p className="mb-3 flex items-center gap-2 text-sm font-black uppercase">
      <span className="flex h-6 w-6 items-center justify-center rounded-full bg-pop-pink text-xs text-white">{n}</span>
      {children}
    </p>
  )
}

function RunningState() {
  const [index, setIndex] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => setIndex((i) => (i + 1) % RUNNING_MESSAGES.length), 2200)
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="skeleton flex h-full flex-col items-center justify-center gap-4">
      <span className="h-12 w-12 animate-spin rounded-full border-4 border-white border-t-pop-pink" />
      <p key={index} className="animate-pop-in px-4 text-center text-sm font-bold text-ink/70">
        {RUNNING_MESSAGES[index]}
      </p>
    </div>
  )
}
