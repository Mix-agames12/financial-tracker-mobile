import React from 'react';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ThemeProvider, useTheme } from './src/theme/ThemeContext';
import { useNotifications } from './src/hooks/useNotifications';
import AppNavigator from './src/navigation/AppNavigator';

function ThemedApp() {
  const { isDark } = useTheme();
  const { checkAndScheduleAlerts } = useNotifications();

  React.useEffect(() => {
    checkAndScheduleAlerts();
  }, [checkAndScheduleAlerts]);

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
        <ThemedApp />
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
