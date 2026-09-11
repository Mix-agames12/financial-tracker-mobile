import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';
import { CURRENCY_OPTIONS, CurrencyOption } from '../utils/currency';

interface CurrencyPickerProps {
  selectedCode?: string;
  onSelect: (option: CurrencyOption) => void;
}

function formatExample(option: CurrencyOption): string {
  try {
    return new Intl.NumberFormat(option.locale, {
      style: 'currency',
      currency: option.code,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(1234.5);
  } catch {
    return option.code;
  }
}

/** Lista de monedas disponibles con un ejemplo de su formato. */
export function CurrencyPicker({ selectedCode, onSelect }: CurrencyPickerProps) {
  const { colors } = useTheme();

  return (
    <View>
      {CURRENCY_OPTIONS.map((option) => {
        const selected = option.code === selectedCode;
        return (
          <TouchableOpacity
            key={option.code}
            style={[styles.row, { borderBottomColor: colors.outlineVariant }]}
            onPress={() => onSelect(option)}
            accessibilityState={{ selected }}
          >
            <View style={[styles.codeBox, { backgroundColor: selected ? colors.primary : colors.surfaceContainerHighest }]}>
              <Text style={[styles.code, { color: selected ? colors.onPrimary : colors.onSurface }]}>{option.code}</Text>
            </View>
            <View style={styles.texts}>
              <Text style={[styles.name, { color: selected ? colors.primary : colors.onSurface }]}>{option.name}</Text>
              <Text style={[styles.example, { color: colors.onSurfaceVariant }]}>{formatExample(option)}</Text>
            </View>
            {selected && <Ionicons name="checkmark" size={20} color={colors.primary} />}
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  codeBox: {
    width: 52,
    paddingVertical: 6,
    borderRadius: 8,
    alignItems: 'center',
    marginRight: 12,
  },
  code: {
    fontSize: 13,
    fontWeight: 'bold',
  },
  texts: {
    flex: 1,
  },
  name: {
    fontSize: 16,
  },
  example: {
    fontSize: 12,
    marginTop: 2,
  },
});
