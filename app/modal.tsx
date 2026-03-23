import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Screen } from '@/components/ui/screen';
import { Radius, Spacing } from '@/constants/theme';
import { useThemeColor } from '@/hooks/use-theme-color';

const checklist = [
  'Use semantic design tokens instead of one-off inline colors.',
  'Keep screens thin by moving shared UI into reusable primitives.',
  'Ship with lint + typecheck scripts so every change has a default quality gate.',
  'Document architecture decisions in the repo, not just in tribal knowledge.',
];

export default function StandardsModal() {
  const borderColor = useThemeColor({}, 'border');

  return (
    <Screen scrollable>
      <View style={styles.header}>
        <ThemedText type="eyebrow" colorName="accent">
          Team baseline
        </ThemedText>
        <ThemedText type="headline">Industrial-strength defaults for shipping faster</ThemedText>
        <ThemedText type="body" colorName="textMuted">
          These are the practices this repo now encourages whenever new features or screens are added.
        </ThemedText>
      </View>

      {checklist.map((item, index) => (
        <ThemedView
          key={item}
          colorName="surface"
          style={[styles.row, { borderColor }]}> 
          <View style={styles.indexBadge}>
            <ThemedText type="bodySemiBold">0{index + 1}</ThemedText>
          </View>
          <ThemedText style={styles.rowText}>{item}</ThemedText>
        </ThemedView>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: {
    gap: Spacing.sm,
  },
  row: {
    alignItems: 'center',
    borderRadius: Radius.md,
    borderWidth: 1,
    flexDirection: 'row',
    gap: Spacing.md,
    padding: Spacing.md,
  },
  indexBadge: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
    minWidth: 44,
  },
  rowText: {
    flex: 1,
  },
});
