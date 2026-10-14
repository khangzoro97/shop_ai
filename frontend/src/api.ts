export type WorkflowId =
  | 'PRODUCT_EXACT'
  | 'PRODUCT_PHOTO'
  | 'PRODUCT_HOLDING'
  | 'VIRTUAL_TRYON'
  | 'PRODUCT_VIDEO'
  | 'RELIGHT'
  | 'UPSCALE_2K'
  | 'UPSCALE_4K'
export type OutputType = 'IMAGE' | 'VIDEO'
export type OutputFormat = 'SQUARE' | 'PORTRAIT' | 'STORY'
export type GenerationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type Category = 'FASHION' | 'COSMETICS'
export type Placement = 'BOTTOM_CENTER' | 'BOTTOM_LEFT' | 'BOTTOM_RIGHT'
export type VideoResolution = 'P360' | 'P720' | 'P1080'
export type MusicMood = 'UPBEAT' | 'CHILL' | 'LUXURY' | 'CUTE' | 'FESTIVE' | 'CINEMATIC'

/** Video credits = seconds x creditsPerSecond of the resolution, plus musicCredits with music. */
export interface VideoSettings {
  presetSeconds: number[]
  minSeconds: number
  maxSeconds: number
  defaultSeconds: number
  resolutions: { id: VideoResolution; label: string; hint: string; creditsPerSecond: number }[]
  defaultResolution: VideoResolution
  music: { id: MusicMood; label: string; hint: string }[]
  musicCredits: number
}

export interface VideoOptions {
  seconds: number
  resolution: VideoResolution
  music: MusicMood | null
}

export interface Workflow {
  id: WorkflowId
  label: string
  description: string
  credits: number
  maxVariants: number
  outputType: OutputType
  preservesProduct: boolean
  /** Runs on a finished result (relight, upscale) instead of from the Studio form. */
  enhancesResult: boolean
  imageSlots: { key: string; label: string; required: boolean }[]
  styles: { id: string; group: string; label: string; hint: string }[]
  formats: { id: OutputFormat; label: string; width: number; height: number }[]
  placements: { id: Placement; label: string }[]
  video: VideoSettings | null
}

export interface GenerationPage {
  items: Generation[]
  page: number
  totalPages: number
  totalItems: number
}

export interface Generation {
  id: string
  workflow: WorkflowId
  outputType: OutputType
  style: string | null
  format: OutputFormat | null
  status: GenerationStatus
  creditsCharged: number
  outputUrls: string[]
  errorMessage: string | null
  /** Set when OCR found label text the AI changed. */
  qualityWarning: string | null
  variants: number
  video: VideoOptions | null
  /** Simple mode: the description the customer submitted. */
  brief: string | null
  /** Simple mode: what the AI understood from the description, in Vietnamese. */
  summary: string | null
  /** Simple mode: the AI is still reading the description. */
  planning: boolean
  canRetry: boolean
  createdAt: string
}

export interface Account {
  id: number
  email: string
  shopName: string
  role: 'CUSTOMER' | 'ADMIN'
  credits: number
  phone: string | null
  phoneVerified: boolean
}

export interface AuthResponse {
  token: string
  account: Account
}

export interface SentCode {
  phone: string
  expiresInSeconds: number
  /** Only returned while the backend has no SMS/Zalo sender (development). */
  devCode: string | null
}

export interface PortfolioItem {
  id: number
  title: string
  client: string
  category: Category
  workflow: WorkflowId
  outputType: OutputType
  beforeUrl: string | null
  afterUrl: string
  sample: boolean
}

export interface CreateGenerationRequest {
  workflow: WorkflowId
  style?: string
  prompt?: string
  format?: OutputFormat
  placement?: Placement
  productName?: string
  variants?: number
  durationSeconds?: number
  resolution?: VideoResolution
  music?: MusicMood
  /** Simple mode: the customer's description (may be empty); the AI derives the prompts from it. */
  brief?: string
  images: Record<string, string>
}

export interface AssistRequest {
  workflow: WorkflowId
  text: string
  /** Product photo, so the AI writes a scene that suits it. */
  image?: string
  durationSeconds?: number
}

const TOKEN_KEY = 'fortfio.token'

