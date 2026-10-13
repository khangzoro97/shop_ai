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
  createdAt: string
}

export interface Account {
  id: number
  email: string
  shopName: string
  credits: number
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
}

export interface CreateGenerationRequest {
  workflow: WorkflowId
  style?: string
  prompt?: string
  format?: OutputFormat
  images: Record<string, string>
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) {
    const problem = await res.json().catch(() => null)
    throw new Error(problem?.detail ?? `Lỗi ${res.status}`)
  }
  return res.json() as Promise<T>
}

export const api = {
  me: () => request<Account>('/me'),
  workflows: () => request<Workflow[]>('/workflows'),
  portfolio: (category?: Category) =>
    request<PortfolioItem[]>(`/portfolio${category ? `?category=${category}` : ''}`),
  generations: () => request<Generation[]>('/generations'),
  generation: (id: string) => request<Generation>(`/generations/${id}`),
  createGeneration: (body: CreateGenerationRequest) =>
    request<Generation>('/generations', { method: 'POST', body: JSON.stringify(body) }),
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
