import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ThemeProvider, useTheme } from './src/theme/ThemeContext';
import { useNotifications } from './src/hooks/useNotifications';
import AppNavigator from './src/navigation/AppNavigator';
import * as SplashScreen from 'expo-splash-screen';
import { Animated, View, StyleSheet, Image, AppState } from 'react-native';
import { initCurrency } from './src/utils/currency';
import { CategoryRepo } from './src/db/storage';
import { syncAfterDataChange } from './src/utils/dataSync';
import { linkLegacyCardLoans } from './src/utils/cardPurchases';

SplashScreen.preventAutoHideAsync().catch(() => {});

function AnimatedSplashScreen({ children }: { children: React.ReactNode }) {
  const [isAppReady, setAppReady] = React.useState(false);
  const [isSplashAnimationComplete, setAnimationComplete] = React.useState(false);
  const animation = React.useRef(new Animated.Value(1)).current;
  const { colors } = useTheme();

  React.useEffect(() => {
    async function prepare() {
      try {
        // Moneda (detectada en el primer inicio) y categorías listas antes de pintar la app;
        // la espera mínima conserva la animación del splash.
        await Promise.all([
          initCurrency(),
          CategoryRepo.seedDefaults(),
          // Préstamos de compras con tarjeta anteriores a cardId: se vinculan a su tarjeta.
          linkLegacyCardLoans(),
          new Promise(resolve => setTimeout(resolve, 1500)),
        ]);
      } catch (e) {
        console.warn(e);
      } finally {
        setAppReady(true);
      }
    }
    prepare();
  }, []);

  React.useEffect(() => {
    if (isAppReady) {
      SplashScreen.hideAsync().catch(() => {});
      Animated.timing(animation, {
        toValue: 0,
        duration: 2000,
        useNativeDriver: true,
      }).start(() => setAnimationComplete(true));
    }
  }, [isAppReady, animation]);

  return (
    <View style={{ flex: 1 }}>
      {isAppReady && children}
      {!isSplashAnimationComplete && (
        <Animated.View
          pointerEvents="none"
          style={[
            StyleSheet.absoluteFill,
            {
              backgroundColor: '#312e81ff',
              opacity: animation,
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 9999,
            },
          ]}
        >
          <Image
            style={{ width: 180, height: 180, resizeMode: 'contain' }}
            source={require('./assets/splash-icon.png')}
          />
        </Animated.View>
      )}
    </View>
  );
}

function ThemedApp() {
  const { isDark } = useTheme();
  useNotifications();

  React.useEffect(() => {
    // Recordatorios, alerta de presupuesto y widget se recalculan al abrir y al volver a la app
    // (cambio de día o de mes).
    syncAfterDataChange();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') syncAfterDataChange();
    });
    return () => subscription.remove();
  }, []);

  return (
    <>
      <StatusBar style={isDark ? 'light' : 'dark'} backgroundColor="transparent" translucent />
      <AppNavigator />
    </>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <AnimatedSplashScreen>
          <ThemedApp />
        </AnimatedSplashScreen>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
