import { View, Text, Pressable, StyleSheet } from 'react-native';
import { Link } from 'expo-router';

export default function Home() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Home Screen 🚀</Text>

      <Link href="/modal" asChild>
        <Pressable style={styles.button}>
          <Text style={styles.buttonText}>Open Modal</Text>
        </Pressable>
      </Link>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontSize: 20,
    marginBottom: 20,
  },
  button: {
    backgroundColor: 'black',
    padding: 12,
    borderRadius: 8,
  },
  buttonText: {
    color: 'white',
  },
});