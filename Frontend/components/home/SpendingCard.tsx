import React from 'react';
import { StyleSheet, View, TouchableOpacity } from 'react-native';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';

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
        <ThemedText style={[styles.categoryName, { color: theme.text }]}>{name}</ThemedText>
      </View>
      <ThemedText style={[styles.categoryPercentage, { color: theme.textSecondary }]}>{percentage}</ThemedText>
      <ThemedText style={[styles.categoryAmount, { color: theme.text }]}>{amount}</ThemedText>
    </View>
  );
};

interface SpendingCardProps {
  theme: any;
  isDark: boolean;
}

export const SpendingCard: React.FC<SpendingCardProps> = ({ theme, isDark }) => {
  return (
    <View style={[styles.spendingCard, { backgroundColor: theme.surface }]}>
      <View style={styles.spendingHeader}>
        <ThemedText style={[styles.spendingTitle, { color: theme.text }]}>Spending</ThemedText>
        <TouchableOpacity style={styles.simpleDropdown}>
          <ThemedText style={[styles.simpleDropdownText, { color: theme.textSecondary }]}>March 2024</ThemedText>
          <IconSymbol name="chevron.down" size={14} color={theme.textSecondary} />
        </TouchableOpacity>
      </View>
      
      <View style={styles.spendingContent}>
        <View style={styles.amountContainer}>
          <ThemedText style={[styles.totalAmount, { color: theme.text }]} adjustsFontSizeToFit={false}>
            $6,870.45
          </ThemedText>
        </View>
        <ThemedText style={[styles.totalLabel, { color: theme.textSecondary }]}>Total Tracked Amount</ThemedText>
        <ThemedText style={[styles.monthLabel, { color: theme.textSecondary }]}>March 2024</ThemedText>
        
        <View style={styles.chartWrapper}>
          <View style={[styles.donutBase, { backgroundColor: isDark ? '#333' : '#E8ECEF' }]}>
             <View style={[styles.segment, { backgroundColor: BRAND_COLORS.chart.housing, transform: [{ rotate: '0deg' }] }]} />
             <View style={[styles.segment, { backgroundColor: BRAND_COLORS.chart.food, transform: [{ rotate: '126deg' }] }]} />
             <View style={[styles.segment, { backgroundColor: BRAND_COLORS.chart.transport, transform: [{ rotate: '205deg' }] }]} />
             <View style={[styles.segment, { backgroundColor: BRAND_COLORS.chart.shopping, transform: [{ rotate: '270deg' }] }]} />
             <View style={[styles.segment, { backgroundColor: BRAND_COLORS.chart.leisure, transform: [{ rotate: '324deg' }] }]} />
             
             <View style={[styles.donutInner, { backgroundColor: theme.surface }]}>
               <ThemedText style={[styles.expendedAmount, { color: theme.text }]}>$6,870.45</ThemedText>
               <ThemedText style={[styles.expendedLabel, { color: theme.textSecondary }]}>Expended</ThemedText>
             </View>
          </View>
        </View>

        <View style={styles.categoryList}>
          <CategoryRow color={BRAND_COLORS.chart.housing} name="Housing" percentage="35%" amount="$2,404.66" theme={theme} />
          <CategoryRow color={BRAND_COLORS.chart.food} name="Food & Drink" percentage="22%" amount="$1,511.50" theme={theme} />
          <CategoryRow color={BRAND_COLORS.chart.transport} name="Transport" percentage="18%" amount="$1,236.68" theme={theme} />
          <CategoryRow color={BRAND_COLORS.chart.shopping} name="Shopping" percentage="15%" amount="$1,030.57" theme={theme} />
          <CategoryRow color={BRAND_COLORS.chart.leisure} name="Leisure" percentage="10%" amount="$687.05" theme={theme} />
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  spendingCard: {
    marginHorizontal: 20,
    marginTop: 20,
    borderRadius: 24,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.1,
    shadowRadius: 15,
    elevation: 5,
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
    alignItems: 'center',
  },
  amountContainer: {
    width: '100%',
    alignItems: 'center',
    paddingVertical: 4,
  },
  totalAmount: {
    fontSize: 28,
    fontWeight: '900',
    marginBottom: 4,
    includeFontPadding: false,
  },
  totalLabel: {
    fontSize: 14,
    fontWeight: '700',
    marginBottom: 2,
  },
  monthLabel: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 32,
  },
  chartWrapper: {
    width: 220,
    height: 220,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 40,
  },
  donutBase: {
    width: 200,
    height: 200,
    borderRadius: 100,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  segment: {
    position: 'absolute',
    width: '100%',
    height: '100%',
    borderRadius: 100,
    borderWidth: 25,
    borderColor: 'transparent',
    borderTopColor: 'inherit',
  },
  donutInner: {
    width: 150,
    height: 150,
    borderRadius: 75,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 2,
  },
  expendedAmount: {
    fontSize: 20,
    fontWeight: '900',
  },
  expendedLabel: {
    fontSize: 12,
    fontWeight: '700',
    marginTop: 2,
  },
  categoryList: {
    width: '100%',
    gap: 16,
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  categoryLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    flex: 2,
  },
  colorDot: {
    width: 14,
    height: 14,
    borderRadius: 5,
  },
  categoryName: {
    fontSize: 15,
    fontWeight: '700',
  },
  categoryPercentage: {
    fontSize: 14,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
  },
  categoryAmount: {
    fontSize: 15,
    fontWeight: '800',
    flex: 1.5,
    textAlign: 'right',
  },
});
