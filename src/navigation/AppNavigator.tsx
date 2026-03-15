import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NavigationContainer, DefaultTheme, DarkTheme as NavDarkTheme } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import { useTheme } from '../theme/ThemeContext';
import { RootStackParamList, BottomTabParamList } from './types';

import DashboardScreen from '../screens/DashboardScreen';
import ExpensesScreen from '../screens/ExpensesScreen';
import IncomeScreen from '../screens/IncomeScreen';
import LoansScreen from '../screens/LoansScreen';
import AnalyticsScreen from '../screens/AnalyticsScreen';
import SettingsScreen from '../screens/SettingsScreen';

const Tab = createBottomTabNavigator<BottomTabParamList>();
const Stack = createNativeStackNavigator<RootStackParamList>();

function MainTabs() {
  const { colors, isDark } = useTheme();

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.surfaceContainerLowest,
          borderTopColor: colors.outlineVariant,
          height: 64,
          paddingBottom: 8,
          paddingTop: 8,
        },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.onSurfaceVariant,
        tabBarIcon: ({ focused, color, size }) => {
          let iconName = 'apps-outline';

          switch (route.name) {
            case 'Dashboard':
              iconName = focused ? 'apps' : 'apps-outline';
              break;
            case 'Expenses':
              iconName = focused ? 'receipt' : 'receipt-outline';
              break;
            case 'Income':
              iconName = focused ? 'wallet' : 'wallet-outline';
              break;
            case 'Loans':
              iconName = focused ? 'card' : 'card-outline'; // using card for loans/debts
              break;
            case 'Analytics':
              iconName = focused ? 'bar-chart' : 'bar-chart-outline';
              break;
          }

          return <Ionicons name={iconName as any} size={24} color={color} />;
        },
        tabBarLabelStyle: {
          fontFamily: 'sans-serif',
          fontSize: 12,
        },
      })}
    >
      <Tab.Screen name="Dashboard" component={DashboardScreen} options={{ tabBarLabel: 'Inicio' }} />
      <Tab.Screen name="Expenses" component={ExpensesScreen} options={{ tabBarLabel: 'Gastos' }} />
      <Tab.Screen name="Income" component={IncomeScreen} options={{ tabBarLabel: 'Ingresos' }} />
      <Tab.Screen name="Loans" component={LoansScreen} options={{ tabBarLabel: 'Préstamos' }} />
      <Tab.Screen name="Analytics" component={AnalyticsScreen} options={{ tabBarLabel: 'Análisis' }} />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const { isDark, colors } = useTheme();

  const CustomNavTheme = isDark
    ? {
        ...NavDarkTheme,
        colors: {
          ...NavDarkTheme.colors,
          background: colors.background,
          card: colors.surfaceContainerLowest,
          text: colors.onSurface,
          border: colors.outlineVariant,
          primary: colors.primary,
        },
      }
    : {
        ...DefaultTheme,
        colors: {
          ...DefaultTheme.colors,
          background: colors.background,
          card: colors.surfaceContainerLowest,
          text: colors.onSurface,
          border: colors.outlineVariant,
          primary: colors.primary,
        },
      };

  return (
    <NavigationContainer theme={CustomNavTheme}>
      <Stack.Navigator>
        <Stack.Screen 
          name="MainTabs" 
          component={MainTabs} 
          options={{ headerShown: false }} 
        />
        <Stack.Screen 
          name="Settings" 
          component={SettingsScreen} 
          options={{ 
            title: 'Configuración',
            headerStyle: { backgroundColor: colors.surfaceContainerLowest },
            headerTintColor: colors.onSurface,
          }} 
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
