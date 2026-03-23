import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { Colors } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';

export default function RootLayout() {
  const colorScheme = useColorScheme() ?? 'light';
  const theme = Colors[colorScheme];

  return (
    <>
      <StatusBar style={colorScheme === 'dark' ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          animation: 'slide_from_right',
          contentStyle: { backgroundColor: theme.background },
          headerShadowVisible: false,
          headerStyle: { backgroundColor: theme.background },
          headerTintColor: theme.text,
          headerTitleStyle: { fontWeight: '700' },
        }}>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen
          name="modal"
          options={{
            presentation: 'modal',
            title: 'Delivery standards',
          }}
        />
      </Stack>
    </>
  );
}
