import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import {
  api,
  fileToDataUrl,
  type InputIssue,
  type OutputFormat,
  type VideoResolution,
  type Workflow,
  type WorkflowId,
} from '../api.ts'
import { useAuth } from '../auth/context.ts'
import { CREDIT_VND, FEATURED_PLAN, formatVnd } from '../pricing.ts'
import { videoCredits } from '../studio.ts'
import CreditShortfall from './CreditShortfall.tsx'
import { ChoiceButton, StepLabel, UploadSlot } from './StudioControls.tsx'

type Kind = 'image' | 'model' | 'video'

const KINDS: { id: Kind; label: string; hint: string }[] = [
  { id: 'image', label: 'Ảnh sản phẩm', hint: 'Giữ nguyên nhãn' },
  { id: 'model', label: 'Người mẫu', hint: 'Cầm hoặc mặc thử' },
  { id: 'video', label: 'Video', hint: 'TikTok, Reels' },
]

const MODEL_WORKFLOWS: { id: WorkflowId; label: string }[] = [
  { id: 'PRODUCT_HOLDING', label: 'Cầm sản phẩm' },
  { id: 'VIRTUAL_TRYON', label: 'Mặc thử quần áo' },
]

const PLATFORMS: { format: OutputFormat; label: string; hint: string }[] = [
  { format: 'SQUARE', label: 'Shopee', hint: 'Vuông 1:1' },
  { format: 'STORY', label: 'TikTok', hint: 'Dọc 9:16' },
  { format: 'PORTRAIT', label: 'Facebook', hint: 'Dọc 3:4' },
]

/** 360p blurs the label, which defeats the point of keeping the product as-is. */
const QUALITY: { id: VideoResolution; label: string }[] = [
  { id: 'P720', label: 'Tiêu chuẩn 720p' },
  { id: 'P1080', label: 'Nét cao 1080p' },
]

/** The AI picks the actual mood from the description; this one is used if it cannot. */
const DEFAULT_MUSIC = 'UPBEAT'

const MAX_BRIEF = 3000
/** The description box grows with its text up to this height, then scrolls. */
const MAX_EDITOR_HEIGHT = 480
const ASSIST_TIMEOUT_MS = 35000
const ASSIST_FAILED = 'AI chưa viết được, bạn vẫn có thể tạo với mô tả hiện tại.'

const PLACEHOLDERS: Partial<Record<WorkflowId, string>> = {
  PRODUCT_EXACT:
    'Ví dụ: serum trên bàn đá, có hoa, ánh nắng sáng.\n\nĐể trống, AI sẽ tự chọn bối cảnh hợp với sản phẩm. Gõ ý ngắn rồi bấm "AI viết chi tiết" để AI viết giúp.',
  PRODUCT_HOLDING:
    'Ví dụ: cầm sản phẩm ngang mặt, nghiêng nhẹ, mỉm cười.\n\nĐể trống, AI sẽ tự chọn cách cầm tự nhiên.',
  PRODUCT_VIDEO:
    'Ví dụ: máy quay tiến chậm lại gần, cánh hoa rơi, nắng chiếu qua cửa sổ.\n\nĐể trống, AI sẽ tự viết kịch bản hợp với sản phẩm.',
}

/** Photos the AI looks at right after upload; people photos are expected to show people. */
const CHECKED_SLOTS = ['product', 'garment']

const SWITCH_LABELS: Partial<Record<WorkflowId, string>> = {
  PRODUCT_EXACT: 'Ảnh sản phẩm',
  PRODUCT_HOLDING: 'Cầm sản phẩm',
  VIRTUAL_TRYON: 'Mặc thử quần áo',
}

/** Phrases asking for a person in the picture; clauses with "không" ("không có người") are ignored. */
const PEOPLE_WORDS =
  /người mẫu|cô gái|chàng trai|phụ nữ|đàn ông|em bé|bàn tay|tay cầm|cầm trên tay|đang cầm|người cầm|người đứng|người ngồi|khuôn mặt/

function mentionsPeople(text: string) {
  return text
    .toLowerCase()
    .split(/[.,;:\n]/)
    .some((clause) => PEOPLE_WORDS.test(clause) && !clause.includes('không'))
}

/** Same photo and workflow give the same answer, so each pair is checked once. */
function checkKey(workflow: WorkflowId, slot: string, image: string) {
  return `${workflow}|${slot}|${image.length}|${image.slice(-32)}`
}

