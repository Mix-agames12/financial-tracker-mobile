import { getMonthlySummary, getTotalBalance, SettingsRepo } from '../db/storage';
import { applySavedCurrency } from '../utils/currency';
import { formatCurrency, formatDateShort, getMonthName } from '../utils/formatters';
import { getUpcomingPayments } from '../utils/paymentReminders';

export interface WidgetSummary {
  appName: string;
  monthLabel: string;
  balance: string;
  isNegative: boolean;
  monthIncome: string;
  monthExpenses: string;
  nextPayment: string | null;
  updatedAt: string;
}

/** Datos del widget ya formateados. También corre en el task headless del widget, sin montar App. */
export async function getWidgetSummary(): Promise<WidgetSummary> {
  await applySavedCurrency();
  const now = new Date();
  const [settings, totals, monthly, upcoming] = await Promise.all([
    SettingsRepo.get(),
    getTotalBalance(),
    getMonthlySummary(now.getFullYear(), now.getMonth() + 1),
    getUpcomingPayments(30),
  ]);
  const next = upcoming.find((p) => p.daysLeft >= 0);

  return {
    appName: settings.appName || 'Mi Dinero',
    monthLabel: `${getMonthName(now.getMonth() + 1)} ${now.getFullYear()}`,
    balance: formatCurrency(totals.balance),
    isNegative: totals.balance < 0,
    monthIncome: formatCurrency(monthly.totalIncome),
    monthExpenses: formatCurrency(monthly.totalExpenses),
    nextPayment: next ? `${next.title} · ${formatDateShort(next.dueDate)} · ${formatCurrency(next.amount)}` : null,
    updatedAt: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
  };
}
