import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Alert, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { LineChart, BarChart, PieChart } from 'react-native-chart-kit';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { BottomSheet } from '../components/BottomSheet';
import { Chip } from '../components/Chip';

import { formatCurrency, getMonthName, getWeekNumber, getCurrencySymbol, toLocalDateStr } from '../utils/formatters';
import { ExpenseRepo, IncomeRepo, AccountRepo, CategoryRepo, getMonthlySummary } from '../db/storage';

export default function AnalyticsScreen() {
  const { colors, isDark } = useTheme();
  const insets = useSafeAreaInsets();
  // Se recalcula con rotaciones, pantallas plegables o multiventana.
  const { width: screenWidth } = useWindowDimensions();
  const chartWidth = screenWidth - 32;

  const [refreshing, setRefreshing] = useState(false);
  const [period, setPeriod] = useState<'daily' | 'weekly' | 'monthly'>('daily');
  
  // Data State
  const [totalSpent, setTotalSpent] = useState(0);
  const [totalIncome, setTotalIncome] = useState(0);
  
  const [lineData, setLineData] = useState<{ labels: string[], datasets: { data: number[] }[] }>({ labels: [], datasets: [{ data: [] }] });
  const [barData, setBarData] = useState<{ labels: string[], datasets: { data: number[] }[] }>({ labels: [], datasets: [{ data: [] }] });
  
  const [pieCategory, setPieCategory] = useState<any[]>([]);
  const [pieMethod, setPieMethod] = useState<any[]>([]);
  
  const [barExpenseAcc, setBarExpenseAcc] = useState<{ labels: string[], datasets: { data: number[], colors?: any[] }[] }>({ labels: [], datasets: [{ data: [] }] });
  const [barIncomeAcc, setBarIncomeAcc] = useState<{ labels: string[], datasets: { data: number[], colors?: any[] }[] }>({ labels: [], datasets: [{ data: [] }] });

  // Comparison State
  const now = new Date();
  const [compareP1, setCompareP1] = useState(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`);
  const [compareP2, setCompareP2] = useState(`${now.getFullYear()}-${String(Math.max(1, now.getMonth())).padStart(2, '0')}`);
  const [compCategory, setCompCategory] = useState('');
  const [compMethod, setCompMethod] = useState('');
  const [compIncomeSource, setCompIncomeSource] = useState('');
  const [comparisonResult, setComparisonResult] = useState<any>(null);

  const recentMonthsList = useMemo(() => {
    const list = [];
    const d = new Date();
    for (let i = 0; i < 24; i++) {
      const m = d.getMonth() + 1;
      const y = d.getFullYear();
      list.push(`${y}-${String(m).padStart(2, '0')}`);
      d.setMonth(d.getMonth() - 1);
    }
    return list;
  }, []);

  const [optCategories, setOptCategories] = useState<string[]>([]);
  const [optMethods, setOptMethods] = useState<string[]>([]);
  const [optIncomeSources, setOptIncomeSources] = useState<string[]>([]);
  const [pickerConfig, setPickerConfig] = useState<{
    visible: boolean;
    title: string;
    options: string[];
    value: string;
    onSelect: (v: string) => void;
  }>({ visible: false, title: '', options: [], value: '', onSelect: () => {} });

  // Modals
  const [isInfoOpen, setInfoOpen] = useState(false);
  const [infoText, setInfoText] = useState('');

  const loadData = async () => {
    try {
      const allExpenses = await ExpenseRepo.getAll();
      const allIncomes = await IncomeRepo.getAll();
      const allAccounts = await AccountRepo.getAll();
      const allCategories = await CategoryRepo.getAll();
      const accNames = new Set(allAccounts.map(a => a.name));

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
          const dateStr = toLocalDateStr(d);
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
          
          const startStr = toLocalDateStr(weekStart);
          const endStr = toLocalDateStr(weekEnd);
          
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

      // Prevent chart-kit zero-variation division-by-zero crash
      const forceVariation = (arr: number[]) => {
        if (arr.length === 0) return [0, 1];
        const max = Math.max(...arr);
        const min = Math.min(...arr);
        if (max === min) return [...arr.slice(0, -1), max + 1];
        return arr;
      };

      expData = forceVariation(expData);
      incData = forceVariation(incData);

      setLineData({
        labels: labels, 
        datasets: [{ data: expData }]
      });

      setBarData({
        labels: labels,
        datasets: [{ data: incData }]
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
          name: name.length > 18 ? name.slice(0, 16) + '..' : name,
          population: Math.round(val * 100) / 100,
          // Color personalizado de la categoría; la paleta sólo cubre categorías sin color propio.
          color: allCategories.find(c => c.name === name)?.color || catColors[i % catColors.length],
          legendFontColor: colors.onSurfaceVariant,
          legendFontSize: 11
        })));
      setOptCategories(Object.keys(byCategory));

      const byMethod: Record<string, number> = {};
      allExpenses.forEach(e => {
        const m = e.paymentMethod || 'Otro';
        byMethod[m] = (byMethod[m] || 0) + e.amount;
      });
      setPieMethod(Object.entries(byMethod).map(([name, val], i) => ({
        name: name.length > 18 ? name.slice(0, 16) + '..' : name,
        population: Math.round(val * 100) / 100,
        color: catColors[(i + 3) % catColors.length],
        legendFontColor: colors.onSurfaceVariant,
        legendFontSize: 11
      })));
      setOptMethods(Object.keys(byMethod));

      // Account Expense Bar
      const byAccountExp: Record<string, number> = {};
      allExpenses.forEach(e => {
        if (e.paymentMethod === 'Débito' && e.accountName && accNames.has(e.accountName)) {
           byAccountExp[e.accountName] = (byAccountExp[e.accountName] || 0) + e.amount;
        }
      });
      const expAccVals = Object.values(byAccountExp);
      setBarExpenseAcc({
        labels: Object.keys(byAccountExp),
        datasets: [{ 
          data: expAccVals.length ? expAccVals : [0.01], 
          colors: expAccVals.length ? expAccVals.map((_, i) => () => catColors[i % catColors.length]) : [() => '#f43f5e'] 
        }]
      });

      // Account Income Bar
      const byAccountInc: Record<string, number> = {};
      allIncomes.forEach(i => {
        const acc = i.bankAccount || 'Efectivo';
        byAccountInc[acc] = (byAccountInc[acc] || 0) + i.amount;
      });
      const incAccVals = Object.values(byAccountInc);
      setBarIncomeAcc({
        labels: Object.keys(byAccountInc).map(l => l.slice(0,6)),
        datasets: [{ 
          data: incAccVals.length ? incAccVals : [0.01],
          colors: incAccVals.length ? incAccVals.map((_, i) => () => catColors[(i + 2) % catColors.length]) : [() => '#10b981']
        }]
      });

      const byIncomeSrcObj: Record<string, number> = {};
      allIncomes.forEach(i => {
        const src = i.source || 'Otro';
        byIncomeSrcObj[src] = (byIncomeSrcObj[src] || 0) + i.amount;
      });
      setOptIncomeSources(Object.keys(byIncomeSrcObj));

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

      const s1 = await getMonthlySummary(y1, m1);
      const s2 = await getMonthlySummary(y2, m2);

      const fE1 = s1.expenses.filter(e => (!compCategory || (e.category || '').toLowerCase().includes(compCategory.toLowerCase())) && (!compMethod || (e.paymentMethod || '').toLowerCase().includes(compMethod.toLowerCase())));
      const fE2 = s2.expenses.filter(e => (!compCategory || (e.category || '').toLowerCase().includes(compCategory.toLowerCase())) && (!compMethod || (e.paymentMethod || '').toLowerCase().includes(compMethod.toLowerCase())));
      
      const fI1 = s1.incomes.filter(i => (!compIncomeSource || (i.source || '').toLowerCase().includes(compIncomeSource.toLowerCase())));
      const fI2 = s2.incomes.filter(i => (!compIncomeSource || (i.source || '').toLowerCase().includes(compIncomeSource.toLowerCase())));

      const s1Exp = fE1.reduce((s, e) => s + e.amount, 0);
      const s2Exp = fE2.reduce((s, e) => s + e.amount, 0);
      const s1Inc = fI1.reduce((s, i) => s + i.amount, 0);
      const s2Inc = fI2.reduce((s, i) => s + i.amount, 0);

      const diff = s1Exp - s2Exp;
      const diffPercent = s2Exp > 0 ? ((diff / s2Exp) * 100).toFixed(1) : 0;

      setComparisonResult({
        y1, m1, y2, m2, 
        summary1: { totalExpenses: s1Exp, totalIncome: s1Inc }, 
        summary2: { totalExpenses: s2Exp, totalIncome: s2Inc }, 
        diff, diffPercent
      });
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'Fallo al calcular comparativa');
    }
  };

  const showInfo = (type: 'spent' | 'saved' | 'avg') => {
    const texts = {
      spent: 'Suma total de todos los gastos registrados en el período seleccionado (diario: últimos 30 días, semanal: últimas 12 semanas, mensual: últimos 12 meses).',
      saved: 'Diferencia entre ingresos totales y gastos totales del período. Ahorro = Ingresos - Gastos. Si es negativo se muestra en rojo.',
      avg: 'Gasto promedio por día. Se calcula dividiendo el total gastado entre la cantidad de días del período.',
    };
    setInfoText(texts[type]);
    setInfoOpen(true);
  };

  const daysInPeriod = period === 'daily' ? 30 : period === 'weekly' ? 84 : 365;
  const avgSpent = totalSpent / Math.max(1, daysInPeriod);
  const saved = totalIncome - totalSpent;

  const chartConfig = {
    backgroundGradientFrom: colors.surface,
    backgroundGradientTo: colors.surface,
    color: (opacity = 1) => colors.primary.replace(')', `, ${opacity})`).replace('rgb', 'rgba'), // Approximation if rgb, works better if hex but chart-kit accepts it
    labelColor: (opacity = 1) => colors.onSurfaceVariant,
    strokeWidth: 2,
    barPercentage: 0.5,
    decimalPlaces: 2,
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
    decimalPlaces: 2,
    propsForDots: { r: "3", strokeWidth: "1", stroke: baseColor },
  });

  return (
    <View style={[styles.container, { backgroundColor: colors.background, paddingTop: insets.top }]}>
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
            <Text style={[styles.statValue, { color: colors.error }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>{formatCurrency(totalSpent)}</Text>
          </Card>
          <Card style={[styles.statCard, { flex: 1 }]}>
            <TouchableOpacity style={styles.infoBtn} onPress={() => showInfo('saved')}>
              <Ionicons name="information-circle" size={16} color={colors.onSurfaceVariant} />
            </TouchableOpacity>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Ahorrado</Text>
            <Text style={[styles.statValue, { color: saved < 0 ? colors.error : colors.secondary }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>{formatCurrency(saved)}</Text>
          </Card>
          <Card style={[styles.statCard, { flex: 1 }]}>
            <TouchableOpacity style={styles.infoBtn} onPress={() => showInfo('avg')}>
              <Ionicons name="information-circle" size={16} color={colors.onSurfaceVariant} />
            </TouchableOpacity>
            <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>Promedio/día</Text>
            <Text style={[styles.statValue, { color: colors.tertiary }]} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}>{formatCurrency(avgSpent)}</Text>
          </Card>
        </View>

        {/* Charts */}
        <View>
          <View style={styles.sectionTitleRow}>
            <Ionicons name="trending-up" size={20} color={colors.onSurface} />
            <Text style={[styles.sectionTitle, { color: colors.onSurface }]}>Gastos en el tiempo</Text>
          </View>
          <View style={{ position: 'relative' }}>
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              {lineData.labels.length > 0 && (
                <LineChart
                  data={lineData} 
                  width={Math.max(chartWidth, lineData.labels.length * 45)} 
                  height={220} 
                  yAxisLabel={getCurrencySymbol()}
                  chartConfig={getSafeChartConfig('#f43f5e')} 
                  bezier 
                  style={{ paddingRight: 16 }} 
                  onDataPointClick={({ value, index }) => Alert.alert(lineData.labels[index], `Total gastado: ${formatCurrency(value)}`)}
                />
              )}
            </ScrollView>
            {lineData.labels.length > 0 && (
              <View style={{ position: 'absolute', left: 0, top: 0, width: 55, height: 220, overflow: 'hidden', backgroundColor: colors.background }}>
                <LineChart
                  data={lineData}
                  width={chartWidth}
                  height={220}
                  yAxisLabel={getCurrencySymbol()}
                  withVerticalLabels={false}
                  withInnerLines={false}
                  chartConfig={{
                    ...getSafeChartConfig('transparent'),
                    color: () => 'rgba(0,0,0,0)',
                    labelColor: (opacity = 1) => isDark ? `rgba(255, 255, 255, ${opacity})` : `rgba(0, 0, 0, ${opacity})`,
                    fillShadowGradientOpacity: 0,
                    propsForDots: { r: '0' },
                    propsForBackgroundLines: { strokeWidth: '0' }
                  }}
                  bezier
                  onDataPointClick={({ value, index }) => Alert.alert(lineData.labels[index], `Total gastado: ${formatCurrency(value)}`)}
                />
              </View>
            )}
          </View>
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
          <View style={{ position: 'relative' }}>
            <Card style={{ padding: 16 }}>
              {barExpenseAcc.labels.length > 0 && barExpenseAcc.labels.map((label, i) => {
                const val = barExpenseAcc.datasets[0].data[i];
                if (val === 0.01) return null; // Ignore forced variation placeholder
                const maxVal = Math.max(...barExpenseAcc.datasets[0].data.filter(v => v !== 0.01));
                const color = barExpenseAcc.datasets[0].colors?.[i]?.() || colors.error;
                const pct = maxVal > 0 ? (val / maxVal) * 100 : 0;
                return (
                  <View key={label} style={{ marginBottom: 12 }}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
                      <Text style={{ color: colors.onSurface, fontSize: 14 }}>{label}</Text>
                      <Text style={{ color: colors.onSurfaceVariant, fontSize: 14, fontWeight: 'bold' }}>{formatCurrency(val)}</Text>
                    </View>
                    <View style={{ height: 12, backgroundColor: colors.surfaceContainerHighest, borderRadius: 6, overflow: 'hidden' }}>
                      <View style={{ width: `${pct}%`, height: '100%', backgroundColor: color, borderRadius: 6 }} />
                    </View>
                  </View>
                );
              })}
              {(!barExpenseAcc.labels.length || barExpenseAcc.datasets[0].data[0] === 0.01) && (
                <Text style={{ textAlign: 'center', color: colors.onSurfaceVariant, padding: 24 }}>Sin datos</Text>
              )}
            </Card>
          </View>
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
                <TouchableOpacity onPress={() => setPickerConfig({
                  visible: true, title: 'Seleccionar Período 1', options: recentMonthsList,
                  value: compareP1, onSelect: (v) => setCompareP1(v)
                })}>
                  <View pointerEvents="none">
                    <TextField label="Período 1" placeholder="YYYY-MM" value={compareP1} onChangeText={() => {}} />
                  </View>
                </TouchableOpacity>
              </View>
              <View style={{ flex: 1 }}>
                <TouchableOpacity onPress={() => setPickerConfig({
                  visible: true, title: 'Seleccionar Período 2', options: recentMonthsList,
                  value: compareP2, onSelect: (v) => setCompareP2(v)
                })}>
                  <View pointerEvents="none">
                    <TextField label="Período 2" placeholder="YYYY-MM" value={compareP2} onChangeText={() => {}} />
                  </View>
                </TouchableOpacity>
              </View>
            </View>
            <View style={{ flexDirection: 'row', gap: 12, marginBottom: 16 }}>
              <View style={{ flex: 1 }}>
                <TouchableOpacity onPress={() => setPickerConfig({
                  visible: true, title: 'Seleccionar Categoría', options: ['Todas', ...optCategories],
                  value: compCategory || 'Todas', onSelect: (v) => setCompCategory(v === 'Todas' ? '' : v)
                })}>
                  <View pointerEvents="none">
                    <TextField label="Filtro Categoría" placeholder="Todas" value={compCategory || 'Todas'} onChangeText={() => {}} />
                  </View>
                </TouchableOpacity>
              </View>
              <View style={{ flex: 1 }}>
                <TouchableOpacity onPress={() => setPickerConfig({
                  visible: true, title: 'Seleccionar Método', options: ['Todos', ...optMethods],
                  value: compMethod || 'Todos', onSelect: (v) => setCompMethod(v === 'Todos' ? '' : v)
                })}>
                  <View pointerEvents="none">
                    <TextField label="Filtro de Pago" placeholder="Todos" value={compMethod || 'Todos'} onChangeText={() => {}} />
                  </View>
                </TouchableOpacity>
              </View>
            </View>
            <View style={{ flexDirection: 'row', gap: 12, marginBottom: 16 }}>
              <View style={{ flex: 1 }}>
                <TouchableOpacity onPress={() => setPickerConfig({
                  visible: true, title: 'Seleccionar Ingreso', options: ['Todas', ...optIncomeSources],
                  value: compIncomeSource || 'Todas', onSelect: (v) => setCompIncomeSource(v === 'Todas' ? '' : v)
                })}>
                  <View pointerEvents="none">
                    <TextField label="Filtro de Ingresos" placeholder="Todas las fuentes" value={compIncomeSource || 'Todas'} onChangeText={() => {}} />
                  </View>
                </TouchableOpacity>
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
                  <Text style={{ fontSize: 13, color: colors.onSurfaceVariant, marginBottom: 8, fontWeight: 'bold' }}>Resultado Comparativo</Text>
                  
                  <Text style={{ textAlign: 'center', fontSize: 14, color: colors.onSurface, marginBottom: 8, lineHeight: 20 }}>
                    {comparisonResult.diff > 0 
                      ? `Has gastado más en el Período 1 respecto al Período 2.\nEl sobrecosto aumentó un ${Math.abs(Number(comparisonResult.diffPercent))}%.`
                      : comparisonResult.diff < 0 
                        ? `¡Excelente! Has ahorrado en el Período 1 frente al Período 2.\nTus gastos se redujeron un ${Math.abs(Number(comparisonResult.diffPercent))}%.`
                        : `El volumen de gastos es idéntico en ambos períodos.`}
                  </Text>
                  
                  <Text style={{ fontSize: 24, fontWeight: 'bold', color: comparisonResult.diff > 0 ? colors.error : colors.secondary }}>
                    {comparisonResult.diff > 0 ? '+' : ''}{formatCurrency(comparisonResult.diff)}
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

      <BottomSheet visible={pickerConfig.visible} onClose={() => setPickerConfig(prev => ({ ...prev, visible: false }))} title={pickerConfig.title}>
        <ScrollView style={{ maxHeight: 300 }} nestedScrollEnabled>
          {pickerConfig.options.map((opt, i) => (
            <TouchableOpacity 
              key={i} 
              style={{ paddingVertical: 16, borderBottomWidth: 1, borderBottomColor: colors.outlineVariant, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}
              onPress={() => {
                pickerConfig.onSelect(opt);
                setPickerConfig(prev => ({ ...prev, visible: false }));
              }}
            >
              <Text style={{ fontSize: 16, color: pickerConfig.value === opt ? colors.primary : colors.onSurface }}>{opt}</Text>
              {pickerConfig.value === opt && <Ionicons name="checkmark" size={20} color={colors.primary} />}
            </TouchableOpacity>
          ))}
        </ScrollView>
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
    borderRadius: 16, overflow: 'hidden', paddingRight: 16
  }
});
