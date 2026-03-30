import { Image } from 'expo-image';
import { StyleSheet, ScrollView, View, TouchableOpacity, Platform, StatusBar as RNStatusBar } from 'react-native';

import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';
import { Colors } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';

const BRAND_COLORS = {
  primary: '#5E72E4', // Vibrant Blue
  secondary: '#825EE4', // Purple
  accent1: '#FB6340', // Orange/Coral
  chase: '#117aca',
  wellsFargo: '#d71e28',
  amex: '#c3a334',
};

export default function HomeScreen() {
  const colorScheme = useColorScheme() ?? 'light';
  const isDark = colorScheme === 'dark';
  
  const theme = {
    background: isDark ? '#121212' : '#F8F9FE',
    surface: isDark ? '#1E1E1E' : '#FFFFFF',
    text: isDark ? '#FFFFFF' : '#1A1C1E',
    textSecondary: isDark ? '#A0A0A0' : '#666666',
    border: isDark ? '#333333' : '#F0F2F5',
    cardShadow: isDark ? 'rgba(0,0,0,0.5)' : 'rgba(0,0,0,0.05)',
  };

  return (
    <View style={[styles.mainContainer, { backgroundColor: theme.background }]}>
      <RNStatusBar barStyle={isDark ? "light-content" : "dark-content"} />
      <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
        {/* Header */}
        <View style={[styles.header, { backgroundColor: theme.surface, borderBottomColor: theme.border }]}>
          <View style={styles.headerLeft}>
            <Image
              source="https://i.pravatar.cc/150?u=assetrak"
              style={[styles.avatar, { borderColor: BRAND_COLORS.primary }]}
            />
            <ThemedText style={[styles.appName, { color: BRAND_COLORS.primary }]}>ASSETRAK</ThemedText>
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

        {/* Title and Date Picker */}
        <View style={styles.titleSection}>
          <ThemedText type="title" style={[styles.pageTitle, { color: theme.text }]}>Home page</ThemedText>
          <TouchableOpacity style={[styles.datePicker, { backgroundColor: theme.surface }]}>
            <ThemedText style={[styles.dateText, { color: theme.textSecondary }]}>March 2024</ThemedText>
            <IconSymbol name="chevron.down" size={18} color={BRAND_COLORS.primary} />
          </TouchableOpacity>
        </View>

        {/* Bank Balances */}
        <View style={styles.section}>
          <ThemedText type="subtitle" style={[styles.sectionTitle, { color: theme.text }]}>Bank Balances</ThemedText>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.balanceCardsContainer}>
            <BalanceCard 
              bank="Chase Freedom" 
              balance="$4,120.30" 
              lastSync="16:33" 
              accentColor={BRAND_COLORS.chase}
              theme={theme}
            />
            <BalanceCard 
              bank="Wells Fargo" 
              balance="$2,450.15" 
              lastSync="16:38" 
              accentColor={BRAND_COLORS.wellsFargo}
              theme={theme}
            />
            <BalanceCard 
              bank="AMEX Gold" 
              balance="$1,300.00" 
              lastSync="16:33" 
              accentColor={BRAND_COLORS.amex}
              theme={theme}
            />
          </ScrollView>
        </View>

        {/* Recent Activity */}
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

        {/* Action Cards */}
        <View style={styles.actionCardsRow}>
          <ActionCard 
            icon="pencil" 
            title="Manual Entry" 
            description="Track cash & expenses" 
            buttonText="Add Expense" 
            iconBg={BRAND_COLORS.accent1}
            theme={theme}
          />
          <ActionCard 
            icon="arrow.2.circlepath" 
            title="Bank Sync" 
            description="Automatic updates" 
            buttonText="Manage" 
            iconBg={BRAND_COLORS.secondary}
            theme={theme}
          />
        </View>
      </ScrollView>
    </View>
  );
}

function BalanceCard({ bank, balance, lastSync, accentColor, theme }: { bank: string, balance: string, lastSync: string, accentColor: string, theme: any }) {
  return (
    <View style={[styles.balanceCard, { backgroundColor: theme.surface, borderTopColor: accentColor, borderTopWidth: 4 }]}>
      <ThemedText style={[styles.bankName, { color: theme.textSecondary }]}>{bank}</ThemedText>
      <ThemedText style={[styles.bankBalance, { color: theme.text }]}>{balance}</ThemedText>
      <ThemedText style={[styles.lastSync, { color: theme.textSecondary }]}>Last sync: {lastSync}</ThemedText>
      <TouchableOpacity style={[styles.checkBalanceButton, { borderColor: accentColor }]}>
        <ThemedText style={[styles.checkBalanceText, { color: accentColor }]}>Check Balance</ThemedText>
      </TouchableOpacity>
    </View>
  );
}

function ActivityItem({ logo, name, subtext, amount, category, theme }: { logo: string, name: string, subtext: string, amount: string, category: string, theme: any }) {
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
}

function ActionCard({ icon, title, description, buttonText, iconBg, theme }: { icon: any, title: string, description: string, buttonText: string, iconBg: string, theme: any }) {
  return (
    <View style={[styles.actionCard, { backgroundColor: theme.surface }]}>
      <View style={[styles.actionIconContainer, { backgroundColor: iconBg + '20' }]}>
        <IconSymbol name={icon} size={22} color={iconBg} />
      </View>
      <ThemedText style={[styles.actionTitle, { color: theme.text }]}>{title}</ThemedText>
      <ThemedText style={[styles.actionDescription, { color: theme.textSecondary }]}>{description}</ThemedText>
      <TouchableOpacity style={[styles.actionButton, { backgroundColor: iconBg }]}>
        <ThemedText style={styles.actionButtonText}>{buttonText}</ThemedText>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  mainContainer: {
    flex: 1,
  },
  container: {
    flex: 1,
  },
  contentContainer: {
    paddingBottom: 40,
  },
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
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    borderWidth: 2,
  },
  appName: {
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: -0.5,
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
  titleSection: {
    paddingHorizontal: 20,
    paddingVertical: 20,
  },
  pageTitle: {
    fontSize: 28,
    fontWeight: '800',
    marginBottom: 12,
  },
  datePicker: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 12,
    alignSelf: 'flex-start',
    gap: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.05,
    shadowRadius: 5,
    elevation: 2,
  },
  dateText: {
    fontSize: 15,
    fontWeight: '600',
  },
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
    padding: 20,
    borderRadius: 24,
    width: 160,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
  },
  bankName: {
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 6,
  },
  bankBalance: {
    fontSize: 20,
    fontWeight: '800',
    marginBottom: 4,
  },
  lastSync: {
    fontSize: 11,
    marginBottom: 16,
  },
  checkBalanceButton: {
    borderWidth: 1.5,
    borderRadius: 12,
    paddingVertical: 8,
    alignItems: 'center',
  },
  checkBalanceText: {
    fontSize: 12,
    fontWeight: '700',
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
    color: '#FF5252', // Brighter red for dark mode visibility
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
  actionCardsRow: {
    flexDirection: 'row',
    paddingHorizontal: 20,
    marginTop: 28,
    gap: 16,
  },
  actionCard: {
    flex: 1,
    padding: 20,
    borderRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
    alignItems: 'flex-start',
  },
  actionIconContainer: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  actionTitle: {
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 4,
  },
  actionDescription: {
    fontSize: 12,
    marginBottom: 20,
    lineHeight: 16,
  },
  actionButton: {
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 16,
    width: '100%',
    alignItems: 'center',
  },
  actionButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFF',
  },
});
