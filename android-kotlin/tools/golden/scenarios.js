// Escenarios de los vectores dorados. Cada uno siembra el almacén, ejecuta una secuencia de ops con la
// lógica original (ver ops.js) y guarda resultados + estado final. GoldenRunner.kt repite los mismos pasos
// contra la versión Kotlin y compara. Reloj por defecto: martes 2026-11-17 12:00 (hora local sin DST).
'use strict';

// ---- Constructores de datos ----
const acc = (id, name, bankName, initialBalance = 0, accountType = 'Ahorro') =>
  ({ id, name, bankName, accountType, initialBalance, referenceNumber: '', createdAt: '2026-01-01T00:00:00.000Z' });
const card = (id, name, bankName, cutOffDay, paymentDueDay, creditLimit, currentBalance) =>
  ({ id, name, bankName, cutOffDay, paymentDueDay, creditLimit, currentBalance, createdAt: '2026-01-01T00:00:00.000Z' });
const income = (id, amount, source, date, bankAccount, extra = {}) =>
  ({ id, amount, source, detail: source, bankAccount, bankName: (bankAccount.split(' - ')[1]) || '', date, createdAt: '2026-01-01T00:00:00.000Z', ...extra });
const expense = (id, amount, date, category, detail, paymentMethod, extra = {}) =>
  ({ id, amount, date, category, detail, paymentMethod, createdAt: '2026-01-01T00:00:00.000Z', ...extra });
const loan = (id, name, totalAmount, installments, paidInstallments, monthlyQuota, extra = {}) =>
  ({ id, name, totalAmount, installments, paidInstallments, monthlyQuota, interestRate: 0, status: 'active', createdAt: '2026-01-01T00:00:00.000Z', ...extra });
const settings = (extra = {}) =>
  ({ id: 'default', appName: 'Mi Dinero', monthlyGoal: 0, theme: 'dark', currency: 'USD', currencyLocale: 'es-EC', onboardingCompleted: true, ...extra });

const TAXES = { enabled: true, ivaRate: 15, comisionRate: 2, isdRate: 5, applyTo: ['Gastos:Tecnología', 'Préstamos', 'Inversiones'] };

const expenseForm = (over = {}) => ({
  amountStr: '100', categoryType: 'Alimentación', detail: 'Compra', paymentMethod: 'Efectivo',
  selectedDebit: '', selectedCredit: '', isDeferred: false, deferredMonths: '', dateStr: '2026-11-17', timeStr: '12:00',
  tags: '', isRecurring: false, recurringFrequency: 'monthly', recurringDay: '', recurringDate: '', subscriptionType: '',
  ...over,
});

const fn = (name, ...args) => ({ op: 'fn', args: { name, args } });
const js = (kind, values, digits) => ({ op: 'js', args: { kind, values, digits } });
const d = (iso) => ({ $date: iso });

