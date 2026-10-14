import type { MusicMood, VideoResolution, VideoSettings } from './api.ts'

export function videoCredits(settings: VideoSettings, seconds: number, resolution: VideoResolution, music?: MusicMood) {
  const perSecond = settings.resolutions.find((r) => r.id === resolution)?.creditsPerSecond ?? 0
  return seconds * perSecond + (music ? settings.musicCredits : 0)
}

export function minVideoCredits(settings: VideoSettings) {
  return settings.minSeconds * Math.min(...settings.resolutions.map((r) => r.creditsPerSecond))
}
