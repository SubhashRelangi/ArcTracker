import React, { useState, useEffect } from 'react';
import { StyleSheet, View, TouchableOpacity, ScrollView, Image, ActivityIndicator, Alert } from 'react-native';
import { useRouter } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { getMe, getSpendingSummary } from '@/services/api';

export default function ProfileScreen() {
  const router = useRouter();
  const { theme, isDark, themePreference, setThemePreference } = useTheme();
  const [activeTab, setActiveTab] = useState('Expense');
  const [user, setUser] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [totalMonthlySpent, setTotalMonthlySpent] = useState(0);

  useEffect(() => {
    fetchProfileData();
  }, []);

  const fetchProfileData = async () => {
    try {
      const [userResponse, spendingResponse] = await Promise.all([
        getMe(),
        getSpendingSummary()
      ]);
      
      setUser(userResponse.data);
      
      const total = spendingResponse.data.reduce((acc: number, item: any) => acc + Math.abs(item._sum.amount || 0), 0);
      setTotalMonthlySpent(total);
    } catch (error) {
      console.error('Failed to fetch profile data', error);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    Alert.alert(
      'Logout',
      'Are you sure you want to logout?',
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Logout', 
          style: 'destructive',
          onPress: async () => {
            await AsyncStorage.clear();
            router.replace('/login');
          }
        }
      ]
    );
  };

  const menuItems = [
    { icon: 'creditcard', label: 'My Card', hasChevron: true },
    { icon: 'shield', label: 'Security Settings', hasChevron: true },
    { icon: 'bell', label: 'Notification', hasChevron: true },
    { icon: 'clock.arrow.circlepath', label: 'Transaction History', hasChevron: true },
  ];

  if (loading) {
    return (
      <View style={[styles.container, { backgroundColor: theme.background, justifyContent: 'center' }]}>
        <ActivityIndicator size="large" color={BRAND_COLORS.secondary} />
      </View>
    );
  }

  return (
    <ScrollView style={[styles.container, { backgroundColor: theme.background }]} bounces={false}>
      {/* Header Section */}
      <View style={[styles.header, { backgroundColor: BRAND_COLORS.secondary }]} />

      {/* Profile Info Section */}
      <View style={[styles.profileCard, { backgroundColor: theme.background }]}>
        <View style={styles.profileHeader}>
          <View style={[styles.avatarWrapper, { borderColor: theme.background }]}>
            <Image 
              source={{ uri: `https://ui-avatars.com/api/?name=${user?.name || 'User'}&background=random` }} 
              style={styles.avatar} 
            />
          </View>
          <TouchableOpacity 
            style={[styles.logoutButton, { backgroundColor: '#FF5252' }]}
            onPress={handleLogout}
          >
            <ThemedText style={styles.logoutButtonText}>Logout</ThemedText>
          </TouchableOpacity>
        </View>

        <View style={styles.userInfo}>
          <ThemedText style={styles.userName}>{user?.name || 'Ryan Raynolds'}</ThemedText>
          <ThemedText style={[styles.joinedDate, { color: theme.textSecondary }]}>
            Joined since {user ? new Date(user.createdAt).getFullYear() : '2023'}
          </ThemedText>
        </View>

        {/* Monthly Tracker Card */}
        <View style={[styles.trackerCard, { backgroundColor: theme.surface, borderColor: theme.border }]}>
          <ThemedText style={styles.trackerTitle}>Monthly Tracker</ThemedText>
          
          <View style={[styles.tabContainer, { backgroundColor: isDark ? '#262626' : '#F8F9FE' }]}>
            {['Expense', 'Income'].map((tab) => (
              <TouchableOpacity 
                key={tab} 
                onPress={() => setActiveTab(tab)}
                style={[
                  styles.tab, 
                  activeTab === tab && [styles.activeTab, { backgroundColor: isDark ? '#333' : '#FFF' }]
                ]}
              >
                <ThemedText style={[
                  styles.tabText, 
                  { color: activeTab === tab ? BRAND_COLORS.secondary : theme.textSecondary }
                ]}>
                  {tab}
                </ThemedText>
              </TouchableOpacity>
            ))}
          </View>

          <View style={styles.amountContainer}>
            <View>
              <ThemedText style={styles.amountText}>
                ${activeTab === 'Expense' ? totalMonthlySpent.toFixed(2) : '0.00'}
              </ThemedText>
              <ThemedText style={styles.trendText}>
                {activeTab === 'Expense' ? 'Tracking your monthly spending' : 'No income recorded yet'}
              </ThemedText>
            </View>
            <TouchableOpacity onPress={() => router.push('/(tabs)/search')}>
              <IconSymbol name="chevron.right" size={20} color={BRAND_COLORS.secondary} />
            </TouchableOpacity>
          </View>
        </View>

        {/* Theme Selection Section */}
        <View style={styles.sectionHeader}>
          <ThemedText style={styles.sectionTitle}>App Appearance</ThemedText>
        </View>
        
        <View style={[styles.themeSelectorContainer, { backgroundColor: theme.surface, borderColor: theme.border }]}>
          {(['system', 'light', 'dark'] as const).map((pref) => (
            <TouchableOpacity 
              key={pref}
              onPress={() => setThemePreference(pref)}
              style={[
                styles.themeOption,
                themePreference === pref && { backgroundColor: isDark ? '#333' : '#F0EEFF' }
              ]}
            >
              <View style={styles.menuItemLeft}>
                <View style={[styles.iconCircle, { backgroundColor: isDark ? '#262626' : '#F8F9FE' }]}>
                  <IconSymbol 
                    name={pref === 'dark' ? 'moon' : pref === 'light' ? 'house.fill' : 'arrow.2.circlepath' as any} 
                    size={20} 
                    color={themePreference === pref ? BRAND_COLORS.secondary : theme.textSecondary} 
                  />
                </View>
                <ThemedText style={[
                  styles.menuLabel, 
                  { color: themePreference === pref ? BRAND_COLORS.secondary : theme.text }
                ]}>
                  {pref.charAt(0).toUpperCase() + pref.slice(1)} Mode
                </ThemedText>
              </View>
              {themePreference === pref && (
                <IconSymbol name="chevron.right" size={20} color={BRAND_COLORS.secondary} />
              )}
            </TouchableOpacity>
          ))}
        </View>

        {/* Menu Items */}
        <View style={styles.menuContainer}>
          {menuItems.map((item, index) => (
            <View key={index} style={styles.menuItemWrapper}>
              <TouchableOpacity 
                style={styles.menuItem} 
                onPress={() => console.log(item.label)}
              >
                <View style={styles.menuItemLeft}>
                  <View style={[styles.iconCircle, { backgroundColor: isDark ? '#262626' : '#F8F9FE' }]}>
                    <IconSymbol name={item.icon as any} size={20} color={BRAND_COLORS.secondary} />
                  </View>
                  <ThemedText style={styles.menuLabel}>{item.label}</ThemedText>
                </View>
                {item.hasChevron && (
                  <IconSymbol name="chevron.right" size={20} color={theme.textSecondary} />
                )}
              </TouchableOpacity>
              {index < menuItems.length - 1 && <View style={[styles.separator, { backgroundColor: theme.border }]} />}
            </View>
          ))}
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    height: 180,
    paddingTop: 60,
    paddingHorizontal: 20,
  },
  profileCard: {
    flex: 1,
    marginTop: -40,
    borderTopLeftRadius: 40,
    borderTopRightRadius: 40,
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  profileHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    marginBottom: 16,
  },
  avatarWrapper: {
    width: 100,
    height: 100,
    borderRadius: 50,
    borderWidth: 5,
    overflow: 'hidden',
    marginTop: -60,
    backgroundColor: '#EEE',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  avatar: {
    width: '100%',
    height: '100%',
  },
  logoutButton: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 20,
  },
  logoutButtonText: {
    color: '#FFF',
    fontWeight: '700',
    fontSize: 14,
  },
  userInfo: {
    marginBottom: 24,
  },
  userName: {
    fontSize: 24,
    fontWeight: '800',
    marginBottom: 4,
  },
  joinedDate: {
    fontSize: 14,
    fontWeight: '500',
  },
  trackerCard: {
    borderRadius: 24,
    padding: 20,
    marginBottom: 24,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.05,
    shadowRadius: 10,
    elevation: 2,
  },
  trackerTitle: {
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 16,
  },
  tabContainer: {
    flexDirection: 'row',
    borderRadius: 20,
    padding: 4,
    marginBottom: 20,
  },
  tab: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 18,
  },
  activeTab: {
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  tabText: {
    fontSize: 14,
    fontWeight: '600',
  },
  amountContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  amountText: {
    fontSize: 32,
    fontWeight: '900',
  },
  trendText: {
    fontSize: 12,
    color: '#4CAF50',
    fontWeight: '600',
    marginTop: 4,
  },
  sectionHeader: {
    marginBottom: 12,
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '800',
    opacity: 0.8,
  },
  themeSelectorContainer: {
    borderRadius: 24,
    padding: 8,
    marginBottom: 24,
    borderWidth: 1,
  },
  themeOption: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 12,
    borderRadius: 16,
  },
  menuContainer: {
    marginBottom: 40,
  },
  menuItemWrapper: {
    width: '100%',
  },
  menuItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 15,
  },
  menuItemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 15,
  },
  iconCircle: {
    width: 40,
    height: 40,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
  },
  menuLabel: {
    fontSize: 16,
    fontWeight: '600',
  },
  separator: {
    height: 1,
    width: '100%',
    marginLeft: 55,
  },
});
