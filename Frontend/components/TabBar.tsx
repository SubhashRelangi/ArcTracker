import React, { useEffect, useState } from 'react';
import { View, StyleSheet, Pressable, LayoutChangeEvent, Dimensions } from 'react-native';
import Animated, { 
  useAnimatedStyle, 
  useSharedValue, 
  withSpring, 
  withTiming,
  interpolateColor
} from 'react-native-reanimated';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomTabBarProps } from '@react-navigation/bottom-tabs';

import { useTheme } from '@/hooks/use-theme';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const TAB_BAR_WIDTH = SCREEN_WIDTH * 0.9;
const TAB_BAR_HEIGHT = 64;

export const TabBar = ({ state, descriptors, navigation }: BottomTabBarProps) => {
  const insets = useSafeAreaInsets();
  const { theme, isDark } = useTheme();
  const [tabWidth, setTabWidth] = useState(0);
  
  const translateX = useSharedValue(0);
  const bubbleScaleX = useSharedValue(1);

  useEffect(() => {
    if (tabWidth > 0) {
      // Simple and snappy spring transition for the position
      translateX.value = withSpring((state.index * tabWidth) + 6, {
        damping: 20,
        stiffness: 150,
        mass: 1,
      });
    }
  }, [state.index, tabWidth]);

  const onLayout = (event: LayoutChangeEvent) => {
    const { width } = event.nativeEvent.layout;
    setTabWidth((width - 12) / state.routes.length);
  };

  const animatedBubbleStyle = useAnimatedStyle(() => {
    return {
      transform: [
        { translateX: translateX.value },
      ],
    };
  });

  return (
    <View style={[styles.container, { bottom: insets.bottom + 10 }]}>
      <View style={[styles.tabBar, { backgroundColor: isDark ? '#1C1C1E' : '#FFFFFF' }]} onLayout={onLayout}>
        <Animated.View 
          style={[
            styles.bubble, 
            { 
              width: tabWidth - 12, // Subtracted margin for centering
              backgroundColor: '#00BAF2'
            }, 
            animatedBubbleStyle
          ]} 
        />
        
        {state.routes.map((route, index) => {
          const { options } = descriptors[route.key];
          const isFocused = state.index === index;

          const onPress = () => {
            const event = navigation.emit({
              type: 'tabPress',
              target: route.key,
              canPreventDefault: true,
            });

            if (!isFocused && !event.defaultPrevented) {
              navigation.navigate(route.name);
            }
          };

          let iconName: keyof typeof Ionicons.glyphMap = 'home-outline';
          if (route.name === 'index') iconName = isFocused ? 'home' : 'home-outline';
          else if (route.name === 'search') iconName = isFocused ? 'search' : 'search-outline';
          else if (route.name === 'profile') iconName = isFocused ? 'person' : 'person-outline';

          return (
            <Pressable
              key={route.key}
              onPress={onPress}
              style={styles.tabItem}
            >
              <Ionicons 
                name={iconName} 
                size={24} 
                color={isFocused ? '#FFFFFF' : (isDark ? '#9BA1A6' : '#687076')} 
              />
            </Pressable>
          );
        })}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    alignSelf: 'center',
    width: TAB_BAR_WIDTH,
    zIndex: 100,
  },
  tabBar: {
    flexDirection: 'row',
    height: TAB_BAR_HEIGHT,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
    elevation: 5,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    zIndex: 10,
  },
  bubble: {
    position: 'absolute',
    height: TAB_BAR_HEIGHT - 12,
    borderRadius: 26,
    marginHorizontal: 6, // This ensures the bubble is centered within its translateX range
  },
});
