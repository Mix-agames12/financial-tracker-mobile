import { CreditCardRepo, ExpenseRepo, LoanRepo } from '../db/storage';
import { CreditCard, Expense, Loan } from '../types';
import { lastDateForDay, nextDateForDay, roundMoney, toLocalDateStr } from './formatters';

/** Sufijo del gasto adicional que registra los impuestos/comisiones de una compra. */
export const TAX_DETAIL_SUFFIX = ' (Impuestos/Comisiones)';

// El préstamo de una compra con tarjeta se crea milisegundos después de su gasto.
const LEGACY_LINK_WINDOW_MS = 60_000;

interface PurchasePlan {
  name: string;
  installments: number;
  nextPaymentDate: string;
}

/**
 * Parte de la compra que sigue cargada en la tarjeta. Los préstamos vinculados la guardan en
 * cardOutstanding para que pagos, ediciones y borrados muevan exactamente lo que se cargó.
 */
export function loanCardOutstanding(loan: Loan): number {
  if (!loan.cardId) return 0;
  const stored = loan.cardOutstanding ?? (loan.status === 'paid' ? 0 : loan.totalAmount);
  return Math.max(0, roundMoney(Number(stored) || 0));
}

/** Suma `delta` al saldo de la tarjeta (negativo para restar), sin bajar de 0. */
async function changeCardBalance(cardId: string | undefined, delta: number): Promise<void> {
  if (!cardId || !delta) return;
  const card = await CreditCardRepo.get(cardId);
  if (!card) return;
  const currentBalance = Math.max(0, roundMoney((Number(card.currentBalance) || 0) + delta));
  await CreditCardRepo.update({ ...card, currentBalance });
}

function purchasePlan(expense: Expense, card: CreditCard, nextPaymentDate: string): PurchasePlan {
  const deferredMonths = expense.isDeferred ? Number(expense.deferredMonths) || 0 : 0;
  return deferredMonths > 0
    ? { name: `Diferido: ${expense.detail}`, installments: deferredMonths, nextPaymentDate }
    : { name: `TC ${card.name}: ${expense.detail}`, installments: 1, nextPaymentDate };
}

/** Carga la compra en la tarjeta y crea su préstamo, vinculado a la tarjeta y al gasto. */
export async function registerCardPurchase(
  expense: Expense,
  card: CreditCard,
  totalCharge: number,
  nextPaymentDate: string
): Promise<void> {
  const charge = roundMoney(totalCharge);
  const plan = purchasePlan(expense, card, nextPaymentDate);
  await changeCardBalance(card.id, charge);
  await LoanRepo.add({
    name: plan.name,
    totalAmount: charge,
    installments: plan.installments,
    paidInstallments: 0,
    monthlyQuota: roundMoney(charge / plan.installments),
    interestRate: 0,
    nextPaymentDate: plan.nextPaymentDate,
    status: 'active',
    cardId: card.id,
    sourceExpenseId: expense.id,
    cardOutstanding: charge,
  });
}

/**
 * Cambia en `delta` el monto de una compra ya registrada (positivo si aumentó): ajusta la tarjeta y
 * recalcula las cuotas pendientes sobre lo que falta pagar del préstamo.
 */
async function adjustPurchaseLoan(loan: Loan, delta: number, plan?: PurchasePlan): Promise<void> {
  const pendingInstallments = Math.max(0, loan.installments - loan.paidInstallments);
  const remainingDebt = loan.status === 'paid' ? 0 : pendingInstallments * loan.monthlyQuota;
  const nextDebt = Math.max(0, roundMoney(remainingDebt + delta));
  const isPaid = nextDebt <= 0;

  const outstanding = loanCardOutstanding(loan);
  const nextOutstanding = isPaid ? 0 : Math.max(0, roundMoney(outstanding + delta));
  await changeCardBalance(loan.cardId, roundMoney(nextOutstanding - outstanding));

  const installments = Math.max(plan?.installments ?? loan.installments, loan.paidInstallments + (isPaid ? 0 : 1));
  const keepDate = loan.paidInstallments > 0 && !!loan.nextPaymentDate;
  await LoanRepo.update({
    ...loan,
    name: plan?.name ?? loan.name,
    totalAmount: Math.max(0, roundMoney(loan.totalAmount + delta)),
    installments,
    monthlyQuota: isPaid ? loan.monthlyQuota : roundMoney(nextDebt / (installments - loan.paidInstallments)),
    cardOutstanding: nextOutstanding,
    status: isPaid ? 'paid' : 'active',
    // Sin cuotas pagadas, la fecha de pago se recalcula con la nueva fecha de compra.
    nextPaymentDate: isPaid ? '' : keepDate ? loan.nextPaymentDate : plan?.nextPaymentDate ?? loan.nextPaymentDate,
  });
}

