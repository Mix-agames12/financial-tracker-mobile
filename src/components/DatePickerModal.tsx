import React, { useMemo } from 'react';
import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { BottomSheet } from './BottomSheet';
import { useTheme } from '../theme/ThemeContext';
import { Ionicons } from '@expo/vector-icons';
import { toLocalDateStr } from '../utils/formatters';

interface DatePickerModalProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  value: string;
  onSelect: (dateStr: string) => void;
}

export function DatePickerModal({ visible, onClose, title = "Seleccionar fecha", value, onSelect }: DatePickerModalProps) {
  const { colors } = useTheme();

  // Se recalcula al abrir para que "Hoy" siga siendo correcto si la app pasa la medianoche abierta.
  const options = useMemo(() => {
    const list: { label: string; value: string; subLabel?: string }[] = [];
    const today = new Date();

    // Hoy
    list.push({ label: 'Hoy', value: toLocalDateStr(today) });

    // Ayer
    const ayer = new Date(today);
    ayer.setDate(ayer.getDate() - 1);
    list.push({ label: 'Ayer', value: toLocalDateStr(ayer) });

    // Últimos 7 días
    for (let i = 2; i <= 6; i++) {
        const d = new Date(today);
        d.setDate(d.getDate() - i);
        list.push({
            label: `Hace ${i} días`,
            value: toLocalDateStr(d),
            subLabel: `${d.getDate()}/${d.getMonth() + 1}`
        });
    }

    // Un mes atras estricto
    const mes = new Date(today);
    mes.setMonth(mes.getMonth() - 1);
    list.push({
        label: `Hace 1 mes`,
        value: toLocalDateStr(mes)
    });

    return list;
  }, [visible]);

  return (
    <BottomSheet visible={visible} onClose={onClose} title={title}>
      <ScrollView style={{ maxHeight: 350 }} nestedScrollEnabled>
        {options.map((opt, i) => (
          <TouchableOpacity
            key={i}
            style={{
                paddingVertical: 16,
                borderBottomWidth: 1,
                borderBottomColor: colors.outlineVariant,
                flexDirection: 'row',
                justifyContent: 'space-between',
                alignItems: 'center'
            }}
            onPress={() => {
              onSelect(opt.value);
              onClose();
            }}
          >
            <View>
                <Text style={{ fontSize: 16, color: value === opt.value ? colors.primary : colors.onSurface }}>
                    {opt.label}
                </Text>
                {opt.subLabel && (
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>{opt.subLabel}</Text>
                )}
            </View>

            {value === opt.value && <Ionicons name="checkmark" size={20} color={colors.primary} />}
          </TouchableOpacity>
        ))}
      </ScrollView>
    </BottomSheet>
  );
}
