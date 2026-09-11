import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { BottomSheet } from './BottomSheet';
import { Button } from './Button';
import { CurrencyPicker } from './CurrencyPicker';
import { useTheme } from '../theme/ThemeContext';
import { SettingsRepo } from '../db/storage';
import { getCurrencyOption, saveCurrency } from '../utils/currency';
import { formatCurrency, getCurrencyConfig } from '../utils/formatters';

interface WelcomeSheetProps {
  visible: boolean;
  appName: string;
  onDone: () => void;
}

/** Primer inicio: muestra la moneda detectada según la región del dispositivo para confirmarla o cambiarla. */
export function WelcomeSheet({ visible, appName, onDone }: WelcomeSheetProps) {
  const { colors } = useTheme();
  const [choosing, setChoosing] = useState(false);
  const [currency, setCurrency] = useState(getCurrencyConfig().currency);

  useEffect(() => {
    if (visible) setCurrency(getCurrencyConfig().currency);
  }, [visible]);

  const finish = async () => {
    await SettingsRepo.save({ onboardingCompleted: true });
    setChoosing(false);
    onDone();
  };

  const option = getCurrencyOption(currency);

  return (
    <BottomSheet
      visible={visible}
      onClose={finish}
      title={choosing ? 'Elige tu moneda' : `¡Bienvenido a ${appName}!`}
    >
      {choosing ? (
        <CurrencyPicker
          selectedCode={currency}
          onSelect={async (selected) => {
            await saveCurrency({ currency: selected.code, locale: selected.locale });
            setCurrency(selected.code);
            setChoosing(false);
          }}
        />
      ) : (
        <View style={styles.container}>
          <Ionicons name="earth" size={48} color={colors.primary} style={styles.icon} />
          <Text style={[styles.text, { color: colors.onSurface }]}>
            Configuramos la moneda según la región de tu dispositivo. Puedes cambiarla ahora o después en Configuración.
          </Text>
          <View style={[styles.preview, { backgroundColor: colors.surfaceContainerHighest }]}>
            <Text style={{ fontSize: 13, color: colors.onSurfaceVariant }}>
              {option ? `${option.name} (${currency})` : currency}
            </Text>
            <Text style={[styles.amount, { color: colors.onSurface }]}>{formatCurrency(1234.5)}</Text>
          </View>
          <Button title="Continuar" onPress={finish} style={styles.fullWidth} />
          <Button
            title="Cambiar moneda"
            variant="outlined"
            onPress={() => setChoosing(true)}
            style={[styles.fullWidth, { marginTop: 12 }]}
          />
        </View>
      )}
    </BottomSheet>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    paddingVertical: 8,
  },
  icon: {
    marginBottom: 12,
  },
  text: {
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
    marginBottom: 16,
  },
  preview: {
    alignSelf: 'stretch',
    alignItems: 'center',
    padding: 16,
    borderRadius: 16,
    marginBottom: 24,
  },
  amount: {
    fontSize: 28,
    fontWeight: 'bold',
    marginTop: 4,
  },
  fullWidth: {
    alignSelf: 'stretch',
  },
});
