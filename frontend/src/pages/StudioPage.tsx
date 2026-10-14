import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import {
  api,
  ApiError,
  fileToDataUrl,
  pendingTrial,
  type Generation,
  type GenerationStatus,
  type MusicMood,
  type OutputFormat,
  type Placement,
  type VideoResolution,
  type VideoSettings,
  type Workflow,
  type WorkflowId,
} from '../api.ts'
import { useAuth } from '../auth/context.ts'
import CreditShortfall from '../components/CreditShortfall.tsx'
import FilterMenu, { type FilterOption } from '../components/FilterMenu.tsx'
import MediaPreview from '../components/MediaPreview.tsx'
import PhoneVerifyCard from '../components/PhoneVerifyCard.tsx'
import SimpleStudio from '../components/SimpleStudio.tsx'
import { ChoiceButton, StepLabel, UploadSlot } from '../components/StudioControls.tsx'
import { CREDIT_VND, FEATURED_PLAN, formatVnd } from '../pricing.ts'
import { minVideoCredits, videoCredits } from '../studio.ts'

const POLL_INTERVAL_MS = 3000

const FORMAT_LABEL: Record<OutputFormat, string> = {
  SQUARE: '1:1',
  PORTRAIT: '3:4',
  STORY: '9:16',
}

const RESOLUTION_LABEL: Record<VideoResolution, string> = {
  P360: '360p',
  P720: '720p',
  P1080: '1080p',
}

const MUSIC_LABEL: Record<MusicMood, string> = {
  UPBEAT: 'Sôi động',
  CHILL: 'Nhẹ nhàng',
  LUXURY: 'Sang trọng',
  CUTE: 'Dễ thương',
  FESTIVE: 'Lễ hội, Tết',
  CINEMATIC: 'Điện ảnh',
}

const PRODUCT_TIPS = [
  'Sản phẩm đứng thẳng, chụp ngang tầm, chiếm khoảng 70% khung hình',
  'Nền trơn, đủ sáng, không bày thêm cây cỏ hay đồ trang trí',
  'Tránh mặt bàn bóng gương: phần phản chiếu sẽ bị giữ lại',
  'Nhãn, chữ và logo nét, không bị che',
  'Mỗi ảnh chỉ một sản phẩm',
]

const TRYON_TIPS = [
  'Ảnh người mẫu: đứng thẳng, thấy rõ thân trên, không khoanh tay',
  'Ảnh quần áo: trải phẳng hoặc treo móc, nền trơn, không nhăn',
  'Ánh sáng đều, không mặc áo khoác che áo cần thử',
]

const HOLDING_TIPS = [
  'Ảnh người mẫu: thấy rõ thân trên và hai tay, tay không cầm đồ vật khác',
  'Gương mặt, trang phục và bối cảnh được giữ nguyên, nên chọn ảnh đẹp sẵn',
  'Ảnh sản phẩm: chụp thẳng, nhãn rõ, nền trơn',
]

const VARIANT_CHOICES = [1, 2, 4]

const PAGE_SIZE = 6

const RESULT_GROUPS: { id: string; label: string; workflows?: WorkflowId[] }[] = [
  { id: 'all', label: 'Tất cả' },
  { id: 'product', label: 'Ảnh sản phẩm', workflows: ['PRODUCT_EXACT', 'PRODUCT_PHOTO'] },
  { id: 'model', label: 'Người mẫu', workflows: ['PRODUCT_HOLDING', 'VIRTUAL_TRYON'] },
  { id: 'video', label: 'Video', workflows: ['PRODUCT_VIDEO'] },
  { id: 'edited', label: 'Ảnh đã chỉnh', workflows: ['RELIGHT', 'UPSCALE_2K', 'UPSCALE_4K'] },
]

const STATUS_FILTERS: FilterOption<GenerationStatus | undefined>[] = [
  { value: undefined, label: 'Mọi trạng thái', dot: 'bg-linear-to-br from-pop-pink to-pop-violet' },
  { value: 'RUNNING', label: 'Đang tạo', dot: 'bg-pop-violet' },
  { value: 'SUCCEEDED', label: 'Hoàn thành', dot: 'bg-emerald-500' },
  { value: 'FAILED', label: 'Lỗi', dot: 'bg-red-500' },
]

const RUNNING_MESSAGES = ['AI đang xem sản phẩm...', 'Đang dựng bối cảnh...', 'Đang chỉnh ánh sáng...', 'Sắp xong rồi nè...']
const PLANNING_MESSAGES = ['AI đang đọc mô tả của bạn...', 'Đang lên ý tưởng bối cảnh...']

type StudioMode = 'simple' | 'advanced'
const MODE_KEY = 'fortfio.studioMode'

const VERIFY_CARD_ID = 'verify-phone'
/** Below this width the phone card starts collapsed so the upload form shows on the first screen. */
const DESKTOP_QUERY = '(min-width: 768px)'

