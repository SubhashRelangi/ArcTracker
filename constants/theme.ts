import { Platform } from 'react-native';

const brandPrimary = '#255CF3';
const brandAccent = '#6B8AFF';

export const Colors = {
  light: {
    text: '#0F172A',
    textMuted: '#475569',
    background: '#F8FAFC',
    surface: '#FFFFFF',
    surfaceMuted: '#E2E8F0',
    tint: brandPrimary,
    accent: brandAccent,
    icon: '#64748B',
    success: '#15803D',
    warning: '#B45309',
    danger: '#B91C1C',
    border: '#D9E2F2',
    shadow: 'rgba(15, 23, 42, 0.08)',
    tabIconDefault: '#94A3B8',
    tabIconSelected: brandPrimary,
  },
  dark: {
    text: '#E2E8F0',
    textMuted: '#94A3B8',
    background: '#020617',
    surface: '#0F172A',
    surfaceMuted: '#1E293B',
    tint: '#D6E4FF',
    accent: '#90A9FF',
    icon: '#94A3B8',
    success: '#4ADE80',
    warning: '#FBBF24',
    danger: '#F87171',
    border: '#1E293B',
    shadow: 'rgba(15, 23, 42, 0.4)',
    tabIconDefault: '#64748B',
    tabIconSelected: '#D6E4FF',
  },
} as const;

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 40,
} as const;

export const Radius = {
  sm: 8,
  md: 12,
  lg: 20,
  pill: 999,
} as const;

export const Fonts = Platform.select({
  ios: {
    sans: 'system-ui',
    serif: 'ui-serif',
    rounded: 'ui-rounded',
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
    serif: "Georgia, 'Times New Roman', serif",
    rounded:
      "'SF Pro Rounded', 'Hiragino Maru Gothic ProN', Meiryo, 'MS PGothic', sans-serif",
    mono: "SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace",
  },
});
