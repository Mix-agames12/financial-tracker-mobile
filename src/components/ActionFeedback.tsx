import React, { useState, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export const ToastManager = {
  listener: null as ((msg: string) => void) | null,
  show: (msg: string = 'Guardado con éxito') => {
    if (ToastManager.listener) {
      ToastManager.listener(msg);
    }
  }
};

export const ActionFeedback = () => {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [msg, setMsg] = useState('');
  const scale = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    ToastManager.listener = (message) => {
      setMsg(message);
      scale.setValue(0);
      Animated.spring(scale, { 
        toValue: 1, 
        useNativeDriver: true, 
        tension: 80,
        friction: 5 
      }).start();
      
      setTimeout(() => {
        Animated.timing(scale, { 
          toValue: 0, 
          duration: 250, 
          useNativeDriver: true 
        }).start();
      }, 2000);
    };
    return () => { ToastManager.listener = null; };
  }, [scale]);

  return (
    <Animated.View 
      pointerEvents="none"
      style={[
        styles.container, 
        { 
          top: Math.max(insets.top + 20, 40), 
          transform: [{ scale }], 
          opacity: scale.interpolate({ inputRange: [0, 0.5, 1], outputRange: [0, 1, 1] }), 
          backgroundColor: colors.primaryContainer,
          shadowColor: '#000000'
        }
      ]}
    >
      <Ionicons name="checkmark-circle" size={24} color={colors.primary} />
      <Text style={[styles.text, { color: colors.onPrimaryContainer }]}>{msg}</Text>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 30,
    elevation: 8,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    zIndex: 9999,
  },
  text: {
    marginLeft: 8,
    fontSize: 16,
    fontWeight: '600'
  }
});
