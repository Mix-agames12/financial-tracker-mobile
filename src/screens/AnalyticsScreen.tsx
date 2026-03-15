import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Dimensions, Alert } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { LineChart, BarChart, PieChart } from 'react-native-chart-kit';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { BottomSheet } from '../components/BottomSheet';
import { Chip } from '../components/Chip';

import { formatCurrency, getMonthName, getWeekNumber, getToday } from '../utils/formatters';
import { ExpenseRepo, IncomeRepo, getMonthlySummary } from '../db/storage';

const screenWidth = Dimensions.get('window').width;
const chartWidth = screenWidth - 32;

export default function AnalyticsScreen() {
  const { colors, isDark } = useTheme();

  const [refreshing, setRefreshing] = useState(false);
  const [period, setPeriod] = useState<'daily' | 'weekly' | 'monthly'>('daily');
  
  // Data State
  const [totalSpent, setTotalSpent] = useState(0);
  const [totalIncome, setTotalIncome] = useState(0);
  
  const [lineData, setLineData] = useState<{ labels: string[], datasets: { data: number[] }[] }>({ labels: [], datasets: [{ data: [] }] });
  const [barData, setBarData] = useState<{ labels: string[], datasets: { data: number[] }[] }>({ labels: [], datasets: [{ data: [] }] });
  
  const [pieCategory, setPieCategory] = useState<any[]>([]);
  const [pieMethod, setPieMethod] = useState<any[]>([]);
  
  const [barExpenseAcc, setBarExpenseAcc] = useState<{ labels: string[], datasets: { data: number[] }[] }>({ labels: [], datasets: [{ data: [] }] });
  const [barIncomeAcc, setBarIncomeAcc] = useState<{ labels: string[], datasets: { data: number[] }[] }>({ labels: [], datasets: [{ data: [] }] });

  // Comparison State
  const now = new Date();
  const [compareP1, setCompareP1] = useState(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`);
  const [compareP2, setCompareP2] = useState(`${now.getFullYear()}-${String(Math.max(1, now.getMonth())).padStart(2, '0')}`);
  const [comparisonResult, setComparisonResult] = useState<any>(null);

  // Modals
  const [isInfoOpen, setInfoOpen] = useState(false);
  const [infoText, setInfoText] = useState('');

  const loadData = async () => {
    try {
      const allExpenses = await ExpenseRepo.getAll();
      const allIncomes = await IncomeRepo.getAll();

      const n = new Date();
      const year = n.getFullYear();
      const month = n.getMonth() + 1;

      let labels: string[] = [];
      let expData: number[] = [];
      let incData: number[] = [];
      let tSpent = 0;
      let tIncome = 0;

      if (period === 'daily') {
        for (let i = 29; i >= 0; i--) {
          const d = new Date(); d.setDate(d.getDate() - i);
          const dateStr = d.toISOString().split('T')[0];
          labels.push(`${d.getDate()}/${d.getMonth() + 1}`);
          
          const dayExp = allExpenses.filter(e => e.date === dateStr).reduce((s, e) => s + Number(e.amount), 0);
          const dayInc = allIncomes.filter(inc => inc.date === dateStr).reduce((s, inc) => s + Number(inc.amount), 0);
          
          expData.push(dayExp); incData.push(dayInc);
          tSpent += dayExp; tIncome += dayInc;
        }
      } else if (period === 'weekly') {
        for (let i = 11; i >= 0; i--) {
          const weekStart = new Date(); weekStart.setDate(weekStart.getDate() - (i * 7) - weekStart.getDay() + 1);
          const weekEnd = new Date(weekStart); weekEnd.setDate(weekEnd.getDate() + 6);
          
          const startStr = weekStart.toISOString().split('T')[0];
          const endStr = weekEnd.toISOString().split('T')[0];
          
          labels.push(`S${getWeekNumber(weekStart)}`);
          
          const weekExp = allExpenses.filter(e => e.date >= startStr && e.date <= endStr).reduce((s, e) => s + Number(e.amount), 0);
          const weekInc = allIncomes.filter(inc => inc.date >= startStr && inc.date <= endStr).reduce((s, inc) => s + Number(inc.amount), 0);
          
          expData.push(weekExp); incData.push(weekInc);
          tSpent += weekExp; tIncome += weekInc;
        }
      } else {
        for (let i = 11; i >= 0; i--) {
          const d = new Date(year, month - 1 - i, 1);
          const m = d.getMonth() + 1; const y = d.getFullYear();
          labels.push(getMonthName(m).slice(0, 3));
          
          const start = `${y}-${String(m).padStart(2, '0')}-01`;
          const lastDay = new Date(y, m, 0).getDate();
          const end = `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
          
          const monthExp = allExpenses.filter(e => e.date >= start && e.date <= end).reduce((s, e) => s + Number(e.amount), 0);
          const monthInc = allIncomes.filter(inc => inc.date >= start && inc.date <= end).reduce((s, inc) => s + Number(inc.amount), 0);
          
          expData.push(monthExp); incData.push(monthInc);
          tSpent += monthExp; tIncome += monthInc;
        }
      }

      setTotalSpent(tSpent);
      setTotalIncome(tIncome);

      // We ensure array isn't completely empty and has variation to prevent chart-kit crash
      // We ensure array isn't completely empty and has variation to prevent chart-kit crash
      if (expData.length === 0 || expData.every(v => v === 0)) expData = expData.length > 0 ? [...expData.slice(0, -1), 1] : [1];
      if (incData.length === 0 || incData.every(v => v === 0)) incData = incData.length > 0 ? [...incData.slice(0, -1), 1] : [1];

      setLineData({
        labels: labels.filter((_, i) => i % Math.max(1, Math.floor(labels.length / 6)) === 0), // Thin out labels
        datasets: [{ data: expData }]
      });

      setBarData({
        labels: labels.filter((_, i) => i % Math.max(1, Math.floor(labels.length / 6)) === 0),
        datasets: [{ data: incData }] // We only show one dataset per BarChart in chart-kit easily, let's show income. We will do 2 separate or use Stacked but it's complex
      });

      // Doughnut logic (PieChart in React Native Chart Kit)
      const catColors = ['#f43f5e', '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4', '#ec4899', '#64748b'];
      
      const byCategory: Record<string, number> = {};
      allExpenses.forEach(e => {
        const cat = e.category || 'Sin categoría';
        byCategory[cat] = (byCategory[cat] || 0) + e.amount;
      });
      setPieCategory(Object.entries(byCategory)
        .sort((a,b) => b[1] - a[1]) // Sort descending
        .map(([name, val], i) => ({
          name: name.length > 12 ? name.slice(0, 10) + '..' : name,
          population: val,
          color: catColors[i % catColors.length],
          legendFontColor: colors.onSurfaceVariant,
          legendFontSize: 11
        })));

      const byMethod: Record<string, number> = {};
      allExpenses.forEach(e => {
        const m = e.paymentMethod || 'Otro';
        byMethod[m] = (byMethod[m] || 0) + e.amount;
      });
      setPieMethod(Object.entries(byMethod).map(([name, val], i) => ({
        name,
        population: val,
        color: catColors[(i + 3) % catColors.length],
        legendFontColor: colors.onSurfaceVariant,
        legendFontSize: 11
      })));

      // Account Expense Bar
      const byAccountExp: Record<string, number> = {};
      allExpenses.forEach(e => {
        const acc = e.accountName || e.cardName || 'Efectivo';
        byAccountExp[acc] = (byAccountExp[acc] || 0) + e.amount;
      });
      setBarExpenseAcc({
        labels: Object.keys(byAccountExp).map(l => l.slice(0,6)),
        datasets: [{ data: Object.values(byAccountExp).length ? Object.values(byAccountExp) : [0.01] }]
      });

      // Account Income Bar
      const byAccountInc: Record<string, number> = {};
      allIncomes.forEach(i => {
        const acc = i.bankAccount || 'Efectivo';
        byAccountInc[acc] = (byAccountInc[acc] || 0) + i.amount;
      });
      setBarIncomeAcc({
        labels: Object.keys(byAccountInc).map(l => l.slice(0,6)),
        datasets: [{ data: Object.values(byAccountInc).length ? Object.values(byAccountInc) : [0.01] }]
      });

    } catch (e) {
      console.error(e);
    }
  };

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [period])
  );

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const handleComparison = async () => {
    if (!compareP1 || !compareP2) return;
    try {
      const [y1, m1] = compareP1.split('-').map(Number);
      const [y2, m2] = compareP2.split('-').map(Number);

      if (!y1 || !m1 || !y2 || !m2) {
        Alert.alert('Error', 'Usa el formato YYYY-MM para comparar');
        return;
      }

      const summary1 = await getMonthlySummary(y1, m1);
      const summary2 = await getMonthlySummary(y2, m2);

      const diff = summary1.totalExpenses - summary2.totalExpenses;
      const diffPercent = summary2.totalExpenses > 0 ? ((diff / summary2.totalExpenses) * 100).toFixed(1) : 0;

      setComparisonResult({
        y1, m1, y2, m2, summary1, summary2, diff, diffPercent
      });
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Fallo al calcular comparativa');
    }
  };

  const showInfo = (type: 'spent' | 'saved' | 'avg') => {
    const texts = {
      spent: 'Suma total de todos los gastos registrados en el período seleccionado (diario: últimos 30 días, semanal: últimas 12 semanas, mensual: últimos 12 meses).',
      saved: 'Diferencia entre ingresos totales y gastos totales del período. Ahorro = Ingresos - Gastos.',
      avg: 'Gasto promedio por día. Se calcula dividiendo el total gastado entre la cantidad de días del período.',
    };
    setInfoText(texts[type]);
    setInfoOpen(true);
  };

  const daysInPeriod = period === 'daily' ? 30 : period === 'weekly' ? 84 : 365;
  const avgSpent = totalSpent / Math.max(1, daysInPeriod);

  const chartConfig = {
    backgroundGradientFrom: colors.surface,
    backgroundGradientTo: colors.surface,
    color: (opacity = 1) => colors.primary.replace(')', `, ${opacity})`).replace('rgb', 'rgba'), // Approximation if rgb, works better if hex but chart-kit accepts it
    labelColor: (opacity = 1) => colors.onSurfaceVariant,
    strokeWidth: 2,
    barPercentage: 0.5,
    decimalPlaces: 0,
    propsForDots: { r: "3", strokeWidth: "1", stroke: colors.primary },
    style: { borderRadius: 16 }
  };
  
  // Safe config for chart kit which strictly requires hex or rgba
  const getSafeChartConfig = (baseColor: string) => ({
    backgroundGradientFrom: isDark ? '#1e293b' : '#ffffff',
    backgroundGradientTo: isDark ? '#1e293b' : '#ffffff',
    color: (opacity = 1) => baseColor,
    labelColor: (opacity = 1) => isDark ? '#94a3b8' : '#64748b',
    strokeWidth: 2,
    barPercentage: 0.6,
    decimalPlaces: 0,
    propsForDots: { r: "3", strokeWidth: "1", stroke: baseColor },
  });

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Análisis</Text>
      </View>

      <ScrollView 
        contentContainerStyle={{ padding: 16, paddingBottom: 100, gap: 24 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      >
        
        {/* Toggle Period */}
        <View style={{ flexDirection: 'row', backgroundColor: colors.surfaceContainer, borderRadius: 20, padding: 4 }}>
          {(['daily', 'weekly', 'monthly'] as const).map(p => (
            <TouchableOpacity 
              key={p} 
              style={[styles.toggleBtn, period === p && { backgroundColor: colors.primary }]}
              onPress={() => setPeriod(p)}
            >
              <Text style={{ 
                fontSize: 14, fontWeight: '600', 
                color: period === p ? colors.onPrimary : colors.onSurfaceVariant 
              }}>
                {p === 'daily' ? 'Diario' : p === 'weekly' ? 'Semanal' : 'Mensual'}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Overview Stats */}
        <View style={{ flexDirection: 'row', gap: 12 }}>
          <Card style={[styles.statCard, { flex: 1 }]}>
            <TouchableOpacity style={styles.infoBtn} onPress={() => showInfo('spent')}>
              <Ionicons name="information-circle" size={16} color={colors.onSurfaceVariant} />
            </TouchableOpacity>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Gastado</Text>
            <Text style={[styles.statValue, { color: colors.error }]}>{formatCurrency(totalSpent)}</Text>
          </Card>
          <Card style={[styles.statCard, { flex: 1 }]}>
            <TouchableOpacity style={styles.infoBtn} onPress={() => showInfo('saved')}>
              <Ionicons name="information-circle" size={16} color={colors.onSurfaceVariant} />
            </TouchableOpacity>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Ahorrado</Text>
            <Text style={[styles.statValue, { color: colors.secondary }]}>{formatCurrency(Math.max(0, totalIncome - totalSpent))}</Text>
          </Card>
          <Card style={[styles.statCard, { flex: 1 }]}>
            <TouchableOpacity style={styles.infoBtn} onPress={() => showInfo('avg')}>
              <Ionicons name="information-circle" size={16} color={colors.onSurfaceVariant} />
            </TouchableOpacity>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Promedio/día</Text>
            <Text style={[styles.statValue, { color: colors.tertiary }]}>{formatCurrency(avgSpent)}</Text>
          </Card>
        </View>

        {/* Charts */}
        <View>
          <View style={styles.sectionTitleRow}>
            <Ionicons name="trending-up" size={20} color={colors.onSurface} />
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Gastos en el tiempo</Text>
          </View>
          {lineData.labels.length > 0 && (
            <LineChart
              data={lineData} 
              width={chartWidth} 
              height={220} 
              chartConfig={getSafeChartConfig('#f43f5e')} 
              bezier 
              style={styles.chart} 
            />
          )}
        </View>

        <View>
          <View style={styles.sectionTitleRow}>
            <Ionicons name="pie-chart" size={20} color={colors.onSurface} />
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Por categoría</Text>
          </View>
          {pieCategory.length > 0 ? (
            <PieChart
              data={pieCategory} 
              width={chartWidth} 
              height={200} 
              chartConfig={getSafeChartConfig('#3b82f6')} 
              accessor={"population"} 
              backgroundColor={"transparent"} 
              paddingLeft={"15"} 
              absolute 
              avoidFalseZero
            />
          ) : (
            <Text style={{ textAlign: 'center', color: colors.onSurfaceVariant, padding: 24 }}>Sin datos</Text>
          )}
        </View>

        <View>
          <View style={styles.sectionTitleRow}>
            <Ionicons name="card" size={20} color={colors.onSurface} />
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Por método de pago</Text>
          </View>
          {pieMethod.length > 0 ? (
            <PieChart
              data={pieMethod} 
              width={chartWidth} 
              height={200} 
              chartConfig={getSafeChartConfig('#10b981')} 
              accessor={"population"} 
              backgroundColor={"transparent"} 
              paddingLeft={"15"} 
              absolute 
              avoidFalseZero
            />
          ) : (
            <Text style={{ textAlign: 'center', color: colors.onSurfaceVariant, padding: 24 }}>Sin datos</Text>
          )}
        </View>

        <View>
          <View style={styles.sectionTitleRow}>
            <Ionicons name="business" size={20} color={colors.onSurface} />
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Gastos por cuenta</Text>
          </View>
          {barExpenseAcc.labels.length > 0 && (
            <BarChart
              data={barExpenseAcc} 
              width={chartWidth} 
              height={220} 
              yAxisLabel="$" 
              yAxisSuffix=""
              chartConfig={getSafeChartConfig('#f43f5e')} 
              style={styles.chart}
              showValuesOnTopOfBars 
            />
          )}
        </View>


        {/* Comparison */}
        <View style={{ marginTop: 16 }}>
          <View style={styles.sectionTitleRow}>
            <Ionicons name="swap-horizontal" size={20} color={colors.onSurface} />
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Comparativa mensual</Text>
          </View>
          
          <Card>
            <View style={{ flexDirection: 'row', gap: 12, marginBottom: 16 }}>
              <View style={{ flex: 1 }}>
                <TextField label="Período 1" placeholder="YYYY-MM" value={compareP1} onChangeText={setCompareP1} />
              </View>
              <View style={{ flex: 1 }}>
                <TextField label="Período 2" placeholder="YYYY-MM" value={compareP2} onChangeText={setCompareP2} />
              </View>
            </View>
            <Button title="Comparar" onPress={handleComparison} icon="analytics" />

            {comparisonResult && (
              <View style={{ marginTop: 24, gap: 16, borderTopWidth: 1, borderTopColor: colors.outlineVariant, paddingTop: 16 }}>
                <View style={{ flexDirection: 'row', gap: 12 }}>
                  <View style={{ flex: 1, alignItems: 'center', padding: 12, backgroundColor: colors.surfaceContainer, borderRadius: 12 }}>
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>{getMonthName(comparisonResult.m1)} {comparisonResult.y1}</Text>
                    <Text style={{ fontSize: 18, fontWeight: 'bold', color: colors.error, marginVertical: 4 }}>{formatCurrency(comparisonResult.summary1.totalExpenses)}</Text>
                    <Text style={{ fontSize: 10, color: colors.onSurfaceVariant }}>Ingreso: {formatCurrency(comparisonResult.summary1.totalIncome)}</Text>
                  </View>
                  <View style={{ flex: 1, alignItems: 'center', padding: 12, backgroundColor: colors.surfaceContainer, borderRadius: 12 }}>
                    <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>{getMonthName(comparisonResult.m2)} {comparisonResult.y2}</Text>
                    <Text style={{ fontSize: 18, fontWeight: 'bold', color: colors.error, marginVertical: 4 }}>{formatCurrency(comparisonResult.summary2.totalExpenses)}</Text>
                    <Text style={{ fontSize: 10, color: colors.onSurfaceVariant }}>Ingreso: {formatCurrency(comparisonResult.summary2.totalIncome)}</Text>
                  </View>
                </View>

                <View style={{ alignItems: 'center', padding: 16, backgroundColor: colors.surfaceContainerHighest, borderRadius: 12 }}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Diferencia de gastos</Text>
                  <Text style={{ fontSize: 24, fontWeight: 'bold', color: comparisonResult.diff > 0 ? colors.error : colors.secondary, marginTop: 4 }}>
                    {comparisonResult.diff > 0 ? '+' : ''}{formatCurrency(comparisonResult.diff)}
                  </Text>
                  <Text style={{ fontSize: 14, color: colors.onSurfaceVariant }}>
                    ({comparisonResult.diff > 0 ? '+' : ''}{comparisonResult.diffPercent}%)
                  </Text>
                </View>
              </View>
            )}
          </Card>
        </View>

      </ScrollView>

      <BottomSheet visible={isInfoOpen} onClose={() => setInfoOpen(false)} title="¿Cómo se calcula?">
        <Text style={{ fontSize: 16, lineHeight: 24, color: colors.onSurface, marginBottom: 24 }}>
          {infoText}
        </Text>
        <Button title="Entendido" onPress={() => setInfoOpen(false)} />
      </BottomSheet>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    paddingHorizontal: 16, paddingTop: 16, paddingBottom: 8,
  },
  title: { fontSize: 24, fontWeight: 'bold' },
  toggleBtn: {
    flex: 1, alignItems: 'center', paddingVertical: 8, borderRadius: 16,
  },
  statCard: {
    alignItems: 'center', paddingVertical: 16, paddingHorizontal: 4, position: 'relative'
  },
  infoBtn: {
    position: 'absolute', top: 8, right: 8, padding: 4
  },
  statLabel: { fontSize: 11, marginTop: 8 },
  statValue: { fontSize: 16, fontWeight: 'bold', marginTop: 4 },
  sectionTitleRow: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12,
  },
  sectionTitle: { fontSize: 18, fontWeight: '600' },
  chart: {
    borderRadius: 16, overflow: 'hidden', paddingRight: 16, marginLeft: -16
  }
});
