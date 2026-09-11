import { CreditCardRepo, ExpenseRepo, LoanRepo } from '../db/storage';
import { loanCardOutstanding, TAX_DETAIL_SUFFIX } from './cardPurchases';
import { daysUntil, roundMoney, toLocalDateStr } from './formatters';

export type UpcomingPaymentKind = 'loan' | 'card' | 'recurring';

export interface UpcomingPayment {
  id: string;
  kind: UpcomingPaymentKind;
  title: string;
  amount: number;
  dueDate: string; // 'YYYY-MM-DD'
  daysLeft: number; // negativo = vencido
}

/** Próxima fecha (hoy o posterior) con ese día del mes; en meses cortos usa el último día. */
export function nextDateForDay(day: number, from: Date = new Date()): string {
  const safeDay = Math.min(Math.max(Math.trunc(day) || 1, 1), 31);
  const today = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  const year = today.getFullYear();
  const month = today.getMonth();
  const thisMonth = new Date(year, month, Math.min(safeDay, new Date(year, month + 1, 0).getDate()));
  if (thisMonth >= today) return toLocalDateStr(thisMonth);
  return toLocalDateStr(new Date(year, month + 1, Math.min(safeDay, new Date(year, month + 2, 0).getDate())));
}

/**
 * Pagos pendientes dentro de `horizonDays` días, ordenados por fecha.
 * Incluye préstamos vencidos (daysLeft < 0) para poder mostrarlos en la app.
 */
export async function getUpcomingPayments(horizonDays = 30): Promise<UpcomingPayment[]> {
  const [allLoans, cards, expenses] = await Promise.all([
    LoanRepo.getAll(),
    CreditCardRepo.getAll(),
    ExpenseRepo.getAll(),
  ]);
  const payments: UpcomingPayment[] = [];

  allLoans.filter((loan) => loan.status !== 'paid').forEach((loan) => {
    if (!loan.nextPaymentDate || loan.paidInstallments >= loan.installments) return;
    payments.push({
      id: `loan-${loan.id}`,
      kind: 'loan',
      title: loan.name,
      amount: Number(loan.monthlyQuota) || 0,
      dueDate: loan.nextPaymentDate,
      daysLeft: daysUntil(loan.nextPaymentDate),
    });
  });

  // Lo que cargaron las compras registradas en la app ya tiene su aviso como préstamo vinculado
  // (cardId); la tarjeta sólo avisa del saldo ingresado manualmente.
  cards.forEach((card) => {
    if (!card.paymentDueDay) return;
    const tracked = allLoans
      .filter((l) => l.cardId === card.id)
      .reduce((sum, l) => sum + loanCardOutstanding(l), 0);
    const manualBalance = roundMoney((Number(card.currentBalance) || 0) - tracked);
    if (manualBalance <= 0) return;
    const dueDate = nextDateForDay(card.paymentDueDay);
    payments.push({
      id: `card-${card.id}`,
      kind: 'card',
      title: `Tarjeta ${card.name}`,
      amount: manualBalance,
      dueDate,
      daysLeft: daysUntil(dueDate),
    });
  });

  // Un gasto recurrente se registra varias veces (una por mes): se toma el más reciente de cada uno.
  const seen = new Set<string>();
  expenses
    .filter((e) => e.isRecurring && !e.parentExpenseId && !(e.detail || '').endsWith(TAX_DETAIL_SUFFIX))
    .sort((a, b) => (b.date || '').localeCompare(a.date || ''))
    .forEach((exp) => {
      const day = exp.recurringFrequency === 'specific' && exp.recurringDay
        ? exp.recurringDay
        : Number((exp.date || '').split('-')[2]);
      if (!day) return;
      const key = `${exp.detail}|${exp.category}|${day}`.toLowerCase();
      if (seen.has(key)) return;
      seen.add(key);
      const dueDate = nextDateForDay(day);
      payments.push({
        id: `recurring-${exp.id}`,
        kind: 'recurring',
        title: exp.detail || exp.category,
        amount: Number(exp.amount) || 0,
        dueDate,
        daysLeft: daysUntil(dueDate),
      });
    });

  return payments
    .filter((p) => p.daysLeft <= horizonDays)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
}
