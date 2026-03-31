import { useAppTheme } from './use-theme-context';

export function useColorScheme() {
  const { colorScheme } = useAppTheme();
  return colorScheme;
}
