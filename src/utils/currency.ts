import * as Localization from 'expo-localization';
import { SettingsRepo } from '../db/storage';
import { CurrencyConfig, isValidCurrencyConfig, setCurrencyConfig } from './formatters';

export interface CurrencyOption {
  code: string;
  name: string;
  locale: string; // formato por defecto al elegirla manualmente
}

export const CURRENCY_OPTIONS: CurrencyOption[] = [
  { code: 'USD', name: 'Dólar estadounidense', locale: 'es-EC' },
  { code: 'EUR', name: 'Euro', locale: 'es-ES' },
  { code: 'MXN', name: 'Peso mexicano', locale: 'es-MX' },
  { code: 'COP', name: 'Peso colombiano', locale: 'es-CO' },
  { code: 'PEN', name: 'Sol peruano', locale: 'es-PE' },
  { code: 'CLP', name: 'Peso chileno', locale: 'es-CL' },
  { code: 'ARS', name: 'Peso argentino', locale: 'es-AR' },
  { code: 'BOB', name: 'Boliviano', locale: 'es-BO' },
  { code: 'PYG', name: 'Guaraní paraguayo', locale: 'es-PY' },
  { code: 'UYU', name: 'Peso uruguayo', locale: 'es-UY' },
  { code: 'GTQ', name: 'Quetzal guatemalteco', locale: 'es-GT' },
  { code: 'HNL', name: 'Lempira hondureño', locale: 'es-HN' },
  { code: 'NIO', name: 'Córdoba nicaragüense', locale: 'es-NI' },
  { code: 'CRC', name: 'Colón costarricense', locale: 'es-CR' },
  { code: 'DOP', name: 'Peso dominicano', locale: 'es-DO' },
  { code: 'BRL', name: 'Real brasileño', locale: 'pt-BR' },
  { code: 'CAD', name: 'Dólar canadiense', locale: 'en-CA' },
  { code: 'GBP', name: 'Libra esterlina', locale: 'en-GB' },
];

export function getCurrencyOption(code?: string): CurrencyOption | undefined {
  return CURRENCY_OPTIONS.find((o) => o.code === code);
}

/** Moneda sugerida por la región configurada en el dispositivo (no requiere permisos de ubicación). */
export function detectDeviceCurrency(): CurrencyConfig {
  try {
    const [locale] = Localization.getLocales();
    const code = locale?.currencyCode?.toUpperCase();
    const region = locale?.regionCode?.toUpperCase();
    if (code) {
      const candidates = [
        region ? `es-${region}` : null,
        getCurrencyOption(code)?.locale,
        locale?.languageTag,
        'es-EC',
      ].filter((l): l is string => !!l);
      for (const candidate of candidates) {
        const config = { currency: code, locale: candidate };
        if (isValidCurrencyConfig(config)) return config;
      }
    }
  } catch (e) {
    console.warn('No se pudo detectar la moneda del dispositivo', e);
  }
  return { currency: 'USD', locale: 'es-EC' };
}

/**
 * Aplica la moneda guardada. Sólo en el primer inicio (sin moneda guardada) la detecta desde
 * la región del dispositivo y la persiste; la hoja de bienvenida pide confirmarla.
 */
export async function initCurrency(): Promise<void> {
  const settings = await SettingsRepo.get();
  if (settings.currency) {
    setCurrencyConfig({ currency: settings.currency, locale: settings.currencyLocale });
    return;
  }
  const detected = detectDeviceCurrency();
  setCurrencyConfig(detected);
  await SettingsRepo.save({ currency: detected.currency, currencyLocale: detected.locale });
}

/** Aplica la moneda guardada sin detectar ni persistir (contextos headless como el widget). */
export async function applySavedCurrency(): Promise<void> {
  const settings = await SettingsRepo.get();
  if (settings.currency) {
    setCurrencyConfig({ currency: settings.currency, locale: settings.currencyLocale });
  }
}

export async function saveCurrency(config: CurrencyConfig): Promise<void> {
  setCurrencyConfig(config);
  await SettingsRepo.save({ currency: config.currency, currencyLocale: config.locale });
}
