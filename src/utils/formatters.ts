// ===== Moneda =====

export interface CurrencyConfig {
  currency: string; // ISO 4217, p. ej. 'USD'
  locale: string; // p. ej. 'es-EC'
}

const DEFAULT_CURRENCY_CONFIG: CurrencyConfig = { currency: 'USD', locale: 'es-EC' };

let currencyConfig: CurrencyConfig = DEFAULT_CURRENCY_CONFIG;
let currencyFormatter: Intl.NumberFormat | null = null;

function buildFormatter(config: CurrencyConfig): Intl.NumberFormat {
  return new Intl.NumberFormat(config.locale, {
    style: 'currency',
    currency: config.currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function isValidCurrencyConfig(config: CurrencyConfig): boolean {
  try {
    buildFormatter(config).format(1);
    return true;
  } catch {
    return false;
  }
}

/** Define la moneda usada por formatCurrency en toda la app (se carga desde Settings al iniciar). */
export function setCurrencyConfig(config: Partial<CurrencyConfig>) {
  const next: CurrencyConfig = {
    currency: config.currency || DEFAULT_CURRENCY_CONFIG.currency,
    locale: config.locale || DEFAULT_CURRENCY_CONFIG.locale,
  };
  currencyConfig = isValidCurrencyConfig(next) ? next : DEFAULT_CURRENCY_CONFIG;
  currencyFormatter = null;
}

export function getCurrencyConfig(): CurrencyConfig {
  return currencyConfig;
}

function getFormatter(): Intl.NumberFormat {
  if (!currencyFormatter) currencyFormatter = buildFormatter(currencyConfig);
  return currencyFormatter;
}

export function formatCurrency(amount: number | string | undefined): string {
  const num = typeof amount === 'string' ? parseFloat(amount) : Number(amount) || 0;
  return getFormatter().format(Number.isFinite(num) ? num : 0);
}

/** Símbolo de la moneda activa (p. ej. "$", "€", "S/"). */
export function getCurrencySymbol(): string {
  const symbol = getFormatter().format(0).replace(/[\d\s.,-]/g, '');
  return symbol || currencyConfig.currency;
}

// ===== Montos =====

/** Deja un único separador decimal ('.') y como máximo `maxDecimals` decimales. */
export function sanitizeDecimalInput(text: string, maxDecimals = 2): string {
  const cleaned = text.replace(/,/g, '.').replace(/[^0-9.]/g, '');
  const dotIndex = cleaned.indexOf('.');
  if (dotIndex === -1) return cleaned;
  const intPart = cleaned.slice(0, dotIndex) || '0';
  const decimals = cleaned.slice(dotIndex + 1).replace(/\./g, '').slice(0, maxDecimals);
  return `${intPart}.${decimals}`;
}

export function sanitizeIntegerInput(text: string): string {
  return text.replace(/[^0-9]/g, '');
}

/** Redondea a 2 decimales evitando errores de coma flotante (0.1 + 0.2). */
export function roundMoney(value: number): number {
  const num = Number(value);
  if (!Number.isFinite(num)) return 0;
  return Math.round((num + Number.EPSILON) * 100) / 100;
}

/** Valor para precargar un campo de monto al editar: máximo 2 decimales, vacío si no hay valor. */
export function toAmountInput(value: number | string | undefined | null): string {
  if (value === undefined || value === null || value === '') return '';
  const num = Number(value);
  return Number.isFinite(num) ? String(roundMoney(num)) : '';
}

// ===== Fechas =====

/** 'YYYY-MM-DD' en hora local. toISOString() usa UTC y adelanta el día por la noche en UTC-5. */
export function toLocalDateStr(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatDate(dateStr: string | undefined): string {
  if (!dateStr) return '';
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleDateString('es-EC', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateShort(dateStr: string | undefined): string {
  if (!dateStr) return '';
  const d = new Date(dateStr + 'T00:00:00');
  return d.toLocaleDateString('es-EC', { day: '2-digit', month: 'short' });
}

export function formatDateTime(dateStr: string | undefined, timeStr?: string): string {
  if (!dateStr) return '';
  const d = new Date(dateStr + 'T' + (timeStr || '00:00'));
  return d.toLocaleDateString('es-EC', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

export function formatPercent(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

/** "1 mes" / "3 meses". */
export function formatMonths(n: number): string {
  return `${n} ${n === 1 ? 'mes' : 'meses'}`;
}

export function getToday(): string {
  return toLocalDateStr(new Date());
}

export function getNow(): string {
  const d = new Date();
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function getMonthName(month: number): string {
  const months = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
  return months[month - 1] || '';
}

export function getWeekNumber(date: Date | string): number {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + 3 - (d.getDay() + 6) % 7);
  const week1 = new Date(d.getFullYear(), 0, 4);
  return 1 + Math.round(((d.getTime() - week1.getTime()) / 86400000 - 3 + (week1.getDay() + 6) % 7) / 7);
}

export function getDaysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

export function daysUntil(dateStr: string): number {
  const now = new Date();
  now.setHours(0, 0, 0, 0);
  const target = new Date(dateStr + 'T00:00:00');
  return Math.ceil((target.getTime() - now.getTime()) / 86400000);
}
