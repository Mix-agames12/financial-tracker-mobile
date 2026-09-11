export interface Expense {
  id: string;
  amount: number;
  date: string;
  category: string;
  detail: string;
  paymentMethod: 'Efectivo' | 'Débito' | 'Crédito' | string;
  accountName?: string;
  cardName?: string;
  isDeferred?: boolean;
  deferredMonths?: number;
  monthlyQuota?: number;
  isRecurring?: boolean;
  recurringFrequency?: 'monthly' | 'specific' | string;
  recurringDay?: number;
  parentExpenseId?: string; // cargo de impuestos/comisiones → id de la compra que lo originó
}

export interface Category {
  id: string;
  name: string;
  type: 'expense' | 'income' | string;
  color: string;
  icon: string;
  isCustomColor?: boolean;
}

export interface Account {
  id: string;
  name: string;
  referenceNumber?: string;
  accountType: 'Ahorro' | 'Corriente' | string;
  bankName: string;
  initialBalance?: number;
}

export interface CreditCard {
  id: string;
  name: string;
  bankName: string;
  cutOffDay: number; // 1-31
  paymentDueDay: number; // 1-31
  creditLimit: number;
  currentBalance: number;
}

export interface Loan {
  id: string;
  name: string;
  totalAmount: number;
  installments: number;
  paidInstallments: number;
  monthlyQuota: number;
  interestRate: number;
  totalWithInterest?: number;
  nextPaymentDate?: string;
  status?: 'active' | 'paid' | string;
  cardId?: string; // tarjeta en la que se cargó la compra
  sourceExpenseId?: string; // gasto (compra con tarjeta) que creó el préstamo
  cardOutstanding?: number; // parte de la compra que sigue cargada en la tarjeta
}

export interface Investment {
  id: string;
  entity: string;
  monthlyDeposit: number;
  totalMonths: number;
  remainingMonths: number;
  detail: string;
  totalDeposited: number;
}

export interface Salary {
  id: string;
  amount: number;
  payDateType: 'specific' | 'last' | 'lastBusiness' | string;
  payDate: string;
  recurrence: 'Mensual' | 'Quincenal' | 'Semanal' | string;
  bankAccount?: string;
}

export interface Income {
  id: string;
  amount: number;
  source: string;
  detail: string;
  bankAccount: string;
  bankName: string;
  date: string;
  isSalary?: boolean;
}

export interface TaxesConfig {
  enabled: boolean;
  ivaRate: number;
  comisionRate: number;
  isdRate: number;
  applyTo: string[];
}

export interface Settings {
  id: string; // usually 'default'
  appName: string;
  monthlyGoal: number;
  theme: 'light' | 'dark' | 'system';
  salaryPromptPostponedUntil?: number;
  taxes?: TaxesConfig;
  currency?: string; // ISO 4217, p. ej. 'USD'
  currencyLocale?: string; // p. ej. 'es-EC'
  onboardingCompleted?: boolean;
  lastBudgetAlert?: string; // 'YYYY-MM:near' | 'YYYY-MM:over'
}
