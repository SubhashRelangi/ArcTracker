import React, { createContext, useContext, useState, useEffect } from 'react';
import { useColorScheme as useRNColorScheme, Appearance } from 'react-native';

export type ThemePreference = 'system' | 'light' | 'dark';
type ThemeMode = 'light' | 'dark';

interface ThemeContextType {
  themePreference: ThemePreference;
  colorScheme: ThemeMode;
  isDark: boolean;
  setThemePreference: (pref: ThemePreference) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemColorScheme = useRNColorScheme() ?? 'light';
  const [themePreference, setThemePreferenceState] = useState<ThemePreference>('system');
  const [resolvedColorScheme, setResolvedColorScheme] = useState<ThemeMode>(systemColorScheme);

  useEffect(() => {
    if (themePreference === 'system') {
      setResolvedColorScheme(systemColorScheme);
    } else {
      setResolvedColorScheme(themePreference);
    }
  }, [themePreference, systemColorScheme]);

  useEffect(() => {
    const subscription = Appearance.addChangeListener(({ colorScheme: newScheme }) => {
      if (themePreference === 'system' && newScheme) {
        setResolvedColorScheme(newScheme as ThemeMode);
      }
    });

    return () => subscription.remove();
  }, [themePreference]);

  const setThemePreference = (pref: ThemePreference) => {
    setThemePreferenceState(pref);
  };

  const isDark = resolvedColorScheme === 'dark';

  return (
    <ThemeContext.Provider 
      value={{ 
        themePreference, 
        colorScheme: resolvedColorScheme, 
        isDark, 
        setThemePreference 
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useAppTheme must be used within a ThemeProvider');
  }
  return context;
}
