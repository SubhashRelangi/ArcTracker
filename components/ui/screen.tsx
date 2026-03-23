import { ReactNode } from 'react';
import { SafeAreaView, ScrollView, StyleSheet, View } from 'react-native';

import { Spacing } from '@/constants/theme';
import { useThemeColor } from '@/hooks/use-theme-color';

type ScreenProps = {
  children: ReactNode;
  scrollable?: boolean;
};

export function Screen({ children, scrollable = false }: ScreenProps) {
  const backgroundColor = useThemeColor({}, 'background');
  const surfaceColor = useThemeColor({}, 'surface');

  const content = <View style={styles.content}>{children}</View>;

  return (
    <SafeAreaView style={[styles.safeArea, { backgroundColor }]}> 
      {scrollable ? (
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={[
            styles.scrollContent,
            { backgroundColor, borderColor: surfaceColor },
          ]}>
          {content}
        </ScrollView>
      ) : (
        content
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
  },
  content: {
    flex: 1,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.xl,
    gap: Spacing.lg,
  },
});
