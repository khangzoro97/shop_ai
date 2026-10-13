/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ZALO_PHONE?: string
  readonly VITE_CONTACT_EMAIL?: string
  readonly VITE_FREE_TRIAL_IMAGES?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
