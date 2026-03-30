import React from 'react';
import { StyleSheet, ScrollView, View, StatusBar as RNStatusBar } from 'react-native';

import { BRAND_COLORS } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// Import components
import { Header } from '@/components/home/Header';
import { SpendingCard } from '@/components/home/SpendingCard';
import { BanksSection } from '@/components/home/BanksSection';
import { RecentActivity } from '@/components/home/RecentActivity';
import { ActionCards } from '@/components/home/ActionCards';

export default function Homepage() {
  const { theme, isDark } = useTheme();

  return (
    <View style={[styles.mainContainer, { backgroundColor: theme.background }]}>
      <RNStatusBar barStyle={isDark ? "light-content" : "dark-content"} />
      <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
        <Header theme={theme} isDark={isDark} />
        <SpendingCard theme={theme} isDark={isDark} />
        <BanksSection theme={theme} />
        <RecentActivity theme={theme} />
        <ActionCards theme={theme} BRAND_COLORS={BRAND_COLORS} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  mainContainer: {
    flex: 1,
  },
  container: {
    flex: 1,
  },
  contentContainer: {
    paddingBottom: 40,
  },
});
