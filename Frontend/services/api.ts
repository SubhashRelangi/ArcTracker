import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';

const API_URL = 'http://localhost:3000/api'; // Adjust to your backend URL

export const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor to add token to requests
api.interceptors.request.use(
  async (config) => {
    const token = await AsyncStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const login = (credentials: any) => api.post('/auth/login', credentials);
export const signup = (data: any) => api.post('/auth/signup', data);
export const getMe = () => api.get('/users/me');
export const getAccounts = () => api.get('/accounts');
export const addAccount = (data: any) => api.post('/accounts', data);
export const getTransactions = (search?: string) => api.get('/transactions' + (search ? '?search=' + search : ''));
export const getSpendingSummary = () => api.get('/spending');
export const addTransaction = (data: any) => api.post('/transactions', data);
