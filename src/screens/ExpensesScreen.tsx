import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { Chip } from '../components/Chip';
import { BottomSheet } from '../components/BottomSheet';
import { ToastManager } from '../components/ActionFeedback';
import { DatePickerModal } from '../components/DatePickerModal';
import { formatCurrency, formatDate, getToday, getNow, nextDateForMonthDay, roundMoney, toAmountInput } from '../utils/formatters';
import { syncAfterDataChange } from '../utils/dataSync';

// Heuristic H7: State Memory
let memoryPaymentMethod: 'Efectivo' | 'Débito' | 'Crédito' = 'Débito';
let memoryDebitAcc = '';
let memoryCreditAcc = '';
import { ExpenseRepo, CategoryRepo, AccountRepo, CreditCardRepo, SettingsRepo, getTotalBalance, getAccountBalances } from '../db/storage';
import { deleteExpenseWithCardSync, registerCardPurchase, updateCardPurchase } from '../utils/cardPurchases';
import { Expense, Category, Account, CreditCard, TaxesConfig } from '../types';

interface ExpenseFilters {
  from: string;
  to: string;
  categories: string[];
  method: string;
}

const EMPTY_FILTERS: ExpenseFilters = { from: '', to: '', categories: [], method: '' };

function hasActiveFilters(f: ExpenseFilters): boolean {
  return !!(f.from || f.to || f.categories.length || f.method);
}

/** Cada criterio es opcional: rango de fechas abierto, varias categorías y método de pago. */
function applyExpenseFilters(expenses: Expense[], f: ExpenseFilters): Expense[] {
  return expenses.filter(e =>
    (!f.from || e.date >= f.from) &&
    (!f.to || e.date <= f.to) &&
    (f.categories.length === 0 || f.categories.includes(e.category)) &&
    (!f.method || e.paymentMethod === f.method)
  );
}

type RecurringFrequency = 'monthly' | 'specific' | 'yearly';

const MONTHS_SHORT = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

function describeRecurrence(exp: Expense): string {
  if (exp.recurringFrequency === 'yearly' && exp.recurringMonth && exp.recurringDay) {
    return `Anual · ${exp.recurringDay} ${MONTHS_SHORT[exp.recurringMonth - 1]}`;
  }
  if (exp.recurringFrequency === 'specific' && exp.recurringDay) return `Día ${exp.recurringDay}`;
  return 'Mensual';
}