const TIPS: Partial<Record<WorkflowId, string>> = {
  PRODUCT_EXACT: 'Ảnh rõ nét, nền trơn, nhãn không bị che, mỗi ảnh một sản phẩm.',
  PRODUCT_HOLDING: 'Ảnh người mẫu thấy rõ hai tay. Gương mặt, trang phục và bối cảnh được giữ nguyên.',
  VIRTUAL_TRYON: 'Ảnh người mẫu đứng thẳng; ảnh quần áo trải phẳng hoặc treo móc, nền trơn.',
  PRODUCT_VIDEO: 'Sản phẩm rõ nét, nhãn hướng về máy ảnh. Ảnh khác tỉ lệ sẽ được AI vẽ thêm phần còn thiếu.',
}

const TEMPLATES: Partial<Record<WorkflowId, { label: string; text: string }[]>> = {
  PRODUCT_EXACT: [
    { label: 'Tối giản studio', text: 'Nền studio trơn màu kem, sản phẩm đặt trên bục tròn màu be, ánh sáng mềm chiếu từ trên xuống, bóng đổ nhẹ, phong cách tối giản cao cấp.' },
    { label: 'Bàn đá cẩm thạch', text: 'Mặt bàn đá cẩm thạch trắng vân xám, vài cành hoa hồng pastel phía sau hơi mờ, nắng sớm chiếu xiên từ bên trái, tông kem ấm.' },
    { label: 'Thiên nhiên', text: 'Đặt trên phiến đá giữa rêu xanh và lá dương xỉ, có vài giọt nước long lanh, nắng xuyên qua tán lá, cảm giác tươi mát tự nhiên.' },
    { label: 'Spa thư giãn', text: 'Khay gỗ sáng màu với khăn bông trắng gấp gọn, nến thơm và vài nhánh lá bạch đàn, ánh sáng ấm dịu, không khí spa thư giãn.' },
    { label: 'Biển mùa hè', text: 'Trên cát trắng mịn, phía sau là biển xanh ngọc hơi mờ, vài vỏ sò nhỏ, bóng lá cọ đổ xuống, nắng hè rực rỡ.' },
    { label: 'Tết', text: 'Nền đỏ son với cành mai vàng, bao lì xì và đèn lồng nhỏ, ánh đèn ấm áp, không khí Tết sum vầy.' },
    { label: 'Giáng sinh', text: 'Bàn gỗ với cành thông, quả châu đỏ và dây đèn vàng lấp lánh phía sau, tông đỏ xanh ấm cúng.' },
    { label: 'Sang trọng tối màu', text: 'Nền đen nhung, sản phẩm đặt trên mặt đá đen bóng, ánh sáng viền tạo đường bao sắc nét, phong cách nước hoa cao cấp.' },
  ],
  PRODUCT_HOLDING: [
    { label: 'Cầm ngang mặt', text: 'Người mẫu cầm sản phẩm bằng tay phải, giơ ngang má, nhãn hướng về máy ảnh, mỉm cười nhẹ.' },
    { label: 'Khoe sản phẩm', text: 'Hai tay nâng sản phẩm trước ngực, nghiêng nhẹ về phía máy ảnh, mắt nhìn thẳng, biểu cảm hào hứng.' },
    { label: 'Đang dùng thử', text: 'Một tay cầm sản phẩm ngang cằm như sắp dùng, tay kia chạm nhẹ lên má, biểu cảm thư giãn.' },
    { label: 'Review tự nhiên', text: 'Cầm sản phẩm ngang ngực bằng một tay, dáng đứng thoải mái như đang quay video review.' },
  ],
  PRODUCT_VIDEO: [
    { label: 'Tiến chậm', text: 'Máy quay tiến chậm lại gần sản phẩm, ánh sáng dịu thay đổi nhẹ, hạt bụi lấp lánh trong nắng.' },
    { label: 'Lia vòng', text: 'Máy quay lia vòng nhẹ quanh sản phẩm, ánh sáng phản chiếu trên bề mặt, hậu cảnh mờ dần.' },
    { label: 'Cánh hoa rơi', text: 'Cánh hoa hồng nhẹ nhàng rơi quanh sản phẩm, nắng sớm chiếu xiên, máy quay đứng yên rồi tiến gần.' },
    { label: 'Tươi mát', text: 'Giọt nước bắn nhẹ quanh sản phẩm, mặt nước lăn tăn bên dưới, ánh sáng xanh mát.' },
    { label: 'Tết rộn ràng', text: 'Hoa mai rung nhẹ trong gió, đèn lồng đỏ đung đưa phía sau, ánh đèn ấm lấp lánh.' },
  ],
}

