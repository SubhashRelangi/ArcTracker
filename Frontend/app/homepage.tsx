import React from 'react';
import { StyleSheet, ScrollView, View, StatusBar as RNStatusBar, TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

import { BRAND_COLORS } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// Import components
import { Header } from '@/components/home/Header';
import { SpendingCard } from '@/components/home/SpendingCard';
import { BanksSection } from '@/components/home/BanksSection';
import { RecentActivity } from '@/components/home/RecentActivity';

export default function Homepage() {
  const { theme, isDark } = useTheme();
  const router = useRouter();

  return (
    <View style={[styles.mainContainer, { backgroundColor: theme.background }]}>
      <RNStatusBar barStyle={isDark ? "light-content" : "dark-content"} />
      <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
        <Header theme={theme} isDark={isDark} />
        <SpendingCard theme={theme} isDark={isDark} />
        <BanksSection theme={theme} />
        <RecentActivity theme={theme} />
      </ScrollView>

      {/* Floating Action Button */}
      <TouchableOpacity 
        style={[styles.fab, { backgroundColor: '#00BAF2' }]}
        onPress={() => router.push('/add-expense')}
      >
        <Ionicons name="add" size={32} color="#FFF" />
      </TouchableOpacity>
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
    paddingBottom: 100, // Extra padding for FAB and TabBar
  },
  fab: {
    position: 'absolute',
    right: 24,
    bottom: 24,
    width: 60,
    height: 60,
    borderRadius: 30,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    zIndex: 1000,
  },
});
