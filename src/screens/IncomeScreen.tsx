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
import { ProgressBar } from '../components/ProgressBar';
import { DatePickerModal } from '../components/DatePickerModal';

import { formatCurrency, formatDate, formatMonths, getToday, roundMoney, toAmountInput } from '../utils/formatters';
import { syncAfterDataChange } from '../utils/dataSync';
import { availabilityLevel } from '../utils/cardPurchases';
import { 
  SalaryRepo, IncomeRepo, AccountRepo, CreditCardRepo, InvestmentRepo, ExpenseRepo, getAccountBalances, SettingsRepo 
} from '../db/storage';
import { Salary, Income, Account, CreditCard, Investment, TaxesConfig } from '../types';

// "Nómina" ya no se ofrece como tipo de cuenta.
const ACCOUNT_TYPES = ['Ahorro', 'Corriente'];

export default function IncomeScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  const [refreshing, setRefreshing] = useState(false);
  const [salary, setSalary] = useState<Salary | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [creditCards, setCreditCards] = useState<CreditCard[]>([]);
  const [investments, setInvestments] = useState<Investment[]>([]);
  const [incomes, setIncomes] = useState<Income[]>([]);
  const [taxesConfig, setTaxesConfig] = useState<TaxesConfig | null>(null);

  // Modals visibility
  const [sheetCurrent, setSheetCurrent] = useState<'salary' | 'account' | 'creditCard' | 'investment' | 'income' | null>(null);
  
  // Specific Data state for editing
  const [editingData, setEditingData] = useState<any>(null);

  // General Form States
  const [formName, setFormName] = useState('');
  const [formAmount, setFormAmount] = useState('');
  const [formDetail, setFormDetail] = useState('');

  // Salary Form States
  const [payDateType, setPayDateType] = useState('specific');
  const [payDate, setPayDate] = useState('');
  const [recurrence, setRecurrence] = useState('Mensual');
  const [salaryAccount, setSalaryAccount] = useState('');

  // Account Form States
  const [accountBalances, setAccountBalances] = useState<Record<string, number>>({});
  const [initialBalanceForm, setInitialBalanceForm] = useState('');
  const [accountType, setAccountType] = useState('Ahorro');
  const [bankName, setBankName] = useState('');
  const [refNumber, setRefNumber] = useState('');

  // Credit Card Forms
  const [cutOffDay, setCutOffDay] = useState('');
  const [paymentDay, setPaymentDay] = useState('');
  const [creditLimit, setCreditLimit] = useState('');

  // Investment Forms
  const [remainingMonths, setRemainingMonths] = useState('');
  const [totalMonths, setTotalMonths] = useState('');
  const [totalDeposited, setTotalDeposited] = useState('');
  const [depositSourceAccount, setDepositSourceAccount] = useState('');

  // Income Forms
  const [incomeSource, setIncomeSource] = useState('');
  const [incomeDate, setIncomeDate] = useState(getToday());
  const [isDatePickerOpen, setDatePickerOpen] = useState(false);
  const [incomeAccount, setIncomeAccount] = useState('');

  // Detailed Modals
  const [isDetailOpen, setDetailOpen] = useState(false);
  const [detailType, setDetailType] = useState<'account' | 'creditCard' | 'investment' | 'income' | null>(null);
  const [detailData, setDetailData] = useState<any>(null);

  const loadData = async () => {
    try {
      const sals = await SalaryRepo.getAll();
      setSalary((sals[0] as any) || null);

      const accList = await AccountRepo.getAll();
      setAccounts(accList);
      setCreditCards(await CreditCardRepo.getAll());
      setInvestments(await InvestmentRepo.getAll());

      const incs = await IncomeRepo.getAll();
      incs.sort((a, b) => (b.date || '').localeCompare(a.date || ''));
      setIncomes(incs);

      setAccountBalances(await getAccountBalances());
      const s = await SettingsRepo.get();
      setTaxesConfig(s.taxes || null);
    } catch (e) {
      console.error(e);
    }
  };

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [])
  );

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const closeCurrentSheet = () => {
    setSheetCurrent(null);
    setEditingData(null);
    setDetailOpen(false);
  };

  const openForm = (type: 'salary' | 'account' | 'creditCard' | 'investment' | 'income', data: any = null) => {
    setEditingData(data);
    setSheetCurrent(type);

    if (type === 'salary') {
      setFormAmount(toAmountInput(data?.amount));
      setPayDateType(data?.payDateType || 'specific');
      setPayDate(data?.payDate || '');
      setRecurrence(data?.recurrence || 'Mensual');
      setSalaryAccount(data?.bankAccount || '');
    } else if (type === 'account') {
      setFormName(data?.name || '');
      setBankName(data?.bankName || '');
      setAccountType(ACCOUNT_TYPES.includes(data?.accountType) ? data.accountType : 'Ahorro');
      setRefNumber(data?.referenceNumber || '');
      setInitialBalanceForm(toAmountInput(data?.initialBalance));
    } else if (type === 'creditCard') {
      setFormName(data?.name || '');
      setBankName(data?.bankName || '');
      setCutOffDay(data?.cutOffDay?.toString() || '');
      setPaymentDay(data?.paymentDueDay?.toString() || '');
      setCreditLimit(toAmountInput(data?.creditLimit));
      // El formulario pide el cupo disponible; internamente se guarda lo usado.
      setFormAmount(data?.creditLimit > 0 ? toAmountInput(Math.max(0, data.creditLimit - (Number(data.currentBalance) || 0))) : '');
    } else if (type === 'investment') {
      setFormName(data?.entity || '');
      setFormAmount(toAmountInput(data?.monthlyDeposit));
      setRemainingMonths(data?.remainingMonths?.toString() || '');
      setTotalMonths(data?.totalMonths?.toString() || '');
      setFormDetail(data?.detail || '');
      setTotalDeposited(toAmountInput(data?.totalDeposited));
    } else if (type === 'income') {
      setFormAmount(toAmountInput(data?.amount));
      setIncomeSource(data?.source || '');
      setFormDetail(data?.detail || '');
      setIncomeAccount(data?.bankAccount || '');
      setIncomeDate(data?.date || getToday());
    }
  };

  const openDetail = (type: 'account' | 'creditCard' | 'investment' | 'income', data: any) => {
    setDetailType(type);
    setDetailData(data);
    setDetailOpen(true);
    setDepositSourceAccount('');
  };

  // ----- SAVE HANDLERS -----

  const handleSaveSalary = async () => {
    const amt = parseFloat(formAmount.replace(',', '.'));
    if (isNaN(amt) || amt <= 0) return Alert.alert('Error', 'Ingresa un monto válido');
    
    await SalaryRepo.save({
      id: editingData?.id || 'salary-1',
      amount: amt,
      payDateType,
      payDate: payDateType === 'specific' ? payDate || '1' : '',
      recurrence,
      bankAccount: salaryAccount
    });
    closeCurrentSheet();
    ToastManager.show('Sueldo configurado con éxito');
    loadData();
    syncAfterDataChange();
  };

  const handleSaveAccount = async () => {
    if (!formName.trim() || !bankName.trim()) return Alert.alert('Error', 'Nombre y banco son obligatorios');
    const payload = {
      name: formName.trim(), bankName: bankName.trim(),
      referenceNumber: refNumber.trim(), accountType,
      initialBalance: parseFloat(initialBalanceForm.replace(',', '.')) || 0
    };
    if (editingData) await AccountRepo.update({ ...editingData, ...payload });
    else await AccountRepo.add(payload);
    closeCurrentSheet();
    ToastManager.show('Cuenta guardada con éxito');
    loadData();
    syncAfterDataChange();
  };

  const handleSaveCreditCard = async () => {
    if (!formName.trim() || !bankName.trim()) return Alert.alert('Error', 'Nombre y banco son obligatorios');
    const limit = parseFloat(creditLimit) || 0;
    const available = parseFloat(formAmount);
    if (limit <= 0) return Alert.alert('Error', 'Ingresa el límite de crédito de la tarjeta');
    if (isNaN(available) || available < 0) return Alert.alert('Error', 'Ingresa el cupo disponible (0 si ya no te queda cupo)');
    if (available > limit) return Alert.alert('Error', 'El cupo disponible no puede ser mayor al límite de crédito');
    const cutOff = parseInt(cutOffDay) || 0;
    const dueDay = parseInt(paymentDay) || 0;
    if (cutOff > 31 || dueDay > 31) return Alert.alert('Error', 'Los días de corte y de pago deben estar entre 1 y 31');

    const payload = {
      name: formName.trim(), bankName: bankName.trim(),
      cutOffDay: cutOff,
      paymentDueDay: dueDay,
      creditLimit: roundMoney(limit),
      // Se guarda lo usado (deuda): compras, pagos y validaciones trabajan con ese valor.
      currentBalance: roundMoney(limit - available),
    };
    if (editingData) await CreditCardRepo.update({ ...editingData, ...payload });
    else await CreditCardRepo.add(payload);
    closeCurrentSheet();
    ToastManager.show('Tarjeta guardada con éxito');
    loadData();
    syncAfterDataChange();
  };

  const handleSaveInvestment = async () => {
    const amt = parseFloat(formAmount.replace(',', '.'));
    if (!formName.trim() || isNaN(amt) || amt <= 0) return Alert.alert('Error', 'Entidad y depósito válido son obligatorios');
    const termMonths = parseInt(totalMonths) || 0;
    const monthsLeft = parseInt(remainingMonths) || 0;
    if (termMonths > 0 && monthsLeft > termMonths) {
      return Alert.alert('Error', 'Los meses restantes no pueden superar el plazo total');
    }
    
    const payload = {
      entity: formName.trim(),
      monthlyDeposit: amt,
      remainingMonths: monthsLeft,
      totalMonths: termMonths,
      detail: formDetail.trim(),
      totalDeposited: roundMoney(parseFloat(totalDeposited) || 0),
    };
    if (editingData) await InvestmentRepo.update({ ...editingData, ...payload });
    else await InvestmentRepo.add(payload);
    closeCurrentSheet();
    ToastManager.show('Inversión guardada con éxito');
    loadData();
    syncAfterDataChange();
  };

  const handleSaveIncome = async () => {
    const amt = parseFloat(formAmount.replace(',', '.'));
    if (isNaN(amt) || amt <= 0) return Alert.alert('Error', 'Monto válido obligatorio');
    if (!formDetail.trim() || !incomeAccount) return Alert.alert('Error', 'Detalle y cuenta son obligatorios');

    const payload = {
      amount: amt,
      source: incomeSource.trim(),
      detail: formDetail.trim(),
      bankAccount: incomeAccount,
      bankName: incomeAccount.split(' - ')[1] || '',
      date: incomeDate || getToday(),
    };

    if (editingData) await IncomeRepo.update({ ...editingData, ...payload });
    else await IncomeRepo.add(payload);
    closeCurrentSheet();
    ToastManager.show('Ingreso guardado con éxito');
    loadData();
    syncAfterDataChange();
  };

  // ----- DELETE HANDLERS -----

  const handleDelete = (repo: any, id: string, name: string) => {
    Alert.alert('Eliminar', `¿Estás seguro de eliminar ${name}?`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Eliminar', style: 'destructive', onPress: async () => {
          await repo.delete(id);
          closeCurrentSheet();
          loadData();
          syncAfterDataChange();
        } 
      }
    ]);
  };

  const handleInvestmentDeposit = async () => {
    const inv = detailData as Investment;

    const acc = accounts.find(a => a.name === depositSourceAccount);
    if (!acc) return Alert.alert('Error', 'Cuenta origen inválida');

    const amount = inv.monthlyDeposit;
    let taxComision = 0;
    let taxIva = 0;
    let taxIsd = 0;
    let appliesTaxes = false;

    if (taxesConfig?.enabled && taxesConfig.applyTo.includes('Inversiones')) {
      appliesTaxes = true;
      taxComision = (amount * taxesConfig.comisionRate) / 100;
      taxIva = (taxComision * taxesConfig.ivaRate) / 100;
      taxIsd = (amount * taxesConfig.isdRate) / 100;
    }
    const totalTax = taxComision + taxIva + taxIsd;
    const totalCharge = amount + totalTax;

    if ((accountBalances[acc.id] || 0) < totalCharge) {
      return Alert.alert('Saldo Insuficiente', `La cuenta ${depositSourceAccount} no tiene fondos suficientes para descontar ${formatCurrency(totalCharge)}. Disponible: ${formatCurrency(accountBalances[acc.id] || 0)}`);
    }

    await ExpenseRepo.add({
      amount: inv.monthlyDeposit,
      category: 'Inversión',
      detail: `Depósito mensual — ${inv.entity}`,
      accountName: depositSourceAccount,
      paymentMethod: 'Débito',
      date: getToday(),
      ...( { 
        isInvestmentDeposit: true, 
        investmentId: inv.id, 
        time: new Date().toTimeString().slice(0, 5), 
        tags: ['inversión', 'depósito'] 
      } as any )
    });

    if (appliesTaxes && totalTax > 0) {
      await ExpenseRepo.add({
        amount: Math.round(totalTax * 100) / 100,
        category: 'Otros',
        detail: `Depósito mensual (Retenciones) — ${inv.entity}`,
        accountName: depositSourceAccount,
        paymentMethod: 'Débito',
        date: getToday(),
        ...( { time: new Date().toTimeString().slice(0, 5), tags: ['impuestos', 'inversión'] } as any )
      });
    }

    await InvestmentRepo.update({
      ...inv,
      totalDeposited: roundMoney((inv.totalDeposited || 0) + inv.monthlyDeposit),
      remainingMonths: Math.max(0, (inv.remainingMonths || 0) - 1)
    });

    closeCurrentSheet();
    Alert.alert('Éxito', `Depósito de ${formatCurrency(inv.monthlyDeposit)} registrado como gasto`);
    loadData();
    syncAfterDataChange();
  };


  const totalExtraIncome = useMemo(() => incomes.reduce((s, i) => s + i.amount, 0), [incomes]);

  return (
    <View style={[styles.container, { backgroundColor: colors.background, paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Ingresos</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}>
        
        {/* Salary */}
        <View style={styles.sectionHeader}>
          <Ionicons name="cash-outline" size={20} color={colors.onSurfaceVariant} />
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Sueldo</Text>
        </View>
        <TouchableOpacity activeOpacity={0.8} onPress={() => openForm('salary', salary)}>
          <Card gradientGreen>
            {salary ? (
              <View>
                <Text style={styles.cardLabel}>Sueldo configurado</Text>
                <Text style={styles.cardValue} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>{formatCurrency(salary.amount)}</Text>
                <Text style={styles.cardSubtext}>
                  Día de pago: {salary.payDateType === 'last' ? 'Último día del mes' : salary.payDateType === 'lastBusiness' ? 'Último hábil' : 'Día ' + salary.payDate} · {salary.recurrence || 'Mensual'}
                  {salary.bankAccount ? `\nDepositado en: ${salary.bankAccount}` : ''}
                </Text>
              </View>
            ) : (
              <View style={styles.emptyCardContent}>
                <Ionicons name="add-circle-outline" size={40} color="rgba(255,255,255,0.7)" />
                <Text style={styles.cardLabel}>Configurar sueldo</Text>
              </View>
            )}
          </Card>
        </TouchableOpacity>

        {/* Accounts */}
        <View style={styles.sectionHeader}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Ionicons name="wallet-outline" size={20} color={colors.onSurfaceVariant} />
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Cuentas Bancarias</Text>
          </View>
          <TouchableOpacity onPress={() => openForm('account')}>
            <Ionicons name="add-circle" size={28} color={colors.primary} />
          </TouchableOpacity>
        </View>
        {accounts.length === 0 ? (
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>Sin cuentas registradas</Text>
        ) : (
          accounts.map(acc => (
            <TouchableOpacity key={acc.id} onPress={() => openDetail('account', acc)}>
              <Card style={styles.smallCard}>
                <View style={styles.rowBetween}>
                  <View>
                    <Text style={[styles.cardTitle, { color: colors.onSurface }]}>{acc.name}</Text>
                    <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>{acc.bankName} · {acc.accountType}</Text>
                  </View>
                  <Text style={[styles.cardTitle, { color: colors.secondary }]}>{formatCurrency(accountBalances[acc.id] || 0)}</Text>
                </View>
              </Card>
            </TouchableOpacity>
          ))
        )}

        {/* Credit Cards */}
        <View style={styles.sectionHeader}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Ionicons name="card-outline" size={20} color={colors.onSurfaceVariant} />
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Tarjetas de Crédito</Text>
          </View>
          <TouchableOpacity onPress={() => openForm('creditCard')}>
            <Ionicons name="add-circle" size={28} color={colors.primary} />
          </TouchableOpacity>
        </View>
        {creditCards.length === 0 ? (
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>Sin tarjetas registradas</Text>
        ) : (
          creditCards.map(cc => {
            // Se destaca el cupo disponible: verde con cupo de sobra, rojo cuando casi no queda.
            const hasLimit = cc.creditLimit > 0;
            const available = hasLimit ? Math.max(0, cc.creditLimit - cc.currentBalance) : 0;
            const level = hasLimit ? availabilityLevel(available / cc.creditLimit) : 'success';
            const levelColor = level === 'danger' ? colors.error : level === 'warning' ? colors.tertiary : colors.secondary;
            return (
              <TouchableOpacity key={cc.id} onPress={() => openDetail('creditCard', cc)}>
                <Card style={[styles.smallCard, hasLimit && level === 'danger' && { backgroundColor: `${colors.error}15`, borderColor: colors.error, borderWidth: 1 }]}>
                  <View style={[styles.rowBetween, { marginBottom: hasLimit ? 8 : 0 }]}>
                    <View style={{ flex: 1, paddingRight: 8 }}>
                      <Text style={[styles.cardTitle, { color: colors.onSurface }]} numberOfLines={1}>{cc.name}</Text>
                      <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>{cc.bankName}</Text>
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Text style={{ fontSize: 10, color: colors.onSurfaceVariant }}>{hasLimit ? 'Disponible' : 'Usado'}</Text>
                      <Text style={[styles.cardTitle, { color: hasLimit ? levelColor : colors.onSurface }]}>
                        {formatCurrency(hasLimit ? available : cc.currentBalance)}
                      </Text>
                    </View>
                  </View>
                  {hasLimit && (
                    <View style={{ gap: 4 }}>
                      <ProgressBar progress={available / cc.creditLimit} colorVariant={level} />
                      <View style={styles.rowBetween}>
                        <Text style={{ fontSize: 10, color: colors.onSurfaceVariant }}>Usado: {formatCurrency(cc.currentBalance)}</Text>
                        <Text style={{ fontSize: 10, color: colors.onSurfaceVariant }}>Límite: {formatCurrency(cc.creditLimit)}</Text>
                      </View>
                    </View>
                  )}
                </Card>
              </TouchableOpacity>
            );
          })
        )}

        {/* Investments */}
        <View style={styles.sectionHeader}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Ionicons name="trending-up-outline" size={20} color={colors.onSurfaceVariant} />
            <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Fondos de Inversión</Text>
          </View>
          <TouchableOpacity onPress={() => openForm('investment')}>
            <Ionicons name="add-circle" size={28} color={colors.primary} />
          </TouchableOpacity>
        </View>
        {investments.length === 0 ? (
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>Sin fondos registrados</Text>
        ) : (
          investments.map(inv => (
            <TouchableOpacity key={inv.id} onPress={() => openDetail('investment', inv)}>
              <Card style={styles.smallCard}>
                <View style={styles.rowBetween}>
                  <View style={{ flex: 1, paddingRight: 8 }}>
                    <Text style={[styles.cardTitle, { color: colors.onSurface }]} numberOfLines={1}>{inv.entity}</Text>
                    <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]} numberOfLines={1}>{inv.detail}</Text>
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Text style={[styles.cardTitle, { color: colors.primary }]}>{formatCurrency(inv.monthlyDeposit)}/mes</Text>
                    <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>
                      {inv.remainingMonths > 0
                        ? `${formatMonths(inv.remainingMonths)} ${inv.remainingMonths === 1 ? 'restante' : 'restantes'}`
                        : 'Completado'}
                    </Text>
                  </View>
                </View>
                <View style={[styles.rowBetween, { marginTop: 8, flexWrap: 'wrap', gap: 4 }]}>
                  <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>
                    Total depositado actualmente: <Text style={{ color: colors.secondary, fontWeight: '600' }}>{formatCurrency(inv.totalDeposited)}</Text>
                  </Text>
                  {inv.totalMonths > 0 && (
                    <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>
                      {Math.max(0, inv.totalMonths - inv.remainingMonths)} de {formatMonths(inv.totalMonths)}
                    </Text>
                  )}
                </View>
                {inv.remainingMonths > 0 && (
                  <View style={{ marginTop: 12 }}>
                    <ProgressBar 
                      progress={Math.max(0.05, 1 - (inv.remainingMonths / (inv.totalMonths || inv.remainingMonths)))} 
                      colorVariant="primary" 
                    />
                  </View>
                )}
              </Card>
            </TouchableOpacity>
          ))
        )}

        {/* Extra Incomes */}
        <View style={styles.sectionHeader}>
          <Ionicons name="cash" size={20} color={colors.onSurfaceVariant} />
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Ingresos extra</Text>
        </View>

        <Card elevated>
          <View style={styles.rowBetween}>
            <View>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total extra</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.secondary }}>{formatCurrency(totalExtraIncome)}</Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Registros</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.onSurface }}>{incomes.length}</Text>
            </View>
          </View>
        </Card>

        {incomes.length === 0 ? (
          <View style={styles.emptyState}>
            <Ionicons name="wallet-outline" size={48} color={colors.outline} />
            <Text style={[styles.emptyTitle, { color: colors.onSurfaceVariant }]}>Sin ingresos extra</Text>
          </View>
        ) : (
          <View style={{ marginTop: 16 }}>
            {incomes.map(inc => (
              <TouchableOpacity 
                key={inc.id} 
                style={[styles.listItem, { borderBottomColor: colors.outlineVariant }]}
                onPress={() => openDetail('income', inc)}
              >
                <View style={[styles.listIcon, { backgroundColor: colors.secondaryContainer }]}>
                  <Ionicons name="arrow-down" size={20} color={colors.secondary} />
                </View>
                <View style={styles.listContent}>
                  <Text style={[styles.listTitle, { color: colors.onSurface }]}>{inc.source || 'Ingreso'}</Text>
                  <Text style={[styles.listSubtitle, { color: colors.onSurfaceVariant }]}>{inc.detail} {inc.bankName ? `· ${inc.bankName}` : ''}</Text>
                </View>
                <View style={styles.listTrailing}>
                  <Text style={[styles.listAmount, { color: colors.secondary }]}>+{formatCurrency(inc.amount)}</Text>
                  <Text style={[styles.listTime, { color: colors.onSurfaceVariant }]}>{formatDate(inc.date)}</Text>
                </View>
              </TouchableOpacity>
            ))}
          </View>
        )}

      </ScrollView>

      {/* FAB Extra Income */}
      <TouchableOpacity 
        style={[styles.fab, { backgroundColor: colors.secondary }]} 
        activeOpacity={0.8}
        onPress={() => openForm('income')}
      >
        <Ionicons name="add" size={32} color={colors.onSecondary} />
      </TouchableOpacity>

      {/* ================= MODALS: FORMS ================= */}

      <BottomSheet visible={sheetCurrent === 'salary'} onClose={closeCurrentSheet} title="Configurar sueldo">
        <View style={{ gap: 16 }}>
          <TextField label="Monto *" keyboardType="decimal-pad" value={formAmount} onChangeText={setFormAmount} />
          
          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Tipo de día de pago</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {[
                { label: 'Día específico', val: 'specific' }, 
                { label: 'Último día mes', val: 'last' }, 
                { label: 'Último hábil', val: 'lastBusiness' }
              ].map(opt => (
                <Chip key={opt.val} label={opt.label} active={payDateType === opt.val} onPress={() => setPayDateType(opt.val)} />
              ))}
            </View>
          </View>

          {payDateType === 'specific' && (
            <TextField 
              label="Día de pago (1-31)" 
              placeholder="Ej. 15" 
              keyboardType="number-pad" 
              value={payDate} 
              onChangeText={(text) => {
                let val = text.replace(/[^0-9]/g, '');
                if (val !== '') {
                  let num = parseInt(val, 10);
                  if (num > 31) val = '31';
                  if (num === 0) val = '';
                }
                setPayDate(val);
              }} 
            />
          )}

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Recurrencia</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {['Mensual', 'Quincenal', 'Semanal'].map(r => (
                <Chip key={r} label={r} active={recurrence === r} onPress={() => setRecurrence(r)} />
              ))}
            </View>
          </View>

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Depositar sueldo en cuenta (Opcional)</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {accounts.map(a => (
                <Chip key={a.name} label={a.name} active={salaryAccount === a.name} onPress={() => setSalaryAccount(salaryAccount === a.name ? '' : a.name)} />
              ))}
            </ScrollView>
          </View>

          <Button title="Guardar sueldo" onPress={handleSaveSalary} icon="save" />
        </View>
      </BottomSheet>


      <BottomSheet visible={sheetCurrent === 'account'} onClose={closeCurrentSheet} title={editingData ? 'Editar cuenta' : 'Nueva cuenta'}>
        <View style={{ gap: 16 }}>
          <TextField label="Nombre de cuenta *" placeholder="Ej. Ahorros Principales" value={formName} onChangeText={setFormName} />
          <TextField label="Número de referencia" placeholder="Ej. 123456789" value={refNumber} onChangeText={setRefNumber} />
          
          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {ACCOUNT_TYPES.map(t => (
                <Chip key={t} label={t} active={accountType === t} onPress={() => setAccountType(t)} />
              ))}
            </View>
          </View>

          <TextField label="Banco *" value={bankName} onChangeText={setBankName} />
          <TextField label="Monto inicial actual (Opcional)" placeholder="Ej. 1500.00" keyboardType="decimal-pad" value={initialBalanceForm} onChangeText={setInitialBalanceForm} />
          <View style={{ flexDirection: 'row', gap: 12 }}>
             <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={closeCurrentSheet} />
             <Button style={{ flex: 1 }} title="Guardar cuenta" onPress={handleSaveAccount} icon="save" />
          </View>
        </View>
      </BottomSheet>

      <BottomSheet visible={sheetCurrent === 'creditCard'} onClose={closeCurrentSheet} title={editingData ? 'Editar tarjeta' : 'Nueva tarjeta'}>
        <View style={{ gap: 16 }}>
          <TextField label="Nombre o Alias *" placeholder="Ej. Visa Oro" value={formName} onChangeText={setFormName} />
          <TextField label="Banco *" placeholder="Ej. Santander" value={bankName} onChangeText={setBankName} />
          <TextField
            label="Límite de crédito *"
            placeholder="Ej. 5000"
            keyboardType="decimal-pad"
            value={creditLimit}
            onChangeText={setCreditLimit}
            help="Monto máximo que el banco te permite usar con esta tarjeta."
          />
          <TextField
            label="Cupo disponible *"
            placeholder="Ej. 3750.50"
            keyboardType="decimal-pad"
            value={formAmount}
            onChangeText={setFormAmount}
            help="Lo que aún puedes gastar hoy. Si no has usado la tarjeta es igual al límite; si ya no te queda cupo, escribe 0. No puede ser mayor al límite."
          />
          {(() => {
            const limit = parseFloat(creditLimit) || 0;
            const available = parseFloat(formAmount);
            if (limit <= 0 || isNaN(available) || available > limit) return null;
            return (
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
                Usado actualmente: {formatCurrency(limit - available)}
              </Text>
            );
          })()}
          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}>
              <TextField
                label="Día de corte"
                placeholder="Ej. 15"
                keyboardType="number-pad"
                value={cutOffDay}
                onChangeText={setCutOffDay}
                help="Día del mes en que el banco cierra tu estado de cuenta. Las compras posteriores se cobran en el pago siguiente."
              />
            </View>
            <View style={{ flex: 1 }}>
              <TextField
                label="Día de pago"
                placeholder="Ej. 5"
                keyboardType="number-pad"
                value={paymentDay}
                onChangeText={setPaymentDay}
                help="Fecha máxima del mes para pagar lo facturado al corte."
              />
            </View>
          </View>
          <View style={{ flexDirection: 'row', gap: 12 }}>
             <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={closeCurrentSheet} />
             <Button style={{ flex: 1 }} title="Guardar tarjeta" onPress={handleSaveCreditCard} icon="save" />
          </View>
        </View>
      </BottomSheet>

      <BottomSheet visible={sheetCurrent === 'investment'} onClose={closeCurrentSheet} title={editingData ? 'Editar fondo' : 'Nuevo fondo'}>
        <View style={{ gap: 16 }}>
          <TextField label="Entidad *" placeholder="Ej. GBM+" value={formName} onChangeText={setFormName} />
          <TextField label="Depósito mensual *" placeholder="Ej. 200.00" keyboardType="decimal-pad" value={formAmount} onChangeText={setFormAmount} />
          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}><TextField label="Plazo total (meses)" placeholder="Ej. 24" keyboardType="number-pad" value={totalMonths} onChangeText={setTotalMonths} /></View>
            <View style={{ flex: 1 }}><TextField label="Meses restantes" placeholder="Ej. 12" keyboardType="number-pad" value={remainingMonths} onChangeText={setRemainingMonths} /></View>
          </View>
          <TextField label="Detalle" placeholder="Ej. Fondo para retiro" value={formDetail} onChangeText={setFormDetail} />
          <TextField label="Total depositado actualmente" placeholder="Ej. 2400.00" keyboardType="decimal-pad" value={totalDeposited} onChangeText={setTotalDeposited} />
          <View style={{ flexDirection: 'row', gap: 12 }}>
             <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={closeCurrentSheet} />
             <Button style={{ flex: 1 }} title="Guardar fondo" onPress={handleSaveInvestment} icon="save" />
          </View>
        </View>
      </BottomSheet>

      <BottomSheet visible={sheetCurrent === 'income'} onClose={closeCurrentSheet} title={editingData ? 'Editar ingreso' : 'Nuevo ingreso'}>
        <View style={{ gap: 16 }}>
          <TextField label="Monto *" placeholder="Ej. 550.00" keyboardType="decimal-pad" value={formAmount} onChangeText={setFormAmount} />
          <TextField label="Fuente" placeholder="Ej. Venta garaje" value={incomeSource} onChangeText={setIncomeSource} />
          <TextField label="Detalle *" placeholder="Ej. Venta bicicleta" value={formDetail} onChangeText={setFormDetail} />
          
          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Cuenta destino *</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {accounts.map(a => (
                <Chip key={a.name} label={`${a.name} · ${a.bankName}`} active={incomeAccount === `${a.name} - ${a.bankName}`} onPress={() => setIncomeAccount(`${a.name} - ${a.bankName}`)} />
              ))}
            </ScrollView>
          </View>

          <TouchableOpacity onPress={() => setDatePickerOpen(true)}>
            <View pointerEvents="none">
              <TextField label="Fecha *" placeholder="YYYY-MM-DD" value={incomeDate} onChangeText={() => {}} />
            </View>
          </TouchableOpacity>

          <View style={{ flexDirection: 'row', gap: 12 }}>
             <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={closeCurrentSheet} />
             <Button style={{ flex: 1 }} title="Guardar ingreso" onPress={handleSaveIncome} icon="save" />
          </View>
        </View>
      </BottomSheet>

      <DatePickerModal 
        visible={isDatePickerOpen} 
        onClose={() => setDatePickerOpen(false)} 
        value={incomeDate} 
        onSelect={setIncomeDate} 
      />

      {/* ================= MODALS: DETAILS ================= */}

      <BottomSheet visible={isDetailOpen} onClose={closeCurrentSheet} title={detailType === 'account' ? 'Cuenta' : detailType === 'creditCard' ? 'Tarjeta' : detailType === 'investment' ? 'Inversión' : 'Ingreso'}>
        {detailData && (
          <View>
            <View style={[styles.detailGrid, { borderTopColor: colors.outlineVariant, borderTopWidth: 1, paddingTop: 16 }]}>
              {detailType === 'account' && (
                <>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Banco</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.bankName}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Tipo</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.accountType}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Referencia</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.referenceNumber || '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Balance Calculado</Text><Text style={{ fontSize: 16, color: colors.secondary, fontWeight: 'bold' }}>{formatCurrency(accountBalances[detailData.id] || 0)}</Text></View>
                </>
              )}
              {detailType === 'creditCard' && (
                <>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Banco</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.bankName}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Límite</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatCurrency(detailData.creditLimit)}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Día corte</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.cutOffDay || '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Día pago</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.paymentDueDay || '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cupo disponible</Text><Text style={{ fontSize: 16, color: colors.secondary, fontWeight: 'bold' }}>{detailData.creditLimit > 0 ? formatCurrency(Math.max(0, detailData.creditLimit - (Number(detailData.currentBalance) || 0))) : '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Usado</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatCurrency(detailData.currentBalance || 0)}</Text></View>
                </>
              )}
              {detailType === 'investment' && (
                <>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Plazo total</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.totalMonths ? formatMonths(detailData.totalMonths) : '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Meses restantes</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatMonths(detailData.remainingMonths || 0)}</Text></View>
                  {detailData.totalMonths > 0 && (
                    <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Meses depositados</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatMonths(Math.max(0, detailData.totalMonths - (detailData.remainingMonths || 0)))}</Text></View>
                  )}
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Depósito mensual</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatCurrency(detailData.monthlyDeposit)}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total depositado actualmente</Text><Text style={{ fontSize: 16, color: colors.secondary }}>{formatCurrency(detailData.totalDeposited)}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Detalle</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.detail || '-'}</Text></View>
                </>
              )}
              {detailType === 'income' && (
                <>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Monto</Text><Text style={{ fontSize: 16, color: colors.secondary }}>+{formatCurrency(detailData.amount)}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Fuente</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.source || '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cuenta</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.bankAccount}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Fecha</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatDate(detailData.date)}</Text></View>
                  <View style={{ width: '100%' }}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Detalle</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.detail}</Text></View>
                </>
              )}
            </View>

            {detailType === 'investment' && (detailData as Investment).remainingMonths > 0 && accounts.length > 0 && (
              <Card style={{ marginTop: 24, borderWidth: 1, borderColor: colors.primary, padding: 12 }}>
                <Text style={{ fontSize: 14, fontWeight: 'bold', marginBottom: 16, color: colors.primary }}>Registrar depósito mensual</Text>
                
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Cuenta origen</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, marginBottom: 16 }}>
                  {accounts.map(a => (
                    <Chip key={a.name} label={`${a.name} · ${a.bankName}`} active={depositSourceAccount === a.name} onPress={() => setDepositSourceAccount(a.name)} />
                  ))}
                </ScrollView>

                {(() => {
                  const amountParsed = (detailData as Investment).monthlyDeposit;
                  let taxComision = 0;
                  let taxIva = 0;
                  let taxIsd = 0;
                  let appliesTaxes = false;

                  if (taxesConfig?.enabled && taxesConfig.applyTo.includes('Inversiones')) {
                    appliesTaxes = true;
                    taxComision = (amountParsed * taxesConfig.comisionRate) / 100;
                    taxIva = (taxComision * taxesConfig.ivaRate) / 100;
                    taxIsd = (amountParsed * taxesConfig.isdRate) / 100;
                  }
                  const totalTax = taxComision + taxIva + taxIsd;
                  const totalCharge = amountParsed + totalTax;

                  if (appliesTaxes && totalTax > 0) {
                    return (
                      <View style={{ backgroundColor: colors.surfaceContainerHighest, padding: 12, borderRadius: 12, marginBottom: 16 }}>
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
                          <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.onSurface }}>Total retenido y debitado</Text>
                          <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.primary }}>{formatCurrency(totalCharge)}</Text>
                        </View>
                      </View>
                    );
                  }
                  return null;
                })()}

                <Button 
                  title={`Depositar ${formatCurrency(detailData.monthlyDeposit)}`} 
                  disabled={!depositSourceAccount} 
                  onPress={handleInvestmentDeposit} 
                />
              </Card>
            )}
            
            <View style={{ flexDirection: 'row', gap: 12, marginTop: 32 }}>
              <Button style={{ flex: 1 }} variant="outlined" title="Editar" icon="pencil" onPress={() => { setDetailOpen(false); if (detailType) openForm(detailType, detailData); }} />
              <Button style={{ flex: 1 }} variant="danger" title="Eliminar" icon="trash" onPress={() => handleDelete(
                detailType === 'account' ? AccountRepo : detailType === 'creditCard' ? CreditCardRepo : detailType === 'investment' ? InvestmentRepo : IncomeRepo,
                detailData.id,
                detailData.name || detailData.entity || detailData.source || 'Ingreso'
              )} />
            </View>
          </View>
        )}
      </BottomSheet>

    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingTop: 16, paddingBottom: 8,
  },
  title: { fontSize: 24, fontWeight: 'bold' },
  scrollContent: { padding: 16, paddingBottom: 100, gap: 8 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 16, marginBottom: 8 },
  sectionTitle: { fontSize: 14, fontWeight: '600', textTransform: 'uppercase' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  
  cardLabel: { color: 'rgba(255,255,255,0.8)', fontSize: 14 },
  cardValue: { color: '#fff', fontSize: 32, fontWeight: 'bold', marginVertical: 8 },
  cardSubtext: { color: 'rgba(255,255,255,0.9)', fontSize: 12 },
  emptyCardContent: { alignItems: 'center', justifyContent: 'center', paddingVertical: 16 },
  
  smallCard: { padding: 12, marginBottom: 8, borderRadius: 12 },
  cardTitle: { fontSize: 16, fontWeight: '600' },
  cardSubtitle: { fontSize: 12, marginTop: 2 },
  emptyText: { textAlign: 'center', fontSize: 14, paddingVertical: 16, opacity: 0.7 },

  emptyState: { alignItems: 'center', paddingVertical: 40 },
  emptyTitle: { fontSize: 16, fontWeight: 'bold', marginTop: 12 },

  listItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1 },
  listIcon: { width: 40, height: 40, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  listContent: { flex: 1, marginRight: 8 },
  listTitle: { fontSize: 16, fontWeight: '500' },
  listSubtitle: { fontSize: 12, marginTop: 2 },
  listTrailing: { alignItems: 'flex-end' },
  listAmount: { fontSize: 16, fontWeight: 'bold' },
  listTime: { fontSize: 12, marginTop: 2 },

  fab: {
    position: 'absolute', bottom: 24, right: 24,
    width: 56, height: 56, borderRadius: 28,
    alignItems: 'center', justifyContent: 'center',
    elevation: 4, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4,
  },

  detailGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, rowGap: 24 },
  detailItem: { width: '45%' },
});
