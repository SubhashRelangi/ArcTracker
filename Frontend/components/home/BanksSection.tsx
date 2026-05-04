import React, { useEffect, useState } from 'react';
import { StyleSheet, View, ScrollView, TouchableOpacity, ActivityIndicator } from 'react-native';
import { useRouter } from 'expo-router';
import { Image } from 'expo-image';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';
import { getAccounts } from '@/services/api';

interface BalanceCardProps {
  logo: string;
  bank: string;
  subtitle: string;
  balance: number;
  buttonText: string;
  backgroundColor: string;
  theme: any;
  isDotted?: boolean;
  onPress?: () => void;
}

const BalanceCard: React.FC<BalanceCardProps> = ({ 
  logo, 
  bank, 
  subtitle, 
  balance,
  buttonText, 
  backgroundColor, 
  theme,
  isDotted = false,
  onPress
}) => {
  const textColor = isDotted ? theme.text : '#FFFFFF';
  
  return (
    <View style={[
      styles.balanceCard, 
      { backgroundColor: isDotted ? 'transparent' : backgroundColor },
      isDotted && [styles.dottedCard, { borderColor: theme.icon + '40' }]
    ]}>
      <View style={styles.cardTopRow}>
        <View style={styles.cardTextContainer}>
          <ThemedText style={[styles.bankName, { color: textColor }]} numberOfLines={1}>{bank}</ThemedText>
          <ThemedText style={[styles.bankSubtitle, { color: textColor }]} numberOfLines={2}>
            {isDotted ? subtitle : `Balance: $${balance.toFixed(2)}`}
          </ThemedText>
          {!isDotted && <ThemedText style={[styles.bankAccNo, { color: textColor }]}>{subtitle}</ThemedText>}
        </View>
        <View style={styles.bankLogoCircle}>
          <Image source={logo} style={styles.bankLogoSmall} />
        </View>
      </View>
      
      <TouchableOpacity style={styles.actionButton} onPress={onPress}>
        <ThemedText style={styles.actionButtonText}>{buttonText}</ThemedText>
      </TouchableOpacity>
    </View>
  );
};

interface BanksSectionProps {
  theme: any;
}

export const BanksSection: React.FC<BanksSectionProps> = ({ theme }) => {
  const router = useRouter();
  const [accounts, setAccounts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchAccounts();
  }, []);

  const fetchAccounts = async () => {
    try {
      const response = await getAccounts();
      setAccounts(response.data);
    } catch (error) {
      console.error('Error fetching accounts:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.section}>
      <ThemedText type="subtitle" style={[styles.sectionTitle, { color: theme.text }]}>Your Accounts</ThemedText>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.balanceCardsContainer}>
        {loading ? (
          <ActivityIndicator size="large" color={BRAND_COLORS.primary} style={{ padding: 40 }} />
        ) : (
          accounts.map((acc) => (
            <BalanceCard 
              key={acc.id}
              logo={acc.logoUrl}
              bank={acc.bankName} 
              subtitle={`A/c No: ${acc.accountNumber}`} 
              balance={acc.balance}
              buttonText="Check Balance"
              backgroundColor={acc.color}
              theme={theme}
            />
          ))
        )}
        
        <BalanceCard 
          logo="https://logo.clearbit.com/paytm.com"
          bank="Add Account" 
          subtitle="Link a new bank account" 
          balance={0}
          buttonText="Link Now"
          backgroundColor="#fff"
          isDotted={true}
          theme={theme}
          onPress={() => router.push('/link-account')}
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
    fontSize: 24,
    fontWeight: '800',
    marginBottom: 20,
  },
  balanceCardsContainer: {
    paddingHorizontal: 20,
    gap: 12,
    paddingBottom: 8,
  },
  balanceCard: {
    padding: 20,
    borderRadius: 32,
    width: 230,
    height: 190,
    justifyContent: 'space-between',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
  },
  dottedCard: {
    borderWidth: 2,
    borderColor: '#ccc',
    borderStyle: 'dashed',
    backgroundColor: 'transparent',
    elevation: 0,
    shadowOpacity: 0,
  },
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  cardTextContainer: {
    flex: 1,
    marginRight: 8,
  },
  bankName: {
    fontSize: 22,
    fontWeight: '800',
    color: '#FFFFFF',
    marginBottom: 4,
  },
  bankSubtitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
    opacity: 0.9,
    lineHeight: 20,
  },
  bankAccNo: {
    fontSize: 13,
    fontWeight: '500',
    color: '#FFFFFF',
    opacity: 0.7,
    marginTop: 4,
  },
  bankLogoCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  bankLogoSmall: {
    width: 28,
    height: 28,
    contentFit: 'contain',
  },
  actionButton: {
    backgroundColor: '#D1E6FF',
    paddingVertical: 12,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionButtonText: {
    color: '#000000',
    fontSize: 16,
    fontWeight: '700',
  },
});
