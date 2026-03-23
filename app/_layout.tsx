import { Stack } from 'expo-router';

export default function RootLayout() {
  return (
    <Stack>
      {/* Home Screen */}
      <Stack.Screen name="index" options={{ title: 'Home' }} />

      {/* Modal Screen */}
      <Stack.Screen 
        name="modal" 
        options={{ presentation: 'modal', title: 'Modal' }} 
      />
    </Stack>
  );
}