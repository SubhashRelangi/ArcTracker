import React, { useEffect, useState } from 'react';
import { StyleSheet, View, TouchableOpacity, ActivityIndicator } from 'react-native';
import { useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { Ionicons } from '@expo/vector-icons';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';
import { getTransactions } from '@/services/api';

const CATEGORY_ICONS: Record<string, any> = {
  Food: 'fast-food',
  Shopping: 'cart',
  Transport: 'bus',
  Housing: 'home',
  Leisure: 'game-controller',
  Utilities: 'flash',
  Entertainment: 'tv',
};

interface ActivityItemProps {
  logo: string;
  name: string;
  subtext: string;
  amount: number;
  category: string;
  theme: any;
}

const ActivityItem: React.FC<ActivityItemProps> = ({ logo, name, subtext, amount, category, theme }) => {
  const isExpense = amount < 0;
  const iconName = CATEGORY_ICONS[category] || 'receipt';
  
  return (
    <View style={styles.activityItem}>
      <View style={[styles.logoContainer, { backgroundColor: theme.background }]}>
        {logo && logo !== 'https://via.placeholder.com/48' ? (
          <Image source={logo} style={styles.activityLogo} />
        ) : (
          <Ionicons name={iconName} size={24} color={BRAND_COLORS.primary} />
        )}
      </View>
      <View style={styles.activityDetails}>
        <ThemedText style={[styles.activityName, { color: theme.text }]}>{name}</ThemedText>
        <ThemedText style={[styles.activitySubtext, { color: theme.textSecondary }]}>{subtext}</ThemedText>
      </View>
      <View style={styles.activityAmountSection}>
        <ThemedText style={[styles.activityAmount, { color: isExpense ? '#FF5252' : '#4CAF50' }]}>
          {isExpense ? '-' : '+'}${Math.abs(amount).toFixed(2)}
        </ThemedText>
        <ThemedText style={[styles.activityCategory, { color: BRAND_COLORS.primary }]}>{category}</ThemedText>
      </View>
    </View>
  );
};

interface RecentActivityProps {
  theme: any;
}

export const RecentActivity: React.FC<RecentActivityProps> = ({ theme }) => {
  const router = useRouter();
  const [transactions, setTransactions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchTransactions();
  }, []);

  const fetchTransactions = async () => {
    try {
      const response = await getTransactions();
      setTransactions(response.data);
    } catch (error) {
      console.error('Error fetching transactions:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.section}>
      <ThemedText type="subtitle" style={[styles.sectionTitle, { color: theme.text }]}>Recent Activity</ThemedText>
      <View style={[styles.activityCard, { backgroundColor: theme.surface }]}>
        {loading ? (
          <ActivityIndicator size="large" color={BRAND_COLORS.primary} style={{ padding: 20 }} />
        ) : transactions.length > 0 ? (
          transactions.slice(0, 5).map((item) => (
            <ActivityItem 
              key={item.id}
              logo={item.logoUrl} 
              name={item.merchant} 
              subtext={item.subtext} 
              amount={item.amount} 
              category={item.category} 
              theme={theme}
            />
          ))
        ) : (
          <View style={{ padding: 20, alignItems: 'center' }}>
            <ThemedText style={{ color: theme.textSecondary }}>No recent activity</ThemedText>
          </View>
        )}
        
        <TouchableOpacity 
          style={[styles.viewHistoryButton, { borderTopColor: theme.border }]}
          onPress={() => router.push('/(tabs)/search')}
        >
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
