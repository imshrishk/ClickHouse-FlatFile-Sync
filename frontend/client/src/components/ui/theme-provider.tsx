import * as React from "react"
import { createContext, useContext, useEffect, useState } from "react"
import { ThemeProvider as NextThemesProvider, useTheme as useNextTheme } from "next-themes"

// Define our own type for ThemeProviderProps
interface ThemeProviderProps {
  children: React.ReactNode
  defaultTheme?: string
  storageKey?: string
  [key: string]: any
}

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      themes={['light', 'dark', 'blue', 'purple']}
      {...props}
    >
      <ThemeProviderContent>
        {children}
      </ThemeProviderContent>
    </NextThemesProvider>
  )
}

interface ThemeProviderContentProps {
  children: React.ReactNode
}

// This wrapper component connects next-themes with our ThemeContext
function ThemeProviderContent({ children }: ThemeProviderContentProps) {
  const nextTheme = useNextTheme()
  
  const value = React.useMemo(
    () => ({
      theme: nextTheme.theme,
      setTheme: nextTheme.setTheme,
      themes: nextTheme.themes || ['light', 'dark', 'blue', 'purple'],
    }),
    [nextTheme.theme, nextTheme.setTheme, nextTheme.themes]
  )
  
  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  )
}

// Setup theme switcher component
export function ThemeSwitcher() {
  const { theme, setTheme } = useThemeContext()
  
  return (
    <div className="flex gap-2">
      <button
        onClick={() => setTheme("light")}
        className={`w-8 h-8 rounded-full flex items-center justify-center ${
          theme === "light" ? "bg-blue-100 text-blue-600" : "bg-gray-100 text-gray-400"
        }`}
        aria-label="Light theme"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>
      </button>
      
      <button
        onClick={() => setTheme("dark")}
        className={`w-8 h-8 rounded-full flex items-center justify-center ${
          theme === "dark" ? "bg-slate-700 text-slate-200" : "bg-gray-100 text-gray-400"
        }`}
        aria-label="Dark theme"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>
      </button>
      
      <button
        onClick={() => setTheme("blue")}
        className={`w-8 h-8 rounded-full flex items-center justify-center ${
          theme === "blue" ? "bg-blue-100 text-blue-600" : "bg-gray-100 text-gray-400"
        }`}
        aria-label="Blue theme"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M2 12c0 5.5 4.5 10 10 10s10-4.5 10-10S17.5 2 12 2 2 6.5 2 12z"/><path d="M12 2v20"/><path d="M12 12 2 12"/><path d="m19 5-7 7"/><path d="m5 5 7 7"/></svg>
      </button>
      
      <button
        onClick={() => setTheme("purple")}
        className={`w-8 h-8 rounded-full flex items-center justify-center ${
          theme === "purple" ? "bg-purple-100 text-purple-600" : "bg-gray-100 text-gray-400"
        }`}
        aria-label="Purple theme"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6c0 2-2 2-2 4v10c0 .6-.4 1-1 1H9a1 1 0 0 1-1-1V10c0-2-2-2-2-4V2h12z"/><path d="M18 2H6"/><path d="M10 18v4"/><path d="M14 18v4"/></svg>
      </button>
    </div>
  )
}

// Create and export our main theme context
export interface ThemeContextType {
  theme?: string
  setTheme: (theme: string) => void
  themes: string[]
}

export const ThemeContext = createContext<ThemeContextType>({
  setTheme: () => null,
  themes: ['light', 'dark', 'blue', 'purple'],
})

// Custom hook to use our theme context
export function useThemeContext() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error("useThemeContext must be used within a ThemeProvider")
  }
  return context
}