import React, { useState } from 'react';
import { StyleSheet, View, TouchableOpacity, TextInput, ActivityIndicator, Alert, ScrollView } from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { useTheme } from '@/hooks/use-theme';
import { addAccount } from '@/services/api';

const BANK_PRESETS = [
  { name: 'Chase', color: '#117aca', logo: 'https://logo.clearbit.com/chase.com' },
  { name: 'Wells Fargo', color: '#d71e28', logo: 'https://logo.clearbit.com/wellsfargo.com' },
  { name: 'Bank of America', color: '#0067b1', logo: 'https://logo.clearbit.com/bankofamerica.com' },
  { name: 'Paytm', color: '#00BAF2', logo: 'https://logo.clearbit.com/paytm.com' },
];

export default function LinkAccountScreen() {
  const { theme, isDark } = useTheme();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [bankName, setBankName] = useState('');
  const [accNo, setAccNo] = useState('');
  const [balance, setBalance] = useState('');
  const [selectedPreset, setSelectedPreset] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const handlePresetSelect = (preset: any) => {
    setSelectedPreset(preset);
    setBankName(preset.name);
  };

  const handleLink = async () => {
    if (!bankName || !accNo || !balance) {
      Alert.alert('Error', 'Please fill all fields');
      return;
    }

    setLoading(true);
    try {
      await addAccount({
        bankName,
        accountNumber: accNo,
        balance: parseFloat(balance),
        color: selectedPreset?.color || '#00BAF2',
        logoUrl: selectedPreset?.logo || 'https://via.placeholder.com/48'
      });
      Alert.alert('Success', 'Bank account linked successfully', [
        { text: 'OK', onPress: () => router.back() }
      ]);
    } catch (error) {
      console.error('Failed to link account', error);
      Alert.alert('Error', 'Failed to link bank account');
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
        <ThemedText style={styles.title}>Link Bank Account</ThemedText>
        <View style={{ width: 40 }} />
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <ThemedText style={styles.label}>Select Bank</ThemedText>
        <View style={styles.presetsRow}>
          {BANK_PRESETS.map((bank) => (
            <TouchableOpacity 
              key={bank.name}
              onPress={() => handlePresetSelect(bank)}
              style={[
                styles.presetItem, 
                { backgroundColor: theme.surface, borderColor: selectedPreset?.name === bank.name ? bank.color : theme.border }
              ]}
            >
              <ThemedText style={[styles.presetText, selectedPreset?.name === bank.name && { color: bank.color }]}>
                {bank.name}
              </ThemedText>
            </TouchableOpacity>
          ))}
        </View>

        <ThemedText style={styles.label}>Bank Name</ThemedText>
        <TextInput 
          style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.surface }]}
          placeholder="e.g., SBI Bank"
          placeholderTextColor={theme.textSecondary}
          value={bankName}
          onChangeText={setBankName}
        />

        <ThemedText style={styles.label}>Account Number (Last 4 digits)</ThemedText>
        <TextInput 
          style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.surface }]}
          placeholder="e.g., 6828"
          placeholderTextColor={theme.textSecondary}
          keyboardType="numeric"
          maxLength={4}
          value={accNo}
          onChangeText={setAccNo}
        />

        <ThemedText style={styles.label}>Current Balance</ThemedText>
        <TextInput 
          style={[styles.input, { color: theme.text, borderColor: theme.border, backgroundColor: theme.surface }]}
          placeholder="0.00"
          placeholderTextColor={theme.textSecondary}
          keyboardType="numeric"
          value={balance}
          onChangeText={setBalance}
        />

        <TouchableOpacity 
          style={[styles.saveButton, { backgroundColor: selectedPreset?.color || '#00BAF2' }]} 
          onPress={handleLink}
          disabled={loading}
        >
          {loading ? (
            <ActivityIndicator color="#FFF" />
          ) : (
            <ThemedText style={styles.saveButtonText}>Link Account</ThemedText>
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
  presetsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 8,
  },
  presetItem: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 2,
  },
  presetText: {
    fontSize: 13,
    fontWeight: '700',
  },
  input: {
    height: 56,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    fontSize: 16,
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
