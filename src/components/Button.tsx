import React from 'react';
import { 
  TouchableOpacity, 
  Text, 
  StyleSheet, 
  TouchableOpacityProps, 
  ActivityIndicator,
  View
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';

interface ButtonProps extends TouchableOpacityProps {
  title: string;
  variant?: 'filled' | 'outlined' | 'tonal' | 'danger';
  icon?: keyof typeof Ionicons.glyphMap;
  loading?: boolean;
}

export const Button: React.FC<ButtonProps> = ({ 
  title, 
  variant = 'filled', 
  icon, 
  loading,
  style, 
  disabled,
  ...rest 
}) => {
  const { colors } = useTheme();

  const getBackgroundColor = () => {
    if (disabled) return colors.surfaceContainerHighest;
    switch (variant) {
      case 'filled': return colors.primary;
      case 'outlined': return 'transparent';
      case 'tonal': return colors.primaryContainer;
      case 'danger': return colors.error;
    }
  };

  const getTextColor = () => {
    if (disabled) return colors.onSurfaceVariant;
    switch (variant) {
      case 'filled': return colors.onPrimary;
      case 'outlined': return colors.primary;
      case 'tonal': return colors.onPrimaryContainer;
      case 'danger': return colors.onError;
    }
  };

  const getBorderColor = () => {
    if (variant === 'outlined') {
      return disabled ? colors.outlineVariant : colors.outline;
    }
    return 'transparent';
  };

  return (
    <TouchableOpacity
      disabled={disabled || loading}
      style={[
        styles.button,
        { 
          backgroundColor: getBackgroundColor(),
          borderColor: getBorderColor(),
          borderWidth: variant === 'outlined' ? 1.5 : 0,
        },
        style
      ]}
      {...rest}
    >
      {loading ? (
        <ActivityIndicator color={getTextColor()} />
      ) : (
        <View style={styles.content}>
          {icon && (
            <Ionicons 
              name={icon} 
              size={20} 
              color={getTextColor()} 
              style={styles.icon} 
            />
          )}
          <Text style={[styles.text, { color: getTextColor() }]}>
            {title}
          </Text>
        </View>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 9999, // full
    minHeight: 44,
    justifyContent: 'center',
    alignItems: 'center',
    flexDirection: 'row',
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    marginRight: 8,
  },
  text: {
    fontSize: 14,
    fontWeight: '600',
    fontFamily: 'sans-serif-medium',
  },
});
