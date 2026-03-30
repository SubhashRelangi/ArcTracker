import React from 'react';
import { StyleSheet, View, TouchableOpacity } from 'react-native';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ThemedText } from '@/components/themed-text';

interface ActionCardProps {
  icon: any;
  title: string;
  description: string;
  buttonText: string;
  iconBg: string;
  theme: any;
}

const ActionCard: React.FC<ActionCardProps> = ({ icon, title, description, buttonText, iconBg, theme }) => {
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
};

interface ActionCardsProps {
  theme: any;
  BRAND_COLORS: any;
}

export const ActionCards: React.FC<ActionCardsProps> = ({ theme, BRAND_COLORS }) => {
  return (
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
  );
};

const styles = StyleSheet.create({
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
