import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { Chip } from '../components/Chip';
import { BottomSheet } from '../components/BottomSheet';
import { ProgressBar } from '../components/ProgressBar';

import { formatCurrency, formatDate, getToday } from '../utils/formatters';
import { 
  SalaryRepo, IncomeRepo, AccountRepo, CreditCardRepo, InvestmentRepo, ExpenseRepo 
} from '../db/storage';
import { Salary, Income, Account, CreditCard, Investment } from '../types';

export default function IncomeScreen() {
  const { colors } = useTheme();

  const [refreshing, setRefreshing] = useState(false);
  const [salary, setSalary] = useState<Salary | null>(null);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [creditCards, setCreditCards] = useState<CreditCard[]>([]);
  const [investments, setInvestments] = useState<Investment[]>([]);
  const [incomes, setIncomes] = useState<Income[]>([]);

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

  // Account Form States
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
  const [incomeAccount, setIncomeAccount] = useState('');

  // Detailed Modals
  const [isDetailOpen, setDetailOpen] = useState(false);
  const [detailType, setDetailType] = useState<'account' | 'creditCard' | 'investment' | 'income' | null>(null);
  const [detailData, setDetailData] = useState<any>(null);

  const loadData = async () => {
    try {
      const sals = await SalaryRepo.getAll();
      setSalary((sals[0] as any) || null);

      setAccounts(await AccountRepo.getAll());
      setCreditCards(await CreditCardRepo.getAll());
      setInvestments(await InvestmentRepo.getAll());

      const incs = await IncomeRepo.getAll();
      incs.sort((a, b) => (b.date || '').localeCompare(a.date || ''));
      setIncomes(incs);
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
      setFormAmount(data?.amount?.toString() || '');
      setPayDateType(data?.payDateType || 'specific');
      setPayDate(data?.payDate || '');
      setRecurrence(data?.recurrence || 'Mensual');
    } else if (type === 'account') {
      setFormName(data?.name || '');
      setBankName(data?.bankName || '');
      setAccountType(data?.accountType || 'Ahorro');
      setRefNumber(data?.referenceNumber || '');
    } else if (type === 'creditCard') {
      setFormName(data?.name || '');
      setBankName(data?.bankName || '');
      setCutOffDay(data?.cutOffDay?.toString() || '');
      setPaymentDay(data?.paymentDueDay?.toString() || '');
      setCreditLimit(data?.creditLimit?.toString() || '');
      setFormAmount(data?.currentBalance?.toString() || '');
    } else if (type === 'investment') {
      setFormName(data?.entity || '');
      setFormAmount(data?.monthlyDeposit?.toString() || '');
      setRemainingMonths(data?.remainingMonths?.toString() || '');
      setTotalMonths(data?.totalMonths?.toString() || '');
      setFormDetail(data?.detail || '');
      setTotalDeposited(data?.totalDeposited?.toString() || '');
    } else if (type === 'income') {
      setFormAmount(data?.amount?.toString() || '');
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
      recurrence
    });
    closeCurrentSheet();
    loadData();
  };

  const handleSaveAccount = async () => {
    if (!formName.trim() || !bankName.trim()) return Alert.alert('Error', 'Nombre y banco son obligatorios');
    const payload = {
      name: formName.trim(), bankName: bankName.trim(),
      referenceNumber: refNumber.trim(), accountType
    };
    if (editingData) await AccountRepo.update({ ...editingData, ...payload });
    else await AccountRepo.add(payload);
    closeCurrentSheet();
    loadData();
  };

  const handleSaveCreditCard = async () => {
    if (!formName.trim() || !bankName.trim()) return Alert.alert('Error', 'Nombre y banco son obligatorios');
    const payload = {
      name: formName.trim(), bankName: bankName.trim(),
      cutOffDay: parseInt(cutOffDay) || 0,
      paymentDueDay: parseInt(paymentDay) || 0,
      creditLimit: parseFloat(creditLimit.replace(',', '.')) || 0,
      currentBalance: parseFloat(formAmount.replace(',', '.')) || 0,
    };
    if (editingData) await CreditCardRepo.update({ ...editingData, ...payload });
    else await CreditCardRepo.add(payload);
    closeCurrentSheet();
    loadData();
  };

  const handleSaveInvestment = async () => {
    const amt = parseFloat(formAmount.replace(',', '.'));
    if (!formName.trim() || isNaN(amt) || amt <= 0) return Alert.alert('Error', 'Entidad y depósito válido son obligatorios');
    
    const payload = {
      entity: formName.trim(),
      monthlyDeposit: amt,
      remainingMonths: parseInt(remainingMonths) || 0,
      totalMonths: parseInt(totalMonths) || 0,
      detail: formDetail.trim(),
      totalDeposited: parseFloat(totalDeposited.replace(',', '.')) || 0,
    };
    if (editingData) await InvestmentRepo.update({ ...editingData, ...payload });
    else await InvestmentRepo.add(payload);
    closeCurrentSheet();
    loadData();
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
    loadData();
  };

  // ----- DELETE HANDLERS -----

  const handleDelete = (repo: any, id: string, name: string) => {
    Alert.alert('Eliminar', `¿Estás seguro de eliminar ${name}?`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Eliminar', style: 'destructive', onPress: async () => {
          await repo.delete(id);
          closeCurrentSheet();
          loadData();
        } 
      }
    ]);
  };

  const handleInvestmentDeposit = async () => {
    const inv = detailData as Investment;
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

    await InvestmentRepo.update({
      ...inv,
      totalDeposited: (inv.totalDeposited || 0) + inv.monthlyDeposit,
      remainingMonths: Math.max(0, (inv.remainingMonths || 0) - 1)
    });

    closeCurrentSheet();
    Alert.alert('Éxito', `Depósito de ${formatCurrency(inv.monthlyDeposit)} registrado como gasto`);
    loadData();
  };


  const totalExtraIncome = useMemo(() => incomes.reduce((s, i) => s + i.amount, 0), [incomes]);

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
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
                <Text style={styles.cardValue}>{formatCurrency(salary.amount)}</Text>
                <Text style={styles.cardSubtext}>
                  Día de pago: {salary.payDateType === 'last' ? 'Último día del mes' : salary.payDateType === 'lastBusiness' ? 'Último hábil' : 'Día ' + salary.payDate} · {salary.recurrence || 'Mensual'}
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
                  <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>{acc.referenceNumber}</Text>
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
          creditCards.map(cc => (
            <TouchableOpacity key={cc.id} onPress={() => openDetail('creditCard', cc)}>
              <Card style={styles.smallCard}>
                <View style={styles.rowBetween}>
                  <View>
                    <Text style={[styles.cardTitle, { color: colors.onSurface }]}>{cc.name}</Text>
                    <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>{cc.bankName}</Text>
                  </View>
                  {cc.currentBalance > 0 && <Text style={[styles.cardTitle, { color: colors.error }]}>{formatCurrency(cc.currentBalance)}</Text>}
                </View>
              </Card>
            </TouchableOpacity>
          ))
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
                    <Text style={[styles.cardSubtitle, { color: colors.onSurfaceVariant }]}>{inv.remainingMonths} meses est.</Text>
                  </View>
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
            <TextField label="Día de pago (1-28)" keyboardType="number-pad" value={payDate} onChangeText={setPayDate} />
          )}

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Recurrencia</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {['Mensual', 'Quincenal', 'Semanal'].map(r => (
                <Chip key={r} label={r} active={recurrence === r} onPress={() => setRecurrence(r)} />
              ))}
            </View>
          </View>

          <Button title="Guardar sueldo" onPress={handleSaveSalary} icon="save" />
        </View>
      </BottomSheet>


      <BottomSheet visible={sheetCurrent === 'account'} onClose={closeCurrentSheet} title={editingData ? 'Editar cuenta' : 'Nueva cuenta'}>
        <View style={{ gap: 16 }}>
          <TextField label="Nombre de cuenta *" value={formName} onChangeText={setFormName} />
          <TextField label="Número de referencia" value={refNumber} onChangeText={setRefNumber} />
          
          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Tipo</Text>
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {['Ahorro', 'Corriente', 'Nómina'].map(t => (
                <Chip key={t} label={t} active={accountType === t} onPress={() => setAccountType(t)} />
              ))}
            </View>
          </View>

          <TextField label="Banco *" value={bankName} onChangeText={setBankName} />
          <Button title="Guardar cuenta" onPress={handleSaveAccount} icon="save" />
        </View>
      </BottomSheet>

      <BottomSheet visible={sheetCurrent === 'creditCard'} onClose={closeCurrentSheet} title={editingData ? 'Editar tarjeta' : 'Nueva tarjeta'}>
        <View style={{ gap: 16 }}>
          <TextField label="Nombre o Alias *" value={formName} onChangeText={setFormName} />
          <TextField label="Banco *" value={bankName} onChangeText={setBankName} />
          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}><TextField label="Día corte" keyboardType="numeric" value={cutOffDay} onChangeText={setCutOffDay} /></View>
            <View style={{ flex: 1 }}><TextField label="Día pago" keyboardType="numeric" value={paymentDay} onChangeText={setPaymentDay} /></View>
          </View>
          <TextField label="Límite crédito" keyboardType="decimal-pad" value={creditLimit} onChangeText={setCreditLimit} />
          <TextField label="Saldo actual" keyboardType="decimal-pad" value={formAmount} onChangeText={setFormAmount} />
          <Button title="Guardar tarjeta" onPress={handleSaveCreditCard} icon="save" />
        </View>
      </BottomSheet>

      <BottomSheet visible={sheetCurrent === 'investment'} onClose={closeCurrentSheet} title={editingData ? 'Editar fondo' : 'Nuevo fondo'}>
        <View style={{ gap: 16 }}>
          <TextField label="Entidad *" value={formName} onChangeText={setFormName} />
          <TextField label="Depósito mensual *" keyboardType="decimal-pad" value={formAmount} onChangeText={setFormAmount} />
          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}><TextField label="Meses est" keyboardType="numeric" value={remainingMonths} onChangeText={setRemainingMonths} /></View>
            <View style={{ flex: 1 }}><TextField label="Meses tot" keyboardType="numeric" value={totalMonths} onChangeText={setTotalMonths} /></View>
          </View>
          <TextField label="Detalle" value={formDetail} onChangeText={setFormDetail} />
          <TextField label="Depositado hasta ahora" keyboardType="decimal-pad" value={totalDeposited} onChangeText={setTotalDeposited} />
          <Button title="Guardar fondo" onPress={handleSaveInvestment} icon="save" />
        </View>
      </BottomSheet>

      <BottomSheet visible={sheetCurrent === 'income'} onClose={closeCurrentSheet} title={editingData ? 'Editar ingreso' : 'Nuevo ingreso'}>
        <View style={{ gap: 16 }}>
          <TextField label="Monto *" keyboardType="decimal-pad" value={formAmount} onChangeText={setFormAmount} />
          <TextField label="Fuente" value={incomeSource} onChangeText={setIncomeSource} />
          <TextField label="Detalle *" value={formDetail} onChangeText={setFormDetail} />
          
          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Cuenta destino *</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
              {accounts.map(a => (
                <Chip key={a.name} label={`${a.name} · ${a.bankName}`} active={incomeAccount === `${a.name} - ${a.bankName}`} onPress={() => setIncomeAccount(`${a.name} - ${a.bankName}`)} />
              ))}
            </ScrollView>
          </View>

          <TextField label="Fecha" placeholder="YYYY-MM-DD" value={incomeDate} onChangeText={setIncomeDate} />
          <Button title="Guardar ingreso" onPress={handleSaveIncome} icon="save" />
        </View>
      </BottomSheet>

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
                </>
              )}
              {detailType === 'creditCard' && (
                <>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Banco</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.bankName}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Límite</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatCurrency(detailData.creditLimit)}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Día corte</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.cutOffDay || '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Día pago</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.paymentDueDay || '-'}</Text></View>
                </>
              )}
              {detailType === 'investment' && (
                <>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Meses restantes</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.remainingMonths || 0}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Meses totales</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{detailData.totalMonths || '-'}</Text></View>
                  <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total depositado</Text><Text style={{ fontSize: 16, color: colors.secondary }}>{formatCurrency(detailData.totalDeposited)}</Text></View>
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
