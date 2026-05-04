import React, { useEffect, useState } from 'react';
import { StyleSheet, View, TouchableOpacity, ActivityIndicator } from 'react-native';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';
import { getSpendingSummary } from '@/services/api';

interface CategoryRowProps {
  color: string;
  name: string;
  percentage: string;
  amount: string;
  theme: any;
}

const CategoryRow: React.FC<CategoryRowProps> = ({ color, name, percentage, amount, theme }) => {
  return (
    <View style={styles.categoryRow}>
      <View style={styles.categoryLeft}>
        <View style={[styles.colorDot, { backgroundColor: color }]} />
        <ThemedText style={[styles.categoryName, { color: theme.text }]} numberOfLines={1}>{name}</ThemedText>
      </View>
      <ThemedText style={[styles.categoryAmount, { color: theme.text }]}>{amount}</ThemedText>
    </View>
  );
};

interface SpendingCardProps {
  theme: any;
  isDark: boolean;
}

export const SpendingCard: React.FC<SpendingCardProps> = ({ theme, isDark }) => {
  const [summary, setSummary] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalSpent, setTotalSpent] = useState(0);

  useEffect(() => {
    fetchSpending();
  }, []);

  const fetchSpending = async () => {
    try {
      const response = await getSpendingSummary();
      const data = response.data;
      
      const total = data.reduce((acc: number, item: any) => acc + Math.abs(item._sum.amount || 0), 0);
      setTotalSpent(total);
      
      // Map colors to categories
      const mappedData = data.map((item: any) => ({
        name: item.category,
        amount: Math.abs(item._sum.amount || 0),
        percentage: total > 0 ? (Math.abs(item._sum.amount || 0) / total) * 100 : 0,
        color: BRAND_COLORS.chart[item.category.toLowerCase() as keyof typeof BRAND_COLORS.chart] || BRAND_COLORS.primary
      }));
      
      setSummary(mappedData);
    } catch (error) {
      console.error('Error fetching spending summary:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={[styles.spendingCard, { backgroundColor: theme.surface }]}>
      <View style={styles.spendingHeader}>
        <ThemedText style={[styles.spendingTitle, { color: theme.text }]}>Spending</ThemedText>
        <TouchableOpacity style={styles.simpleDropdown}>
          <ThemedText style={[styles.simpleDropdownText, { color: theme.textSecondary }]}>This Month</ThemedText>
          <IconSymbol name="chevron.down" size={14} color={theme.textSecondary} />
        </TouchableOpacity>
      </View>
      
      {loading ? (
        <ActivityIndicator size="large" color={BRAND_COLORS.primary} />
      ) : (
        <View style={styles.spendingContent}>
          <View style={styles.leftColumn}>
            <ThemedText style={[styles.totalAmount, { color: theme.text }]}>
              ${totalSpent.toFixed(2)}
            </ThemedText>
            <ThemedText style={[styles.totalLabel, { color: theme.textSecondary }]}>
              Total spent this month
            </ThemedText>
            
            <View style={[styles.barGraphContainer, { backgroundColor: isDark ? '#333' : '#F0F0F0' }]}>
              {summary.map((cat, index) => (
                <View 
                  key={index} 
                  style={[
                    styles.barSegment, 
                    { 
                      backgroundColor: cat.color, 
                      flex: cat.percentage,
                      borderTopLeftRadius: index === 0 ? 6 : 0,
                      borderBottomLeftRadius: index === 0 ? 6 : 0,
                      borderTopRightRadius: index === summary.length - 1 ? 6 : 0,
                      borderBottomRightRadius: index === summary.length - 1 ? 6 : 0,
                    }
                  ]} 
                />
              ))}
            </View>
          </View>

          <View style={styles.rightColumn}>
            {summary.map((cat, index) => (
              <CategoryRow 
                key={index}
                color={cat.color} 
                name={cat.name} 
                amount={`$${cat.amount.toFixed(0)}`} 
                theme={theme} 
                percentage={cat.percentage.toFixed(0) + "%"}
              />
            ))}
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  spendingCard: {
    marginHorizontal: 20,
    marginTop: 20,
    borderRadius: 28,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
  },
  spendingHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  spendingTitle: {
    fontSize: 20,
    fontWeight: '800',
  },
  simpleDropdown: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  simpleDropdownText: {
    fontSize: 14,
    fontWeight: '600',
  },
  spendingContent: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 24,
  },
  leftColumn: {
    flex: 1.2,
    justifyContent: 'center',
  },
  rightColumn: {
    flex: 1,
    gap: 12,
  },
  totalAmount: {
    fontSize: 32,
    fontWeight: '900',
    letterSpacing: -0.5,
  },
  totalLabel: {
    fontSize: 13,
    fontWeight: '600',
    marginTop: 4,
    marginBottom: 20,
  },
  barGraphContainer: {
    height: 12,
    width: '100%',
    borderRadius: 6,
    flexDirection: 'row',
    overflow: 'hidden',
  },
  barSegment: {
    height: '100%',
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  categoryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flex: 1,
  },
  colorDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  categoryName: {
    fontSize: 14,
    fontWeight: '700',
    flex: 1,
  },
  categoryAmount: {
    fontSize: 14,
    fontWeight: '800',
    textAlign: 'right',
  },
});
