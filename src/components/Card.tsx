import React from 'react';
import { View, StyleSheet, ViewProps } from 'react-native';
import { useTheme } from '../theme/ThemeContext';

interface CardProps extends ViewProps {
  elevated?: boolean;
  gradient?: boolean;
  gradientGreen?: boolean;
}

export const Card: React.FC<CardProps> = ({ 
  children, 
  style, 
  elevated, 
  gradient, 
  gradientGreen,
  ...rest 
}) => {
  const { colors } = useTheme();

  return (
    <View 
      style={[
        styles.card,
        { backgroundColor: colors.surfaceContainer },
        elevated && { backgroundColor: colors.surfaceContainerHigh, elevation: 4, shadowOpacity: 0.2 },
        gradient && { backgroundColor: colors.primaryDark },
        gradientGreen && { backgroundColor: colors.secondaryDark },
        style
      ]}
      {...rest}
    >
      {children}
    </View>
  );
};

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    padding: 16,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    marginBottom: 16,
  },
});
