import React from 'react';
import { View, StyleSheet, ViewProps } from 'react-native';
import { useTheme } from '../theme/ThemeContext';

interface ProgressBarProps extends ViewProps {
  progress: number; // 0 to 1
  colorVariant?: 'primary' | 'success' | 'warning' | 'danger';
}

export const ProgressBar: React.FC<ProgressBarProps> = ({ 
  progress, 
  colorVariant = 'primary', 
  style 
}) => {
  const { colors } = useTheme();

  const getFillColor = () => {
    switch(colorVariant) {
      case 'primary': return colors.primary;
      case 'success': return colors.secondary;
      case 'warning': return colors.tertiary;
      case 'danger': return colors.error;
    }
  };

  const safeProgress = Math.min(Math.max(progress, 0), 1);

  return (
    <View style={[styles.track, { backgroundColor: colors.surfaceContainerHighest }, style]}>
      <View 
        style={[
          styles.fill, 
          { 
            backgroundColor: getFillColor(), 
            width: `${safeProgress * 100}%` 
          }
        ]} 
      />
    </View>
  );
};

const styles = StyleSheet.create({
  track: {
    width: '100%',
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 4,
  },
});