export default function StudioPage() {
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [selected, setSelected] = useState<Workflow>()
  const { account, refreshAccount } = useAuth()
  const [history, setHistory] = useState<Generation[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [groupFilter, setGroupFilter] = useState('all')
  const [statusFilter, setStatusFilter] = useState<GenerationStatus>()
  const [images, setImages] = useState<Record<string, string>>({})
  const [style, setStyle] = useState<string>()
  const [format, setFormat] = useState<OutputFormat>()
  const [prompt, setPrompt] = useState('')
  const [placement, setPlacement] = useState<Placement>()
  const [productName, setProductName] = useState('')
  const [variants, setVariants] = useState(1)
  const [seconds, setSeconds] = useState(5)
  const [resolution, setResolution] = useState<VideoResolution>('P720')
  const [music, setMusic] = useState<MusicMood>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()
  const [retryingId, setRetryingId] = useState<string>()
  const [mode, setMode] = useState<StudioMode>(() =>
    localStorage.getItem(MODE_KEY) === 'advanced' ? 'advanced' : 'simple',
  )

  const [verifyOpen, setVerifyOpen] = useState(() => window.matchMedia(DESKTOP_QUERY).matches)

  function changeMode(next: StudioMode) {
    setMode(next)
    localStorage.setItem(MODE_KEY, next)
  }

  function openPhoneVerify() {
    setVerifyOpen(true)
    requestAnimationFrame(() => {
      const card = document.getElementById(VERIFY_CARD_ID)
      card?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      card?.querySelector<HTMLInputElement>('input[type=tel]')?.focus({ preventScroll: true })
    })
  }

  const refresh = useCallback(() => {
    refreshAccount().catch(() => {})
    const workflows = RESULT_GROUPS.find((g) => g.id === groupFilter)?.workflows
    api
      .generations(page, PAGE_SIZE, { workflows, status: statusFilter })
      .then((result) => {
        setHistory(result.items)
        setTotalPages(result.totalPages)
      })
      .catch(() => {})
  }, [refreshAccount, page, groupFilter, statusFilter])

  /** Clears the filters too, so a job that was just created is always visible. */
  function showNewest() {
    if (page === 0 && groupFilter === 'all' && !statusFilter) refresh()
    else {
      setPage(0)
      setGroupFilter('all')
      setStatusFilter(undefined)
    }
  }

  function changeFilter(group: string, status?: GenerationStatus) {
    setGroupFilter(group)
    setStatusFilter(status)
    setPage(0)
  }

  const filtered = groupFilter !== 'all' || statusFilter !== undefined

  useEffect(() => {
    api.workflows().then((list) => {
      setWorkflows(list)
      selectWorkflow(list.find((w) => !w.enhancesResult))
    })
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const claimedTrial = useRef(false)
  useEffect(() => {
    const id = pendingTrial.get()
    if (!id || claimedTrial.current) return
    claimedTrial.current = true
    api
      .claimTrial(id)
      .then(() => {
        pendingTrial.clear()
        refresh()
      })
      .catch((e: ApiError) => {
        // 409: still being generated, try again on the next visit.
        if (e.status !== 409) pendingTrial.clear()
      })
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
    setPlacement(workflow?.placements[0]?.id)
    setProductName('')
    setVariants(1)
    if (workflow?.video) {
      setSeconds(workflow.video.defaultSeconds)
      setResolution(workflow.video.defaultResolution)
      setMusic(undefined)
    }
    setError(undefined)
  }

  function clearImage(key: string) {
    setImages(({ [key]: _removed, ...rest }) => rest)
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
      await api.createGeneration({
        workflow: selected.id,
        style,
        format,
        placement,
        productName: productName.trim() || undefined,
        variants,
        prompt: prompt || undefined,
        ...(selected.video && { durationSeconds: seconds, resolution, music }),
        images,
      })
      showNewest()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleEnhance(id: string, workflow: WorkflowId, variant: number, style?: string) {
    await api.enhanceGeneration(id, { workflow, style, variant })
    showNewest()
  }

  async function handleRetry(id: string) {
    setRetryingId(id)
    setError(undefined)
    try {
      await api.retryGeneration(id)
      showNewest()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setRetryingId(undefined)
    }
  }

  const videoSettings = selected?.video ?? undefined
  const validLength =
    !videoSettings || (seconds >= videoSettings.minSeconds && seconds <= videoSettings.maxSeconds)
  const ready = (selected?.imageSlots.every((slot) => !slot.required || images[slot.key]) ?? false) && validLength
  const hasScene = Boolean(images.scene)
  const variantChoices = VARIANT_CHOICES.filter((n) => n <= (selected?.maxVariants ?? 1))
  const totalCredits = videoSettings
    ? videoCredits(videoSettings, seconds, resolution, music)
    : (selected?.credits ?? 0) * variants
  const affordable = !account || account.credits >= totalCredits
  let stepNo = 1
  const workflowOf = (id: string) => workflows.find((w) => w.id === id)
  const label = (id: string) => workflowOf(id)?.label ?? id
  const slotKeys = selected?.imageSlots.map((slot) => slot.key) ?? []
  const tips = !slotKeys.includes('person') ? PRODUCT_TIPS : slotKeys.includes('product') ? HOLDING_TIPS : TRYON_TIPS
  const creators = workflows.filter((w) => !w.enhancesResult)
  const enhancers = workflows.filter((w) => w.enhancesResult)
  const styleGroups = new Set(selected?.styles.map((s) => s.group))
  const styleStep = styleGroups.size === 1 ? [...styleGroups][0] : 'Chọn vibe'
  const acceptsPrompt = selected !== undefined && selected.styles.length > 0 && selected.id !== 'VIRTUAL_TRYON'

  return (
    <div className="mx-auto max-w-6xl px-4 py-5 sm:px-6 md:py-8">
      <div className="bg-gradient-pop relative mb-5 overflow-hidden rounded-3xl border-2 border-ink p-4 text-white shadow-[4px_4px_0_0_var(--color-ink)] md:mb-8 md:rounded-[2rem] md:p-8 md:shadow-[6px_6px_0_0_var(--color-ink)]">
        <div className="pointer-events-none absolute -right-10 -bottom-16 h-56 w-56 animate-blob rounded-full bg-pop-lime/50 blur-3xl" />
        <div className="relative flex items-center justify-between gap-4 md:items-end">
          <div>
            <span className="sticker bg-white text-ink">✦ AI Studio</span>
            <div className="hidden md:block">
              <h1 className="mt-4 text-4xl font-black">Hôm nay mình làm ảnh gì nè?</h1>
              <p className="mt-2 text-white/85">
                {mode === 'simple' ? 'Tải ảnh lên, kể AI nghe bạn muốn gì, bấm tạo.' : 'Tải ảnh lên, chọn vibe, bấm tạo.'}{' '}
                Lỗi AI thì tự hoàn credit.
              </p>
            </div>
          </div>
          {account && (
            <div className="rounded-2xl border-2 border-ink bg-white px-4 py-1.5 text-ink shadow-[3px_3px_0_0_var(--color-ink)] md:px-5 md:py-3 md:shadow-[4px_4px_0_0_var(--color-ink)]">
              <p className="text-[10px] font-bold text-ink/50 uppercase md:text-xs">Số dư</p>
              <p key={account.credits} className="animate-pop-in text-xl font-black md:text-3xl">
                {account.credits} <span className="text-sm font-bold text-pop-pink md:text-base">credit</span>
              </p>
            </div>
          )}
        </div>
      </div>

      {account && !account.phoneVerified && (
        <PhoneVerifyCard id={VERIFY_CARD_ID} open={verifyOpen} onOpenChange={setVerifyOpen} />
      )}

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[440px_minmax(0,1fr)]">
        <div className="h-fit space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div role="tablist" aria-label="Chế độ tạo" className="flex shrink-0 rounded-full border-2 border-ink bg-white p-1">
              {(['simple', 'advanced'] as const).map((m) => (
                <button
                  type="button"
                  role="tab"
                  key={m}
                  aria-selected={mode === m}
                  onClick={() => changeMode(m)}
                  className={`min-h-9 rounded-full px-4 text-sm font-bold whitespace-nowrap transition ${
                    mode === m ? 'bg-ink text-white' : 'text-ink/60 hover:text-ink'
                  }`}
                >
                  {m === 'simple' ? 'Đơn giản' : 'Nâng cao'}
                </button>
              ))}
            </div>
            <p className="text-right text-xs text-ink/50">{mode === 'simple' ? 'Kể AI nghe' : 'Tự chỉnh thông số'}</p>
          </div>
          {mode === 'simple' && workflows.length > 0 && (
            <SimpleStudio workflows={workflows} onCreated={showNewest} onVerifyPhone={openPhoneVerify} />
          )}
          {mode === 'advanced' && (
            <form
              onSubmit={handleSubmit}
              className="h-fit space-y-6 rounded-3xl border-2 border-ink bg-white p-6 shadow-[6px_6px_0_0_var(--color-ink)]"
            >
              <div>
                <StepLabel n={stepNo++}>Chọn tính năng</StepLabel>
                <div className="space-y-2.5">
                  {creators.map((w) => {
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
                            {w.video ? `từ ${minVideoCredits(w.video)}` : w.credits} credit
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
                    <StepLabel n={stepNo++}>Tải ảnh lên</StepLabel>
                    <div className="grid grid-cols-2 gap-3">
                      {selected.imageSlots.map((slot) => (
                        <UploadSlot
                          key={slot.key}
                          label={slot.label}
                          required={slot.required}
                          image={images[slot.key]}
                          onFile={(file) => handleFile(slot.key, file)}
                          onClear={() => clearImage(slot.key)}
                        />
                      ))}
                    </div>
                    {selected.preservesProduct && (
                      <input
                        value={productName}
                        onChange={(e) => setProductName(e.target.value)}
                        maxLength={80}
                        placeholder="Tên sản phẩm (nên điền), ví dụ: chai serum trắng"
                        className="mt-3 w-full rounded-2xl border-2 border-ink/10 bg-cream p-3 text-sm transition focus:border-ink focus:outline-none"
                      />
                    )}
                    {selected.preservesProduct && (
                      <p className="mt-1.5 px-1 text-xs text-ink/50">
                        Có tên sản phẩm, AI tách nền sạch hơn khi ảnh chụp có đồ trang trí xung quanh.
                      </p>
                    )}
                    <ul className="mt-3 space-y-1 rounded-2xl bg-cream p-3 text-xs text-ink/70">
                      <li className="font-bold text-ink">Ảnh đẹp thì kết quả mới đẹp:</li>
                      {tips.map((tip) => (
                        <li key={tip}>✓ {tip}</li>
                      ))}
                    </ul>
                  </div>

                  {selected.styles.length > 0 && (
                    <div>
                      <StepLabel n={stepNo++}>{styleStep}</StepLabel>
                      {hasScene && (
                        <p className="mb-2 rounded-2xl bg-pop-lime/30 p-3 text-xs font-medium">
                          Đã có ảnh bối cảnh mẫu: AI sẽ dựng bối cảnh theo ảnh đó thay cho vibe.
                        </p>
                      )}
                      <VibePicker
                        key={selected.id}
                        styles={selected.styles}
                        value={style}
                        onChange={setStyle}
                        disabled={hasScene}
                      />
                    </div>
                  )}

                  {selected.formats.length > 0 && (
                    <div>
                      <StepLabel n={stepNo++}>{videoSettings ? 'Tỉ lệ khung hình' : 'Tỉ lệ theo sàn'}</StepLabel>
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
                      {videoSettings && (
                        <p className="mt-2 px-1 text-xs text-ink/50">
                          Ảnh khác tỉ lệ sẽ được AI vẽ thêm phần bối cảnh còn thiếu, sản phẩm giữ nguyên.
                        </p>
                      )}
                    </div>
                  )}

                  {videoSettings && (
                    <VideoOptionsPicker
                      key={selected.id}
                      settings={videoSettings}
                      stepNo={stepNo++}
                      seconds={seconds}
                      onSeconds={setSeconds}
                      resolution={resolution}
                      onResolution={setResolution}
                      music={music}
                      onMusic={setMusic}
                    />
                  )}

                  {(selected.placements.length > 0 || variantChoices.length > 1) && (
                    <div>
                      <StepLabel n={stepNo++}>Bố cục và số phương án</StepLabel>
                      {selected.placements.length > 0 && (
                        <div className="mb-3 grid grid-cols-3 gap-2">
                          {selected.placements.map((p) => (
                            <ChoiceButton key={p.id} active={placement === p.id} onClick={() => setPlacement(p.id)}>
                              {p.label}
                            </ChoiceButton>
                          ))}
                        </div>
                      )}
                      {variantChoices.length > 1 && (
                        <div className="grid grid-cols-3 gap-2">
                          {variantChoices.map((n) => (
                            <ChoiceButton key={n} active={variants === n} onClick={() => setVariants(n)}>
                              {n} phương án
                            </ChoiceButton>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {acceptsPrompt && (
                    <textarea
                      value={prompt}
                      onChange={(e) => setPrompt(e.target.value)}
                      maxLength={500}
                      rows={3}
                      placeholder="Yêu cầu thêm (không bắt buộc), ví dụ: tông hồng pastel, thêm hoa hồng"
                      className="w-full rounded-2xl border-2 border-ink/10 bg-cream p-3 text-sm transition focus:border-ink focus:outline-none"
                    />
                  )}

                  {error && affordable && (
                    <p className="animate-pop-in rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
                      {error}
                    </p>
                  )}
                  <CreditShortfall needed={totalCredits} onVerifyPhone={openPhoneVerify} />

                  <button
                    type="submit"
                    disabled={!ready || !affordable || submitting}
                    className="shine bg-gradient-pop relative w-full overflow-hidden rounded-full py-4 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:scale-100"
                  >
                    {submitting ? 'Đang gửi...' : `Tạo ngay ✦ ${totalCredits} credit`}
                  </button>
                  <p className="-mt-3 text-center text-xs text-ink/50">
                    {totalCredits} credit ≈ {formatVnd(totalCredits * CREDIT_VND)} theo gói {FEATURED_PLAN.name}. AI lỗi
                    sẽ tự hoàn credit.
                  </p>
                </>
              )}
            </form>
          )}
        </div>

        <section id="results" className="scroll-mt-24">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-2xl font-black">Kết quả của bạn</h2>
            {hasRunning && (
              <span className="flex items-center gap-2 rounded-full bg-pop-lime px-3 py-1 text-xs font-bold">
                <span className="h-2 w-2 animate-ping rounded-full bg-ink" /> AI đang làm việc
              </span>
            )}
          </div>

          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div role="tablist" aria-label="Lọc theo loại" className="flex max-w-full gap-1 overflow-x-auto rounded-full border-2 border-ink bg-white p-1 [scrollbar-width:none]">
              {RESULT_GROUPS.map((g) => (
                <button
                  type="button"
                  role="tab"
                  key={g.id}
                  aria-selected={groupFilter === g.id}
                  onClick={() => changeFilter(g.id, statusFilter)}
                  className={`rounded-full px-3 py-1.5 text-xs font-bold whitespace-nowrap transition ${
                    groupFilter === g.id ? 'bg-ink text-white' : 'text-ink/60 hover:text-ink'
                  }`}
                >
                  {g.label}
                </button>
              ))}
            </div>
            <FilterMenu
              label="Lọc theo trạng thái"
              options={STATUS_FILTERS}
              value={statusFilter}
              onChange={(status) => changeFilter(groupFilter, status)}
            />
          </div>

          {history.length === 0 && !filtered && (
            <div className="flex flex-col items-center rounded-3xl border-2 border-dashed border-ink/20 bg-white p-12 text-center">
              <span className="animate-float text-5xl font-black text-pop-pink">✦</span>
              <p className="mt-4 text-lg font-black">Chưa có ảnh nào</p>
              <p className="mt-1 text-ink/60">Tải ảnh sản phẩm lên và bấm "Tạo ngay" để bắt đầu.</p>
            </div>
          )}
          {history.length === 0 && filtered && (
            <div className="flex flex-col items-center rounded-3xl border-2 border-dashed border-ink/20 bg-white p-12 text-center">
              <p className="text-lg font-black">Không có kết quả nào khớp bộ lọc</p>
              <button
                type="button"
                onClick={() => changeFilter('all')}
                className="mt-3 rounded-full border-2 border-ink px-4 py-1.5 text-sm font-bold transition hover:bg-pop-lime"
              >
                Xem tất cả
              </button>
            </div>
          )}

          <div className="grid gap-5 sm:grid-cols-2">
            {history.map((g) => (
              <ResultCard
                key={g.id}
                generation={g}
                label={label(g.workflow)}
                retrying={retryingId === g.id}
                retryDisabled={retryingId !== undefined}
                onRetry={() => handleRetry(g.id)}
                enhancers={enhancers}
                onEnhance={(workflow, variant, style) => handleEnhance(g.id, workflow, variant, style)}
              />
            ))}
          </div>

          {totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onChange={(p) => {
                setPage(p)
                document.getElementById('results')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
              }}
            />
          )}
        </section>
      </div>
    </div>
  )
}

interface ResultCardProps {
  generation: Generation
  label: string
  retrying: boolean
  retryDisabled: boolean
  onRetry: () => void
  enhancers: Workflow[]
  onEnhance: (workflow: WorkflowId, variant: number, style?: string) => Promise<void>
}

function ResultCard({
  generation: g,
  label,
  retrying,
  retryDisabled,
  onRetry,
  enhancers,
  onEnhance,
}: ResultCardProps) {
  const [shown, setShown] = useState(0)
  const [previewing, setPreviewing] = useState(false)
  const [enhancing, setEnhancing] = useState(false)
  const index = Math.min(shown, g.outputUrls.length - 1)
  const url = g.outputUrls[index]
  const video = g.outputType === 'VIDEO'
  const canEnhance = g.status === 'SUCCEEDED' && !video && enhancers.length > 0

  return (
    <article className="group flex animate-pop-in flex-col overflow-hidden rounded-3xl border-2 border-ink bg-white transition hover:-translate-y-1 hover:shadow-[6px_6px_0_0_var(--color-ink)]">
      <div className="relative aspect-[4/5] overflow-hidden bg-cream">
        {g.status === 'RUNNING' && <RunningState planning={g.planning} />}
        {g.status === 'FAILED' && (
          <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center">
            <span className="text-3xl font-black text-red-400">!</span>
            <p className="text-sm font-medium text-red-600">{g.errorMessage}</p>
          </div>
        )}
        {g.status === 'SUCCEEDED' && (
          <button
            type="button"
            onClick={() => setPreviewing(true)}
            aria-label={`Xem ${label}`}
            className="block h-full w-full cursor-zoom-in"
          >
            {video ? (
              <video src={url} muted loop autoPlay playsInline className="h-full w-full animate-pop-in object-cover" />
            ) : (
              <img
                key={url}
                src={url}
                alt={label}
                className="h-full w-full animate-pop-in object-cover transition duration-500 group-hover:scale-105"
              />
            )}
            <span className="absolute right-3 bottom-3 rounded-full bg-ink/70 px-3 py-1 text-xs font-bold text-white opacity-0 transition group-hover:opacity-100">
              {video ? '▶ Xem video' : '⤢ Xem ảnh đầy đủ'}
            </span>
          </button>
        )}
        {g.status === 'SUCCEEDED' && <span className="sticker absolute top-3 left-3 bg-pop-lime">Xong ✓</span>}
        {g.format && (
          <span className="absolute top-3 right-3 rounded-full bg-white/90 px-2 py-0.5 text-xs font-bold">
            {FORMAT_LABEL[g.format]}
          </span>
        )}
      </div>
      {g.status === 'SUCCEEDED' && g.outputUrls.length > 1 && (
        <div className="flex gap-2 px-4 pt-3">
          {g.outputUrls.map((u, i) => (
            <button
              type="button"
              key={u}
              onClick={() => setShown(i)}
              aria-label={`Phương án ${i + 1}`}
              className={`h-12 w-12 overflow-hidden rounded-xl border-2 transition ${
                i === shown ? 'border-pop-pink' : 'border-ink/10 hover:border-ink'
              }`}
            >
              <img src={u} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
      {g.qualityWarning && (
        <p className="mx-4 mt-3 rounded-2xl border-2 border-amber-300 bg-amber-50 p-3 text-xs font-medium text-amber-800">
          ⚠ {g.qualityWarning}
        </p>
      )}
      <div className="mt-auto space-y-3 p-4 text-sm">
        <div className="min-w-0">
          <p className="truncate font-black">{label}</p>
          {g.video && (
            <p className="font-semibold text-ink/70">
              {g.video.seconds} giây · {RESOLUTION_LABEL[g.video.resolution]}
              {g.video.music ? ` · ♪ ${MUSIC_LABEL[g.video.music]}` : ' · Không nhạc'}
            </p>
          )}
          {g.summary && (
            <p className="mt-1.5 rounded-xl bg-pop-violet/10 px-2.5 py-1.5 text-xs text-ink/80" title={g.brief ?? undefined}>
              <span className="font-bold text-pop-violet">✦ AI hiểu: </span>
              {g.summary}
            </p>
          )}
          <p className="text-ink/50">{new Date(g.createdAt).toLocaleString('vi-VN')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {canEnhance && (
            <button
              type="button"
              onClick={() => setEnhancing((open) => !open)}
              aria-expanded={enhancing}
              className={`rounded-full border-2 px-3 py-1.5 font-bold transition ${
                enhancing ? 'border-pop-pink bg-pop-pink text-white' : 'border-ink hover:bg-pop-lime'
              }`}
            >
              Chỉnh thêm ✦
            </button>
          )}
          {g.canRetry && (
            <button
              type="button"
              onClick={onRetry}
              disabled={retryDisabled}
              title={`Tạo lại với cùng ảnh và lựa chọn · ${g.creditsCharged} credit`}
              className="rounded-full border-2 border-ink px-3 py-1.5 font-bold transition hover:bg-pop-lime disabled:opacity-40"
            >
              {retrying ? '...' : 'Làm lại ↻'}
            </button>
          )}
          {g.status === 'SUCCEEDED' && (
            <a
              href={url}
              data-track="download"
              target="_blank"
              rel="noreferrer"
              className="rounded-full bg-ink px-4 py-2 font-bold text-white transition hover:bg-pop-pink"
            >
              Tải về ↓
            </a>
          )}
        </div>
      </div>
      {canEnhance && enhancing && (
        <EnhancePanel
          enhancers={enhancers}
          variantLabel={g.outputUrls.length > 1 ? `phương án ${index + 1}` : undefined}
          onEnhance={async (workflow, style) => {
            await onEnhance(workflow, index, style)
            setEnhancing(false)
          }}
        />
      )}
      {previewing && (
        <MediaPreview
          urls={g.outputUrls}
          index={index}
          video={video}
          title={label}
          onIndexChange={setShown}
          onClose={() => setPreviewing(false)}
        />
      )}
    </article>
  )
}

function EnhancePanel({ enhancers, variantLabel, onEnhance }: {
  enhancers: Workflow[]
  variantLabel?: string
  onEnhance: (workflow: WorkflowId, style?: string) => Promise<void>
}) {
  const relight = enhancers.find((w) => w.styles.length > 0)
  const upscales = enhancers.filter((w) => w.styles.length === 0)
  const [light, setLight] = useState<string>()
  const [busy, setBusy] = useState<WorkflowId>()
  const [error, setError] = useState<string>()

  async function run(workflow: WorkflowId, style?: string) {
    setBusy(workflow)
    setError(undefined)
    try {
      await onEnhance(workflow, style)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className="space-y-4 border-t-2 border-ink/10 bg-cream/60 p-4 text-sm">
      {variantLabel && <p className="text-xs font-bold text-ink/60">Áp dụng cho {variantLabel}</p>}
      {relight && (
        <div>
          <p className="mb-2 font-black">
            {relight.label} <span className="font-medium text-ink/50">· {relight.credits} credit</span>
          </p>
          <div className="grid grid-cols-3 gap-1.5">
            {relight.styles.map((s) => (
              <button
                type="button"
                key={s.id}
                title={s.hint}
                onClick={() => setLight(s.id)}
                className={`rounded-xl border-2 px-2 py-1.5 text-xs font-bold transition ${
                  light === s.id ? 'border-ink bg-ink text-white' : 'border-ink/10 bg-white hover:border-ink'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            disabled={!light || busy !== undefined}
            onClick={() => run(relight.id, light)}
            className="mt-2 w-full rounded-full bg-ink py-2 font-bold text-white transition hover:bg-pop-pink disabled:opacity-40"
          >
            {busy === relight.id ? 'Đang gửi...' : light ? 'Áp dụng ánh sáng này' : 'Chọn một kiểu ánh sáng'}
          </button>
        </div>
      )}
      {upscales.length > 0 && (
        <div>
          <p className="mb-2 font-black">Phóng to độ phân giải</p>
          <div className="grid grid-cols-2 gap-2">
            {upscales.map((w) => (
              <button
                type="button"
                key={w.id}
                title={w.description}
                disabled={busy !== undefined}
                onClick={() => run(w.id)}
                className="rounded-2xl border-2 border-ink/10 bg-white p-2 font-bold transition hover:border-ink disabled:opacity-40"
              >
                {busy === w.id ? 'Đang gửi...' : w.label}
                <span className="block text-xs font-medium text-ink/50">{w.credits} credit</span>
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-ink/50">Ảnh nét hơn khi in hoặc làm banner. Chữ quá nhỏ trên nhãn không khôi phục được.</p>
        </div>
      )}
      {error && <p className="rounded-xl bg-red-50 p-2 text-xs font-medium text-red-600">{error}</p>}
    </div>
  )
}

function Pagination({ page, totalPages, onChange }: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  const navClass =
    'rounded-full border-2 border-ink px-4 py-2 text-sm font-bold transition hover:bg-pop-lime disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent'
  return (
    <nav aria-label="Phân trang kết quả" className="mt-6 flex flex-wrap items-center justify-center gap-2">
      <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)} className={navClass}>
        ‹ Trước
      </button>
      {pageNumbers(page, totalPages).map((p, i) =>
        p === undefined ? (
          <span key={`gap-${i}`} className="px-1 font-bold text-ink/40">
            …
          </span>
        ) : (
          <button
            type="button"
            key={p}
            onClick={() => onChange(p)}
            aria-current={p === page ? 'page' : undefined}
            className={`h-10 w-10 rounded-full text-sm font-bold transition ${
              p === page ? 'bg-ink text-white' : 'border-2 border-ink/10 hover:border-ink'
            }`}
          >
            {p + 1}
          </button>
        ),
      )}
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className={navClass}
      >
        Sau ›
      </button>
    </nav>
  )
}

/** First, last and the pages around the current one; undefined marks a gap. */
function pageNumbers(page: number, totalPages: number): (number | undefined)[] {
  const pages: (number | undefined)[] = []
  for (let p = 0; p < totalPages; p++) {
    if (p === 0 || p === totalPages - 1 || Math.abs(p - page) <= 1) pages.push(p)
    else if (pages.at(-1) !== undefined) pages.push(undefined)
  }
  return pages
}

function VibePicker({ styles, value, onChange, disabled }: {
  styles: Workflow['styles']
  value?: string
  onChange: (id: string) => void
  disabled: boolean
}) {
  const groups = [...new Set(styles.map((s) => s.group))]
  const [group, setGroup] = useState(() => styles.find((s) => s.id === value)?.group ?? groups[0])
  const current = styles.find((s) => s.id === value)

  return (
    <div className={disabled ? 'pointer-events-none opacity-40' : ''}>
      {groups.length > 1 && (
        <div role="tablist" className="mb-3 flex flex-wrap gap-1 rounded-2xl bg-cream p-1">
          {groups.map((g) => (
            <button
              type="button"
              role="tab"
              key={g}
              aria-selected={g === group}
              onClick={() => setGroup(g)}
              className={`shrink-0 rounded-full px-3 py-1.5 text-xs font-bold whitespace-nowrap transition ${
                g === group ? 'bg-ink text-white' : 'text-ink/60 hover:text-ink'
              }`}
            >
              {g}
              {current?.group === g && g !== group && <span className="ml-1 text-pop-pink">●</span>}
            </button>
          ))}
        </div>
      )}
      <div className="grid grid-cols-2 gap-2">
        {styles
          .filter((s) => s.group === group)
          .map((s) => (
            <button
              type="button"
              key={s.id}
              onClick={() => onChange(s.id)}
              className={`rounded-2xl border-2 p-3 text-left transition ${
                value === s.id
                  ? 'border-ink bg-ink text-white'
                  : 'border-ink/10 bg-white hover:-translate-y-0.5 hover:border-ink'
              }`}
            >
              <span className="block text-sm font-bold">{s.label}</span>
              <span className={`mt-0.5 block text-xs ${value === s.id ? 'text-white/70' : 'text-ink/50'}`}>
                {s.hint}
              </span>
            </button>
          ))}
      </div>
    </div>
  )
}

function VideoOptionsPicker({
  settings,
  stepNo,
  seconds,
  onSeconds,
  resolution,
  onResolution,
  music,
  onMusic,
}: {
  settings: VideoSettings
  stepNo: number
  seconds: number
  onSeconds: (seconds: number) => void
  resolution: VideoResolution
  onResolution: (resolution: VideoResolution) => void
  music?: MusicMood
  onMusic: (music?: MusicMood) => void
}) {
  const [custom, setCustom] = useState(!settings.presetSeconds.includes(seconds))
  const [draft, setDraft] = useState(String(seconds))
  const outOfRange = seconds < settings.minSeconds || seconds > settings.maxSeconds

  function typeSeconds(value: string) {
    setDraft(value)
    onSeconds(Number.parseInt(value, 10) || 0)
  }

  return (
    <div className="space-y-4">
      <StepLabel n={stepNo}>Thời lượng, độ nét và nhạc</StepLabel>

      <div>
        <p className="mb-2 text-xs font-bold text-ink/60">Thời lượng</p>
        <div className="flex flex-wrap gap-2 whitespace-nowrap">
          {settings.presetSeconds.map((s) => (
            <ChoiceButton
              key={s}
              active={!custom && seconds === s}
              onClick={() => {
                setCustom(false)
                onSeconds(s)
              }}
            >
              {s} giây
            </ChoiceButton>
          ))}
          <ChoiceButton
            active={custom}
            onClick={() => {
              setCustom(true)
              typeSeconds(String(seconds))
            }}
          >
            Tự nhập
          </ChoiceButton>
        </div>
        {custom && (
          <label className="mt-2 flex items-center gap-2 text-sm font-bold">
            <input
              type="number"
              inputMode="numeric"
              min={settings.minSeconds}
              max={settings.maxSeconds}
              value={draft}
              onChange={(e) => typeSeconds(e.target.value)}
              className={`w-24 rounded-2xl border-2 bg-cream px-3 py-2 transition focus:outline-none ${
                outOfRange ? 'border-red-400' : 'border-ink/10 focus:border-ink'
              }`}
            />
            giây
            <span className={`text-xs font-medium ${outOfRange ? 'text-red-600' : 'text-ink/50'}`}>
              từ {settings.minSeconds} đến {settings.maxSeconds} giây
            </span>
          </label>
        )}
        <p className="mt-2 px-1 text-xs text-ink/50">
          Video trên 15 giây được ghép từ nhiều đoạn nối tiếp nhau nên cần chờ thêm vài phút.
        </p>
      </div>

      <div>
        <p className="mb-2 text-xs font-bold text-ink/60">Độ phân giải</p>
        <div className="grid grid-cols-3 gap-2">
          {settings.resolutions.map((r) => (
            <button
              type="button"
              key={r.id}
              onClick={() => onResolution(r.id)}
              className={`rounded-2xl border-2 p-3 text-left transition ${
                resolution === r.id ? 'border-ink bg-ink text-white' : 'border-ink/10 bg-white hover:border-ink'
              }`}
            >
              <span className="block text-sm font-black">{r.label}</span>
              <span className={`block text-xs font-bold ${resolution === r.id ? 'text-pop-lime' : 'text-pop-pink'}`}>
                {r.creditsPerSecond} credit/giây
              </span>
              <span className={`mt-1 block text-xs ${resolution === r.id ? 'text-white/70' : 'text-ink/50'}`}>
                {r.hint}
              </span>
            </button>
          ))}
        </div>
      </div>

      <div>
        <p className="mb-2 text-xs font-bold text-ink/60">
          Nhạc nền <span className="font-medium text-ink/40">· AI sáng tác riêng, không lo bản quyền · +{settings.musicCredits} credit</span>
        </p>
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => onMusic(undefined)}
            className={`rounded-2xl border-2 p-3 text-left transition ${
              !music ? 'border-ink bg-ink text-white' : 'border-ink/10 bg-white hover:border-ink'
            }`}
          >
            <span className="block text-sm font-bold">Không nhạc</span>
            <span className={`mt-0.5 block text-xs ${!music ? 'text-white/70' : 'text-ink/50'}`}>
              Tự ghép nhạc trend trên TikTok
            </span>
          </button>
          {settings.music.map((m) => (
            <button
              type="button"
              key={m.id}
              onClick={() => onMusic(m.id)}
              className={`rounded-2xl border-2 p-3 text-left transition ${
                music === m.id ? 'border-ink bg-ink text-white' : 'border-ink/10 bg-white hover:border-ink'
              }`}
            >
              <span className="block text-sm font-bold">♪ {m.label}</span>
              <span className={`mt-0.5 block text-xs ${music === m.id ? 'text-white/70' : 'text-ink/50'}`}>
                {m.hint}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

function RunningState({ planning }: { planning: boolean }) {
  const [index, setIndex] = useState(0)
  const messages = planning ? PLANNING_MESSAGES : RUNNING_MESSAGES

  useEffect(() => {
    const timer = setInterval(() => setIndex((i) => i + 1), 2200)
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="skeleton flex h-full flex-col items-center justify-center gap-4">
      <span className="h-12 w-12 animate-spin rounded-full border-4 border-white border-t-pop-pink" />
      <p key={index} className="animate-pop-in px-4 text-center text-sm font-bold text-ink/70">
        {messages[index % messages.length]}
      </p>
    </div>
  )
}