export default function ExpensesScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [creditCards, setCreditCards] = useState<CreditCard[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  const [taxesConfig, setTaxesConfig] = useState<TaxesConfig | null>(null);

  // Filters State: borrador en la hoja; sólo se aplica al pulsar "Aplicar filtros".
  const [isFilterOpen, setFilterOpen] = useState(false);
  const [filterFrom, setFilterFrom] = useState('');
  const [filterTo, setFilterTo] = useState('');
  const [filterCats, setFilterCats] = useState<string[]>([]);
  const [filterMethod, setFilterMethod] = useState('');
  const [appliedFilters, setAppliedFilters] = useState<ExpenseFilters>(EMPTY_FILTERS);
  const [expenseCategoryNames, setExpenseCategoryNames] = useState<string[]>([]);
  const activeFilters = hasActiveFilters(appliedFilters);

  // Detail State
  const [isDetailOpen, setDetailOpen] = useState(false);
  const [selectedExpense, setSelectedExpense] = useState<Expense | null>(null);

  // Form State
  const [isFormOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  
  const [amountStr, setAmountStr] = useState('');
  const [categoryType, setCategoryType] = useState('');
  const [detail, setDetail] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<'Efectivo' | 'Débito' | 'Crédito'>('Débito');
  const [selectedDebit, setSelectedDebit] = useState('');
  const [selectedCredit, setSelectedCredit] = useState('');
  const [isDeferred, setIsDeferred] = useState(false);
  const [deferredMonths, setDeferredMonths] = useState('');
  const [dateStr, setDateStr] = useState(getToday());
  const [isDatePickerOpen, setDatePickerOpen] = useState(false);
  const [timeStr, setTimeStr] = useState(getNow());
  const [tags, setTags] = useState('');
  
  const [isRecurring, setIsRecurring] = useState(false);
  const [recurringFrequency, setRecurringFrequency] = useState<RecurringFrequency>('monthly');
  const [recurringDay, setRecurringDay] = useState('');
  const [recurringDate, setRecurringDate] = useState(''); // cobro anual (YYYY-MM-DD)
  const [subscriptionType, setSubscriptionType] = useState('');

  const loadData = async (filters: ExpenseFilters = appliedFilters) => {
    try {
      let data = await ExpenseRepo.getAll();
      
      // Categorías presentes en los gastos (incluye "Préstamo" o "Inversión", que genera la app).
      setExpenseCategoryNames(Array.from(new Set(data.map(e => e.category).filter(Boolean))));
      data = applyExpenseFilters(data, filters);

      data.sort((a, b) => (b.date || '').localeCompare(a.date || ''));
      setExpenses(data);

      await CategoryRepo.seedDefaults();
      const cats = await CategoryRepo.getAll();
      setCategories(cats.filter(c => c.type === 'expense'));
      setAccounts(await AccountRepo.getAll());
      setCreditCards(await CreditCardRepo.getAll());
      const s = await SettingsRepo.get();
      setTaxesConfig(s.taxes || null);
    } catch (e) {
      console.error(e);
    }
  };

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [appliedFilters])
  );

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const openFilters = () => {
    // El borrador parte de los filtros aplicados.
    setFilterFrom(appliedFilters.from);
    setFilterTo(appliedFilters.to);
    setFilterCats(appliedFilters.categories);
    setFilterMethod(appliedFilters.method);
    setFilterOpen(true);
  };

  const toggleFilterCat = (name: string) => {
    setFilterCats(prev => prev.includes(name) ? prev.filter(c => c !== name) : [...prev, name]);
  };

  const handleApplyFilters = () => {
    const next: ExpenseFilters = { from: filterFrom, to: filterTo, categories: filterCats, method: filterMethod };
    if (!hasActiveFilters(next)) {
      Alert.alert('Aviso', 'Elige al menos un criterio: fechas, categorías o método de pago.');
      return;
    }
    if (next.from && next.to && next.from > next.to) {
      Alert.alert('Aviso', 'La fecha "Desde" no puede ser posterior a "Hasta".');
      return;
    }
    setAppliedFilters(next);
    setFilterOpen(false);
  };

  const clearFilters = () => {
    setFilterFrom('');
    setFilterTo('');
    setFilterCats([]);
    setFilterMethod('');
    setAppliedFilters(EMPTY_FILTERS);
    setFilterOpen(false);
  };

  const openNewForm = () => {
    setEditingId(null);
    setAmountStr('');
    setCategoryType('');
    setDetail('');
    setPaymentMethod(memoryPaymentMethod);
    setSelectedDebit(memoryDebitAcc);
    setSelectedCredit(memoryCreditAcc);
    setIsDeferred(false);
    setDeferredMonths('');
    setDateStr(getToday());
    setTimeStr(getNow());
    setTags('');
    setIsRecurring(false);
    setRecurringFrequency('monthly');
    setRecurringDay('');
    setRecurringDate('');
    setSubscriptionType('');
    setDetailOpen(false);
    setFormOpen(true);
  };

  const openEditForm = (exp: Expense) => {
    setEditingId(exp.id);
    setAmountStr(toAmountInput(exp.amount));
    setCategoryType(exp.category);
    setDetail(exp.detail);
    setPaymentMethod((exp.paymentMethod as any) || 'Efectivo');
    setSelectedDebit(exp.accountName || '');
    setSelectedCredit(exp.cardName || '');
    setIsDeferred(exp.isDeferred || false);
    setDeferredMonths(exp.deferredMonths?.toString() || '');
    setDateStr(exp.date);
    setTimeStr((exp as any).time || getNow());
    setTags((exp as any).tags?.join(', ') || '');
    setIsRecurring(exp.isRecurring || false);
    setRecurringFrequency((exp.recurringFrequency as RecurringFrequency) || 'monthly');
    setRecurringDay(exp.recurringDay?.toString() || '');
    setRecurringDate(exp.recurringFrequency === 'yearly' && exp.recurringMonth && exp.recurringDay
      ? nextDateForMonthDay(exp.recurringMonth, exp.recurringDay)
      : '');
    setSubscriptionType((exp as any).subscriptionType || '');
    setDetailOpen(false);
    setFormOpen(true);
  };

  const calculateCreditCardNextPaymentDate = (expenseDateStr: string, cutOffDay: number, paymentDueDay: number): string => {
    if (!expenseDateStr || !cutOffDay || !paymentDueDay) return '';
    const expDate = new Date(expenseDateStr + 'T12:00:00Z');
    const expDay = expDate.getUTCDate();
    const expMonth = expDate.getUTCMonth(); 
    const expYear = expDate.getUTCFullYear();

    let dueMonth = expMonth + 1;
    let dueYear = expYear;

    if (expDay > cutOffDay) {
      dueMonth += 1;
    }

    if (dueMonth > 11) {
      dueMonth -= 12;
      dueYear += 1;
    }

    const mStr = String(dueMonth + 1).padStart(2, '0');
    // El día de pago no puede pasar del último día del mes (p. ej. 31 en noviembre).
    const lastDayOfDueMonth = new Date(dueYear, dueMonth + 1, 0).getDate();
    const dStr = String(Math.min(paymentDueDay, lastDayOfDueMonth)).padStart(2, '0');
    return `${dueYear}-${mStr}-${dStr}`;
  };

  const handleSaveExpense = async () => {
    const amount = parseFloat(amountStr.replace(',', '.'));
    if (isNaN(amount) || amount <= 0) {
      Alert.alert('Error', 'Ingresa un monto válido');
      return;
    }
    if (!categoryType) {
      Alert.alert('Error', 'Selecciona una categoría');
      return;
    }
    if (!detail.trim()) {
      Alert.alert('Error', 'Ingresa el detalle del gasto');
      return;
    }

    let taxComision = 0;
    let taxIva = 0;
    let taxIsd = 0;
    let appliesTaxes = false;

    if (taxesConfig?.enabled && taxesConfig.applyTo.includes(`Gastos:${categoryType}`)) {
      appliesTaxes = true;
      taxComision = (amount * taxesConfig.comisionRate) / 100;
      taxIva = (taxComision * taxesConfig.ivaRate) / 100;
      taxIsd = (amount * taxesConfig.isdRate) / 100;
    }
    const totalTax = taxComision + taxIva + taxIsd;
    const totalCharge = amount + totalTax;

    // Recurrente anual: día y mes de la fecha de cobro elegida (por defecto, la del gasto).
    const [, annualMonth, annualDay] = (recurringDate || dateStr || getToday()).split('-').map(Number);

    const payload: Omit<Expense, 'id'> = {
      amount: Math.round(amount * 100) / 100,
      category: categoryType,
      detail: detail.trim(),
      paymentMethod,
      accountName: (paymentMethod === 'Débito' || paymentMethod === 'Efectivo') ? selectedDebit : '',
      cardName: paymentMethod === 'Crédito' ? selectedCredit : '',
      isDeferred: paymentMethod === 'Crédito' ? isDeferred : false,
      deferredMonths: paymentMethod === 'Crédito' && isDeferred ? parseInt(deferredMonths) || 0 : 0,
      date: dateStr || getToday(),
      ...( { 
        time: timeStr, 
        tags: tags.split(',').map(t => t.trim()).filter(Boolean),
        isRecurring,
        recurringFrequency: isRecurring ? recurringFrequency : undefined,
        recurringDay: !isRecurring
          ? undefined
          : recurringFrequency === 'specific'
            ? Math.min(31, Math.max(1, parseInt(recurringDay) || 1))
            : recurringFrequency === 'yearly' ? annualDay : undefined,
        recurringMonth: isRecurring && recurringFrequency === 'yearly' ? annualMonth : undefined,
        subscriptionType: categoryType === 'Suscripción' ? subscriptionType : '',
      } as any )
    };

    try {
      if (payload.paymentMethod === 'Crédito' && payload.cardName) {
        const card = creditCards.find(c => c.name === payload.cardName);
        if (card && card.creditLimit > 0) {
          let balanceAfter = card.currentBalance + totalCharge;
          let available = card.creditLimit - card.currentBalance;
          // Al editar una compra de esta misma tarjeta, su monto anterior ya está en el saldo.
          if (editingId && selectedExpense?.paymentMethod === 'Crédito' && selectedExpense.cardName === card.name) {
            balanceAfter = card.currentBalance - selectedExpense.amount + payload.amount;
            available += selectedExpense.amount;
          }
          if (balanceAfter > card.creditLimit) {
            Alert.alert('Cupo excedido', `El gasto supera el límite. Disponible estimado: ${formatCurrency(Math.max(0, available))}`);
            return;
          }
        }
      } else if ((payload.paymentMethod === 'Débito' || payload.paymentMethod === 'Efectivo') && payload.accountName) {
        const accBals = await getAccountBalances();
        const acc = accounts.find(a => a.name === payload.accountName);
        if (acc) {
          let balanceAfter = accBals[acc.id] || 0;
          if (editingId && selectedExpense) balanceAfter += selectedExpense.amount;
          if (balanceAfter < totalCharge) {
            Alert.alert('Saldo Insuficiente', `La cuenta ${payload.accountName} no tiene fondos suficientes. Disponible estimado: ${formatCurrency(balanceAfter)}`);
            return;
          }
        }
      }

      const chargeToDeductGlobal = editingId ? payload.amount : totalCharge;
      if (payload.paymentMethod !== 'Crédito') {
        const tb = await getTotalBalance();
        let globalAfter = tb.balance;
        if (editingId && selectedExpense) globalAfter += selectedExpense.amount;
        if (globalAfter < chargeToDeductGlobal) {
          Alert.alert('Balance Negativo', `No se permite un balance total negativo. Disponible global estimado: ${formatCurrency(globalAfter)}`);
          return;
        }
      }

      // Compras con tarjeta: saldo de la tarjeta y préstamo quedan vinculados al gasto.
      const card = payload.paymentMethod === 'Crédito' && payload.cardName
        ? creditCards.find(c => c.name === payload.cardName)
        : undefined;
      const nextPaymentDate = card
        ? calculateCreditCardNextPaymentDate(payload.date, card.cutOffDay, card.paymentDueDay)
        : '';

      if (editingId) {
        const before = await ExpenseRepo.get(editingId);
        const updated = await ExpenseRepo.update({ id: editingId, ...payload });
        if (before) await updateCardPurchase(before, updated, card, nextPaymentDate);
      } else {
        const expense = await ExpenseRepo.add(payload);
        if (appliesTaxes && totalTax > 0) {
           await ExpenseRepo.add({ 
              ...payload, 
              detail: `${payload.detail} (Impuestos/Comisiones)`, 
              amount: roundMoney(totalTax),
              parentExpenseId: expense.id 
           });
        }

        if (card) {
          await registerCardPurchase(expense, card, totalCharge, nextPaymentDate);
        }
      }

      // Memorize choices for the next entry
      memoryPaymentMethod = payload.paymentMethod as any;
      if (payload.accountName) memoryDebitAcc = payload.accountName;
      if (payload.cardName) memoryCreditAcc = payload.cardName;

      setFormOpen(false);
      ToastManager.show('Gasto guardado con éxito');
      loadData();
      syncAfterDataChange();
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo guardar el gasto');
    }
  };

  const handleDeleteExpense = () => {
    if (!selectedExpense) return;
    const isCardPurchase = selectedExpense.paymentMethod === 'Crédito' && !!selectedExpense.cardName;
    const message = isCardPurchase
      ? '¿Estás seguro de eliminar este gasto? Lo que siga pendiente de la compra se quitará de la tarjeta y se eliminarán su préstamo y su cargo de impuestos, si los tiene.'
      : '¿Estás seguro de eliminar este gasto?';
    Alert.alert('Eliminar Gasto', message, [
      { text: 'Cancelar', style: 'cancel' },
      { 
        text: 'Eliminar', style: 'destructive', 
        onPress: async () => {
          await deleteExpenseWithCardSync(selectedExpense.id);
          setDetailOpen(false);
          loadData();
          syncAfterDataChange();
        }
      }
    ]);
  };

  const totalSpent = useMemo(() => expenses.reduce((s, e) => s + e.amount, 0), [expenses]);

  // Categorías configuradas + las que sólo aparecen en los gastos (sin color propio).
  const filterCategoryOptions = useMemo(() => [
    ...categories.map(c => ({ name: c.name, color: c.color as string | undefined })),
    ...expenseCategoryNames
      .filter(name => !categories.some(c => c.name === name))
      .map(name => ({ name, color: undefined as string | undefined })),
  ], [categories, expenseCategoryNames]);
  let lastDateRendered = '';

  return (
    <View style={[styles.container, { backgroundColor: colors.background, paddingTop: insets.top }]}>
      
      {/* Header */}
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Gastos</Text>
        <TouchableOpacity 
          style={[styles.iconBtn, { backgroundColor: activeFilters ? colors.primaryContainer : 'transparent' }]}
          onPress={openFilters}
        >
          <Ionicons name="filter" size={24} color={activeFilters ? colors.primary : colors.onSurface} />
        </TouchableOpacity>
      </View>

      {/* Summary Card */}
      <View style={{ paddingHorizontal: 16 }}>
        <Card elevated>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <View>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total gastado</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.error }}>{formatCurrency(totalSpent)}</Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Transacciones</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.onSurface }}>{expenses.length}</Text>
            </View>
          </View>
        </Card>
      </View>

      {/* List */}
      <ScrollView 
        contentContainerStyle={{ padding: 16, paddingBottom: 100 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      >
        {expenses.length === 0 ? (
          <View style={styles.emptyState}>
            <Ionicons name="receipt-outline" size={48} color={colors.outline} />
            <Text style={[styles.emptyTitle, { color: colors.onSurfaceVariant }]}>Sin gastos</Text>
            <Text style={[styles.emptySubtitle, { color: colors.onSurfaceVariant }]}>Toca el botón + para registrar uno</Text>
          </View>
        ) : (
          expenses.map((exp, i) => {
            const showDateHeader = exp.date !== lastDateRendered;
            if (showDateHeader) lastDateRendered = exp.date;

            const cat = categories.find(c => c.name === exp.category);
            const iconName = cat?.icon || 'receipt';
            const iconColor = cat?.color || colors.error;
            const iconBgColor = cat?.color ? cat.color + '20' : colors.errorContainer;

            return (
              <React.Fragment key={exp.id}>
                {showDateHeader && (
                  <Text style={[styles.dateHeader, { color: colors.onSurfaceVariant }]}>{formatDate(exp.date)}</Text>
                )}
                <TouchableOpacity 
                  style={[styles.listItem, { borderBottomColor: colors.outlineVariant }]}
                  onPress={() => {
                    setSelectedExpense(exp);
                    setDetailOpen(true);
                  }}
                >
                  <View style={[styles.listIcon, { backgroundColor: iconBgColor }]}>
                    <Ionicons name={iconName as any} size={20} color={iconColor} />
                  </View>
                  <View style={styles.listContent}>
                    <Text style={[styles.listTitle, { color: colors.onSurface }]} numberOfLines={1}>{exp.detail}</Text>
                    <Text style={[styles.listSubtitle, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
                      {exp.category} 
                      {exp.paymentMethod === 'Débito' && exp.accountName ? ` · ${exp.accountName}` : ''}
                      {exp.paymentMethod === 'Crédito' && exp.cardName ? ` · ${exp.cardName}` : ''}
                      {exp.isDeferred ? ` · Diferido ${exp.deferredMonths}m` : ''}
                      {exp.isRecurring ? (exp.recurringFrequency === 'yearly' ? ' · Anual' : ' · Recurrente') : ''}
                    </Text>
                  </View>
                  <View style={styles.listTrailing}>
                    <Text style={[styles.listAmount, { color: colors.error }]}>-{formatCurrency(exp.amount)}</Text>
                    <Text style={[styles.listTime, { color: colors.onSurfaceVariant }]}>{(exp as any).time || ''}</Text>
                  </View>
                </TouchableOpacity>
              </React.Fragment>
            );
          })
        )}
      </ScrollView>

      {/* FAB */}
      <TouchableOpacity 
        style={[styles.fab, { backgroundColor: colors.error }]} 
        activeOpacity={0.8}
        onPress={openNewForm}
      >
        <Ionicons name="add" size={32} color={colors.onError} />
      </TouchableOpacity>


      {/* ==== DETAILS MODAL ==== */}
      <BottomSheet visible={isDetailOpen} onClose={() => setDetailOpen(false)} title="Detalle del gasto">
        {selectedExpense && (
          <View>
            <View style={{ alignItems: 'center', marginBottom: 24 }}>
              <Text style={{ color: colors.onSurfaceVariant }}>Monto</Text>
              <Text style={{ fontSize: 36, fontWeight: 'bold', color: colors.error }}>
                -{formatCurrency(selectedExpense.amount)}
              </Text>
            </View>

            <View style={[styles.detailGrid, { borderTopColor: colors.outlineVariant, borderTopWidth: 1, paddingTop: 16 }]}>
              <View style={styles.detailItem}>
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Categoría</Text>
                <Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedExpense.category}</Text>
              </View>
              <View style={styles.detailItem}>
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Método</Text>
                <Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedExpense.paymentMethod}</Text>
              </View>
              <View style={styles.detailItem}>
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cuenta/Tarjeta</Text>
                <Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedExpense.accountName || selectedExpense.cardName || '-'}</Text>
              </View>
              <View style={styles.detailItem}>
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Fecha</Text>
                <Text style={{ fontSize: 16, color: colors.onSurface }}>{formatDate(selectedExpense.date)}</Text>
              </View>
              {selectedExpense.isDeferred && (
                <View style={styles.detailItem}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Diferido</Text>
                  <Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedExpense.deferredMonths} meses</Text>
                </View>
              )}
              {selectedExpense.isRecurring && (
                <View style={styles.detailItem}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Recurrente</Text>
                  <Text style={{ fontSize: 16, color: colors.onSurface }}>
                    {describeRecurrence(selectedExpense)}
                  </Text>
                </View>
              )}
            </View>

            <View style={{ marginTop: 16, borderTopColor: colors.outlineVariant, borderTopWidth: 1, paddingTop: 16 }}>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Detalle</Text>
              <Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedExpense.detail}</Text>
            </View>
            
            <View style={{ flexDirection: 'row', gap: 12, marginTop: 32 }}>
              <Button style={{ flex: 1 }} variant="outlined" title="Editar" icon="pencil" onPress={() => openEditForm(selectedExpense)} />
              <Button style={{ flex: 1 }} variant="danger" title="Eliminar" icon="trash" onPress={handleDeleteExpense} />
            </View>
          </View>
        )}
      </BottomSheet>


      {/* ==== FILTER MODAL ==== */}
      <BottomSheet visible={isFilterOpen} onClose={() => setFilterOpen(false)} title="Filtrar gastos">
        <View style={{ gap: 16 }}>
          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}>
              <TextField label="Desde (opcional)" placeholder="YYYY-MM-DD" value={filterFrom} onChangeText={setFilterFrom} isDate />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="Hasta (opcional)" placeholder="YYYY-MM-DD" value={filterTo} onChangeText={setFilterTo} isDate />
            </View>
          </View>

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Método de pago</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {['', 'Efectivo', 'Débito', 'Crédito'].map((m) => (
                <Chip key={m || 'all'} label={m || 'Todos'} active={filterMethod === m} onPress={() => setFilterMethod(m)} />
              ))}
            </View>
          </View>

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Categorías (puedes elegir varias)</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              <Chip label="Todas" active={filterCats.length === 0} onPress={() => setFilterCats([])} />
              {filterCategoryOptions.map((c) => (
                <Chip key={c.name} label={c.name} active={filterCats.includes(c.name)} onPress={() => toggleFilterCat(c.name)} color={c.color} />
              ))}
            </View>
          </View>

          <View style={{ marginTop: 16 }}>
            <Button title="Aplicar filtros" onPress={handleApplyFilters} />
            <Button title="Limpiar filtros" variant="outlined" style={{ marginTop: 12 }} onPress={clearFilters} />
          </View>
        </View>
      </BottomSheet>


      {/* ==== FORM MODAL ==== */}
      <BottomSheet visible={isFormOpen} onClose={() => setFormOpen(false)} title={editingId ? 'Editar gasto' : 'Nuevo gasto'}>
        <View style={{ gap: 16 }}>
          <TextField 
            label="Monto *" 
            placeholder="Ej. 25.50" 
            keyboardType="decimal-pad" 
            value={amountStr} 
            onChangeText={setAmountStr} 
          />

          {(() => {
            const amountParsed = parseFloat(amountStr.replace(',', '.')) || 0;
            let taxComision = 0;
            let taxIva = 0;
            let taxIsd = 0;
            let appliesTaxes = false;

            if (taxesConfig?.enabled && taxesConfig.applyTo.includes(`Gastos:${categoryType}`)) {
              appliesTaxes = true;
              taxComision = (amountParsed * taxesConfig.comisionRate) / 100;
              taxIva = (taxComision * taxesConfig.ivaRate) / 100;
              taxIsd = (amountParsed * taxesConfig.isdRate) / 100;
            }
            const totalTax = taxComision + taxIva + taxIsd;
            const totalCharge = amountParsed + totalTax;

            if (appliesTaxes && totalTax > 0) {
              return (
                <View style={{ backgroundColor: colors.surfaceContainerHighest, padding: 12, borderRadius: 12 }}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 4 }}>Desglose de Impuestos/Comisión</Text>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ fontSize: 13, color: colors.onSurface }}>Comisión ({taxesConfig?.comisionRate}%)</Text>
                    <Text style={{ fontSize: 13, color: colors.onSurface }}>+{formatCurrency(taxComision)}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ fontSize: 13, color: colors.onSurface }}>IVA sobre Comisión ({taxesConfig?.ivaRate}%)</Text>
                    <Text style={{ fontSize: 13, color: colors.onSurface }}>+{formatCurrency(taxIva)}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                    <Text style={{ fontSize: 13, color: colors.onSurface }}>ISD sobre Principal ({taxesConfig?.isdRate}%)</Text>
                    <Text style={{ fontSize: 13, color: colors.onSurface }}>+{formatCurrency(taxIsd)}</Text>
                  </View>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 8, paddingTop: 8, borderTopWidth: 1, borderTopColor: colors.outlineVariant }}>
                    <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.onSurface }}>Total retenido y monto debitado</Text>
                    <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.primary }}>{formatCurrency(totalCharge)}</Text>
                  </View>
                </View>
              );
            }
            return null;
          })()}

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Categoría *</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {categories.map((c) => (
                <Chip key={c.name} label={c.name} active={categoryType === c.name} onPress={() => setCategoryType(c.name)} icon={c.icon as any} color={c.color} />
              ))}
            </View>
          </View>

          <TextField 
            label="Detalle *" 
            placeholder="Ej. Compra supermercado" 
            value={detail} 
            onChangeText={setDetail} 
          />

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Método de pago</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {(['Efectivo', 'Débito', 'Crédito'] as const).map((m) => (
                <Chip key={m} label={m} active={paymentMethod === m} onPress={() => setPaymentMethod(m)} />
              ))}
            </View>
          </View>

          {(paymentMethod === 'Débito' || paymentMethod === 'Efectivo') && accounts.length > 0 && (
            <View>
               <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>
                 {paymentMethod === 'Efectivo' ? 'Extraído de cuenta (Opcional)' : 'Cuenta de débito'}
               </Text>
               <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
                 {paymentMethod === 'Efectivo' && (
                   <Chip label="Efectivo físico (Bolsillo)" active={!selectedDebit} onPress={() => setSelectedDebit('')} />
                 )}
                 {accounts.map(a => (
                   <Chip key={a.name} label={`${a.name} · ${a.bankName}`} active={selectedDebit === a.name} onPress={() => setSelectedDebit(a.name)} />
                 ))}
               </ScrollView>
            </View>
          )}

          {paymentMethod === 'Crédito' && creditCards.length > 0 && (
            <View style={{ gap: 16 }}>
               <View>
                 <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Tarjeta de crédito</Text>
                 <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
                   {creditCards.map(c => (
                     <Chip key={c.name} label={`${c.name} · ${c.bankName}`} active={selectedCredit === c.name} onPress={() => setSelectedCredit(c.name)} />
                   ))}
                 </ScrollView>
               </View>

               <View>
                 <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>¿Diferido?</Text>
                 <View style={{ flexDirection: 'row', gap: 8 }}>
                   <Chip label="Sí" active={isDeferred} onPress={() => setIsDeferred(true)} />
                   <Chip label="No" active={!isDeferred} onPress={() => setIsDeferred(false)} />
                 </View>
               </View>

               {isDeferred && (
                 <TextField label="Meses diferidos" placeholder="Ej. 3" keyboardType="number-pad" value={deferredMonths} onChangeText={setDeferredMonths} />
               )}
            </View>
          )}

          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}>
              <TouchableOpacity onPress={() => setDatePickerOpen(true)}>
                <View pointerEvents="none">
                  <TextField label="Fecha *" placeholder="YYYY-MM-DD" value={dateStr} onChangeText={() => {}} />
                </View>
              </TouchableOpacity>
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="Hora (Opcional)" placeholder="Ej. 14:30" value={timeStr} onChangeText={setTimeStr} />
            </View>
          </View>

          <View style={{ paddingTop: 8, borderTopWidth: 1, borderTopColor: colors.outlineVariant }}>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>¿Es un gasto recurrente / suscripción?</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <Chip label="Sí" active={isRecurring} onPress={() => setIsRecurring(true)} />
              <Chip label="No" active={!isRecurring} onPress={() => setIsRecurring(false)} />
            </View>
          </View>

          {isRecurring && (
            <View style={{ gap: 16 }}>
              <View>
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Frecuencia</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  <Chip label="Mensual" active={recurringFrequency === 'monthly'} onPress={() => setRecurringFrequency('monthly')} />
                  <Chip label="Día específico" active={recurringFrequency === 'specific'} onPress={() => setRecurringFrequency('specific')} />
                  <Chip
                    label="Anual"
                    active={recurringFrequency === 'yearly'}
                    onPress={() => {
                      setRecurringFrequency('yearly');
                      if (!recurringDate) setRecurringDate(dateStr);
                    }}
                  />
                </View>
              </View>
              {recurringFrequency === 'specific' && (
                <TextField label="Día de cobro (1-31)" placeholder="Ej. 15" keyboardType="number-pad" value={recurringDay} onChangeText={setRecurringDay} />
              )}
              {recurringFrequency === 'yearly' && (
                <TextField
                  label="Fecha de cobro anual"
                  placeholder="YYYY-MM-DD"
                  value={recurringDate || dateStr}
                  onChangeText={setRecurringDate}
                  isDate
                  help="Se repite cada año en este día y mes; te avisaremos antes de cada cobro."
                />
              )}
            </View>
          )}

          <View style={{ flexDirection: 'row', gap: 12, marginTop: 16 }}>
             <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={() => setFormOpen(false)} />
             <Button style={{ flex: 1 }} title={editingId ? 'Actualizar gasto' : 'Guardar gasto'} onPress={handleSaveExpense} />
          </View>
        </View>
      </BottomSheet>

      <DatePickerModal 
        visible={isDatePickerOpen} 
        onClose={() => setDatePickerOpen(false)} 
        value={dateStr} 
        onSelect={setDateStr} 
      />

    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  title: { fontSize: 24, fontWeight: 'bold' },
  iconBtn: {
    width: 40, height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyState: { alignItems: 'center', paddingVertical: 60 },
  emptyTitle: { fontSize: 18, fontWeight: 'bold', marginTop: 16 },
  emptySubtitle: { fontSize: 14, marginTop: 4 },
  dateHeader: { fontSize: 14, fontWeight: '600', marginTop: 16, marginBottom: 8, marginHorizontal: 16 },
  listItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  listIcon: {
    width: 40, height: 40, borderRadius: 12,
    alignItems: 'center', justifyContent: 'center', marginRight: 12,
  },
  listContent: { flex: 1, marginRight: 8 },
  listTitle: { fontSize: 16, fontWeight: '500' },
  listSubtitle: { fontSize: 12, marginTop: 2 },
  listTrailing: { alignItems: 'flex-end' },
  listAmount: { fontSize: 16, fontWeight: 'bold' },
  listTime: { fontSize: 12, marginTop: 2 },
  fab: {
    position: 'absolute',
    bottom: 24, right: 24,
    width: 56, height: 56,
    borderRadius: 28,
    alignItems: 'center', justifyContent: 'center',
    elevation: 4, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4,
  },
  detailGrid: {
    flexDirection: 'row', flexWrap: 'wrap', gap: 16, rowGap: 24,
  },
  detailItem: { width: '45%' },
});
