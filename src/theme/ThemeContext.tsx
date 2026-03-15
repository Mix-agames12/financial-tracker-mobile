import React, { createContext, useContext, useState, useEffect } from 'react';
import { Appearance, ColorSchemeName } from 'react-native';
import { lightColors, darkColors, ThemeColors } from './colors';
import { SettingsRepo } from '../db/storage';

interface ThemeContextType {
  isDark: boolean;
  colors: ThemeColors;
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
}

const ThemeContext = createContext<ThemeContextType>({
  isDark: true,
  colors: darkColors,
  setTheme: () => {},
});

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const systemColorScheme = Appearance.getColorScheme();
  const [themeMode, setThemeMode] = useState<'light' | 'dark' | 'system'>('system');
  const [isDark, setIsDark] = useState<boolean>(systemColorScheme === 'dark');

  useEffect(() => {
    // Load preference from AsyncStorage
    SettingsRepo.get().then((settings) => {
      if (settings && settings.theme) {
        setThemeMode(settings.theme as 'light' | 'dark');
        setIsDark(settings.theme === 'dark');
      }
    });

    const listener = Appearance.addChangeListener(({ colorScheme }) => {
      if (themeMode === 'system') {
        setIsDark(colorScheme === 'dark');
      }
    });

    return () => listener.remove();
  }, [themeMode]);

  const handleSetTheme = async (mode: 'light' | 'dark' | 'system') => {
    setThemeMode(mode);
    if (mode === 'system') {
      setIsDark(Appearance.getColorScheme() === 'dark');
      await SettingsRepo.save({ theme: Appearance.getColorScheme() === 'dark' ? 'dark' : 'light' });
    } else {
      setIsDark(mode === 'dark');
      await SettingsRepo.save({ theme: mode });
    }
  };

  const colors = isDark ? darkColors : lightColors;

  return (
    <ThemeContext.Provider value={{ isDark, colors, setTheme: handleSetTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => useContext(ThemeContext);
