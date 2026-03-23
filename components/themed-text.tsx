import { StyleSheet, Text, type TextProps } from 'react-native';

import { Fonts } from '@/constants/theme';
import { useThemeColor } from '@/hooks/use-theme-color';

type ThemeColorName = keyof typeof import('@/constants/theme').Colors.light;

export type ThemedTextProps = TextProps & {
  lightColor?: string;
  darkColor?: string;
  colorName?: ThemeColorName;
  type?: 'default' | 'defaultSemiBold' | 'subtitle' | 'body' | 'bodySemiBold' | 'caption' | 'eyebrow' | 'title' | 'headline' | 'link';
};

export function ThemedText({
  style,
  lightColor,
  darkColor,
  colorName = 'text',
  type = 'body',
  ...rest
}: ThemedTextProps) {
  const color = useThemeColor({ light: lightColor, dark: darkColor }, colorName);

  return (
    <Text
      style={[
        { color },
        type === 'default' ? styles.body : undefined,
        type === 'body' ? styles.body : undefined,
        type === 'defaultSemiBold' ? styles.bodySemiBold : undefined,
        type === 'bodySemiBold' ? styles.bodySemiBold : undefined,
        type === 'subtitle' ? styles.headline : undefined,
        type === 'caption' ? styles.caption : undefined,
        type === 'eyebrow' ? styles.eyebrow : undefined,
        type === 'title' ? styles.title : undefined,
        type === 'headline' ? styles.headline : undefined,
        type === 'link' ? styles.link : undefined,
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  body: {
    fontFamily: Fonts?.sans,
    fontSize: 16,
    lineHeight: 24,
  },
  bodySemiBold: {
    fontFamily: Fonts?.sans,
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '600',
  },
  caption: {
    fontFamily: Fonts?.sans,
    fontSize: 14,
    lineHeight: 20,
  },
  eyebrow: {
    fontFamily: Fonts?.mono,
    fontSize: 12,
    lineHeight: 16,
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  title: {
    fontFamily: Fonts?.rounded,
    fontSize: 32,
    fontWeight: '700',
    lineHeight: 38,
  },
  headline: {
    fontFamily: Fonts?.rounded,
    fontSize: 22,
    fontWeight: '700',
    lineHeight: 28,
  },
  link: {
    fontFamily: Fonts?.sans,
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '600',
    textDecorationLine: 'underline',
  },
});