/**
 * Simple mode: the customer picks what to make, uploads photos and describes the result in their
 * own words. Only choices that change the price stay as buttons; the AI derives everything else
 * (scene, placement, product name, music mood, video script) from the description.
 */
export default function SimpleStudio({
  workflows,
  onCreated,
  onVerifyPhone,
}: {
  workflows: Workflow[]
  onCreated: () => void
  onVerifyPhone: () => void
}) {
  const { account } = useAuth()
  const [kind, setKind] = useState<Kind>('image')
  const [modelWorkflow, setModelWorkflow] = useState<WorkflowId>('PRODUCT_HOLDING')
  const [images, setImages] = useState<Record<string, string>>({})
  const [brief, setBrief] = useState('')
  const [past, setPast] = useState<string[]>([])
  const [future, setFuture] = useState<string[]>([])
  const [writing, setWriting] = useState(false)
  const [assistNote, setAssistNote] = useState<string>()
  const [format, setFormat] = useState<OutputFormat>('SQUARE')
  const [variants, setVariants] = useState(1)
  const [seconds, setSeconds] = useState(10)
  const [resolution, setResolution] = useState<VideoResolution>('P720')
  const [withMusic, setWithMusic] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()
  const [sent, setSent] = useState(false)
  const [checks, setChecks] = useState<Record<string, InputIssue[] | 'pending'>>({})
  const requestedChecks = useRef(new Set<string>())

  const workflowId: WorkflowId = kind === 'image' ? 'PRODUCT_EXACT' : kind === 'video' ? 'PRODUCT_VIDEO' : modelWorkflow
  const workflow = workflows.find((w) => w.id === workflowId)
  const slots = workflow?.imageSlots.filter((slot) => slot.key !== 'scene') ?? []
  const checkedSlots = slots.filter((slot) => CHECKED_SLOTS.includes(slot.key) && images[slot.key])
  const imageIssues = checkedSlots.flatMap((slot) => {
    const result = checks[checkKey(workflowId, slot.key, images[slot.key])]
    return Array.isArray(result) ? result : []
  })
  const checkingImages = checkedSlots.some(
    (slot) => checks[checkKey(workflowId, slot.key, images[slot.key])] === 'pending',
  )
  const briefWantsPeople = workflowId === 'PRODUCT_EXACT' && mentionsPeople(brief)

  useEffect(() => {
    for (const slot of CHECKED_SLOTS) {
      const image = images[slot]
      if (!image || !workflow?.imageSlots.some((s) => s.key === slot)) continue
      const key = checkKey(workflowId, slot, image)
      if (requestedChecks.current.has(key)) continue
      requestedChecks.current.add(key)
      setChecks((prev) => ({ ...prev, [key]: 'pending' }))
      api
        .checkInput({ workflow: workflowId, slot, image })
        .then(({ issues }) => setChecks((prev) => ({ ...prev, [key]: issues })))
        .catch(() => setChecks((prev) => ({ ...prev, [key]: [] })))
    }
  }, [images, workflow, workflowId])
  const usesBrief = workflowId !== 'VIRTUAL_TRYON'
  const platforms = PLATFORMS.filter((p) => workflow?.formats.some((f) => f.id === p.format))
  const variantChoices = [1, 2, 4].filter((n) => n <= (workflow?.maxVariants ?? 1))
  const video = workflow?.video ?? undefined
  const credits = video
    ? videoCredits(video, seconds, resolution, withMusic ? DEFAULT_MUSIC : undefined)
    : (workflow?.credits ?? 0) * variants
  const ready = slots.every((slot) => !slot.required || images[slot.key]) && !writing
  const affordable = !account || account.credits >= credits
  let stepNo = 1

  useEffect(() => {
    if (!sent) return
    const timer = setTimeout(() => setSent(false), 4000)
    return () => clearTimeout(timer)
  }, [sent])

  function choose(nextKind: Kind, nextModel = modelWorkflow) {
    setKind(nextKind)
    setModelWorkflow(nextModel)
    const next = workflows.find(
      (w) => w.id === (nextKind === 'image' ? 'PRODUCT_EXACT' : nextKind === 'video' ? 'PRODUCT_VIDEO' : nextModel),
    )
    const keys = new Set(next?.imageSlots.map((slot) => slot.key))
    setImages((prev) => Object.fromEntries(Object.entries(prev).filter(([key]) => keys.has(key))))
    setFormat(nextKind === 'video' ? 'STORY' : 'SQUARE')
    setVariants(1)
    setError(undefined)
    setAssistNote(undefined)
  }

  function switchTo(target: WorkflowId) {
    if (target === 'PRODUCT_HOLDING' || target === 'VIRTUAL_TRYON') choose('model', target)
    else if (target === 'PRODUCT_VIDEO') choose('video')
    else choose('image')
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

  /** Replaces the description in a way the undo button can revert. */
  function replaceBrief(text: string) {
    setPast((p) => [...p, brief])
    setFuture([])
    setBrief(text)
  }

  function undo() {
    if (past.length === 0) return
    setFuture((f) => [brief, ...f])
    setBrief(past[past.length - 1])
    setPast((p) => p.slice(0, -1))
  }

  function redo() {
    if (future.length === 0) return
    setPast((p) => [...p, brief])
    setBrief(future[0])
    setFuture((f) => f.slice(1))
  }

  async function assist() {
    if (!workflow || writing) return
    setWriting(true)
    setAssistNote(undefined)
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), ASSIST_TIMEOUT_MS)
    try {
      const { text } = await api.assistPrompt(
        { workflow: workflow.id, text: brief, image: images.product, durationSeconds: video ? seconds : undefined },
        controller.signal,
      )
      setPast((p) => [...p, brief])
      setFuture([])
      await typeOut(text, setBrief)
    } catch (err) {
      setAssistNote(controller.signal.aborted ? ASSIST_FAILED : (err as Error).message)
    } finally {
      clearTimeout(timer)
      setWriting(false)
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!workflow) return
    setSubmitting(true)
    setError(undefined)
    try {
      await api.createGeneration({
        workflow: workflow.id,
        format: platforms.length > 0 ? format : undefined,
        variants,
        brief: usesBrief ? brief.trim() : undefined,
        ...(video && { durationSeconds: seconds, resolution, music: withMusic ? DEFAULT_MUSIC : undefined }),
        images: Object.fromEntries(slots.filter((slot) => images[slot.key]).map((slot) => [slot.key, images[slot.key]])),
      })
      setSent(true)
      onCreated()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-6 rounded-3xl border-2 border-ink bg-white p-6 shadow-[6px_6px_0_0_var(--color-ink)]"
    >
      <div>
        <StepLabel n={stepNo++}>Bạn muốn tạo gì?</StepLabel>
        <div role="tablist" aria-label="Loại kết quả" className="grid grid-cols-3 gap-2">
          {KINDS.map((k) => (
            <button
              type="button"
              role="tab"
              key={k.id}
              aria-selected={kind === k.id}
              onClick={() => choose(k.id)}
              className={`rounded-2xl border-2 px-1.5 py-3 text-center transition ${
                kind === k.id
                  ? 'border-ink bg-pop-lime/30 shadow-[3px_3px_0_0_var(--color-ink)]'
                  : 'border-ink/10 hover:-translate-y-0.5 hover:border-ink'
              }`}
            >
              <span className="block text-sm font-black whitespace-nowrap">{k.label}</span>
              <span className="mt-0.5 block text-xs text-ink/50">{k.hint}</span>
            </button>
          ))}
        </div>
        {kind === 'model' && (
          <div className="mt-3 grid grid-cols-2 gap-2">
            {MODEL_WORKFLOWS.map((m) => (
              <ChoiceButton key={m.id} active={modelWorkflow === m.id} onClick={() => choose('model', m.id)}>
                {m.label}
              </ChoiceButton>
            ))}
          </div>
        )}
      </div>

      <div>
        <StepLabel n={stepNo++}>Tải ảnh lên</StepLabel>
        <div className="grid grid-cols-2 gap-3">
          {slots.map((slot) => (
            <UploadSlot
              key={slot.key}
              label={slot.label}
              required={slot.required}
              image={images[slot.key]}
              onFile={(file) => handleFile(slot.key, file)}
              onClear={() => setImages(({ [slot.key]: _removed, ...rest }) => rest)}
            />
          ))}
        </div>
        {TIPS[workflowId] && <p className="mt-2 px-1 text-xs text-ink/50">{TIPS[workflowId]}</p>}
        {checkingImages && (
          <p className="mt-2 flex items-center gap-2 px-1 text-xs font-semibold text-pop-violet">
            <span className="animate-spin">✦</span> AI đang xem ảnh của bạn...
          </p>
        )}
        {imageIssues.length > 0 && (
          <div role="status" className="mt-3 space-y-2">
            {imageIssues.map((issue) => (
              <InputWarning key={issue.code} message={issue.message} suggest={issue.suggest} onSwitch={switchTo} />
            ))}
          </div>
        )}
      </div>

      {usesBrief ? (
        <div>
          <StepLabel n={stepNo++}>Mô tả kết quả bạn muốn</StepLabel>
          <BriefEditor
            value={brief}
            onChange={(text) => {
              setBrief(text)
              if (future.length > 0) setFuture([])
            }}
            placeholder={PLACEHOLDERS[workflowId] ?? ''}
            templates={TEMPLATES[workflowId] ?? []}
            onTemplate={replaceBrief}
            writing={writing}
            onAssist={assist}
            canUndo={past.length > 0 && !writing}
            canRedo={future.length > 0 && !writing}
            onUndo={undo}
            onRedo={redo}
          />
          {assistNote && (
            <p className="mt-2 rounded-2xl border-2 border-amber-300 bg-amber-50 p-3 text-xs font-medium text-amber-800">
              {assistNote}
            </p>
          )}
          {briefWantsPeople && (
            <div role="status" className="mt-2">
              <InputWarning
                message="Ảnh sản phẩm không vẽ thêm người. Muốn có người mẫu cầm sản phẩm, hãy dùng Người mẫu, Cầm sản phẩm."
                suggest="PRODUCT_HOLDING"
                onSwitch={switchTo}
              />
            </div>
          )}
        </div>
      ) : (
        <p className="rounded-2xl bg-cream p-3 text-sm text-ink/70">
          Thử đồ không cần mô tả: AI mặc đúng trang phục của bạn lên người mẫu, giữ nguyên hình in và chữ trên áo.
        </p>
      )}

      {platforms.length > 0 && (
        <div>
          <StepLabel n={stepNo++}>Đăng ở đâu?</StepLabel>
          <div className="grid grid-cols-3 gap-2">
            {platforms.map((p) => (
              <button
                type="button"
                key={p.format}
                onClick={() => setFormat(p.format)}
                className={`rounded-2xl border-2 p-3 text-center transition ${
                  format === p.format ? 'border-ink bg-pop-lime/30' : 'border-ink/10 hover:border-ink'
                }`}
              >
                <span className="block text-sm font-black">{p.label}</span>
                <span className="block text-xs text-ink/50">{p.hint}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {video ? (
        <div className="space-y-4">
          <StepLabel n={stepNo++}>Thời lượng và chất lượng</StepLabel>
          <div className="grid grid-cols-4 gap-2">
            {video.presetSeconds.map((s) => (
              <ChoiceButton key={s} active={seconds === s} onClick={() => setSeconds(s)}>
                {s} giây
              </ChoiceButton>
            ))}
          </div>
          {seconds > 16 && (
            <p className="-mt-2 px-1 text-xs text-ink/50">Video dài được ghép từ 2 đoạn nên cần chờ thêm vài phút.</p>
          )}
          <div className="grid grid-cols-2 gap-2">
            {QUALITY.map((q) => {
              const perSecond = video.resolutions.find((r) => r.id === q.id)?.creditsPerSecond
              return (
                <ChoiceButton key={q.id} active={resolution === q.id} onClick={() => setResolution(q.id)}>
                  {q.label}
                  <span className="block font-medium text-ink/50">{perSecond} credit/giây</span>
                </ChoiceButton>
              )
            })}
          </div>
          <label className="flex cursor-pointer items-center justify-between gap-3 rounded-2xl border-2 border-ink/10 p-3 transition hover:border-ink">
            <span>
              <span className="block text-sm font-bold">Nhạc nền +{video.musicCredits} credit</span>
              <span className="block text-xs text-ink/50">AI sáng tác nhạc hợp với mô tả, không lo bản quyền</span>
            </span>
            <input
              type="checkbox"
              role="switch"
              checked={withMusic}
              onChange={(e) => setWithMusic(e.target.checked)}
              className="peer sr-only"
            />
            <span className="relative h-7 w-12 shrink-0 rounded-full border-2 border-ink bg-cream transition peer-checked:bg-pop-lime peer-focus-visible:ring-2 peer-focus-visible:ring-pop-pink after:absolute after:top-0.5 after:left-0.5 after:h-5 after:w-5 after:rounded-full after:bg-ink after:transition peer-checked:after:translate-x-5" />
          </label>
        </div>
      ) : (
        variantChoices.length > 1 && (
          <div>
            <StepLabel n={stepNo++}>Số phương án</StepLabel>
            <div className="grid grid-cols-3 gap-2">
              {variantChoices.map((n) => (
                <ChoiceButton key={n} active={variants === n} onClick={() => setVariants(n)}>
                  {n} ảnh · {n * (workflow?.credits ?? 0)} credit
                </ChoiceButton>
              ))}
            </div>
          </div>
        )
      )}

      {error && affordable && (
        <p className="animate-pop-in rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
      <CreditShortfall needed={credits} onVerifyPhone={onVerifyPhone} />

      <div>
        <button
          type="submit"
          disabled={!ready || !affordable || submitting}
          className="shine bg-gradient-pop relative w-full overflow-hidden rounded-full py-4 text-lg font-black text-white shadow-lg shadow-pop-pink/30 transition hover:scale-[1.02] disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:scale-100"
        >
          {submitting ? 'Đang gửi...' : `Tạo ngay ✦ ${credits} credit`}
        </button>
        <p className={`mt-2 text-center text-xs ${sent ? 'font-bold text-green-700' : 'text-ink/50'}`}>
          {sent
            ? 'Đã gửi! AI đang làm, kết quả hiện ở mục "Kết quả của bạn".'
            : !ready && !writing
              ? 'Tải ảnh lên để bắt đầu.'
              : `${credits} credit ≈ ${formatVnd(credits * CREDIT_VND)} theo gói ${FEATURED_PLAN.name}. AI lỗi sẽ tự hoàn credit.`}
        </p>
      </div>
    </form>
  )
}

interface BriefEditorProps {
  value: string
  onChange: (text: string) => void
  placeholder: string
  templates: { label: string; text: string }[]
  onTemplate: (text: string) => void
  writing: boolean
  onAssist: () => void
  canUndo: boolean
  canRedo: boolean
  onUndo: () => void
  onRedo: () => void
}

/** Large description box with undo/redo, sample library, fullscreen and the AI writing button. */
function BriefEditor({
  value,
  onChange,
  placeholder,
  templates,
  onTemplate,
  writing,
  onAssist,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
}: BriefEditorProps) {
  const [fullscreen, setFullscreen] = useState(false)
  const [library, setLibrary] = useState(false)
  const textarea = useRef<HTMLTextAreaElement>(null)

  useLayoutEffect(() => {
    const el = textarea.current
    if (!el || fullscreen) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, MAX_EDITOR_HEIGHT)}px`
  }, [value, fullscreen])

  useEffect(() => {
    if (!fullscreen) return
    const close = (e: KeyboardEvent) => e.key === 'Escape' && setFullscreen(false)
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [fullscreen])

  const toolClass =
    'flex h-8 min-w-8 items-center justify-center rounded-full px-2 text-sm font-bold text-ink/70 transition hover:bg-white hover:text-ink disabled:opacity-30 disabled:hover:bg-transparent'

  const editor = (
    <div
      className={`overflow-hidden rounded-2xl border-2 bg-cream transition ${
        writing ? 'border-pop-violet' : 'border-ink/10 focus-within:border-ink'
      } ${fullscreen ? 'flex h-full w-full max-w-3xl flex-col bg-white shadow-[6px_6px_0_0_var(--color-ink)]' : ''}`}
    >
      <div className="flex items-center gap-1 border-b-2 border-ink/5 px-2 py-1.5">
        <button type="button" onClick={onUndo} disabled={!canUndo} title="Hoàn tác" aria-label="Hoàn tác" className={toolClass}>
          ↶
        </button>
        <button type="button" onClick={onRedo} disabled={!canRedo} title="Làm lại" aria-label="Làm lại" className={toolClass}>
          ↷
        </button>
        {templates.length > 0 && (
          <button
            type="button"
            onClick={() => setLibrary((open) => !open)}
            disabled={writing}
            aria-expanded={library}
            className={`${toolClass} ${library ? 'bg-white text-ink' : ''}`}
          >
            Mẫu gợi ý
          </button>
        )}
        <button
          type="button"
          onClick={() => setFullscreen((open) => !open)}
          title={fullscreen ? 'Thu nhỏ (Esc)' : 'Phóng to để viết dài'}
          aria-label={fullscreen ? 'Thu nhỏ' : 'Phóng to'}
          className={toolClass}
        >
          {fullscreen ? '⤡' : '⤢'}
        </button>
        <button
          type="button"
          onClick={onAssist}
          disabled={writing}
          className="ml-auto flex items-center gap-1.5 rounded-full bg-pop-violet px-3 py-1.5 text-xs font-black text-white transition hover:bg-pop-pink disabled:opacity-60"
        >
          <span className={writing ? 'animate-spin' : ''}>✦</span>
          {writing ? 'AI đang viết...' : value.trim() ? 'AI viết chi tiết' : 'AI gợi ý'}
        </button>
      </div>

      {library && (
        <div className="border-b-2 border-ink/5 bg-white p-3">
          <p className="mb-2 text-xs text-ink/50">Chọn một mẫu để chèn vào ô mô tả, rồi sửa theo ý bạn.</p>
          <div className="flex flex-wrap gap-1.5">
            {templates.map((t) => (
              <button
                type="button"
                key={t.label}
                title={t.text}
                onClick={() => {
                  onTemplate(t.text)
                  setLibrary(false)
                }}
                className="rounded-full border-2 border-ink/10 px-3 py-1 text-xs font-bold transition hover:border-ink hover:bg-pop-lime/30"
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>
      )}

      <textarea
        ref={textarea}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        readOnly={writing}
        maxLength={MAX_BRIEF}
        rows={fullscreen ? undefined : 6}
        placeholder={placeholder}
        aria-label="Mô tả kết quả bạn muốn"
        className={`block w-full resize-none bg-transparent p-3 text-sm leading-relaxed focus:outline-none ${
          fullscreen ? 'h-auto! flex-1 text-base' : ''
        } ${writing ? 'cursor-progress' : ''}`}
      />
      <div className="flex items-center justify-between gap-2 px-3 pb-2 text-xs text-ink/40">
        <span>{writing ? 'Đợi AI viết xong rồi sửa tiếp nhé' : 'Bấm ✦ để AI viết chi tiết, miễn phí'}</span>
        <span className={value.length >= MAX_BRIEF ? 'font-bold text-red-600' : ''}>
          {value.length}/{MAX_BRIEF}
        </span>
      </div>
    </div>
  )

  if (!fullscreen) return editor
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Mô tả kết quả bạn muốn"
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 p-4 md:p-10"
      onClick={(e) => e.target === e.currentTarget && setFullscreen(false)}
    >
      {editor}
    </div>
  )
}

function InputWarning({
  message,
  suggest,
  onSwitch,
}: {
  message: string
  suggest?: WorkflowId | null
  onSwitch: (workflow: WorkflowId) => void
}) {
  const label = suggest ? SWITCH_LABELS[suggest] : undefined
  return (
    <div className="animate-pop-in rounded-2xl border-2 border-amber-300 bg-amber-50 p-3 text-xs font-medium text-amber-900">
      <p>
        <span aria-hidden="true">⚠ </span>
        {message}
      </p>
      {suggest && label && (
        <button
          type="button"
          onClick={() => onSwitch(suggest)}
          className="mt-2 min-h-9 rounded-full border-2 border-ink bg-white px-3 font-bold text-ink shadow-[2px_2px_0_0_var(--color-ink)] transition hover:bg-pop-lime"
        >
          Chuyển sang {label} →
        </button>
      )}
    </div>
  )
}

/** Reveals the AI's text progressively so the customer sees it being written. */
function typeOut(text: string, set: (text: string) => void) {
  return new Promise<void>((resolve) => {
    const start = performance.now()
    const duration = Math.min(1500, 300 + text.length * 4)
    const step = (now: number) => {
      const shown = Math.ceil(text.length * Math.min(1, (now - start) / duration))
      set(text.slice(0, shown))
      if (shown < text.length) requestAnimationFrame(step)
      else resolve()
    }
    requestAnimationFrame(step)
  })
}
