import React, { useState, useEffect } from 'react';
import { StyleSheet, View, TextInput, FlatList, ActivityIndicator, TouchableOpacity, SafeAreaView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useTheme } from '@/hooks/use-theme';
import { ThemedText } from '@/components/themed-text';
import { BRAND_COLORS } from '@/constants/theme';
import { getTransactions } from '@/services/api';

const TransactionItem = ({ item, theme }: { item: any, theme: any }) => {
  const isExpense = item.amount < 0;
  
  return (
    <View style={[styles.transactionItem, { borderBottomColor: theme.border }]}>
      <View style={[styles.logoContainer, { backgroundColor: theme.surface }]}>
        <Image 
          source={item.logoUrl || 'https://via.placeholder.com/48'} 
          style={styles.logo} 
        />
      </View>
      <View style={styles.details}>
        <ThemedText style={styles.merchant}>{item.merchant}</ThemedText>
        <ThemedText style={[styles.date, { color: theme.textSecondary }]}>
          {new Date(item.date).toLocaleDateString()} • {item.category}
        </ThemedText>
      </View>
      <View style={styles.amountContainer}>
        <ThemedText style={[styles.amount, { color: isExpense ? '#FF5252' : '#4CAF50' }]}>
          {isExpense ? '-' : '+'}${Math.abs(item.amount).toFixed(2)}
        </ThemedText>
      </View>
    </View>
  );
};

export default function SearchScreen() {
  const { theme, isDark } = useTheme();
  const [search, setSearch] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const delayDebounceFn = setTimeout(() => {
      fetchResults();
    }, 500);

    return () => clearTimeout(delayDebounceFn);
  }, [search]);

  const fetchResults = async () => {
    setLoading(true);
    try {
      const response = await getTransactions(search);
      setResults(response.data);
    } catch (error) {
      console.error('Error searching transactions:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <View style={styles.header}>
        <ThemedText type="title" style={styles.title}>Search</ThemedText>
        <View style={[styles.searchBar, { backgroundColor: theme.surface, borderColor: theme.border }]}>
          <Ionicons name="search" size={20} color={theme.textSecondary} style={styles.searchIcon} />
          <TextInput
            style={[styles.searchInput, { color: theme.text }]}
            placeholder="Search merchants or categories..."
            placeholderTextColor={theme.textSecondary}
            value={search}
            onChangeText={setSearch}
            autoFocus
          />
          {search.length > 0 && (
            <TouchableOpacity onPress={() => setSearch('')}>
              <Ionicons name="close-circle" size={20} color={theme.textSecondary} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color={BRAND_COLORS.primary} />
        </View>
      ) : results.length > 0 ? (
        <FlatList
          data={results}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => <TransactionItem item={item} theme={theme} />}
          contentContainerStyle={styles.listContent}
          showsVerticalScrollIndicator={false}
        />
      ) : (
        <View style={styles.center}>
          <Ionicons name="receipt-outline" size={64} color={theme.border} />
          <ThemedText style={[styles.emptyText, { color: theme.textSecondary }]}>
            {search ? 'No transactions found' : 'Start typing to search...'}
          </ThemedText>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    padding: 20,
    paddingTop: 40,
  },
  title: {
    fontSize: 28,
    fontWeight: '800',
    marginBottom: 20,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    height: 56,
    borderRadius: 16,
    borderWidth: 1,
  },
  searchIcon: {
    marginRight: 12,
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
    height: '100%',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  listContent: {
    paddingHorizontal: 20,
    paddingBottom: 40,
  },
  transactionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  logoContainer: {
    width: 48,
    height: 48,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  logo: {
    width: 32,
    height: 32,
    borderRadius: 8,
  },
  details: {
    flex: 1,
  },
  merchant: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 4,
  },
  date: {
    fontSize: 13,
  },
  amountContainer: {
    alignItems: 'flex-end',
  },
  amount: {
    fontSize: 16,
    fontWeight: '800',
  },
  emptyText: {
    fontSize: 16,
    marginTop: 16,
    fontWeight: '500',
  },
});
