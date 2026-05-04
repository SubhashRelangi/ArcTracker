import React, { useEffect, useState } from 'react';
import { StyleSheet, View, TouchableOpacity, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';

interface HeaderProps {
  theme: any;
  isDark: boolean;
}

export const Header: React.FC<HeaderProps> = ({ theme, isDark }) => {
  const [userName, setUserName] = useState('');

  useEffect(() => {
    loadUser();
  }, []);

  const loadUser = async () => {
    try {
      const userData = await AsyncStorage.getItem('user');
      if (userData) {
        const user = JSON.parse(userData);
        setUserName(user.name.split(' ')[0]); // Get first name
      }
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <View style={[styles.header, { backgroundColor: theme.surface, borderBottomColor: theme.border }]}>
      <View style={styles.headerLeft}>
        <View>
          <ThemedText style={[styles.appName, { color: BRAND_COLORS.primary }]}>ARCTRACKER</ThemedText>
          {userName ? (
            <ThemedText style={[styles.welcomeText, { color: theme.textSecondary }]}>Hi, {userName} 👋</ThemedText>
          ) : null}
        </View>
      </View>
      <View style={styles.headerRight}>
        <TouchableOpacity style={[styles.iconButton, { backgroundColor: isDark ? '#2C2C2C' : '#F0F2F5' }]}>
          <IconSymbol name="bell" size={24} color={BRAND_COLORS.primary} />
        </TouchableOpacity>
        <TouchableOpacity style={[styles.iconButton, { backgroundColor: isDark ? '#2C2C2C' : '#F0F2F5' }]}>
          <IconSymbol name="gearshape" size={24} color={theme.textSecondary} />
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingTop: Platform.OS === 'ios' ? 60 : 40,
    paddingBottom: 20,
    borderBottomLeftRadius: 24,
    borderBottomRightRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 3,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  appName: {
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  welcomeText: {
    fontSize: 14,
    fontWeight: '600',
    marginTop: -2,
  },
  headerRight: {
    flexDirection: 'row',
    gap: 12,
  },
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
