import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  Expense,
  Category,
  Account,
  CreditCard,
  Loan,
  Investment,
  Settings,
} from '../types';

const STORAGE_PREFIX = '@tracker_gastos_';

const ALL_STORES = [
  'salary',
  'expenses',
  'income',
  'loans',
  'creditCards',
  'reminders',
  'accounts',
  'categories',
  'investments',
  'settings',
];

// ===== Generic CRUD =====
async function getAll<T>(storeName: string): Promise<T[]> {
  try {
    const jsonValue = await AsyncStorage.getItem(`${STORAGE_PREFIX}${storeName}`);
    return jsonValue != null ? JSON.parse(jsonValue) : [];
  } catch (e) {
    console.error(`Error loading ${storeName}`, e);
    return [];
  }
}

async function getById<T extends { id: string }>(storeName: string, id: string): Promise<T | undefined> {
  const all = await getAll<T>(storeName);
  return all.find((item) => item.id === id);
}

async function saveAll<T>(storeName: string, data: T[]): Promise<void> {
  try {
    const jsonValue = JSON.stringify(data);
    await AsyncStorage.setItem(`${STORAGE_PREFIX}${storeName}`, jsonValue);
  } catch (e) {
    console.error(`Error saving ${storeName}`, e);
  }
}

async function add<T extends { id: string }>(storeName: string, data: Omit<T, 'id'> & { id?: string }): Promise<T> {
  const all = await getAll<T>(storeName);
  const newItem = {
    ...data,
    id: data.id || Date.now().toString() + Math.random().toString(36).substring(7),
    createdAt: new Date().toISOString(),
  } as unknown as T;
  all.push(newItem);
  await saveAll(storeName, all);
  return newItem;
}

async function update<T extends { id: string }>(storeName: string, data: T): Promise<T> {
  const all = await getAll<T>(storeName);
  const index = all.findIndex((item) => item.id === data.id);
  if (index !== -1) {
    all[index] = { ...all[index], ...data };
    await saveAll(storeName, all);
    return all[index];
  }
  throw new Error(`Item ${data.id} not found in ${storeName}`);
}

async function remove(storeName: string, id: string): Promise<void> {
  const all = await getAll<{ id: string }>(storeName);
  const filtered = all.filter((item) => item.id !== id);
  await saveAll(storeName, filtered);
}

// ===== Repositories =====

export const SalaryRepo = {
  getAll: () => getAll<{ id: string; amount: number }>('salary'),
  get: (id: string) => getById('salary', id),
  save: async (data: any) => {
    const all = await getAll<{ id: string } & any>('salary');
    if (all.length > 0) {
      return update('salary', { ...all[0], ...data });
    }
    return add('salary', data);
  },
  delete: (id: string) => remove('salary', id),
};

export const ExpenseRepo = {
  getAll: () => getAll<Expense>('expenses'),
  get: (id: string) => getById<Expense>('expenses', id),
  add: (data: any) => add<Expense>('expenses', data),
  update: (data: Expense) => update<Expense>('expenses', data),
  delete: (id: string) => remove('expenses', id),
  getByDateRange: async (start: string, end: string) => {
    const all = await getAll<Expense>('expenses');
    return all.filter((e) => e.date >= start && e.date <= end);
  },
  getByCategory: async (type: string) => {
    const all = await getAll<Expense>('expenses');
    return all.filter((e) => e.category === type);
  },
};

export const IncomeRepo = {
  getAll: () => getAll<any>('income'),
  get: (id: string) => getById('income', id),
  add: (data: any) => add('income', data),
  update: (data: any) => update('income', data),
  delete: (id: string) => remove('income', id),
  getByDateRange: async (start: string, end: string) => {
    const all = await getAll<any>('income');
    return all.filter((i) => i.date >= start && i.date <= end);
  },
};

export const LoanRepo = {
  getAll: () => getAll<Loan>('loans'),
  get: (id: string) => getById<Loan>('loans', id),
  add: (data: any) => add<Loan>('loans', data),
  update: (data: Loan) => update<Loan>('loans', data),
  delete: (id: string) => remove('loans', id),
  getActive: async () => {
    const all = await getAll<Loan & { status?: string }>('loans');
    return all.filter((l) => l.status !== 'paid');
  },
};

export const CreditCardRepo = {
  getAll: () => getAll<CreditCard>('creditCards'),
  get: (id: string) => getById<CreditCard>('creditCards', id),
  add: (data: any) => add<CreditCard>('creditCards', data),
  update: (data: CreditCard) => update<CreditCard>('creditCards', data),
  delete: (id: string) => remove('creditCards', id),
};

export const AccountRepo = {
  getAll: () => getAll<Account>('accounts'),
  get: (id: string) => getById<Account>('accounts', id),
  add: (data: any) => add<Account>('accounts', data),
  update: (data: Account) => update<Account>('accounts', data),
  delete: (id: string) => remove('accounts', id),
};

