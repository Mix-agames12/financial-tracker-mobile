import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, TouchableOpacityProps } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';

interface ChipProps extends TouchableOpacityProps {
  label: string;
  active?: boolean;
  icon?: keyof typeof Ionicons.glyphMap;
}

export const Chip: React.FC<ChipProps> = ({ 
  label, 
  active, 
  icon, 
  style, 
  ...rest 
}) => {
  const { colors } = useTheme();

  return (
    <TouchableOpacity
      style={[
        styles.chip,
        { 
          borderColor: active ? colors.primary : colors.outline,
          backgroundColor: active ? colors.primary : 'transparent',
        },
        style
      ]}
      {...rest}
    >
      {icon && (
        <Ionicons 
          name={icon} 
          size={16} 
          color={active ? colors.onPrimary : colors.onSurfaceVariant} 
          style={{ marginRight: 4 }}
        />
      )}
      <Text style={[
        styles.text,
        { color: active ? colors.onPrimary : colors.onSurfaceVariant }
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