/** Al pagar una cuota: la resta de la tarjeta y devuelve lo que sigue pendiente en ella. */
export async function applyLoanPaymentToCard(loan: Loan, quota: number, isFinalPayment: boolean): Promise<number | undefined> {
  if (!loan.cardId) return undefined;
  const outstanding = loanCardOutstanding(loan);
  // La última cuota libera todo lo pendiente para no dejar centavos de redondeo en la tarjeta.
  const portion = isFinalPayment ? outstanding : Math.min(roundMoney(quota), outstanding);
  await changeCardBalance(loan.cardId, -portion);
  return roundMoney(outstanding - portion);
}

/** Antes de eliminar un préstamo vinculado: quita de la tarjeta lo que seguía pendiente. */
export async function releaseLoanFromCard(loan: Loan): Promise<void> {
  await changeCardBalance(loan.cardId, -loanCardOutstanding(loan));
}

/**
 * Edición de un préstamo vinculado desde Préstamos: si cambian montos o cuotas, la tarjeta pasa a
 * reflejar lo que queda por pagar. Devuelve el nuevo pendiente, o undefined si no hay que tocarlo.
 */
export async function syncLoanEditWithCard(before: Loan, after: Loan): Promise<number | undefined> {
  if (!before.cardId) return undefined;
  const fields = ['totalAmount', 'interestRate', 'installments', 'paidInstallments'] as const;
  if (fields.every((key) => Number(before[key]) === Number(after[key]))) return undefined;

  const outstanding = loanCardOutstanding(before);
  const next = after.status === 'paid'
    ? 0
    : Math.max(0, roundMoney((after.installments - after.paidInstallments) * after.monthlyQuota));
  await changeCardBalance(before.cardId, roundMoney(next - outstanding));
  return next;
}

/**
 * Mantiene tarjeta y préstamo en sincronía al editar un gasto. Con la misma tarjeta ajusta la
 * diferencia; si cambió la tarjeta o el método de pago, deshace la compra anterior y registra la nueva.
 */
export async function updateCardPurchase(
  before: Expense,
  after: Expense,
  card: CreditCard | undefined,
  nextPaymentDate: string
): Promise<void> {
  const [loans, expenses] = await Promise.all([LoanRepo.getAll(), ExpenseRepo.getAll()]);
  const loan = loans.find((l) => l.sourceExpenseId === before.id);
  const taxRows = expenses.filter((e) => e.parentExpenseId === before.id);
  const delta = roundMoney((Number(after.amount) || 0) - (Number(before.amount) || 0));

  if (loan && card && loan.cardId === card.id) {
    await adjustPurchaseLoan(loan, delta, purchasePlan(after, card, nextPaymentDate));
  } else {
    if (loan) {
      await releaseLoanFromCard(loan);
      await LoanRepo.delete(loan.id);
    }
    if (card) {
      if (!loan && before.paymentMethod === 'Crédito' && before.cardName === card.name) {
        // Compra anterior a este cambio, sin préstamo vinculado: sólo se ajusta la diferencia.
        await changeCardBalance(card.id, delta);
      } else {
        // Los cargos de impuestos ya registrados siguen siendo parte de la compra.
        const taxes = taxRows.reduce((sum, e) => sum + (Number(e.amount) || 0), 0);
        await registerCardPurchase(after, card, (Number(after.amount) || 0) + taxes, nextPaymentDate);
      }
    }
  }

  // Los cargos de impuestos acompañan al gasto principal si cambió su método de pago.
  const methodChanged = before.paymentMethod !== after.paymentMethod
    || before.cardName !== after.cardName
    || before.accountName !== after.accountName;
  if (methodChanged) {
    for (const row of taxRows) {
      await ExpenseRepo.update({ ...row, paymentMethod: after.paymentMethod, cardName: after.cardName, accountName: after.accountName });
    }
  }
}