export const CategoryRepo = {
  getAll: () => getAll<Category>('categories'),
  add: (data: any) => add<Category>('categories', data),
  delete: (id: string) => remove('categories', id),
  seedDefaults: async () => {
    const existing = await getAll<Category>('categories');
    if (existing.length > 0) return;
    const defaults = [
      { name: 'Alimentación', icon: 'restaurant', color: '#f59e0b', type: 'expense' },
      { name: 'Transporte', icon: 'directions_car', color: '#3b82f6', type: 'expense' },
      { name: 'Vivienda', icon: 'home', color: '#8b5cf6', type: 'expense' },
      { name: 'Salud', icon: 'favorite', color: '#ef4444', type: 'expense' },
      { name: 'Entretenimiento', icon: 'movie', color: '#ec4899', type: 'expense' },
      { name: 'Educación', icon: 'school', color: '#06b6d4', type: 'expense' },
      { name: 'Ropa', icon: 'checkroom', color: '#f97316', type: 'expense' },
      { name: 'Tecnología', icon: 'devices', color: '#6366f1', type: 'expense' },
      { name: 'Servicios', icon: 'receipt_long', color: '#14b8a6', type: 'expense' },
      { name: 'Suscripción', icon: 'subscriptions', color: '#a855f7', type: 'expense' },
      { name: 'Otros', icon: 'more_horiz', color: '#64748b', type: 'expense' },
    ];
    for (const cat of defaults) {
      await add('categories', cat as any);
    }
  },
};

export const InvestmentRepo = {
  getAll: () => getAll<Investment>('investments'),
  get: (id: string) => getById<Investment>('investments', id),
  add: (data: any) => add<Investment>('investments', data),
  update: (data: Investment) => update<Investment>('investments', data),
  delete: (id: string) => remove('investments', id),
};

export const SettingsRepo = {
  get: async (): Promise<Settings> => {
    const all = await getAll<Settings>('settings');
    return all[0] || { id: 'default', appName: 'Mi Dinero', monthlyGoal: 0, theme: 'dark' };
  },
  save: async (data: Partial<Settings>) => {
    const all = await getAll<Settings>('settings');
    if (all.length > 0) {
      return update('settings', { ...all[0], ...data } as Settings);
    }
    return add('settings', { id: 'default', appName: 'Mi Dinero', monthlyGoal: 0, theme: 'dark', ...data });
  },
};

export async function clearAllData() {
  for (const store of ALL_STORES) {
    if (store === 'settings') continue; // keep settings
    await saveAll(store, []);
  }
}

export async function hasAnyData() {
  for (const store of ALL_STORES) {
    if (store === 'settings' || store === 'categories') continue;
    const items = await getAll(store);
    if (items.length > 0) return true;
  }
  return false;
}

export async function getTotalBalance() {
  const salaries = await SalaryRepo.getAll();
  const incomes = await IncomeRepo.getAll();
  const expenses = await ExpenseRepo.getAll();

  const totalSalary = salaries.reduce((sum, s) => sum + (Number(s.amount) || 0), 0);
  const totalIncome = incomes.reduce((sum, i) => sum + (Number(i.amount) || 0), 0);
  const totalExpenses = expenses.reduce((sum, e) => sum + (Number(e.amount) || 0), 0);

  return {
    totalIncome: totalSalary + totalIncome,
    totalExpenses,
    balance: totalSalary + totalIncome - totalExpenses,
    salaryAmount: totalSalary,
    extraIncome: totalIncome,
  };
}

export async function exportAllData() {
  const data: Record<string, any> = {};
  for (const store of ALL_STORES) {
    data[store] = await getAll(store);
  }
  data._exportDate = new Date().toISOString();
  return data;
}

export async function importAllData(jsonData: Record<string, any>) {
  for (const store of ALL_STORES) {
    if (jsonData[store] && Array.isArray(jsonData[store])) {
      await saveAll(store, jsonData[store]);
    }
  }
}

export async function getMonthlySummary(year: number, month: number) {
  const start = `${year}-${String(month).padStart(2, '0')}-01`;
  const lastDay = new Date(year, month, 0).getDate();
  const end = `${year}-${String(month).padStart(2, '0')}-${lastDay}`;

  const expenses = await ExpenseRepo.getByDateRange(start, end);
  const incomes = await IncomeRepo.getByDateRange(start, end);

  // Include salary in monthly income
  const salaries = await SalaryRepo.getAll();
  const salaryAmount = salaries.reduce((s, sal) => s + (Number(sal.amount) || 0), 0);

  const totalExp = expenses.reduce((s, e) => s + Number(e.amount), 0);
  const totalInc = incomes.reduce((s, i) => s + Number(i.amount), 0) + salaryAmount;

  const byCategory: Record<string, number> = {};
  expenses.forEach(e => {
    const cat = e.category || 'Sin categoría';
    byCategory[cat] = (byCategory[cat] || 0) + Number(e.amount);
  });

  const byPaymentMethod: Record<string, number> = {};
  expenses.forEach(e => {
    const m = e.paymentMethod || 'Otro';
    byPaymentMethod[m] = (byPaymentMethod[m] || 0) + Number(e.amount);
  });

  const byAccount: Record<string, number> = {};
  expenses.forEach(e => {
    const acc = e.accountName || e.cardName || 'Sin cuenta';
    byAccount[acc] = (byAccount[acc] || 0) + Number(e.amount);
  });

  const incomeByAccount: Record<string, number> = {};
  incomes.forEach(i => {
    const acc = i.bankAccount || 'Sin cuenta';
    incomeByAccount[acc] = (incomeByAccount[acc] || 0) + Number(i.amount);
  });

  return {
    totalExpenses: totalExp,
    totalIncome: totalInc,
    salaryAmount,
    byCategory,
    byPaymentMethod,
    byAccount,
    incomeByAccount,
    expenses,
    incomes,
  };
}
