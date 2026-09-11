import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, ActivityIndicator } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { ProgressBar } from '../components/ProgressBar';
import { BottomSheet } from '../components/BottomSheet';
import { Button } from '../components/Button';
import { WelcomeSheet } from '../components/WelcomeSheet';

import { formatCurrency, getMonthName, formatDate, getToday } from '../utils/formatters';
import {
  getTotalBalance,
  getMonthlySummary,
  CategoryRepo,
  ExpenseRepo,
  IncomeRepo,
  LoanRepo,
  SalaryRepo,
  SettingsRepo
} from '../db/storage';
import { Category } from '../types';
import { MainTabNavigationProp } from '../navigation/types';
import { getUpcomingPayments, UpcomingPayment } from '../utils/paymentReminders';
import { syncAfterDataChange } from '../utils/dataSync';

const PAYMENT_ICONS: Record<UpcomingPayment['kind'], keyof typeof Ionicons.glyphMap> = {
  loan: 'business-outline',
  card: 'card-outline',
  recurring: 'repeat-outline',
};

function describeDueDate(daysLeft: number): string {
  if (daysLeft < 0) {
    const days = Math.abs(daysLeft);
    return `Vencido hace ${days} ${days === 1 ? 'día' : 'días'}`;
  }
  if (daysLeft === 0) return 'Vence hoy';
  if (daysLeft === 1) return 'Vence mañana';
  return `En ${daysLeft} días`;
}