/**
 * Elimina un gasto manteniendo la tarjeta al día: una compra con tarjeta libera su saldo pendiente
 * y se lleva su préstamo y su cargo de impuestos; un cargo de impuestos se descuenta de su compra.
 */
export async function deleteExpenseWithCardSync(expenseId: string): Promise<void> {
  const [expense, loans] = await Promise.all([ExpenseRepo.get(expenseId), LoanRepo.getAll()]);
  if (!expense) return;

  const loan = loans.find((l) => l.sourceExpenseId === expense.id);
  if (loan) {
    await releaseLoanFromCard(loan);
    await LoanRepo.delete(loan.id);
    const taxRows = (await ExpenseRepo.getAll()).filter((e) => e.parentExpenseId === expense.id);
    for (const row of taxRows) await ExpenseRepo.delete(row.id);
  } else if (expense.parentExpenseId) {
    const parentLoan = loans.find((l) => l.sourceExpenseId === expense.parentExpenseId);
    if (parentLoan) await adjustPurchaseLoan(parentLoan, -(Number(expense.amount) || 0));
  }

  await ExpenseRepo.delete(expense.id);
}

const createdAtMs = (item: object) => Date.parse((item as { createdAt?: string }).createdAt || '');

/**
 * Vincula los préstamos de compras con tarjeta creados antes de existir cardId (idempotente). No
 * cambia saldos: el código anterior nunca descontaba de la tarjeta, así que toda la compra sigue
 * cargada en ella (cardOutstanding = totalAmount) y se libera al pagar la última cuota o al borrarla.
 */
export async function linkLegacyCardLoans(): Promise<void> {
  const [loans, cards, expenses] = await Promise.all([LoanRepo.getAll(), CreditCardRepo.getAll(), ExpenseRepo.getAll()]);
  const legacy = loans.filter((l) => !l.cardId && (l.name.startsWith('TC ') || l.name.startsWith('Diferido: ')));
  if (legacy.length === 0) return;

  const linkedExpenseIds = new Set(loans.map((l) => l.sourceExpenseId).filter((id): id is string => !!id));
  const purchases = expenses.filter((e) =>
    e.paymentMethod === 'Crédito' && !!e.cardName && !e.parentExpenseId && !(e.detail || '').endsWith(TAX_DETAIL_SUFFIX));

  for (const loan of legacy) {
    const deferred = loan.name.startsWith('Diferido: ');
    const namedCard = deferred ? undefined : cards.find((c) => loan.name.startsWith(`TC ${c.name}: `));
    if (!deferred && !namedCard) continue;
    const detail = namedCard ? loan.name.slice(`TC ${namedCard.name}: `.length) : loan.name.slice('Diferido: '.length);

    const candidates = purchases.filter((e) =>
      !linkedExpenseIds.has(e.id)
      && e.detail === detail
      && !!e.isDeferred === deferred
      && (!namedCard || e.cardName === namedCard.name));
    const loanTime = createdAtMs(loan);
    const closest = candidates
      .map((e) => ({ e, gap: Math.abs(createdAtMs(e) - loanTime) }))
      .filter(({ gap }) => gap <= LEGACY_LINK_WINDOW_MS)
      .sort((a, b) => a.gap - b.gap)[0]?.e;
    const match = closest ?? (candidates.length === 1 ? candidates[0] : undefined);

    const card = namedCard ?? cards.find((c) => c.name === match?.cardName);
    if (!card) continue;

    await LoanRepo.update({ ...loan, cardId: card.id, sourceExpenseId: match?.id, cardOutstanding: roundMoney(loan.totalAmount) });
    if (!match) continue;

    linkedExpenseIds.add(match.id);
    const taxRows = expenses.filter((e) =>
      !e.parentExpenseId
      && e.date === match.date
      && e.cardName === match.cardName
      && e.detail === `${match.detail}${TAX_DETAIL_SUFFIX}`);
    for (const row of taxRows) await ExpenseRepo.update({ ...row, parentExpenseId: match.id });
  }
}

