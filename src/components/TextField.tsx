import React, { useState } from 'react';
import { View, Text, TextInput, StyleSheet, TextInputProps } from 'react-native';
import { useTheme } from '../theme/ThemeContext';

interface TextFieldProps extends TextInputProps {
  label: string;
  error?: string;
}

export const TextField: React.FC<TextFieldProps> = ({ label, error, style, ...rest }) => {
  const { colors } = useTheme();
  const [isFocused, setIsFocused] = useState(false);

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