export default function DashboardScreen() {
  const { colors } = useTheme();
  const navigation = useNavigation<MainTabNavigationProp>();
  const insets = useSafeAreaInsets();

  const [refreshing, setRefreshing] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [data, setData] = useState({
    appName: 'Mi Dinero',
    balance: { totalIncome: 0, totalExpenses: 0, balance: 0, salaryAmount: 0, extraIncome: 0 },
    monthly: { totalExpenses: 0, totalIncome: 0, salaryAmount: 0, byCategory: {} as Record<string, number> },
    totalLoanDebt: 0,
    monthlyGoal: 0,
    recentTx: [] as any[],
  });
  const [categories, setCategories] = useState<Category[]>([]);
  const [upcoming, setUpcoming] = useState<UpcomingPayment[]>([]);
  const [isPaymentsOpen, setPaymentsOpen] = useState(false);
  const [showWelcome, setShowWelcome] = useState(false);

  const [salaryPrompt, setSalaryPrompt] = useState<{ visible: boolean; amount: number; account: string; configId: string } | null>(null);

  const loadData = async () => {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;

    try {
      const balance = await getTotalBalance();
      const monthly = await getMonthlySummary(year, month);
      const settings = await SettingsRepo.get();
      const activeLoans = await LoanRepo.getActive();

      const totalLoanDebt = activeLoans.reduce(
        (acc, l) => acc + ((l.installments - l.paidInstallments) * l.monthlyQuota),
        0
      );

      const allExpenses = await ExpenseRepo.getAll();
      const allIncomes = await IncomeRepo.getAll();

      const allTx = [
        ...allExpenses.map(e => ({
           ...e,
           txType: 'expense',
           sortKey: `${e.date}T${(e as any).time || '00:00:00'}`
        })),
        ...allIncomes.map(i => ({
           ...i,
           txType: 'income',
           sortKey: `${i.date}T23:59:59` // Prioritize incomes at end of day if no time
        })),
      ].sort((a, b) => b.sortKey.localeCompare(a.sortKey)).slice(0, 8);

      setData({
        appName: settings.appName || 'Mi Dinero',
        balance,
        monthly,
        totalLoanDebt,
        monthlyGoal: settings.monthlyGoal || 0,
        recentTx: allTx,
      });
      setCategories(await CategoryRepo.getAll());
      setUpcoming(await getUpcomingPayments(30));

      // Primer inicio: la hoja de bienvenida (moneda) va antes que cualquier otro aviso.
      const needsWelcome = !settings.onboardingCompleted;
      setShowWelcome(needsWelcome);

      // Salary Check Logic
      const salaries = await SalaryRepo.getAll();
      const salaryConfig = salaries[0] as any;
      if (!needsWelcome && salaryConfig && salaryConfig.amount > 0) {
        let payDay = 31;
        if (salaryConfig.payDateType === 'specific') {
          payDay = parseInt(salaryConfig.payDate) || 31;
        } else if (salaryConfig.payDateType === 'last' || salaryConfig.payDateType === 'lastBusiness') {
          payDay = new Date(year, month, 0).getDate();
        }

        const lastDayOfMonth = new Date(year, month, 0).getDate();
        const actualPayDay = Math.min(payDay, lastDayOfMonth);

        if (now.getDate() >= actualPayDay) {
          const start = `${year}-${String(month).padStart(2, '0')}-01`;
          const end = `${year}-${String(month).padStart(2, '0')}-${String(lastDayOfMonth).padStart(2, '0')}`;
          const currentIncomes = await IncomeRepo.getByDateRange(start, end);

          const alreadyPaid = currentIncomes.some(i => i.isSalary);

          if (!alreadyPaid) {
            const postponeUntil = settings.salaryPromptPostponedUntil || 0;
            if (Date.now() > postponeUntil) {
              setSalaryPrompt({
                 visible: true,
                 amount: salaryConfig.amount,
                 account: salaryConfig.bankAccount || 'Efectivo',
                 configId: salaryConfig.id
              });
            }
          }
        }
      }

    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
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

  const handleAcceptSalary = async () => {
    if (!salaryPrompt) return;
    await IncomeRepo.add({
      amount: salaryPrompt.amount,
      source: 'Sueldo (Automático)',
      detail: 'Acreditación confirmada desde notificador de fecha de cobro',
      bankAccount: salaryPrompt.account,
      bankName: salaryPrompt.account,
      date: getToday(),
      isSalary: true
    });
    setSalaryPrompt(null);
    loadData();
    syncAfterDataChange();
  };

  const handlePostponeSalary = async () => {
    setSalaryPrompt(null);
    await SettingsRepo.save({ salaryPromptPostponedUntil: Date.now() + (24 * 60 * 60 * 1000) });
  };

  const handleWelcomeDone = () => {
    setShowWelcome(false);
    loadData();
    syncAfterDataChange();
  };

  // Calculations
  const now = new Date();
  const monthName = getMonthName(now.getMonth() + 1);
  const year = now.getFullYear();

  const goalBase = data.monthlyGoal > 0 ? data.monthlyGoal : (data.monthly.totalIncome > 0 ? data.monthly.totalIncome : 1);
  const goalPercent = (data.monthly.totalExpenses / goalBase);

  let goalStatus: 'success' | 'warning' | 'danger' = 'success';
  if (data.monthlyGoal > 0) {
    if (goalPercent >= 1) goalStatus = 'danger';
    else if (goalPercent >= 0.9) goalStatus = 'warning';
  } else {
    if (goalPercent >= 0.9) goalStatus = 'danger';
    else if (goalPercent >= 0.6) goalStatus = 'warning';
  }

  // Feedback visual: el ahorro negativo se muestra en rojo.
  const savingsColor = data.balance.balance < 0 ? colors.error : colors.secondary;
  const hasUrgentPayments = upcoming.some(p => p.daysLeft <= 3);

  return (
    <View style={[styles.container, { backgroundColor: colors.background, paddingTop: insets.top }]}>

      {/* Header */}
      <View style={styles.header}>
        <View style={{ flex: 1, marginRight: 12 }}>
          <Text style={[styles.greeting, { color: colors.onSurfaceVariant }]}>Bienvenido de vuelta</Text>
          <Text style={[styles.appName, { color: colors.onSurface }]} numberOfLines={1}>{data.appName}</Text>
        </View>
        <View style={{ flexDirection: 'row', gap: 12 }}>
          <TouchableOpacity
            style={[styles.settingsBtn, { backgroundColor: colors.surfaceContainerHighest }]}
            onPress={() => setPaymentsOpen(true)}
            accessibilityLabel={hasUrgentPayments ? 'Próximos pagos, hay pagos por vencer' : 'Próximos pagos'}
          >
            <Ionicons name="notifications-outline" size={24} color={colors.onSurface} />
            {hasUrgentPayments && (
              <View style={[styles.badgeDot, { backgroundColor: colors.error, borderColor: colors.surfaceContainerHighest }]} />
            )}
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.settingsBtn, { backgroundColor: colors.surfaceContainerHighest }]}
            onPress={() => navigation.navigate('Settings' as any)}
          >
            <Ionicons name="settings-outline" size={24} color={colors.onSurface} />
          </TouchableOpacity>
        </View>
      </View>

      {salaryPrompt && (
        <BottomSheet visible={salaryPrompt.visible} onClose={() => setSalaryPrompt(null)} title="¡Día de Sueldo!">
          <View style={{ paddingVertical: 8 }}>
            <Ionicons name="cash-outline" size={48} color={colors.secondary} style={{ textAlign: 'center', marginBottom: 12 }} />
            <Text style={{ color: colors.onSurface, fontSize: 16, marginBottom: 24, textAlign: 'center', lineHeight: 24 }}>
              Parece que ya llegó tu fecha de cobro. ¿Ya recibiste el abono de <Text style={{fontWeight: 'bold', color: colors.secondary}}>{formatCurrency(salaryPrompt.amount)}</Text> en tu cuenta de <Text style={{fontWeight: 'bold'}}>{salaryPrompt.account}</Text>?
            </Text>

            <Button title="Sí, ya se acreditó" onPress={handleAcceptSalary} />
            <View style={{height: 12}} />
            <Button title="Aún no (Preguntar mañana)" variant="tonal" onPress={handlePostponeSalary} />
            <View style={{height: 12}} />
            <Button title="Modificar tarjeta de sueldo" variant="outlined" onPress={() => { setSalaryPrompt(null); navigation.navigate('Settings' as any); }} />
          </View>
        </BottomSheet>
      )}

      <WelcomeSheet visible={showWelcome && !isLoading} appName={data.appName} onDone={handleWelcomeDone} />

      <BottomSheet visible={isPaymentsOpen} onClose={() => setPaymentsOpen(false)} title="Próximos pagos">
        {upcoming.length === 0 ? (
          <View style={styles.emptyState}>
            <Ionicons name="checkmark-circle-outline" size={48} color={colors.outline} />
            <Text style={[styles.emptyTitle, { color: colors.onSurfaceVariant }]}>No tienes pagos próximos</Text>
            <Text style={[styles.emptySubtitle, { color: colors.onSurfaceVariant, textAlign: 'center' }]}>
              Aquí verás cuotas de préstamos, tarjetas y gastos recurrentes de los próximos 30 días
            </Text>
          </View>
        ) : (
          upcoming.map((payment, i) => {
            const statusColor = payment.daysLeft < 0
              ? colors.error
              : payment.daysLeft <= 3 ? colors.tertiary : colors.onSurfaceVariant;
            return (
              <View
                key={payment.id}
                style={[
                  styles.txItem,
                  { borderBottomColor: colors.outlineVariant },
                  i === upcoming.length - 1 && { borderBottomWidth: 0 }
                ]}
              >
                <View style={[styles.txIconBox, { backgroundColor: colors.primaryContainer }]}>
                  <Ionicons name={PAYMENT_ICONS[payment.kind]} size={20} color={colors.primary} />
                </View>
                <View style={styles.txContent}>
                  <Text style={[styles.txTitle, { color: colors.onSurface }]} numberOfLines={1}>{payment.title}</Text>
                  <Text style={[styles.txSubtitle, { color: statusColor }]}>
                    {describeDueDate(payment.daysLeft)} · {formatDate(payment.dueDate)}
                  </Text>
                </View>
                <Text style={[styles.txAmount, { color: colors.onSurface }]}>{formatCurrency(payment.amount)}</Text>
              </View>
            );
          })
        )}
      </BottomSheet>

      {isLoading ? (
        <View style={styles.loading}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={[styles.loadingText, { color: colors.onSurfaceVariant }]}>Cargando tus finanzas…</Text>
        </View>
      ) : (
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      >
        {/* Balance Card */}
        <Card gradient>
          <Text style={styles.cardLabel}>Balance total</Text>
          <Text style={styles.balanceText} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.5}>
            {formatCurrency(data.balance.balance)}
          </Text>
          <View style={styles.balanceRow}>
            <View style={styles.balanceItem}>
              <Ionicons name="arrow-down-circle" size={16} color="rgba(255,255,255,0.8)" />
              <Text style={styles.balanceSubtext}>Ingresos: {formatCurrency(data.balance.totalIncome)}</Text>
            </View>
            <View style={styles.balanceItem}>
              <Ionicons name="arrow-up-circle" size={16} color="rgba(255,255,255,0.8)" />
              <Text style={styles.balanceSubtext}>Gastos: {formatCurrency(data.balance.totalExpenses)}</Text>
            </View>
          </View>
        </Card>

        {/* Quick Stats */}
        <View style={styles.statsRow}>
          <Card style={styles.statCard}>
            <MaterialCommunityIcons name="piggy-bank" size={28} color={savingsColor} />
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Ahorro</Text>
            <Text style={[styles.statValue, { color: savingsColor }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>
              {formatCurrency(data.balance.balance)}
            </Text>
          </Card>
          <Card style={styles.statCard}>
            <Ionicons name="card" size={28} color={colors.tertiary} />
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Deuda activa</Text>
            <Text style={[styles.statValue, { color: colors.tertiary }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>
              {formatCurrency(data.totalLoanDebt)}
            </Text>
          </Card>
        </View>

        {/* Monthly Summary */}
        <View style={styles.sectionHeader}>
          <Ionicons name="calendar-outline" size={20} color={colors.onSurfaceVariant} />
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>{monthName} {year}</Text>
        </View>

        <Card>
          <View style={styles.monthlyRow}>
            <View style={styles.monthlyCol}>
              <Text style={[styles.monthlyLabel, { color: colors.onSurfaceVariant }]}>Ingresos del mes</Text>
              <Text style={[styles.monthlyValue, { color: colors.secondary }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>
                {formatCurrency(data.monthly.totalIncome)}
              </Text>
            </View>
            <View style={[styles.monthlyCol, { alignItems: 'flex-end' }]}>
              <Text style={[styles.monthlyLabel, { color: colors.onSurfaceVariant }]}>Gastos del mes</Text>
              <Text style={[styles.monthlyValue, { color: colors.error }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>
                {formatCurrency(data.monthly.totalExpenses)}
              </Text>
            </View>
          </View>

          <ProgressBar progress={goalPercent} colorVariant={goalStatus} style={{ marginVertical: 12 }} />

          <Text style={[styles.progressText, { color: colors.onSurfaceVariant }]}>
            {data.monthlyGoal > 0
              ? `${(goalPercent * 100).toFixed(1)}% de meta (${formatCurrency(data.monthlyGoal)} máx.) · Restante: ${formatCurrency(Math.max(0, data.monthlyGoal - data.monthly.totalExpenses))}`
              : data.monthly.totalIncome > 0
                ? `${(goalPercent * 100).toFixed(1)}% del ingreso gastado`
                : 'Sin ingresos registrados'
            }
          </Text>

          {goalStatus === 'danger' && data.monthlyGoal > 0 && (
            <View style={[styles.alertBox, { backgroundColor: colors.errorContainer }]}>
              <Ionicons name="alert-circle" size={20} color={colors.error} />
              <Text style={[styles.alertText, { color: colors.error }]}>¡Has superado tu meta mensual!</Text>
            </View>
          )}

          {goalStatus === 'warning' && data.monthlyGoal > 0 && (
            <View style={[styles.alertBox, { backgroundColor: colors.tertiaryContainer }]}>
              <Ionicons name="warning" size={20} color={colors.tertiary || '#d97706'} />
              <Text style={[styles.alertText, { color: colors.tertiary || '#d97706' }]}>Estás muy cerca de tu meta mensual.</Text>
            </View>
          )}
        </Card>

        {/* Recent Transactions */}
        <View style={styles.sectionHeader}>
          <Ionicons name="time-outline" size={20} color={colors.onSurfaceVariant} />
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>Últimas transacciones</Text>
        </View>

        {data.recentTx.length === 0 ? (
          <View style={styles.emptyState}>
            <Ionicons name="receipt-outline" size={48} color={colors.outline} />
            <Text style={[styles.emptyTitle, { color: colors.onSurfaceVariant }]}>Sin transacciones</Text>
            <Text style={[styles.emptySubtitle, { color: colors.onSurfaceVariant }]}>Agrega tu primer gasto o ingreso</Text>
          </View>
        ) : (
          <View style={{ paddingBottom: 24 }}>
            {data.recentTx.map((tx, i) => {
              const isExpense = tx.txType === 'expense';
              // Los gastos usan el icono y el color (personalizable) de su categoría.
              const cat = isExpense ? categories.find(c => c.name === tx.category) : undefined;
              const iconName = cat?.icon || (isExpense ? 'arrow-up-outline' : 'arrow-down-outline');
              const iconColor = cat?.color || (isExpense ? colors.error : colors.secondary);
              const iconBg = cat?.color ? cat.color + '20' : (isExpense ? colors.errorContainer : colors.secondaryContainer);
              return (
                <View
                  key={i}
                  style={[
                    styles.txItem,
                    { borderBottomColor: colors.outlineVariant },
                    i === data.recentTx.length - 1 && { borderBottomWidth: 0 }
                  ]}
                >
                  <View style={[styles.txIconBox, { backgroundColor: iconBg }]}>
                    <Ionicons name={iconName as any} size={20} color={iconColor} />
                  </View>
                  <View style={styles.txContent}>
                    <Text style={[styles.txTitle, { color: colors.onSurface }]} numberOfLines={1}>
                      {tx.detail || tx.source || tx.category || 'Transacción'}
                    </Text>
                    <Text style={[styles.txSubtitle, { color: colors.onSurfaceVariant }]}>
                      {tx.category || tx.source || ''} · {formatDate(tx.date)}
                    </Text>
                  </View>
                  <Text style={[styles.txAmount, { color: isExpense ? colors.error : colors.secondary }]}>
                    {isExpense ? '-' : '+'}{formatCurrency(tx.amount)}
                  </Text>
                </View>
              );
            })}
          </View>
        )}

      </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 16, paddingTop: 16, paddingBottom: 8,
  },
  greeting: { fontSize: 14 },
  appName: { fontSize: 24, fontWeight: 'bold' },
  settingsBtn: {
    padding: 10, borderRadius: 12, justifyContent: 'center', alignItems: 'center'
  },
  badgeDot: { position: 'absolute', top: 8, right: 8, width: 10, height: 10, borderRadius: 5, borderWidth: 2 },
  loading: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12 },
  loadingText: { fontSize: 14 },
  scrollContent: { padding: 16, paddingBottom: 40 },
  cardLabel: { color: 'rgba(255,255,255,0.8)', fontSize: 14 },
  balanceText: { color: '#fff', fontSize: 36, fontWeight: 'bold', marginVertical: 8 },
  balanceRow: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 4, columnGap: 12, marginTop: 8 },
  balanceItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  balanceSubtext: { color: 'rgba(255,255,255,0.9)', fontSize: 12 },
  statsRow: { flexDirection: 'row', gap: 16, marginBottom: 16 },
  statCard: { flex: 1, alignItems: 'center', paddingVertical: 16, marginBottom: 0 },
  statLabel: { fontSize: 12, marginTop: 8 },
  statValue: { fontSize: 18, fontWeight: 'bold', marginTop: 4 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12, marginTop: 8 },
  sectionTitle: { fontSize: 14, fontWeight: '600', textTransform: 'uppercase' },
  monthlyRow: { flexDirection: 'row', justifyContent: 'space-between', gap: 12 },
  monthlyCol: { flex: 1 },
  monthlyLabel: { fontSize: 12 },
  monthlyValue: { fontSize: 18, fontWeight: 'bold' },
  progressText: { fontSize: 12, textAlign: 'center' },
  alertBox: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 12, borderRadius: 8, marginTop: 12 },
  alertText: { fontSize: 14, fontWeight: '600' },
  emptyState: { alignItems: 'center', paddingVertical: 40 },
  emptyTitle: { fontSize: 18, fontWeight: 'bold', marginTop: 16 },
  emptySubtitle: { fontSize: 14, marginTop: 4 },
  txItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1 },
  txIconBox: { width: 40, height: 40, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  txContent: { flex: 1, marginRight: 8 },
  txTitle: { fontSize: 16, fontWeight: '500' },
  txSubtitle: { fontSize: 12, marginTop: 2 },
  txAmount: { fontSize: 16, fontWeight: 'bold' },
});
