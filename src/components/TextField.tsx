import React from 'react';
import { View, Text, TextInput, StyleSheet, TextInputProps } from 'react-native';
import { useTheme } from '../theme/ThemeContext';

interface TextFieldProps extends TextInputProps {
  label: string;
  error?: string;
}

export const TextField: React.FC<TextFieldProps> = ({ label, error, style, ...rest }) => {
  const { colors } = useTheme();
  const [isFocused, setIsFocused] = React.useState(false);

  const handleChangeText = (text: string) => {
    if (rest.keyboardType === 'decimal-pad' || rest.keyboardType === 'numeric') {
      let formatted = text.replace(/[^0-9.,]/g, '').replace(',', '.');
      
      const parts = formatted.split('.');
      if (parts.length > 2) {
        formatted = parts[0] + '.' + parts.slice(1).join('');
      }

      if (parts.length === 2 && parts[1].length > 2) {
        formatted = parts[0] + '.' + parts[1].substring(0, 2);
      }
      
      rest.onChangeText?.(formatted);
    } else {
      rest.onChangeText?.(text);
    }
  };

  return (
    <View style={styles.container}>
      <Text 
        style={[
          styles.label, 
          { color: isFocused ? colors.primaryLight : colors.onSurfaceVariant },
          error && { color: colors.error }
        ]}
      >
        {label}
      </Text>
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
  label: {
    fontSize: 12,
    fontWeight: '500',
    marginBottom: 4,
    fontFamily: 'sans-serif-medium',
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
