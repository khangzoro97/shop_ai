export type WorkflowId = 'PRODUCT_EXACT' | 'PRODUCT_PHOTO' | 'VIRTUAL_TRYON' | 'PRODUCT_VIDEO'
export type OutputType = 'IMAGE' | 'VIDEO'
export type OutputFormat = 'SQUARE' | 'PORTRAIT' | 'STORY'
export type GenerationStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'
export type Category = 'FASHION' | 'COSMETICS'

export interface Workflow {
  id: WorkflowId
  label: string
  description: string
  credits: number
  outputType: OutputType
  preservesProduct: boolean
  imageSlots: { key: string; label: string }[]
  styles: { id: string; label: string }[]
  formats: { id: OutputFormat; label: string; width: number; height: number }[]
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
  images: Record<string, string>
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
  return res.json() as Promise<T>
}

const post = <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body) })

export const api = {
  register: (body: { email: string; password: string; shopName: string }) =>
    post<AuthResponse>('/auth/register', body),
  login: (body: { email: string; password: string }) => post<AuthResponse>('/auth/login', body),
  sendPhoneCode: (phone: string) => post<SentCode>('/me/phone/otp', { phone }),
  verifyPhone: (phone: string, code: string) => post<Account>('/me/phone/verify', { phone, code }),
  me: () => request<Account>('/me'),
  workflows: () => request<Workflow[]>('/workflows'),
  portfolio: (category?: Category) =>
    request<PortfolioItem[]>(`/portfolio${category ? `?category=${category}` : ''}`),
  generations: () => request<Generation[]>('/generations'),
  generation: (id: string) => request<Generation>(`/generations/${id}`),
  createGeneration: (body: CreateGenerationRequest) => post<Generation>('/generations', body),
  retryGeneration: (id: string) => post<Generation>(`/generations/${id}/retry`, {}),
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
