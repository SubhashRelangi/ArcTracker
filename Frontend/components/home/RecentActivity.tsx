import React from 'react';
import { StyleSheet, View, TouchableOpacity } from 'react-native';
import { Image } from 'expo-image';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';

interface ActivityItemProps {
  logo: string;
  name: string;
  subtext: string;
  amount: string;
  category: string;
  theme: any;
}

const ActivityItem: React.FC<ActivityItemProps> = ({ logo, name, subtext, amount, category, theme }) => {
  return (
    <View style={styles.activityItem}>
      <View style={[styles.logoContainer, { backgroundColor: theme.background }]}>
        <Image source={logo} style={styles.activityLogo} />
      </View>
      <View style={styles.activityDetails}>
        <ThemedText style={[styles.activityName, { color: theme.text }]}>{name}</ThemedText>
        <ThemedText style={[styles.activitySubtext, { color: theme.textSecondary }]}>{subtext}</ThemedText>
      </View>
      <View style={styles.activityAmountSection}>
        <ThemedText style={styles.activityAmount}>{amount}</ThemedText>
        <ThemedText style={[styles.activityCategory, { color: BRAND_COLORS.primary }]}>{category}</ThemedText>
      </View>
    </View>
  );
};

interface RecentActivityProps {
  theme: any;
}

export const RecentActivity: React.FC<RecentActivityProps> = ({ theme }) => {
  return (
    <View style={styles.section}>
      <ThemedText type="subtitle" style={[styles.sectionTitle, { color: theme.text }]}>Recent Activity</ThemedText>
      <View style={[styles.activityCard, { backgroundColor: theme.surface }]}>
        <ActivityItem 
          logo="https://logo.clearbit.com/starbucks.com" 
          name="Starbucks" 
          subtext="Coffee & Snacks" 
          amount="-$8.50" 
          category="Food" 
          theme={theme}
        />
        <ActivityItem 
          logo="https://logo.clearbit.com/amazon.com" 
          name="Amazon" 
          subtext="Online Shopping" 
          amount="-$74.99" 
          category="Shopping" 
          theme={theme}
        />
        <ActivityItem 
          logo="https://logo.clearbit.com/shell.com" 
          name="Shell" 
          subtext="Fuel Station" 
          amount="-$62.30" 
          category="Transport" 
          theme={theme}
        />
        <TouchableOpacity style={[styles.viewHistoryButton, { borderTopColor: theme.border }]}>
          <ThemedText style={[styles.viewHistoryText, { color: BRAND_COLORS.primary }]}>View Full History</ThemedText>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  section: {
    marginTop: 24,
  },
  sectionTitle: {
    paddingHorizontal: 20,
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 16,
  },
  activityCard: {
    marginHorizontal: 20,
    borderRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.1,
    shadowRadius: 15,
    elevation: 5,
    padding: 8,
  },
  activityItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
  },
  logoContainer: {
    width: 48,
    height: 48,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  activityLogo: {
    width: 32,
    height: 32,
    borderRadius: 8,
  },
  activityDetails: {
    flex: 1,
  },
  activityName: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 2,
  },
  activitySubtext: {
    fontSize: 13,
  },
  activityAmountSection: {
    alignItems: 'flex-end',
  },
  activityAmount: {
    fontSize: 16,
    fontWeight: '800',
    color: '#FF5252',
    marginBottom: 2,
  },
  activityCategory: {
    fontSize: 12,
    fontWeight: '600',
  },
  viewHistoryButton: {
    paddingVertical: 16,
    borderTopWidth: 1,
    alignItems: 'center',
  },
  viewHistoryText: {
    fontWeight: '700',
    fontSize: 15,
  },
});
