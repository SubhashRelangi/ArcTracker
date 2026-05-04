import React, { useState, useEffect } from 'react';
import { StyleSheet, View, TouchableOpacity, TextInput, ActivityIndicator, Alert, ScrollView } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { addTransaction, getAccounts } from '@/services/api';
import { BRAND_COLORS } from '@/constants/theme';

const CATEGORIES = [
  { name: 'Food', icon: 'fast-food' },
  { name: 'Shopping', icon: 'cart' },
  { name: 'Transport', icon: 'bus' },
  { name: 'Housing', icon: 'home' },
  { name: 'Leisure', icon: 'game-controller' },
];

export default function AddExpenseScreen() {
  const { theme, isDark } = useTheme();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('Food');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [accounts, setAccounts] = useState<any[]>([]);
  const [selectedAccountId, setSelectedAccountId] = useState('');

  useEffect(() => {
    fetchAccounts();
  }, []);

  const fetchAccounts = async () => {
    try {
      const response = await getAccounts();
      setAccounts(response.data);
      if (response.data.length > 0) {
        setSelectedAccountId(response.data[0].id);
      }
    } catch (error) {
      console.error('Failed to fetch accounts', error);
    }
  };

  const handleSave = async () => {
    if (!amount || !category || !description || !selectedAccountId) {
      Alert.alert('Error', 'Please fill all fields');
      return;
    }

    setLoading(true);
    try {
      await addTransaction({
        accountId: selectedAccountId,
        amount: -parseFloat(amount),
        category,
        merchant: description,
        subtext: description,
        type: 'EXPENSE',
        date: new Date(),
        logoUrl: 'https://via.placeholder.com/48'
      });
      Alert.alert('Success', 'Expense saved successfully', [
        { text: 'OK', onPress: () => router.back() }
      ]);
    } catch (error: any) {
      console.error('Failed to save expense', error);
      const message = error.response?.data?.error || 'Failed to save expense';
      Alert.alert('Error', message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.background, paddingTop: insets.top }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Ionicons name="arrow-back" size={24} color={theme.text} />
        </TouchableOpacity>
        <ThemedText style={styles.title}>Add Expense</ThemedText>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText style={styles.label}>Amount</ThemedText>
        <TextInput 
          style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.surface }]}
          placeholder="0.00"
          placeholderTextColor={theme.textSecondary}
          keyboardType="numeric"
          value={amount}
          onChangeText={setAmount}
        />

        <ThemedText style={styles.label}>Category</ThemedText>
        <View style={styles.categoryRow}>
          {CATEGORIES.map((cat) => (
            <TouchableOpacity 
              key={cat.name}
              onPress={() => setCategory(cat.name)}
              style={[
                styles.categoryItem, 
                { backgroundColor: theme.surface, borderColor: category === cat.name ? BRAND_COLORS.primary : theme.border }
              ]}
            >
              <Ionicons 
                name={cat.icon as any} 
                size={20} 
                color={category === cat.name ? BRAND_COLORS.primary : theme.textSecondary} 
              />
              <ThemedText style={[styles.categoryText, category === cat.name && { color: BRAND_COLORS.primary }]}>
                {cat.name}
              </ThemedText>
            </TouchableOpacity>
          ))}
        </View>

        <ThemedText style={styles.label}>Description / Merchant</ThemedText>
        <TextInput 
          style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.surface }]}
          placeholder="What did you buy?"
          placeholderTextColor={theme.textSecondary}
          value={description}
          onChangeText={setDescription}
        />

        <ThemedText style={styles.label}>Account</ThemedText>
        <View style={styles.accountRow}>
          {accounts.map((acc) => (
            <TouchableOpacity 
              key={acc.id}
              onPress={() => setSelectedAccountId(acc.id)}
              style={[
                styles.accountItem, 
                { backgroundColor: theme.surface, borderColor: selectedAccountId === acc.id ? acc.color : theme.border }
              ]}
            >
              <ThemedText style={[styles.accountText, selectedAccountId === acc.id && { color: acc.color }]}>
                {acc.bankName}
              </ThemedText>
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity 
          style={[styles.saveButton, { backgroundColor: '#00BAF2' }]} 
          onPress={handleSave}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <ThemedText style={styles.saveButtonText}>Save Expense</ThemedText>
          )}
        </TouchableOpacity>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    height: 60,
  },
  backButton: {
    padding: 8,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
  },
  content: {
    padding: 24,
    gap: 16,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: -8,
  },
  input: {
    height: 56,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    fontSize: 16,
  },
  categoryRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 8,
  },
  categoryItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 2,
    gap: 8,
  },
  categoryText: {
    fontSize: 13,
    fontWeight: '700',
  },
  accountRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 8,
  },
  accountItem: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 2,
  },
  accountText: {
    fontSize: 13,
    fontWeight: '700',
  },
  saveButton: {
    height: 56,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 32,
  },
  saveButtonText: {
    color: '#FFF',
    fontSize: 18,
    fontWeight: '700',
  },
});