const scenarios = [];

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'js-and-formatters',
  description: 'Semántica JS (toFixed, String(number), parseFloat/parseInt, Math.round) y helpers puros de formatters.ts',
  steps: [
    js('toFixed', [1.005, 2.675, 0.125, 0.25, 1.45, 8.345, 10.075, 0, 123456.789, 0.1 + 0.2, 1e-7, -1.005, -0.5, 99.995], 2),
    js('toFixed', [12.345, 0.05, 0.25, 0.125, 33.35, 99.95, 100, 0.0005], 1),
    js('toString', [1500, 12.5, 0.1, 15000000, 123456789.123, 0.000001, 1e-7, 1e21, 1.5e300, -0.0, 100, 3.14159, 2.5e-5, 123456789012345680000]),
    js('parseFloat', ['12abc', '', '.5', '5.', '1e3', '  7.5', 'abc', '-3.2x', '0x10', '1.2.3', '-', '+4']),
    js('parseInt', ['12.7', '', 'abc', '007', '1e3', ' 42', '-5', '3,5', '99999999999']),
    js('round', [0.5, 1.5, 2.5, -0.5, -1.5, -2.5, 2.4999999, 1e15 + 0.5]),
    fn('roundMoney', 0.30000000000000004), fn('roundMoney', 1.005), fn('roundMoney', 2.675), fn('roundMoney', -1.005),
    fn('roundMoney', 1234.5678), fn('roundMoney', 0.285), fn('roundMoney', 'abc'), fn('roundMoney', 5),
    fn('toAmountInput', null), fn('toAmountInput', ''), fn('toAmountInput', 12.5), fn('toAmountInput', 1500),
    fn('toAmountInput', '12.345'), fn('toAmountInput', 'abc'), fn('toAmountInput', 15000000), fn('toAmountInput', 0.1),
    fn('toAmountInput', 0), fn('toAmountInput', 1234567.891),
    fn('sanitizeDecimalInput', '12,5'), fn('sanitizeDecimalInput', '1.2.3'), fn('sanitizeDecimalInput', 'abc'),
    fn('sanitizeDecimalInput', '.5'), fn('sanitizeDecimalInput', '12.345'), fn('sanitizeDecimalInput', ''),
    fn('sanitizeDecimalInput', '0.'), fn('sanitizeDecimalInput', '5.5.5'), fn('sanitizeDecimalInput', '1,234.56'),
    fn('sanitizeDecimalInput', '3.14159', 4), fn('sanitizeDecimalInput', '7.', 0),
    fn('sanitizeIntegerInput', '12a3'), fn('sanitizeIntegerInput', '--'), fn('sanitizeIntegerInput', '0042'),
    fn('toLocalDateStr', d('2026-03-05T23:59:00')), fn('toLocalDateStr', d('2026-12-31T00:00:00')),
    fn('formatMonths', 1), fn('formatMonths', 3), fn('formatMonths', 0),
    fn('getMonthName', 1), fn('getMonthName', 12), fn('getMonthName', 0), fn('getMonthName', 13),
    fn('getWeekNumber', d('2026-01-01T10:00:00')), fn('getWeekNumber', d('2026-11-17T10:00:00')),
    fn('getWeekNumber', d('2026-12-31T10:00:00')), fn('getWeekNumber', d('2027-01-03T10:00:00')),
    fn('getWeekNumber', d('2024-12-30T10:00:00')), fn('getWeekNumber', d('2021-01-03T10:00:00')),
    fn('getDaysInMonth', 2026, 2), fn('getDaysInMonth', 2028, 2), fn('getDaysInMonth', 2026, 11), fn('getDaysInMonth', 2026, 12),
    fn('daysUntil', '2026-11-17'), fn('daysUntil', '2026-11-18'), fn('daysUntil', '2026-11-16'), fn('daysUntil', '2027-02-28'),
    fn('daysUntil', '2025-12-25'),
    fn('nextDateForDay', 15, d('2026-11-17T12:00:00')), fn('nextDateForDay', 17, d('2026-11-17T12:00:00')),
    fn('nextDateForDay', 31, d('2026-11-17T12:00:00')), fn('nextDateForDay', 31, d('2026-01-31T12:00:00')),
    fn('nextDateForDay', 30, d('2026-02-10T12:00:00')), fn('nextDateForDay', 31, d('2026-12-20T12:00:00')),
    fn('nextDateForDay', 0, d('2026-11-17T12:00:00')), fn('nextDateForDay', 45, d('2026-11-17T12:00:00')),
    fn('nextDateForDay', 5),
    fn('lastDateForDay', 15, d('2026-11-17T12:00:00')), fn('lastDateForDay', 20, d('2026-11-17T12:00:00')),
    fn('lastDateForDay', 31, d('2026-03-01T12:00:00')), fn('lastDateForDay', 31, d('2026-01-10T12:00:00')),
    fn('lastDateForDay', 17, d('2026-11-17T12:00:00')),
    fn('nextDateForMonthDay', 12, 25, d('2026-11-17T12:00:00')), fn('nextDateForMonthDay', 2, 29, d('2026-11-17T12:00:00')),
    fn('nextDateForMonthDay', 2, 29, d('2027-03-01T12:00:00')), fn('nextDateForMonthDay', 11, 17, d('2026-11-17T12:00:00')),
    fn('nextDateForMonthDay', 11, 16, d('2026-11-17T12:00:00')), fn('nextDateForMonthDay', 13, 5, d('2026-11-17T12:00:00')),
    fn('getToday'), fn('getNow'),
    fn('formatPercent', 0.12345), fn('formatPercent', 0.0025), fn('formatPercent', 0.00125), fn('formatPercent', 1),
    fn('formatPercent', 0),
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'card-flow-deferred',
  description: 'Compra diferida con impuestos en tarjeta: alta, resumen de ciclo, pago de cuota, edición, validaciones y borrado',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco Pichincha', 1000)],
    creditCards: [card('card-1', 'Visa Oro', 'Banco X', 15, 5, 5000, 200)],
    settings: [settings()],
  },
  steps: [
    { op: 'saveExpense', args: { as: 'e1', editingId: null, taxesConfig: TAXES, form: expenseForm({
      amountStr: '300', categoryType: 'Tecnología', detail: 'Laptop', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro',
      isDeferred: true, deferredMonths: '3', dateStr: '2026-11-10', timeStr: '10:30', tags: 'trabajo, equipo' }) } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'loanDerived', args: { loanOfExpense: '$e1' } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'Ahorros', taxesConfig: TAXES } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'loanDerived', args: { loanOfExpense: '$e1' } },
    { op: 'saveExpense', args: { editingId: '$e1', taxesConfig: TAXES, form: expenseForm({
      amountStr: '420', categoryType: 'Tecnología', detail: 'Laptop gamer', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro',
      isDeferred: true, deferredMonths: '4', dateStr: '2026-11-10', timeStr: '10:30', tags: 'trabajo' }) } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'getTotalBalance', args: {} },
    { op: 'getAccountBalances', args: {} },
    { op: 'saveExpense', args: { editingId: null, taxesConfig: TAXES, form: expenseForm({
      amountStr: '9000', categoryType: 'Tecnología', detail: 'Carro', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro' }) } },
    { op: 'saveExpense', args: { editingId: null, taxesConfig: TAXES, form: expenseForm({
      amountStr: '5000', categoryType: 'Alimentación', detail: 'Banquete', paymentMethod: 'Débito', selectedDebit: 'Ahorros' }) } },
    { op: 'saveExpense', args: { editingId: null, taxesConfig: null, form: expenseForm({ amountStr: '0' }) } },
    { op: 'saveExpense', args: { editingId: null, taxesConfig: null, form: expenseForm({ categoryType: '' }) } },
    { op: 'saveExpense', args: { editingId: null, taxesConfig: null, form: expenseForm({ detail: '   ' }) } },
    { op: 'deleteExpenseWithCardSync', args: { expenseId: '$e1' } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'getTotalBalance', args: {} },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'card-single-change-method',
  description: 'Compra a crédito sin diferir; se cambia de tarjeta, luego a débito y se elimina',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco Pichincha', 2000)],
    creditCards: [card('card-1', 'Visa Oro', 'Banco X', 15, 5, 3000, 0), card('card-2', 'Mastercard', 'Banco Y', 28, 12, 1000, 100)],
    settings: [settings()],
  },
  steps: [
    { op: 'saveExpense', args: { as: 'e1', editingId: null, taxesConfig: null, form: expenseForm({
      amountStr: '120.55', categoryType: 'Ropa', detail: 'Zapatos', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro', dateStr: '2026-11-20' }) } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'saveExpense', args: { editingId: '$e1', taxesConfig: null, form: expenseForm({
      amountStr: '120.55', categoryType: 'Ropa', detail: 'Zapatos', paymentMethod: 'Crédito', selectedCredit: 'Mastercard', dateStr: '2026-11-20' }) } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-2' } },
    { op: 'saveExpense', args: { editingId: '$e1', taxesConfig: null, form: expenseForm({
      amountStr: '99', categoryType: 'Ropa', detail: 'Zapatos', paymentMethod: 'Débito', selectedDebit: 'Ahorros', dateStr: '2026-11-20' }) } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-2' } },
    { op: 'getTotalBalance', args: {} },
    { op: 'saveExpense', args: { editingId: '$e1', taxesConfig: null, form: expenseForm({
      amountStr: '99', categoryType: 'Ropa', detail: 'Zapatos', paymentMethod: 'Crédito', selectedCredit: 'Mastercard', dateStr: '2026-12-31',
      isRecurring: true, recurringFrequency: 'yearly', recurringDate: '2026-12-31' }) } },
    { op: 'deleteExpenseWithCardSync', args: { expenseId: '$e1' } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-2' } },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'card-tax-row',
  description: 'Se elimina el cargo de impuestos de una compra con tarjeta: la deuda de la compra baja; luego se elimina la compra',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco Pichincha', 5000)],
    creditCards: [card('card-1', 'Visa Oro', 'Banco X', 15, 5, 5000, 0)],
    settings: [settings()],
  },
  steps: [
    { op: 'saveExpense', args: { as: 'e1', editingId: null, taxesConfig: TAXES, form: expenseForm({
      amountStr: '1000', categoryType: 'Tecnología', detail: 'Monitor', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro',
      isDeferred: true, deferredMonths: '6', dateStr: '2026-11-05' }) } },
    { op: 'refExpense', args: { as: 't1', match: { detail: 'Monitor (Impuestos/Comisiones)' } } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'deleteExpenseWithCardSync', args: { expenseId: '$t1' } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'loanDerived', args: { loanOfExpense: '$e1' } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'Ahorros', taxesConfig: TAXES } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'Ahorros', taxesConfig: TAXES } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: '', taxesConfig: TAXES } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'No existe', taxesConfig: TAXES } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'deleteExpenseWithCardSync', args: { expenseId: '$e1' } },
    { op: 'getTotalBalance', args: {} },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'card-pay-all-installments',
  description: 'Una compra a 3 cuotas se paga completa: la última cuota libera lo pendiente de la tarjeta y el préstamo queda pagado',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco Pichincha', 3000)],
    creditCards: [card('card-1', 'Visa Oro', 'Banco X', 10, 30, 2000, 0)],
    settings: [settings()],
  },
  steps: [
    { op: 'saveExpense', args: { as: 'e1', editingId: null, taxesConfig: null, form: expenseForm({
      amountStr: '100', categoryType: 'Otros', detail: 'Compra en tres', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro',
      isDeferred: true, deferredMonths: '3', dateStr: '2026-01-31' }) } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'Ahorros', taxesConfig: null } },
    { op: 'loanDerived', args: { loanOfExpense: '$e1' } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'Ahorros', taxesConfig: null } },
    { op: 'payInstallment', args: { loanOfExpense: '$e1', paymentAccount: 'Ahorros', taxesConfig: null } },
    { op: 'loanDerived', args: { loanOfExpense: '$e1' } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'getAccountBalances', args: {} },
    { op: 'saveExpense', args: { editingId: '$e1', taxesConfig: null, form: expenseForm({
      amountStr: '150', categoryType: 'Otros', detail: 'Compra en tres', paymentMethod: 'Crédito', selectedCredit: 'Visa Oro',
      isDeferred: true, deferredMonths: '3', dateStr: '2026-01-31' }) } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'legacy-link',
  description: 'Vinculación de préstamos de compras con tarjeta anteriores a cardId (idempotente)',
  seed: {
    creditCards: [card('card-1', 'Visa Oro', 'Banco X', 15, 5, 5000, 1000), card('card-2', 'Mastercard', 'Banco Y', 20, 10, 0, 800)],
    expenses: [
      expense('e-a', 500, '2026-09-10', 'Vivienda', 'Nevera', 'Crédito', { cardName: 'Visa Oro', time: '15:00', createdAt: '2026-09-10T15:00:00.000Z' }),
      expense('e-a-tax', 15, '2026-09-10', 'Vivienda', 'Nevera (Impuestos/Comisiones)', 'Crédito', { cardName: 'Visa Oro', createdAt: '2026-09-10T15:00:00.050Z' }),
      expense('e-b', 600, '2026-10-01', 'Tecnología', 'Celular', 'Crédito', { cardName: 'Mastercard', isDeferred: true, deferredMonths: 6, createdAt: '2026-10-01T09:00:00.000Z' }),
      expense('e-c1', 30, '2026-10-05', 'Alimentación', 'Pizza', 'Crédito', { cardName: 'Visa Oro', createdAt: '2026-10-05T20:00:00.000Z' }),
      expense('e-c2', 30, '2026-10-06', 'Alimentación', 'Pizza', 'Crédito', { cardName: 'Visa Oro', createdAt: '2026-10-06T20:00:00.000Z' }),
      expense('e-d', 80, '2026-10-07', 'Alimentación', 'Sushi', 'Crédito', { cardName: 'Visa Oro', createdAt: '2026-10-07T20:00:00.000Z' }),
      expense('e-e', 40, '2026-10-08', 'Salud', 'Vitaminas', 'Débito', { accountName: 'Ahorros', createdAt: '2026-10-08T20:00:00.000Z' }),
    ],
    loans: [
      loan('l-a', 'TC Visa Oro: Nevera', 515, 1, 0, 515, { nextPaymentDate: '2026-10-05', createdAt: '2026-09-10T15:00:00.200Z' }),
      loan('l-b', 'Diferido: Celular', 600, 6, 1, 100, { nextPaymentDate: '2026-12-10', createdAt: '2026-10-01T09:00:00.300Z' }),
      loan('l-c', 'TC Visa Oro: Pizza', 30, 1, 0, 30, { nextPaymentDate: '2026-11-05', createdAt: '2026-10-05T21:30:00.000Z' }),
      loan('l-d', 'TC Visa Oro: Sushi', 80, 1, 0, 80, { nextPaymentDate: '2026-11-05', createdAt: '2026-10-07T20:00:30.000Z' }),
      loan('l-e', 'TC Desconocida: Algo', 99, 1, 0, 99, { createdAt: '2026-10-07T20:00:30.000Z' }),
      loan('l-f', 'Préstamo normal', 1000, 10, 2, 100, { nextPaymentDate: '2026-12-01' }),
      loan('l-g', 'Diferido: Sin compra', 200, 2, 0, 100, { nextPaymentDate: '2026-12-01', createdAt: '2026-10-09T00:00:00.000Z' }),
    ],
    settings: [settings()],
  },
  steps: [
    { op: 'linkLegacyCardLoans', args: {} },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-2' } },
    { op: 'linkLegacyCardLoans', args: {} },
    { op: 'getUpcomingPayments', args: { horizonDays: 60 } },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'upcoming-payments',
  description: 'Próximos pagos: préstamos, saldo manual de tarjetas y gastos recurrentes (mensual, día específico, anual)',
  seed: {
    creditCards: [
      card('card-1', 'Visa Oro', 'Banco X', 15, 25, 5000, 300),
      card('card-2', 'Mastercard', 'Banco Y', 5, 3, 2000, 500),
      card('card-3', 'Sin día', 'Banco Z', 0, 0, 1000, 700),
      card('card-4', 'Cero', 'Banco W', 1, 31, 1000, 0),
    ],
    loans: [
      loan('l-1', 'Préstamo Auto', 15000, 24, 3, 700, { nextPaymentDate: '2026-11-20' }),
      loan('l-2', 'Vencido', 1000, 10, 1, 100, { nextPaymentDate: '2026-11-10' }),
      loan('l-3', 'Lejano', 5000, 10, 0, 500, { nextPaymentDate: '2027-03-01' }),
      loan('l-4', 'Pagado', 500, 5, 5, 100, { status: 'paid', nextPaymentDate: '2026-11-18' }),
      loan('l-5', 'Sin fecha', 500, 5, 1, 100),
      loan('l-6', 'Cuotas completas', 500, 5, 5, 100, { nextPaymentDate: '2026-11-19' }),
      loan('l-7', 'TC Mastercard: Cel', 300, 1, 0, 300, { cardId: 'card-2', sourceExpenseId: 'e-x', cardOutstanding: 300, nextPaymentDate: '2026-12-03' }),
      loan('l-8', 'Hoy', 200, 2, 0, 100, { nextPaymentDate: '2026-11-17' }),
    ],
    expenses: [
      expense('r-1', 12.99, '2026-10-12', 'Suscripción', 'Netflix', 'Crédito', { isRecurring: true, recurringFrequency: 'monthly', cardName: 'Visa Oro' }),
      expense('r-1b', 12.99, '2026-09-12', 'Suscripción', 'Netflix', 'Crédito', { isRecurring: true, recurringFrequency: 'monthly' }),
      expense('r-2', 40, '2026-11-02', 'Salud', 'Gimnasio', 'Débito', { isRecurring: true, recurringFrequency: 'specific', recurringDay: 30 }),
      expense('r-3', 250, '2026-03-15', 'Servicios', 'Seguro', 'Débito', { isRecurring: true, recurringFrequency: 'yearly', recurringDay: 25, recurringMonth: 12 }),
      expense('r-4', 90, '2026-02-28', 'Servicios', 'Dominio', 'Débito', { isRecurring: true, recurringFrequency: 'yearly', recurringDay: 29, recurringMonth: 2 }),
      expense('r-5', 15, '2026-11-03', 'Servicios', 'Netflix (Impuestos/Comisiones)', 'Crédito', { isRecurring: true, recurringFrequency: 'monthly' }),
      expense('r-6', 8, '2026-11-04', 'Servicios', 'Hijo', 'Crédito', { isRecurring: true, recurringFrequency: 'monthly', parentExpenseId: 'e-x' }),
      expense('r-7', 20, '2026-11-31', 'Otros', 'Fecha rara', 'Efectivo', { isRecurring: true, recurringFrequency: 'monthly' }),
      expense('r-8', 5, '2026-10-17', 'Otros', '', 'Efectivo', { isRecurring: true, recurringFrequency: 'monthly' }),
      expense('x-1', 60, '2026-11-01', 'Otros', 'No recurrente', 'Efectivo'),
    ],
    settings: [settings()],
  },
  steps: [
    { op: 'getUpcomingPayments', args: { horizonDays: 30 } },
    { op: 'getUpcomingPayments', args: { horizonDays: 7 } },
    { op: 'getUpcomingPayments', args: { horizonDays: 60 } },
    { op: 'getUpcomingPayments', args: { horizonDays: 400 } },
    { op: 'setClock', args: { at: '2026-12-31T08:00:00' } },
    { op: 'getUpcomingPayments', args: { horizonDays: 45 } },
    { op: 'setClock', args: { at: '2027-02-28T23:30:00' } },
    { op: 'getUpcomingPayments', args: { horizonDays: 30 } },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'aggregates',
  description: 'Balance total, saldos por cuenta y resumen mensual',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco X', 1000), acc('acc-2', 'Corriente', 'Banco Y', 250.5), acc('acc-3', 'Aho', 'Banco Z', 0)],
    income: [
      income('i-1', 1500, 'Sueldo', '2026-11-05', 'Ahorros - Banco X', { isSalary: true }),
      income('i-2', 200.25, 'Venta', '2026-11-10', 'Corriente - Banco Y'),
      income('i-3', 80, 'Regalo', '2026-10-30', 'Ahorros - Banco X'),
      income('i-4', 50, 'Sin cuenta', '2026-11-12', ''),
      income('i-5', 10, 'Otro banco', '2026-02-28', 'Aho - Banco Z'),
    ],
    expenses: [
      expense('e-1', 100, '2026-11-02', 'Alimentación', 'Super', 'Débito', { accountName: 'Ahorros' }),
      expense('e-2', 30.5, '2026-11-17', 'Transporte', 'Taxi', 'Efectivo'),
      expense('e-3', 200, '2026-11-20', 'Tecnología', 'Auricular', 'Crédito', { cardName: 'Visa Oro' }),
      expense('e-4', 45, '2026-10-31', 'Salud', 'Farmacia', 'Débito', { accountName: 'Corriente' }),
      expense('e-5', 12, '2026-02-28', 'Otros', 'Algo', 'Débito', { accountName: 'Aho' }),
      expense('e-6', 7, '2026-11-30', '', 'Sin categoría', ''),
      expense('e-7', 3, '2026-11-30', 'Alimentación', 'Café', 'Débito'),
    ],
    salary: [{ id: 'salary-1', amount: 1500, payDateType: 'specific', payDate: '5', recurrence: 'Mensual', bankAccount: 'Ahorros' }],
    settings: [settings()],
  },
  steps: [
    { op: 'getTotalBalance', args: {} },
    { op: 'getAccountBalances', args: {} },
    { op: 'getMonthlySummary', args: { year: 2026, month: 11 } },
    { op: 'getMonthlySummary', args: { year: 2026, month: 10 } },
    { op: 'getMonthlySummary', args: { year: 2026, month: 2 } },
    { op: 'getMonthlySummary', args: { year: 2027, month: 1 } },
    { op: 'getMonthlySummary', args: { year: 2028, month: 2 } },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'loans-and-cards-forms',
  description: 'Alta/edición/borrado de préstamos (incluidos los vinculados a tarjeta) y validaciones de tarjetas',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco X', 20000)],
    creditCards: [card('card-1', 'Visa Oro', 'Banco X', 15, 5, 5000, 800)],
    loans: [
      loan('l-card', 'TC Visa Oro: Compra', 800, 4, 1, 200, { cardId: 'card-1', sourceExpenseId: 'e-1', cardOutstanding: 600, nextPaymentDate: '2026-12-05' }),
    ],
    expenses: [expense('e-1', 800, '2026-10-20', 'Tecnología', 'Compra', 'Crédito', { cardName: 'Visa Oro' })],
    settings: [settings()],
  },
  steps: [
    { op: 'saveLoan', args: { as: 'l1', editingLoanId: null, form: { name: 'Préstamo Auto', totalAmount: '15000', interestRate: '10.5', installments: '24', paidInstallments: '0', nextPaymentDate: '2026-12-31' } } },
    { op: 'saveLoan', args: { editingLoanId: null, form: { name: '', totalAmount: '15000', interestRate: '', installments: '24', paidInstallments: '0', nextPaymentDate: '' } } },
    { op: 'saveLoan', args: { editingLoanId: null, form: { name: 'X', totalAmount: '0', interestRate: '', installments: '24', paidInstallments: '0', nextPaymentDate: '' } } },
    { op: 'saveLoan', args: { editingLoanId: null, form: { name: 'X', totalAmount: '100', interestRate: '', installments: '0', paidInstallments: '0', nextPaymentDate: '' } } },
    { op: 'saveLoan', args: { editingLoanId: null, form: { name: 'X', totalAmount: '100', interestRate: '', installments: '4', paidInstallments: '5', nextPaymentDate: '' } } },
    { op: 'saveLoan', args: { as: 'l2', editingLoanId: null, form: { name: 'Pagado', totalAmount: '100', interestRate: '0', installments: '4', paidInstallments: '4', nextPaymentDate: '' } } },
    { op: 'payInstallment', args: { loanId: '$l1', paymentAccount: 'Ahorros', taxesConfig: TAXES } },
    { op: 'payInstallment', args: { loanId: '$l1', paymentAccount: 'Ahorros', taxesConfig: null } },
    { op: 'saveLoan', args: { editingLoanId: '$l1', form: { name: 'Préstamo Auto 2', totalAmount: '15000', interestRate: '9', installments: '36', paidInstallments: '2', nextPaymentDate: '2027-02-28' } } },
    { op: 'saveLoan', args: { editingLoanId: 'l-card', form: { name: 'TC Visa Oro: Compra', totalAmount: '800', interestRate: '0', installments: '8', paidInstallments: '1', nextPaymentDate: '2026-12-05' } } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'saveLoan', args: { editingLoanId: 'l-card', form: { name: 'TC Visa Oro: Compra', totalAmount: '800', interestRate: '0', installments: '8', paidInstallments: '8', nextPaymentDate: '' } } },
    { op: 'getCardCycleSummary', args: { cardId: 'card-1' } },
    { op: 'deleteLoan', args: { loanId: 'l-card' } },
    { op: 'deleteLoan', args: { loanId: '$l1' } },
    { op: 'saveCreditCard', args: { as: 'c1', editingCardId: null, form: { name: 'Nueva', bankName: 'B', creditLimit: '5000', available: '3750.50', cutOffDay: '15', paymentDay: '5' } } },
    { op: 'saveCreditCard', args: { editingCardId: null, form: { name: '', bankName: 'B', creditLimit: '5000', available: '1', cutOffDay: '', paymentDay: '' } } },
    { op: 'saveCreditCard', args: { editingCardId: null, form: { name: 'N', bankName: 'B', creditLimit: '0', available: '1', cutOffDay: '', paymentDay: '' } } },
    { op: 'saveCreditCard', args: { editingCardId: null, form: { name: 'N', bankName: 'B', creditLimit: '100', available: '', cutOffDay: '', paymentDay: '' } } },
    { op: 'saveCreditCard', args: { editingCardId: null, form: { name: 'N', bankName: 'B', creditLimit: '100', available: '200', cutOffDay: '', paymentDay: '' } } },
    { op: 'saveCreditCard', args: { editingCardId: null, form: { name: 'N', bankName: 'B', creditLimit: '100', available: '50', cutOffDay: '32', paymentDay: '5' } } },
    { op: 'saveCreditCard', args: { editingCardId: '$c1', form: { name: 'Nueva editada', bankName: 'B2', creditLimit: '6000', available: '0', cutOffDay: '0', paymentDay: '31' } } },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'investment-deposit',
  description: 'Depósito mensual de un fondo de inversión con y sin impuestos; cuenta sin fondos',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco X', 300), acc('acc-2', 'Pobre', 'Banco Y', 10)],
    investments: [
      { id: 'inv-1', entity: 'GBM+', monthlyDeposit: 200, totalMonths: 24, remainingMonths: 2, detail: 'Retiro', totalDeposited: 400, createdAt: '2026-01-01T00:00:00.000Z' },
      { id: 'inv-2', entity: 'Otro', monthlyDeposit: 50, totalMonths: 0, remainingMonths: 0, detail: '', totalDeposited: 0, createdAt: '2026-01-01T00:00:00.000Z' },
    ],
    settings: [settings()],
  },
  steps: [
    { op: 'investmentDeposit', args: { investmentId: 'inv-1', sourceAccount: '', taxesConfig: TAXES } },
    { op: 'investmentDeposit', args: { investmentId: 'inv-1', sourceAccount: 'Pobre', taxesConfig: TAXES } },
    { op: 'investmentDeposit', args: { investmentId: 'inv-1', sourceAccount: 'Ahorros', taxesConfig: TAXES } },
    { op: 'getAccountBalances', args: {} },
    { op: 'investmentDeposit', args: { investmentId: 'inv-1', sourceAccount: 'Ahorros', taxesConfig: null } },
    { op: 'investmentDeposit', args: { investmentId: 'inv-2', sourceAccount: 'Ahorros', taxesConfig: null } },
    { op: 'getAccountBalances', args: {} },
    { op: 'getTotalBalance', args: {} },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'dashboard-salary',
  description: 'Aviso de día de sueldo según tipo de pago, mes y posposición; deuda activa y últimas transacciones',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco X', 100)],
    salary: [{ id: 'salary-1', amount: 1200, payDateType: 'specific', payDate: '15', recurrence: 'Mensual', bankAccount: 'Ahorros', createdAt: '2026-01-01T00:00:00.000Z' }],
    loans: [loan('l-1', 'A', 1000, 10, 2, 100), loan('l-2', 'B', 100, 2, 2, 50, { status: 'paid' }), loan('l-3', 'C', 300, 3, 0, 100)],
    expenses: [
      expense('e-1', 10, '2026-11-17', 'Otros', 'Con hora', 'Efectivo', { time: '08:00' }),
      expense('e-2', 20, '2026-11-17', 'Otros', 'Sin hora', 'Efectivo'),
      expense('e-3', 5, '2026-11-16', 'Otros', 'Ayer', 'Efectivo', { time: '23:00' }),
    ],
    income: [income('i-1', 100, 'Extra', '2026-11-17', 'Ahorros - Banco X')],
    settings: [settings()],
  },
  steps: [
    { op: 'setClock', args: { at: '2026-11-10T12:00:00' } },
    { op: 'dashboard', args: {} },
    { op: 'setClock', args: { at: '2026-11-17T12:00:00' } },
    { op: 'dashboard', args: {} },
    { op: 'saveSettings', args: { patch: { salaryPromptPostponedUntil: 1795021200000 } } },
    { op: 'dashboard', args: {} },
    { op: 'saveSettings', args: { patch: { salaryPromptPostponedUntil: 1794800000000 } } },
    { op: 'dashboard', args: {} },
    { op: 'addIncome', args: { payload: { amount: 1200, source: 'Sueldo (Automático)', detail: 'Acreditación confirmada desde notificador de fecha de cobro', bankAccount: 'Ahorros', bankName: 'Ahorros', date: '2026-11-17', isSalary: true } } },
    { op: 'dashboard', args: {} },
    { op: 'saveSalary', args: { data: { id: 'salary-1', amount: 1200, payDateType: 'last', payDate: '', recurrence: 'Mensual', bankAccount: '' } } },
    { op: 'setClock', args: { at: '2027-02-27T12:00:00' } },
    { op: 'dashboard', args: {} },
    { op: 'setClock', args: { at: '2027-02-28T12:00:00' } },
    { op: 'dashboard', args: {} },
    { op: 'saveSalary', args: { data: { id: 'salary-1', amount: 1200, payDateType: 'specific', payDate: '31', recurrence: 'Mensual', bankAccount: 'Ahorros' } } },
    { op: 'dashboard', args: {} },
    { op: 'saveSalary', args: { data: { id: 'salary-1', amount: 1200, payDateType: 'specific', payDate: '', recurrence: 'Mensual', bankAccount: 'Ahorros' } } },
    { op: 'dashboard', args: {} },
    { op: 'saveSettings', args: { patch: { onboardingCompleted: false } } },
    { op: 'dashboard', args: {} },
  ],
});

// ---------------------------------------------------------------------------------------------------
const analyticsSeed = () => ({
  accounts: [acc('acc-1', 'Ahorros', 'Banco X', 500), acc('acc-2', 'Corriente', 'Banco Y', 0)],
  categories: [
    { id: 'c-1', name: 'Alimentación', type: 'expense', color: '#f59e0b', icon: 'restaurant' },
    { id: 'c-2', name: 'Transporte', type: 'expense', color: '#123456', icon: 'car', isCustomColor: true },
  ],
  income: [
    income('i-1', 1500, 'Sueldo', '2026-11-05', 'Ahorros - Banco X'),
    income('i-2', 300, 'Venta', '2026-11-16', 'Corriente - Banco Y'),
    income('i-3', 1500, 'Sueldo', '2026-10-05', 'Ahorros - Banco X'),
    income('i-4', 90, '', '2026-10-20', ''),
    income('i-5', 1500, 'Sueldo', '2026-03-05', 'Ahorros - Banco X'),
    income('i-6', 40, 'Venta', '2025-12-31', 'Corriente - Banco Y'),
  ],
  expenses: [
    expense('e-1', 25.5, '2026-11-17', 'Alimentación', 'Almuerzo', 'Efectivo'),
    expense('e-2', 60, '2026-11-16', 'Transporte', 'Gasolina', 'Débito', { accountName: 'Ahorros' }),
    expense('e-3', 15, '2026-11-15', 'Alimentación', 'Cena', 'Crédito', { cardName: 'Visa' }),
    expense('e-4', 200, '2026-11-02', 'Tecnología de la información avanzada', 'Teclado', 'Débito', { accountName: 'Corriente' }),
    expense('e-5', 12.25, '2026-10-28', 'Alimentación', 'Snack', 'Débito', { accountName: 'Ahorros' }),
    expense('e-6', 44, '2026-10-01', 'Salud', 'Médico', 'Efectivo'),
    expense('e-7', 80, '2026-03-10', 'Transporte', 'Pasaje', 'Débito', { accountName: 'Fantasma' }),
    expense('e-8', 5, '2026-11-17', '', 'Sin categoría', ''),
    expense('e-9', 33.33, '2026-11-10', 'Alimentación', 'Cena 2', 'Débito', { accountName: 'Ahorros' }),
    expense('e-10', 18, '2026-11-09', 'Transporte', 'Bus', 'Débito', { accountName: 'Ahorros' }),
  ],
  settings: [settings()],
});
const analyticsSteps = [
  { op: 'analytics', args: { period: 'daily' } },
  { op: 'analytics', args: { period: 'weekly' } },
  { op: 'analytics', args: { period: 'monthly' } },
  { op: 'comparison', args: { compareP1: '2026-11', compareP2: '2026-10' } },
  { op: 'comparison', args: { compareP1: '2026-10', compareP2: '2026-11', compCategory: 'alim' } },
  { op: 'comparison', args: { compareP1: '2026-11', compareP2: '2026-10', compMethod: 'déb', compIncomeSource: 'sueldo' } },
  { op: 'comparison', args: { compareP1: '2026-11', compareP2: '2026-11' } },
  { op: 'comparison', args: { compareP1: '2027-01', compareP2: '2027-01' } },
  { op: 'comparison', args: { compareP1: 'abc', compareP2: '2026-10' } },
];
scenarios.push({
  name: 'analytics-tuesday',
  description: 'Series diaria/semanal/mensual, gráficos de pastel y comparativa (martes)',
  seed: analyticsSeed(),
  steps: analyticsSteps,
});
scenarios.push({
  name: 'analytics-sunday',
  description: 'Igual que analytics-tuesday pero en domingo (la semana de Análisis usa getDay() = 0)',
  clock: '2026-11-15T22:30:00',
  seed: analyticsSeed(),
  steps: analyticsSteps,
});
scenarios.push({
  name: 'analytics-january',
  description: 'Mes de enero (la comparativa por defecto de Análisis cae sobre el mismo mes)',
  clock: '2027-01-31T10:00:00',
  seed: analyticsSeed(),
  steps: analyticsSteps,
});

// ---------------------------------------------------------------------------------------------------
const reportSeed = () => ({
  accounts: [acc('acc-1', 'Ahorros', 'Banco X', 1000), acc('acc-2', 'Corriente "Plus"', 'Banco, Y', -50.5)],
  income: [
    income('i-1', 1500, 'Sueldo', '2026-11-05', 'Ahorros - Banco X'),
    income('i-2', 300.456, 'Venta, "garaje"', '2026-10-16', 'Corriente "Plus" - Banco, Y', { detail: 'Bicicleta\nusada' }),
    income('i-3', 1500, 'Sueldo', '2026-03-05', 'Ahorros - Banco X'),
    income('i-4', 40, '', '2025-12-31', ''),
  ],
  expenses: [
    expense('e-1', 25.5, '2026-11-17', 'Alimentación', 'Almuerzo <b>', 'Efectivo', { time: '13:05' }),
    expense('e-2', 60, '2026-11-17', 'Transporte', 'Gasolina & más', 'Débito', { accountName: 'Ahorros', time: '08:00' }),
    expense('e-3', 15.999, '2026-11-15', 'Alimentación', 'Cena "especial"', 'Crédito', { cardName: 'Visa Oro' }),
    expense('e-4', 200, '2026-10-02', 'Tecnología', 'Teclado', 'Débito', { accountName: 'Corriente "Plus"' }),
    expense('e-5', 12.25, '2026-10-28', 'Alimentación', "Snack d'or", 'Débito', {}),
    expense('e-6', 44, '2026-03-01', '', '', '', {}),
    expense('e-7', 3, '2026-11-17', 'Otros', 'Dentro del día', 'Efectivo', { totalCharge: 3.5, time: '13:05' }),
    expense('e-8', 7, '2025-06-30', 'Salud', 'Antiguo', 'Efectivo'),
  ],
  loans: [],
  settings: [settings()],
});
const reportCombos = [];
for (const type of ['all', 'ingresos', 'gastos']) for (const period of ['thisMonth', 'lastMonth', 'thisYear', 'all']) reportCombos.push({ type, period });
scenarios.push({
  name: 'csv-report',
  description: 'CSV de reportes: todos los tipos × períodos (escapado de comillas, BOM, orden descendente)',
  seed: reportSeed(),
  steps: [
    ...['thisMonth', 'lastMonth', 'thisYear', 'all'].map((period) => ({ op: 'reportPeriod', args: { period } })),
    ...reportCombos.map((c) => ({ op: 'generateCsv', args: c })),
    { op: 'setClock', args: { at: '2027-01-15T09:00:00' } },
    { op: 'reportPeriod', args: { period: 'lastMonth' } },
    { op: 'generateCsv', args: { type: 'all', period: 'lastMonth' } },
    { op: 'generateCsv', args: { type: 'all', period: 'thisYear' } },
  ],
});
scenarios.push({
  name: 'pdf-report-model',
  description: 'Datos del PDF (resumen, donas, barras, saldos y detalle) extraídos del HTML original',
  seed: reportSeed(),
  steps: reportCombos.map((c) => ({ op: 'pdfModel', args: c })),
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'categories-and-settings',
  description: 'seedDefaults (sin pisar colores personalizados), ajustes parciales y sueldo (alta + actualización)',
  seed: {
    categories: [
      { id: 'c-1', name: 'Alimentación', type: 'expense', color: '#000001', icon: 'old-icon' },
      { id: 'c-2', name: 'Transporte', type: 'expense', color: '#000002', icon: 'car', isCustomColor: true },
      { id: 'c-3', name: 'Mi categoría', type: 'expense', color: '#000003', icon: 'star' },
    ],
  },
  steps: [
    { op: 'getSettings', args: {} },
    { op: 'seedDefaults', args: {} },
    { op: 'seedDefaults', args: {} },
    { op: 'updateCategoryByName', args: { name: 'Salud', patch: { color: '#ffffff', isCustomColor: true } } },
    { op: 'seedDefaults', args: {} },
    { op: 'updateCategoryByName', args: { name: 'Salud', patch: { color: '#ef4444', isCustomColor: false } } },
    { op: 'seedDefaults', args: {} },
    { op: 'saveSettings', args: { patch: { currency: 'EUR', currencyLocale: 'es-ES' } } },
    { op: 'getSettings', args: {} },
    { op: 'saveSettings', args: { patch: { appName: 'Mis Finanzas', monthlyGoal: 750.5 } } },
    { op: 'saveSettings', args: { patch: { taxes: { enabled: true, ivaRate: 15, comisionRate: 2, isdRate: 5, applyTo: ['Préstamos', 'Gastos:Ropa'] } } } },
    { op: 'saveSettings', args: { patch: { theme: 'light', lastBudgetAlert: '2026-11:near' } } },
    { op: 'getSettings', args: {} },
    { op: 'saveSalary', args: { data: { id: 'salary-1', amount: 900, payDateType: 'specific', payDate: '15', recurrence: 'Mensual', bankAccount: '' } } },
    { op: 'saveSalary', args: { data: { id: 'salary-1', amount: 950, payDateType: 'last', payDate: '', recurrence: 'Quincenal', bankAccount: 'Ahorros' } } },
    { op: 'getTotalBalance', args: {} },
  ],
});

// ---------------------------------------------------------------------------------------------------
scenarios.push({
  name: 'backup-roundtrip',
  description: 'Exportar, borrar todo (se conservan los ajustes), importar parcial y hasAnyData',
  seed: {
    accounts: [acc('acc-1', 'Ahorros', 'Banco X', 100)],
    income: [income('i-1', 10, 'Extra', '2026-11-01', 'Ahorros - Banco X')],
    expenses: [expense('e-1', 5, '2026-11-02', 'Otros', 'X', 'Efectivo')],
    categories: [{ id: 'c-1', name: 'Otros', type: 'expense', color: '#64748b', icon: 'ellipsis-horizontal' }],
    reminders: [{ id: 'r-1', note: 'campo desconocido', when: 123 }],
    settings: [settings({ appName: 'Backup' })],
  },
  steps: [
    { op: 'hasAnyData', args: {} },
    { op: 'exportAllData', args: {} },
    { op: 'clearAllData', args: {} },
    { op: 'hasAnyData', args: {} },
    { op: 'getSettings', args: {} },
    { op: 'importAllData', args: { data: {
      _exportDate: '2026-11-01T00:00:00.000Z',
      accounts: [acc('acc-9', 'Nueva', 'Banco N', 1)],
      expenses: 'no-es-un-arreglo',
      loans: [loan('l-9', 'Importado', 100, 2, 0, 50)],
      settings: [settings({ appName: 'Importado', currency: 'COP', currencyLocale: 'es-CO' })],
      desconocida: [{ id: 'x' }],
    } } },
    { op: 'hasAnyData', args: {} },
    { op: 'getSettings', args: {} },
    { op: 'exportAllData', args: {} },
  ],
});

module.exports = { scenarios };
