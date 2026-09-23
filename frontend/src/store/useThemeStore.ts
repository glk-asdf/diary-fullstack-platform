import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type ThemeMode = 'light' | 'dark'

interface ThemeState {
  mode: ThemeMode
  toggle: () => void
  setMode: (mode: ThemeMode) => void
}

/** 主题偏好，持久化到 localStorage，刷新后保持 */
export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      mode: 'light',
      toggle: () => set((state) => ({ mode: state.mode === 'light' ? 'dark' : 'light' })),
      setMode: (mode) => set({ mode }),
    }),
    { name: 'diary-theme' },
  ),
)
