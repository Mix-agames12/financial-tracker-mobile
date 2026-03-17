import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, TouchableOpacityProps } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';

interface ChipProps extends TouchableOpacityProps {
  label: string;
  active?: boolean;
  icon?: keyof typeof Ionicons.glyphMap;
  color?: string;
}

export const Chip: React.FC<ChipProps> = ({ 
  label, 
  active, 
  icon,
  color: customColor, 
  style, 
  ...rest 
}) => {
  const { colors } = useTheme();

  return (
    <TouchableOpacity
      style={[
        styles.chip,
        { 
          borderColor: active ? (customColor || colors.primary) : (customColor ? customColor + '40' : colors.outline),
          backgroundColor: active ? (customColor || colors.primary) : (customColor ? customColor + '10' : 'transparent'),
        },
        style
      ]}
      {...rest}
    >
      {icon && (
        <Ionicons 
          name={icon} 
          size={16} 
          color={active ? colors.onPrimary : (customColor || colors.onSurfaceVariant)} 
          style={{ marginRight: 4 }}
        />
      )}
      <Text style={[
        styles.text,
        { color: active ? colors.onPrimary : (customColor || colors.onSurfaceVariant) }
      ]}>
        {label}
      </Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
    paddingHorizontal: 14,
    borderRadius: 9999,
    borderWidth: 1.5,
  },
  text: {
    fontSize: 14,
    fontWeight: '500',
    fontFamily: 'sans-serif-medium',
  },
});
