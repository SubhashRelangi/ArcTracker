import React, { useState } from 'react';
import { StyleSheet, View, ScrollView, TouchableOpacity } from 'react-native';
import { Image } from 'expo-image';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';

interface BalanceCardProps {
  logo: string;
  bank: string;
  balance: string;
  accentColor: string;
  theme: any;
}

const BalanceCard: React.FC<BalanceCardProps> = ({ logo, bank, balance, accentColor, theme }) => {
  const [isVisible, setIsVisible] = useState(false);

  return (
    <View style={[styles.balanceCard, { backgroundColor: theme.surface, borderTopColor: accentColor, borderTopWidth: 4 }]}>
      <View style={styles.cardHeader}>
        <View style={[styles.bankLogoContainer, { backgroundColor: theme.background }]}>
          <Image source={logo} style={styles.bankLogoSmall} />
        </View>
        <ThemedText style={[styles.bankName, { color: theme.textSecondary }]} numberOfLines={1}>{bank}</ThemedText>
      </View>
      
      <View style={styles.balanceContainer}>
        {isVisible ? (
          <ThemedText style={[styles.bankBalance, { color: theme.text }]}>{balance}</ThemedText>
        ) : (
          <TouchableOpacity onPress={() => setIsVisible(true)}>
            <ThemedText style={[styles.checkBalanceLink, { color: BRAND_COLORS.primary }]}>Check Balance</ThemedText>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
};

interface BanksSectionProps {
  theme: any;
}

export const BanksSection: React.FC<BanksSectionProps> = ({ theme }) => {
  return (
    <View style={styles.section}>
      <ThemedText type="subtitle" style={[styles.sectionTitle, { color: theme.text }]}>Banks</ThemedText>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.balanceCardsContainer}>
        <BalanceCard 
          logo="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSygESUAPeJMMX06_1CjwAOTU_JKXt1Hw63-w&s"
          bank="Chase Freedom" 
          balance="$4,120.30" 
          accentColor={BRAND_COLORS.chase}
          theme={theme}
        />
        <BalanceCard 
          logo="https://logo.clearbit.com/wellsfargo.com"
          bank="Wells Fargo" 
          balance="$2,450.15" 
          accentColor={BRAND_COLORS.wellsFargo}
          theme={theme}
        />
        <BalanceCard 
          logo="https://logo.clearbit.com/americanexpress.com"
          bank="AMEX Gold" 
          balance="$1,300.00" 
          accentColor={BRAND_COLORS.amex}
          theme={theme}
        />
      </ScrollView>
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
  balanceCardsContainer: {
    paddingHorizontal: 20,
    gap: 16,
    paddingBottom: 8,
  },
  balanceCard: {
    padding: 16,
    borderRadius: 24,
    width: 170,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
    justifyContent: 'space-between',
    minHeight: 140,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 12,
  },
  bankLogoContainer: {
    width: 32,
    height: 32,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bankLogoSmall: {
    width: 20,
    height: 20,
    borderRadius: 4,
  },
  bankName: {
    fontSize: 13,
    fontWeight: '700',
    flex: 1,
  },
  balanceContainer: {
    marginVertical: 12,
    alignItems: 'flex-start',
  },
  bankBalance: {
    fontSize: 18,
    fontWeight: '800',
  },
  checkBalanceLink: {
    fontSize: 14,
    fontWeight: '700',
    textDecorationLine: 'underline',
  },
});
