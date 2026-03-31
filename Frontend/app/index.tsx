import { Redirect } from 'expo-router';

export default function Index() {
  // For now, redirect to login. 
  // In a real app, you would check if the user is authenticated here.
  return <Redirect href="/login" />;
}
