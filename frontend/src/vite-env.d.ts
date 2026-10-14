/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ZALO_PHONE?: string
  readonly VITE_CONTACT_EMAIL?: string
  readonly VITE_FREE_TRIAL_IMAGES?: string
  readonly VITE_BUSINESS_NAME?: string
  readonly VITE_BUSINESS_TAX_ID?: string
  readonly VITE_BUSINESS_ADDRESS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
