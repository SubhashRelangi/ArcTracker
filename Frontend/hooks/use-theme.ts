import { useColorScheme } from '@/hooks/use-color-scheme';

export const useTheme = () => {
  const colorScheme = useColorScheme() ?? 'light';
  const isDark = colorScheme === 'dark';

  const theme = {
    background: isDark ? '#121212' : '#F8F9FE',
    surface: isDark ? '#1E1E1E' : '#FFFFFF',
    text: isDark ? '#FFFFFF' : '#1A1C1E',
    textSecondary: isDark ? '#A0A0A0' : '#666666',
    border: isDark ? '#333333' : '#F0F2F5',
    cardShadow: isDark ? 'rgba(0,0,0,0.5)' : 'rgba(0,0,0,0.05)',
  };

  return {
    theme,
    isDark,
    colorScheme,
  };
};
