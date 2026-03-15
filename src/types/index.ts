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
}

export interface Category {
  id: string;
  name: string;
  type: 'expense' | 'income' | string;
  color: string;
  icon: string;
}

export interface Account {
  id: string;
  name: string;
  referenceNumber?: string;
  accountType: 'Ahorro' | 'Corriente' | 'Nómina' | string;
  bankName: string;
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
}

export interface Income {
  id: string;
  amount: number;
  source: string;
  detail: string;
  bankAccount: string;
  bankName: string;
  date: string;
}

export interface Settings {
  id: string; // usually 'default'
  appName: string;
  monthlyGoal: number;
  theme: 'light' | 'dark' | 'system';
}
