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
  StatusBar
} from 'react-native';
import { useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import Animated, { FadeInUp, FadeInDown } from 'react-native-reanimated';

import { BRAND_COLORS } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ThemedText } from '@/components/themed-text';

const { height } = Dimensions.get('window');

export default function LoginScreen() {
  const router = useRouter();
  const { theme, isDark } = useTheme();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = () => {
    // Navigate to homepage for now
    router.replace('/(tabs)');
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
          {/* Header Image/Gradient Placeholder Area */}
          <Animated.View 
            entering={FadeInDown.delay(200).duration(1000)}
            style={[styles.headerGradient, { backgroundColor: BRAND_COLORS.primary }]}
          >
            <View style={styles.headerContent}>
              <View style={styles.logoCircle}>
                <Ionicons name="wallet" size={40} color="#FFF" />
              </View>
              <ThemedText style={styles.headerTitle}>ArcTracker</ThemedText>
              <ThemedText style={styles.headerSubtitle}>Manage your expenses effortlessly</ThemedText>
            </View>
          </Animated.View>

          <View style={[styles.formContainer, { backgroundColor: theme.background }]}>
            <Animated.View entering={FadeInUp.delay(400).duration(1000)}>
              <ThemedText type="title" style={styles.welcomeText}>Welcome Back</ThemedText>
              <ThemedText style={styles.subWelcomeText}>Sign in to continue</ThemedText>

              {/* Input Fields */}
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
                    placeholder="Enter your password"
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
                <TouchableOpacity style={styles.forgotPassword}>
                  <ThemedText style={styles.forgotPasswordText}>Forgot Password?</ThemedText>
                </TouchableOpacity>
              </View>

              {/* Login Button */}
              <TouchableOpacity 
                style={[styles.loginButton, { backgroundColor: BRAND_COLORS.primary }]}
                onPress={handleLogin}
              >
                <ThemedText style={styles.loginButtonText}>Log In</ThemedText>
              </TouchableOpacity>

              {/* Divider */}
              <View style={styles.dividerContainer}>
                <View style={[styles.divider, { backgroundColor: theme.icon + '40' }]} />
                <ThemedText style={styles.dividerText}>OR</ThemedText>
                <View style={[styles.divider, { backgroundColor: theme.icon + '40' }]} />
              </View>

              {/* Social Login Buttons Placeholder */}
              <View style={styles.socialButtonsContainer}>
                <TouchableOpacity style={[styles.socialButton, { borderColor: theme.icon + '40' }]}>
                  <Ionicons name="logo-google" size={24} color="#DB4437" />
                </TouchableOpacity>
                <TouchableOpacity style={[styles.socialButton, { borderColor: theme.icon + '40' }]}>
                  <Ionicons name="logo-apple" size={24} color={theme.text} />
                </TouchableOpacity>
              </View>

              {/* Sign Up Link */}
              <View style={styles.signupContainer}>
                <ThemedText>Don{"'"}t have an account? </ThemedText>
                <TouchableOpacity onPress={() => router.push('/signup')}>
                  <ThemedText style={styles.signupText}>Sign Up</ThemedText>
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
    height: height * 0.35,
    justifyContent: 'center',
    alignItems: 'center',
    borderBottomLeftRadius: 40,
    borderBottomRightRadius: 40,
  },
  headerContent: {
    alignItems: 'center',
  },
  logoCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 16,
  },
  headerTitle: {
    fontSize: 28,
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
    fontSize: 28,
    marginBottom: 8,
  },
  subWelcomeText: {
    fontSize: 16,
    color: '#888',
    marginBottom: 32,
  },
  inputGroup: {
    marginBottom: 20,
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
  forgotPassword: {
    alignSelf: 'flex-end',
    marginTop: 8,
  },
  forgotPasswordText: {
    color: BRAND_COLORS.primary,
    fontSize: 14,
    fontWeight: '600',
  },
  loginButton: {
    height: 56,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 12,
    shadowColor: BRAND_COLORS.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  loginButtonText: {
    color: '#FFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
  dividerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 32,
  },
  divider: {
    flex: 1,
    height: 1,
  },
  dividerText: {
    marginHorizontal: 16,
    fontSize: 14,
    color: '#888',
  },
  socialButtonsContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 20,
    marginBottom: 32,
  },
  socialButton: {
    width: 60,
    height: 60,
    borderRadius: 30,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  signupContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 20,
  },
  signupText: {
    color: BRAND_COLORS.primary,
    fontWeight: 'bold',
  },
});
