import { Link } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Screen } from '@/components/ui/screen';
import { Radius, Spacing } from '@/constants/theme';
import { useThemeColor } from '@/hooks/use-theme-color';

const pillars = [
  {
    title: 'Typed navigation by default',
    description: 'Expo Router is configured as the app shell so screens, params, and flows stay explicit.',
  },
  {
    title: 'Shared design tokens',
    description: 'Colors, spacing, and typography are centralized to keep UI decisions consistent across surfaces.',
  },
  {
    title: 'Production-ready workflows',
    description: 'The repo now includes documented linting, type safety, and platform scripts for repeatable delivery.',
  },
];

export default function HomeScreen() {
  const tintColor = useThemeColor({}, 'tint');
  const accentColor = useThemeColor({}, 'accent');
  const borderColor = useThemeColor({}, 'border');

  return (
    <Screen scrollable>
      <ThemedView colorName="surface" style={[styles.heroCard, { borderColor }]}> 
        <ThemedText type="eyebrow" colorName="accent">
          ArcTracker · React Native foundation
        </ThemedText>
        <ThemedText type="title">Build features on a codebase that already feels like v1.</ThemedText>
        <ThemedText type="body" colorName="textMuted">
          This starter now emphasizes reusable primitives, semantic theming, and a cleaner screen architecture that scales with teams.
        </ThemedText>

        <Link href="/modal" asChild>
          <Pressable
            accessibilityRole="button"
            style={[styles.primaryButton, { backgroundColor: tintColor }]}> 
            <ThemedText type="bodySemiBold" lightColor="#FFFFFF" darkColor="#020617">
              Review engineering standards
            </ThemedText>
          </Pressable>
        </Link>
      </ThemedView>

      <View style={styles.sectionHeader}>
        <ThemedText type="headline">What changed</ThemedText>
        <ThemedText type="caption" colorName="textMuted">
          High-signal standards that teams expect in a modern React Native workspace.
        </ThemedText>
      </View>

      <View style={styles.grid}>
        {pillars.map((pillar) => (
          <ThemedView
            key={pillar.title}
            colorName="surface"
            style={[styles.pillarCard, { borderColor }]}> 
            <View style={[styles.badge, { backgroundColor: accentColor }]} />
            <ThemedText type="bodySemiBold">{pillar.title}</ThemedText>
            <ThemedText type="caption" colorName="textMuted">
              {pillar.description}
            </ThemedText>
          </ThemedView>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  heroCard: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    gap: Spacing.md,
    padding: Spacing.lg,
  },
  primaryButton: {
    alignItems: 'center',
    borderRadius: Radius.pill,
    marginTop: Spacing.sm,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
  },
  sectionHeader: {
    gap: Spacing.xs,
  },
  grid: {
    gap: Spacing.md,
  },
  pillarCard: {
    borderRadius: Radius.md,
    borderWidth: 1,
    gap: Spacing.sm,
    padding: Spacing.md,
  },
  badge: {
    borderRadius: Radius.pill,
    height: 10,
    width: 48,
  },
});
