export interface ThemeColors {
  primary: string;
  primaryLight: string;
  primaryDark: string;
  primaryContainer: string;
  onPrimary: string;
  onPrimaryContainer: string;

  secondary: string;
  secondaryLight: string;
  secondaryDark: string;
  secondaryContainer: string;
  onSecondary: string;
  onSecondaryContainer: string;

  tertiary: string;
  tertiaryLight: string;
  tertiaryContainer: string;
  onTertiary: string;

  error: string;
  errorLight: string;
  errorContainer: string;
  onError: string;

  background: string;
  surface: string;
  surfaceDim: string;
  surfaceBright: string;
  surfaceContainerLowest: string;
  surfaceContainerLow: string;
  surfaceContainer: string;
  surfaceContainerHigh: string;
  surfaceContainerHighest: string;
  onSurface: string;
  onSurfaceVariant: string;
  outline: string;
  outlineVariant: string;
  
  inverseSurface?: string;
  inverseOnSurface?: string;

  // Extra Semantic
  incomeCard: string;
  savingsCard: string;
}

export const darkColors: ThemeColors = {
  primary: '#6366f1',
  primaryLight: '#818cf8',
  primaryDark: '#4f46e5',
  primaryContainer: '#e0e7ff',
  onPrimary: '#ffffff',
  onPrimaryContainer: '#312e81',

  secondary: '#10b981',
  secondaryLight: '#34d399',
  secondaryDark: '#059669',
  secondaryContainer: '#d1fae5',
  onSecondary: '#ffffff',
  onSecondaryContainer: '#064e3b',

  tertiary: '#f59e0b',
  tertiaryLight: '#fbbf24',
  tertiaryContainer: '#fef3c7',
  onTertiary: '#ffffff',

  error: '#f43f5e',
  errorLight: '#fb7185',
  errorContainer: '#ffe4e6',
  onError: '#ffffff',

  background: '#0f172a',
  surface: '#1e293b',
  surfaceDim: '#0f172a',
  surfaceBright: '#334155',
  surfaceContainerLowest: '#0c1322',
  surfaceContainerLow: '#1a2538',
  surfaceContainer: '#1e293b',
  surfaceContainerHigh: '#273548',
  surfaceContainerHighest: '#334155',
  onSurface: '#f1f5f9',
  onSurfaceVariant: '#94a3b8',
  outline: '#475569',
  outlineVariant: '#334155',

  incomeCard: '#10b981',
  savingsCard: '#f59e0b',
};

export const lightColors: ThemeColors = {
  ...darkColors,
  background: '#f8fafc',
  surface: '#ffffff',
  surfaceDim: '#f1f5f9',
  surfaceBright: '#ffffff',
  surfaceContainerLowest: '#ffffff',
  surfaceContainerLow: '#f8fafc',
  surfaceContainer: '#ffffff',
  surfaceContainerHigh: '#f1f5f9',
  surfaceContainerHighest: '#e2e8f0',
  onSurface: '#0f172a',
  onSurfaceVariant: '#475569',
  outline: '#cbd5e1',
  outlineVariant: '#e2e8f0',
  inverseSurface: '#1e293b',
  inverseOnSurface: '#f1f5f9',
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
};

export const radius = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 28,
  full: 9999,
};
