import React, { useState } from 'react';
import { StyleSheet, View, ScrollView, TouchableOpacity } from 'react-native';
import { Image } from 'expo-image';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';

interface BalanceCardProps {
  logo: string;
  bank: string;
  subtitle: string;
  buttonText: string;
  backgroundColor: string;
  theme: any;
  isDotted?: boolean;
}

const BalanceCard: React.FC<BalanceCardProps> = ({ 
  logo, 
  bank, 
  subtitle, 
  buttonText, 
  backgroundColor, 
  theme,
  isDotted = false
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
          <ThemedText style={[styles.bankSubtitle, { color: textColor }]} numberOfLines={2}>{subtitle}</ThemedText>
        </View>
        <View style={styles.bankLogoCircle}>
          <Image source={logo} style={styles.bankLogoSmall} />
        </View>
      </View>
      
      <TouchableOpacity style={styles.actionButton}>
        <ThemedText style={styles.actionButtonText}>{buttonText}</ThemedText>
      </TouchableOpacity>
    </View>
  );
};

interface BanksSectionProps {
  theme: any;
}

export const BanksSection: React.FC<BanksSectionProps> = ({ theme }) => {
  return (
    <View style={styles.section}>
      <ThemedText type="subtitle" style={[styles.sectionTitle, { color: theme.text }]}>Your Accounts</ThemedText>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.balanceCardsContainer}>
        <BalanceCard 
          logo="https://upload.wikimedia.org/wikipedia/commons/thumb/c/cc/State_Bank_of_India_logo.svg/1024px-State_Bank_of_India_logo.svg.png"
          bank="SBI Bank" 
          subtitle="A/c No: 6828" 
          buttonText="Check Balance"
          backgroundColor="#0052B4"
          theme={theme}
        />
        <BalanceCard 
          logo="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR0Y64iS0m_9f_1x_0Y-5_0_K_W_k_9_9_9_9&s"
          bank="UPI Lite" 
          subtitle="2x Gold Coins on all payments" 
          buttonText="Activate"
          backgroundColor="#41A4F4"
          theme={theme}
        />
        <BalanceCard 
          logo="https://logo.clearbit.com/paytm.com"
          bank="Paytm" 
          subtitle="Wallet Balance: $0" 
          buttonText="Add Money"
          backgroundColor="#fff"
          isDotted={true}
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

