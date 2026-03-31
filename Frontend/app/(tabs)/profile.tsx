import React, { useState } from 'react';
import { StyleSheet, View, TouchableOpacity, ScrollView, Image } from 'react-native';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export default function ProfileScreen() {
  const { theme, isDark, themePreference, setThemePreference } = useTheme();
  const [activeTab, setActiveTab] = useState('Expense');

  const menuItems = [
    { icon: 'creditcard', label: 'My Card', hasChevron: true },
    { icon: 'shield', label: 'Security Settings', hasChevron: true },
    { icon: 'bell', label: 'Notification', hasChevron: true },
    { icon: 'clock.arrow.circlepath', label: 'Transaction History', hasChevron: true },
  ];

  return (
    <ScrollView style={[styles.container, { backgroundColor: theme.background }]} bounces={false}>
      {/* Header Section */}
      <View style={[styles.header, { backgroundColor: BRAND_COLORS.secondary }]} />

      {/* Profile Info Section */}
      <View style={[styles.profileCard, { backgroundColor: theme.background }]}>
        <View style={styles.profileHeader}>
          <View style={[styles.avatarWrapper, { borderColor: theme.background }]}>
            <Image 
              source={{ uri: 'https://i.pravatar.cc/300?u=ryan' }} 
              style={styles.avatar} 
            />
          </View>
          <TouchableOpacity style={[styles.editButton, { backgroundColor: isDark ? '#333' : '#F0EEFF' }]}>
            <ThemedText style={styles.editButtonText}>Edit Profile</ThemedText>
          </TouchableOpacity>
        </View>

        <View style={styles.userInfo}>
          <ThemedText style={styles.userName}>Ryan Raynolds</ThemedText>
          <ThemedText style={[styles.joinedDate, { color: theme.textSecondary }]}>Joined since 2023</ThemedText>
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
              <ThemedText style={styles.amountText}>$292.49</ThemedText>
              <ThemedText style={styles.trendText}>+12% better than last month</ThemedText>
            </View>
            <TouchableOpacity>
              <IconSymbol name="chevron.right" size={20} color={BRAND_COLORS.secondary} />
            </TouchableOpacity>
          </View>

          {/* Simulated Wave Chart */}
          <View style={styles.chartPlaceholder}>
            <View style={styles.waveContainer}>
               <View style={[styles.wave, { backgroundColor: isDark ? BRAND_COLORS.secondary : '#F0EEFF', opacity: isDark ? 0.3 : 0.5 }]} />
            </View>
            <View style={styles.chartLabels}>
              {['Week 1', 'Week 2', 'Week 3', 'Week 4'].map((label) => (
                <ThemedText key={label} style={[styles.chartLabelText, { color: theme.textSecondary }]}>{label}</ThemedText>
              ))}
            </View>
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
  editButton: {
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 20,
  },
  editButtonText: {
    color: BRAND_COLORS.secondary,
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
  chartPlaceholder: {
    height: 120,
    justifyContent: 'flex-end',
  },
  waveContainer: {
    height: 60,
    marginBottom: 10,
    overflow: 'hidden',
  },
  wave: {
    width: '100%',
    height: '100%',
    borderTopLeftRadius: 50,
    borderTopRightRadius: 50,
  },
  chartLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  chartLabelText: {
    fontSize: 12,
    fontWeight: '500',
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
