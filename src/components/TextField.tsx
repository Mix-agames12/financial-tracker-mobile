import React from 'react';
import { View, Text, TextInput, StyleSheet, TextInputProps, TouchableOpacity, Platform } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';
import { sanitizeDecimalInput, sanitizeIntegerInput, toLocalDateStr } from '../utils/formatters';

interface TextFieldProps extends TextInputProps {
  label: string;
  error?: string;
  isDate?: boolean;
  /** Ayuda que se muestra al tocar el ícono ⓘ junto a la etiqueta. */
  help?: string;
}

export const TextField: React.FC<TextFieldProps> = ({ label, error, isDate, help, style, ...rest }) => {
  const { colors } = useTheme();
  const [isFocused, setIsFocused] = React.useState(false);
  const [showPicker, setShowPicker] = React.useState(false);
  const [showHelp, setShowHelp] = React.useState(false);

  const handleDateChange = (event: any, selectedDate?: Date) => {
    setShowPicker(Platform.OS === 'ios');
    if (selectedDate) {
      rest.onChangeText?.(toLocalDateStr(selectedDate));
    }
  };

  const handleChangeText = (text: string) => {
    if (rest.keyboardType === 'decimal-pad' || rest.keyboardType === 'numeric') {
      rest.onChangeText?.(sanitizeDecimalInput(text));
    } else if (rest.keyboardType === 'number-pad') {
      rest.onChangeText?.(sanitizeIntegerInput(text));
    } else {
      rest.onChangeText?.(text);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.labelRow}>
        <Text
          style={[
            styles.label,
            { color: isFocused ? colors.primaryLight : colors.onSurfaceVariant },
            error && { color: colors.error }
          ]}
        >
          {label}
        </Text>
        {help ? (
          <TouchableOpacity
            onPress={() => setShowHelp((visible) => !visible)}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            accessibilityRole="button"
            accessibilityLabel={`Ayuda: ${label}`}
            accessibilityState={{ expanded: showHelp }}
          >
            <Ionicons name={showHelp ? 'information-circle' : 'information-circle-outline'} size={16} color={colors.primary} />
          </TouchableOpacity>
        ) : null}
      </View>
      {help && showHelp ? (
        <Text style={[styles.helpText, { color: colors.onSurfaceVariant, backgroundColor: colors.surfaceContainerHighest }]}>
          {help}
        </Text>
      ) : null}
      {isDate ? (
        <TouchableOpacity activeOpacity={0.8} onPress={() => setShowPicker(true)}>
          <View pointerEvents="none">
            <TextInput
              style={[
                styles.input,
                {
                  backgroundColor: colors.surfaceContainerLow,
                  color: colors.onSurface,
                  borderColor: isFocused ? colors.primary : colors.outline
                },
                error && { borderColor: colors.error },
                style
              ]}
              placeholderTextColor={colors.onSurfaceVariant}
              value={rest.value}
              editable={false}
            />
          </View>
        </TouchableOpacity>
      ) : (
        <TextInput
          style={[
            styles.input,
            {
              backgroundColor: colors.surfaceContainerLow,
              color: colors.onSurface,
              borderColor: isFocused ? colors.primary : colors.outline
            },
            error && { borderColor: colors.error },
            style
          ]}
          placeholderTextColor={colors.onSurfaceVariant}
          onFocus={(e) => {
            setIsFocused(true);
            rest.onFocus?.(e);
          }}
          onBlur={(e) => {
            setIsFocused(false);
            rest.onBlur?.(e);
          }}
          {...rest}
          onChangeText={handleChangeText}
        />
      )}

      {showPicker && isDate && (
        <DateTimePicker
          value={rest.value ? new Date(rest.value + 'T12:00:00') : new Date()}
          mode="date"
          display="default"
          onChange={handleDateChange}
        />
      )}

      {error && (
        <Text style={[styles.errorText, { color: colors.error }]}>
          {error}
        </Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    marginBottom: 4,
  },
  label: {
    flexShrink: 1,
    fontSize: 12,
    fontWeight: '500',
    fontFamily: 'sans-serif-medium',
  },
  helpText: {
    fontSize: 12,
    lineHeight: 17,
    padding: 8,
    borderRadius: 8,
    marginBottom: 6,
  },
  input: {
    borderWidth: 1.5,
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    fontFamily: 'sans-serif',
  },
  errorText: {
    fontSize: 12,
    marginTop: 4,
  },
});