/** Fired when the backend rejects the stored token, so the UI can drop the logged-in state. */
export const SESSION_EXPIRED_EVENT = 'fortfio:session-expired'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = tokenStore.get()
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })
  if (!res.ok) {
    if (res.status === 401 && token) {
      tokenStore.clear()
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
      throw new ApiError('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại', 401)
    }
    const problem = await res.json().catch(() => null)
    const fallback = res.status === 403 ? 'Bạn không có quyền thực hiện thao tác này' : `Lỗi ${res.status}`
    throw new ApiError(problem?.detail ?? fallback, res.status)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

const post = <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body) })

export const api = {
  register: (body: { email: string; password: string; shopName: string }) =>
    post<AuthResponse>('/auth/register', body),
  login: (body: { email: string; password: string }) => post<AuthResponse>('/auth/login', body),
  authOptions: () => request<{ googleClientId: string }>('/auth/options'),
  googleLogin: (credential: string) => post<AuthResponse>('/auth/google', { credential }),
  forgotPassword: (email: string) => post<void>('/auth/password/forgot', { email }),
  resetPassword: (token: string, password: string) =>
    post<AuthResponse>('/auth/password/reset', { token, password }),
  sendPhoneCode: (phone: string) => post<SentCode>('/me/phone/otp', { phone }),
  verifyPhone: (phone: string, code: string) => post<Account>('/me/phone/verify', { phone, code }),
  me: () => request<Account>('/me'),
  workflows: () => request<Workflow[]>('/workflows'),
  portfolio: (category?: Category) =>
    request<PortfolioItem[]>(`/portfolio${category ? `?category=${category}` : ''}`),
  generations: (page: number, size: number, filter: { workflows?: WorkflowId[]; status?: GenerationStatus } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    filter.workflows?.forEach((w) => params.append('workflow', w))
    if (filter.status) params.set('status', filter.status)
    return request<GenerationPage>(`/generations?${params}`)
  },
  generation: (id: string) => request<Generation>(`/generations/${id}`),
  createGeneration: (body: CreateGenerationRequest) => post<Generation>('/generations', body),
  retryGeneration: (id: string) => post<Generation>(`/generations/${id}/retry`, {}),
  enhanceGeneration: (id: string, body: { workflow: WorkflowId; style?: string; variant: number }) =>
    post<Generation>(`/generations/${id}/enhance`, body),
  assistPrompt: (body: AssistRequest, signal?: AbortSignal) =>
    request<{ text: string }>('/prompts/assist', { method: 'POST', body: JSON.stringify(body), signal }),
  startTrial: (image: string, scene: TrialScene) => post<Trial>('/trial', { image, scene }),
  trial: (id: string) => request<Trial>(`/trial/${id}`),
  claimTrial: (id: string) => post<Generation>(`/trial/${id}/claim`, {}),
  funnel: (days: number) => request<Funnel>(`/admin/funnel?days=${days}`),
  checkInput: (body: { workflow: WorkflowId; slot: string; image: string }, signal?: AbortSignal) =>
    request<{ issues: InputIssue[] }>('/inputs/check', { method: 'POST', body: JSON.stringify(body), signal }),
}

export type TrialScene = 'MARBLE' | 'PASTEL' | 'NATURE'

/** Home page try without an account; `previews` are watermarked image paths. */
export interface Trial {
  id: string
  status: GenerationStatus
  error: string | null
  previews: string[]
}

/** Trial waiting to be moved into the visitor's Studio once they log in. */
export const pendingTrial = {
  get: () => localStorage.getItem('fortfio.trial'),
  set: (id: string) => localStorage.setItem('fortfio.trial', id),
  clear: () => localStorage.removeItem('fortfio.trial'),
}

export interface Funnel {
  days: number
  steps: { label: string; count: number }[]
  trials: number
  zaloClicks: number
}

/** Advice about an uploaded photo; `suggest` names a better-suited workflow. */
export interface InputIssue {
  code: string
  message: string
  suggest?: WorkflowId | null
}

const MAX_IMAGE_SIDE = 1536

/** Downscales large phone photos before upload to keep request payloads small. */
export function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', 0.9))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('Không đọc được ảnh'))
    }
    img.src = url
  })
}
