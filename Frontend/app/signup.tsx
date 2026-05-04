import React, { useState } from 'react';
import { 
  StyleSheet, 
  View, 
  TextInput, 
  TouchableOpacity, 
  KeyboardAvoidingView, 
  Platform, 
  ScrollView,
  SafeAreaView,
  Dimensions,
  StatusBar,
  Alert,
  ActivityIndicator
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import Animated, { FadeInUp, FadeInDown } from 'react-native-reanimated';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { BRAND_COLORS } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ThemedText } from '@/components/themed-text';
import { signup } from '@/services/api';

const { height } = Dimensions.get('window');

export default function SignupScreen() {
  const router = useRouter();
  const { theme, isDark } = useTheme();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSignup = async () => {
    if (!fullName || !email || !password) {
      Alert.alert('Error', 'Please fill all fields');
      return;
    }

    setLoading(true);
    try {
      const response = await signup({ name: fullName, email, password });
      await AsyncStorage.setItem('token', response.data.token);
      await AsyncStorage.setItem('user', JSON.stringify(response.data.user));
      router.replace('/(tabs)');
    } catch (error: any) {
      console.error('Signup failed', error);
      const message = error.response?.data?.error || 'Signup failed. Please try again.';
      Alert.alert('Error', message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <StatusBar barStyle={isDark ? "light-content" : "dark-content"} />
      <KeyboardAvoidingView 
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={{ flex: 1 }}
      >
        <ScrollView 
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
        >
          <Animated.View 
            entering={FadeInDown.delay(200).duration(1000)}
            style={[styles.headerGradient, { backgroundColor: BRAND_COLORS.secondary }]}
          >
            <TouchableOpacity 
              style={styles.backButton}
              onPress={() => router.back()}
            >
              <Ionicons name="arrow-back" size={24} color="#FFF" />
            </TouchableOpacity>
            <View style={styles.headerContent}>
              <View style={styles.logoCircle}>
                <Ionicons name="person-add" size={40} color="#FFF" />
              </View>
              <ThemedText style={styles.headerTitle}>Create Account</ThemedText>
              <ThemedText style={styles.headerSubtitle}>Join ArcTracker today</ThemedText>
            </View>
          </Animated.View>

          <View style={[styles.formContainer, { backgroundColor: theme.background }]}>
            <Animated.View entering={FadeInUp.delay(400).duration(1000)}>
              <ThemedText type="title" style={styles.welcomeText}>Get Started</ThemedText>
              <ThemedText style={styles.subWelcomeText}>Create your account to manage expenses</ThemedText>

              <View style={styles.inputGroup}>
                <ThemedText style={styles.inputLabel}>Full Name</ThemedText>
                <View style={[styles.inputWrapper, { backgroundColor: isDark ? '#1E1E1E' : '#F5F5F5' }]}>
                  <Ionicons name="person-outline" size={20} color={theme.icon} style={styles.inputIcon} />
                  <TextInput
                    style={[styles.input, { color: theme.text }]}
                    placeholder="Enter your full name"
                    placeholderTextColor={theme.icon}
                    value={fullName}
                    onChangeText={setFullName}
                  />
                </View>
              </View>

              <View style={styles.inputGroup}>
                <ThemedText style={styles.inputLabel}>Email Address</ThemedText>
                <View style={[styles.inputWrapper, { backgroundColor: isDark ? '#1E1E1E' : '#F5F5F5' }]}>
                  <Ionicons name="mail-outline" size={20} color={theme.icon} style={styles.inputIcon} />
                  <TextInput
                    style={[styles.input, { color: theme.text }]}
                    placeholder="Enter your email"
                    placeholderTextColor={theme.icon}
                    value={email}
                    onChangeText={setEmail}
                    keyboardType="email-address"
                    autoCapitalize="none"
                  />
                </View>
              </View>

              <View style={styles.inputGroup}>
                <ThemedText style={styles.inputLabel}>Password</ThemedText>
                <View style={[styles.inputWrapper, { backgroundColor: isDark ? '#1E1E1E' : '#F5F5F5' }]}>
                  <Ionicons name="lock-closed-outline" size={20} color={theme.icon} style={styles.inputIcon} />
                  <TextInput
                    style={[styles.input, { color: theme.text }]}
                    placeholder="Create a password"
                    placeholderTextColor={theme.icon}
                    value={password}
                    onChangeText={setPassword}
                    secureTextEntry={!showPassword}
                  />
                  <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
                    <Ionicons 
                      name={showPassword ? "eye-off-outline" : "eye-outline"} 
                      size={20} 
                      color={theme.icon} 
                    />
                  </TouchableOpacity>
                </View>
              </View>

              <TouchableOpacity 
                style={[styles.signupButton, { backgroundColor: BRAND_COLORS.secondary }]}
                onPress={handleSignup}
                disabled={loading}
              >
                {loading ? <ActivityIndicator color="#FFF" /> : <ThemedText style={styles.signupButtonText}>Sign Up</ThemedText>}
              </TouchableOpacity>

              <View style={styles.loginContainer}>
                <ThemedText>Already have an account? </ThemedText>
                <TouchableOpacity onPress={() => router.push('/login')}>
                  <ThemedText style={[styles.loginText, { color: BRAND_COLORS.secondary }]}>Log In</ThemedText>
                </TouchableOpacity>
              </View>
            </Animated.View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
  },
  headerGradient: {
    height: height * 0.3,
    justifyContent: 'center',
    alignItems: 'center',
    borderBottomLeftRadius: 40,
    borderBottomRightRadius: 40,
  },
  backButton: {
    position: 'absolute',
    top: 20,
    left: 20,
    zIndex: 10,
    padding: 8,
  },
  headerContent: {
    alignItems: 'center',
  },
  logoCircle: {
    width: 70,
    height: 70,
    borderRadius: 35,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#FFF',
  },
  headerSubtitle: {
    fontSize: 14,
    color: 'rgba(255, 255, 255, 0.8)',
    marginTop: 4,
  },
  formContainer: {
    flex: 1,
    marginTop: -40,
    borderTopLeftRadius: 40,
    borderTopRightRadius: 40,
    paddingHorizontal: 24,
    paddingTop: 40,
    paddingBottom: 24,
  },
  welcomeText: {
    fontSize: 26,
    marginBottom: 8,
  },
  subWelcomeText: {
    fontSize: 15,
    color: '#888',
    marginBottom: 24,
  },
  inputGroup: {
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
    marginLeft: 4,
  },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 16,
    paddingHorizontal: 16,
    height: 56,
  },
  inputIcon: {
    marginRight: 12,
  },
  input: {
    flex: 1,
    fontSize: 16,
    height: '100%',
  },
  signupButton: {
    height: 56,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
    shadowColor: BRAND_COLORS.secondary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  signupButtonText: {
    color: '#FFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
  loginContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 24,
    marginBottom: 20,
  },
  loginText: {
    fontWeight: 'bold',
  },
});
