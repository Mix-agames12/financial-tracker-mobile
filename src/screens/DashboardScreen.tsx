import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { ProgressBar } from '../components/ProgressBar';

import { formatCurrency, getMonthName, formatDate } from '../utils/formatters';
import { 
  getTotalBalance, 
  getMonthlySummary, 
  ExpenseRepo, 
  IncomeRepo, 
  LoanRepo, 
  SettingsRepo 
} from '../db/storage';
import { MainTabNavigationProp } from '../navigation/types';

export default function DashboardScreen() {
  const { colors } = useTheme();
  const navigation = useNavigation<MainTabNavigationProp>();
  
  const [refreshing, setRefreshing] = useState(false);
  const [data, setData] = useState({
    appName: 'Mi Dinero',
    balance: { totalIncome: 0, totalExpenses: 0, balance: 0, salaryAmount: 0, extraIncome: 0 },
    monthly: { totalExpenses: 0, totalIncome: 0, salaryAmount: 0, byCategory: {} as Record<string, number> },
    totalLoanDebt: 0,
    monthlyGoal: 0,
    recentTx: [] as any[],
  });

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
        ...allExpenses.map(e => ({ ...e, txType: 'expense' })),
        ...allIncomes.map(i => ({ ...i, txType: 'income' })),
      ].sort((a, b) => (b.date || '').localeCompare(a.date || '')).slice(0, 8);

      setData({
        appName: settings.appName || 'Mi Dinero',
        balance,
        monthly,
        totalLoanDebt,
        monthlyGoal: settings.monthlyGoal || 0,
        recentTx: allTx,
      });
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

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={[styles.greeting, { color: colors.onSurfaceVariant }]}>Bienvenido de vuelta</Text>
          <Text style={[styles.appName, { color: colors.onSurface }]}>{data.appName}</Text>
        </View>
        <TouchableOpacity 
          style={[styles.settingsBtn, { backgroundColor: colors.surfaceContainerHighest }]}
          onPress={() => navigation.navigate('Settings' as any)}
        >
          <Ionicons name="settings-outline" size={24} color={colors.onSurface} />
        </TouchableOpacity>
      </View>

      <ScrollView 
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      >
        {/* Balance Card */}
        <Card gradient>
          <Text style={styles.cardLabel}>Balance total</Text>
          <Text style={styles.balanceText}>{formatCurrency(data.balance.balance)}</Text>
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
            <Ionicons name="wallet" size={28} color={colors.secondary} />
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Ahorro</Text>
            <Text style={[styles.statValue, { color: colors.secondary }]}>
              {formatCurrency(data.balance.balance > 0 ? data.balance.balance : 0)}
            </Text>
          </Card>
          <Card style={styles.statCard}>
            <Ionicons name="card" size={28} color={colors.tertiary} />
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Deuda activa</Text>
            <Text style={[styles.statValue, { color: colors.tertiary }]}>
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
            <View>
              <Text style={[styles.monthlyLabel, { color: colors.onSurfaceVariant }]}>Ingresos del mes</Text>
              <Text style={[styles.monthlyValue, { color: colors.secondary }]}>
                {formatCurrency(data.monthly.totalIncome)}
              </Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={[styles.monthlyLabel, { color: colors.onSurfaceVariant }]}>Gastos del mes</Text>
              <Text style={[styles.monthlyValue, { color: colors.error }]}>
                {formatCurrency(data.monthly.totalExpenses)}
              </Text>
            </View>
          </View>

          <ProgressBar progress={goalPercent} colorVariant={goalStatus} style={{ marginVertical: 12 }} />

          <Text style={[styles.progressText, { color: colors.onSurfaceVariant }]}>
            {data.monthlyGoal > 0
              ? `${(goalPercent * 100).toFixed(1)}% de meta ($${data.monthlyGoal.toLocaleString('es')} máx.) · Restante: ${formatCurrency(Math.max(0, data.monthlyGoal - data.monthly.totalExpenses))}`
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
              return (
                <View 
                  key={i} 
                  style={[
                    styles.txItem, 
                    { borderBottomColor: colors.outlineVariant },
                    i === data.recentTx.length - 1 && { borderBottomWidth: 0 }
                  ]}
                >
                  <View style={[styles.txIconBox, { backgroundColor: isExpense ? colors.errorContainer : colors.secondaryContainer }]}>
                    <Ionicons 
                      name={isExpense ? 'arrow-up-outline' : 'arrow-down-outline'} 
                      size={20} 
                      color={isExpense ? colors.error : colors.secondary} 
                    />
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
  greeting: { fontSize: 12, fontFamily: 'sans-serif' },
  appName: { fontSize: 24, fontWeight: 'bold' },
  settingsBtn: {
    width: 40, height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scrollContent: { padding: 16, paddingBottom: 40 },
  cardLabel: { color: 'rgba(255,255,255,0.8)', fontSize: 14 },
  balanceText: { color: '#fff', fontSize: 36, fontWeight: 'bold', marginVertical: 8 },
  balanceRow: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 },
  balanceItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  balanceSubtext: { color: 'rgba(255,255,255,0.9)', fontSize: 12 },
  statsRow: { flexDirection: 'row', gap: 16, marginBottom: 16 },
  statCard: { flex: 1, alignItems: 'center', paddingVertical: 16, marginBottom: 0 },
  statLabel: { fontSize: 12, marginTop: 8 },
  statValue: { fontSize: 18, fontWeight: 'bold', marginTop: 4 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12, marginTop: 8 },
  sectionTitle: { fontSize: 14, fontWeight: '600', textTransform: 'uppercase' },
  monthlyRow: { flexDirection: 'row', justifyContent: 'space-between' },
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