/** Lo que queda por pagar de un préstamo según sus cuotas (0 si ya está pagado). */
export function loanRemainingDebt(loan: Loan): number {
  if (loan.status === 'paid') return 0;
  return Math.max(0, roundMoney((loan.installments - loan.paidInstallments) * loan.monthlyQuota));
}

/** Nivel del cupo disponible: más de 30 % verde, entre 10 % y 30 % ámbar, menos de 10 % rojo. */
export function availabilityLevel(availableRatio: number): 'success' | 'warning' | 'danger' {
  if (availableRatio > 0.3) return 'success';
  if (availableRatio >= 0.1) return 'warning';
  return 'danger';
}

export interface CardPurchaseItem {
  loan: Loan;
  expense?: Expense;
  title: string;
  pending: number;
  isPaid: boolean;
}

export interface CardCycleSummary {
  lastCutOff: string | null; // último corte (null si la tarjeta no tiene día de corte)
  nextDueDate: string | null; // próximo día de pago de la tarjeta
  dueAmount: number; // vence en el próximo pago: compras hasta el último corte y cuotas atrasadas
  activeDebt: number; // resto pendiente: compras después del corte y cuotas siguientes
  totalPending: number;
  available: number | null; // cupo disponible (null si la tarjeta no tiene límite)
  hasOverdue: boolean;
  purchases: CardPurchaseItem[]; // pendientes por vencimiento, luego las pagadas
}

function purchaseTitle(loan: Loan, card: CreditCard, expense?: Expense): string {
  if (expense?.detail) return expense.detail;
  const prefix = loan.name.startsWith('Diferido: ') ? 'Diferido: ' : `TC ${card.name}: `;
  return loan.name.startsWith(prefix) ? loan.name.slice(prefix.length) : loan.name;
}

/**
 * Resume las compras de una tarjeta por ciclo de facturación. La fecha de pago de cada compra ya
 * refleja el corte (las posteriores al corte vencen un mes después), así que lo que vence hasta el
 * próximo día de pago es "por pagar" y el resto es deuda activa.
 */
export function getCardCycleSummary(
  card: CreditCard,
  loans: Loan[],
  expensesById: Map<string, Expense>,
  today: Date = new Date()
): CardCycleSummary {
  const nextDueDate = card.paymentDueDay ? nextDateForDay(card.paymentDueDay, today) : null;
  const todayStr = toLocalDateStr(today);
  let dueAmount = 0;
  let totalPending = 0;
  let hasOverdue = false;

  const purchases = loans
    .filter((loan) => loan.cardId === card.id)
    .map((loan) => {
      const expense = loan.sourceExpenseId ? expensesById.get(loan.sourceExpenseId) : undefined;
      const pending = loanRemainingDebt(loan);
      const isPaid = pending <= 0;
      if (!isPaid) {
        totalPending += pending;
        const due = loan.nextPaymentDate;
        if (due && due < todayStr) hasOverdue = true;
        if (due && nextDueDate && due <= nextDueDate) dueAmount += Math.min(roundMoney(loan.monthlyQuota), pending);
      }
      return { loan, expense, title: purchaseTitle(loan, card, expense), pending, isPaid };
    })
    .sort((a, b) => {
      if (a.isPaid !== b.isPaid) return a.isPaid ? 1 : -1;
      if (!a.isPaid) return (a.loan.nextPaymentDate || '9999').localeCompare(b.loan.nextPaymentDate || '9999');
      return (b.expense?.date || '').localeCompare(a.expense?.date || '');
    });

  return {
    lastCutOff: card.cutOffDay ? lastDateForDay(card.cutOffDay, today) : null,
    nextDueDate,
    dueAmount: roundMoney(dueAmount),
    activeDebt: roundMoney(totalPending - dueAmount),
    totalPending: roundMoney(totalPending),
    available: card.creditLimit > 0 ? Math.max(0, roundMoney(card.creditLimit - (Number(card.currentBalance) || 0))) : null,
    hasOverdue,
    purchases,
  };
}
