import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as DocumentPicker from 'expo-document-picker';

import { useTheme } from '../theme/ThemeContext';
import { 
  SettingsRepo, 
  exportAllData, 
  importAllData, 
  clearAllData, 
  hasAnyData,
  CategoryRepo,
  ExpenseRepo,
  IncomeRepo,
  AccountRepo,
  LoanRepo,
  getAccountBalances,
  DEFAULT_EXPENSE_CATEGORIES
} from '../db/storage';
import { Category, Settings } from '../types';
import { generateCSVReport, generatePDFReport, ReportPeriod, REPORT_PERIOD_LABELS } from '../utils/reportGenerator';
import { formatCurrency, getCurrencyConfig, getCurrencySymbol } from '../utils/formatters';
import { detectDeviceCurrency, getCurrencyOption, initCurrency, saveCurrency } from '../utils/currency';
import { syncAfterDataChange } from '../utils/dataSync';

import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { BottomSheet } from '../components/BottomSheet';
import { Chip } from '../components/Chip';
import { CurrencyPicker } from '../components/CurrencyPicker';
import { ToastManager } from '../components/ActionFeedback';

// Colores hex de 6 dígitos: Chip y las listas les agregan sufijos de opacidad ('20', '40').
const CATEGORY_PALETTE = [
  '#ef4444', '#f97316', '#f59e0b', '#eab308', '#84cc16', '#22c55e',
  '#10b981', '#14b8a6', '#06b6d4', '#0ea5e9', '#3b82f6', '#6366f1',
  '#8b5cf6', '#a855f7', '#d946ef', '#ec4899', '#f43f5e', '#64748b',
];

export default function SettingsScreen() {
  const { colors, isDark, setTheme } = useTheme();
  const insets = useSafeAreaInsets();
  
  const [settings, setLocalSettings] = useState<Settings>({ 
    id: 'default', appName: 'Mi Dinero', monthlyGoal: 0, theme: 'dark' 
  });
  
  const [isNameSheetOpen, setNameSheetOpen] = useState(false);
  const [tempName, setTempName] = useState('');

  const [isGoalSheetOpen, setGoalSheetOpen] = useState(false);
  const [tempGoal, setTempGoal] = useState('');

  const [isWipeSheetOpen, setWipeSheetOpen] = useState(false);

  // Taxes
  const [isTaxesSheetOpen, setTaxesSheetOpen] = useState(false);
  const [taxesEnabled, setTaxesEnabled] = useState(false);
  const [ivaRate, setIvaRate] = useState('15');
  const [comisionRate, setComisionRate] = useState('2');
  const [isdRate, setIsdRate] = useState('5');
  const [taxTargets, setTaxTargets] = useState<string[]>([]);
  const [categories, setCategories] = useState<any[]>([]);
  
  // Reports
  const [isReportSheetOpen, setReportSheetOpen] = useState(false);
  const [reportFormat, setReportFormat] = useState<'PDF' | 'CSV'>('PDF');
  const [reportType, setReportType] = useState<'all' | 'ingresos' | 'gastos'>('all');
  const [reportPeriod, setReportPeriod] = useState<ReportPeriod>('thisMonth');
  const [isGeneratingReport, setGeneratingReport] = useState(false);

  // Moneda
  const [isCurrencySheetOpen, setCurrencySheetOpen] = useState(false);

  // Colores de categorías
  const [isColorsSheetOpen, setColorsSheetOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);

  useEffect(() => {
    loadSettings();
  }, [isDark]);

  const loadSettings = async () => {
    const s = await SettingsRepo.get();
    const cats = await CategoryRepo.getAll();
    setCategories(cats);
    setLocalSettings(s);
    setTempName(s.appName);
    setTempGoal(s.monthlyGoal.toString());
    
    if (s.taxes) {
      setTaxesEnabled(s.taxes.enabled);
      setIvaRate(s.taxes.ivaRate.toString());
      setComisionRate(s.taxes.comisionRate.toString());
      setIsdRate(s.taxes.isdRate.toString());
      setTaxTargets(s.taxes.applyTo || []);
    }
  };
  // Nombre del fondo
  const saveName = async () => {
    if (!tempName.trim()) {
      Alert.alert('Error', 'Ingresa un nombre válido');
      return;
    }
    await SettingsRepo.save({ appName: tempName.trim() });
    setNameSheetOpen(false);
    loadSettings();
    syncAfterDataChange();
  };
  // Guardar Meta
  const saveGoal = async () => {
    const parsed = parseFloat(tempGoal);
    if (isNaN(parsed) || parsed < 0) {
      Alert.alert('Error', 'Monto inválido');
      return;
    }
    await SettingsRepo.save({ monthlyGoal: parsed });
    setGoalSheetOpen(false);
    loadSettings();
    syncAfterDataChange();
  };

  // Guardar Impuestos
  const saveTaxes = async () => {
    await SettingsRepo.save({ 
      taxes: {
        enabled: taxesEnabled,
        ivaRate: parseFloat(ivaRate.replace(',', '.')) || 0,
        comisionRate: parseFloat(comisionRate.replace(',', '.')) || 0,
        isdRate: parseFloat(isdRate.replace(',', '.')) || 0,
        applyTo: taxTargets
      }
    });
    setTaxesSheetOpen(false);
    loadSettings();
  };

  const toggleTaxTarget = (t: string) => {
    setTaxTargets(prev => prev.includes(t) ? prev.filter(x => x !== t) : [...prev, t]);
  };

  // Moneda
  const handleSelectCurrency = async (currency: string, locale: string) => {
    await saveCurrency({ currency, locale });
    setCurrencySheetOpen(false);
    ToastManager.show('Moneda actualizada');
    loadSettings();
    syncAfterDataChange();
  };

  // Colores de categorías
  const handleSelectCategoryColor = async (cat: Category, color: string) => {
    await CategoryRepo.update({ ...cat, color, isCustomColor: true });
    setEditingCategory(null);
    ToastManager.show('Color actualizado');
    loadSettings();
  };

  const handleResetCategoryColor = async (cat: Category) => {
    const def = DEFAULT_EXPENSE_CATEGORIES.find(d => d.name === cat.name);
    await CategoryRepo.update({ ...cat, color: def?.color || cat.color, isCustomColor: false });
    setEditingCategory(null);
    ToastManager.show('Color restablecido');
    loadSettings();
  };

  const currencyCode = getCurrencyConfig().currency;
  const currencyOption = getCurrencyOption(currencyCode);
  const deviceCurrency = detectDeviceCurrency();
  const expenseCategories = (categories as Category[]).filter(c => c.type === 'expense');

  // Exportar Backup
  const handleExport = async () => {
    try {
      const data = await exportAllData();
      const jsonStr = JSON.stringify(data, null, 2);
      const fileName = `expense-tracker-backup-${new Date().toISOString()}-tmp.json`;
      const file = new File(Paths.document, fileName);
      file.write(jsonStr);
      
      const canShare = await Sharing.isAvailableAsync();
      if (canShare) {
        await Sharing.shareAsync(file.uri);
      } else {
        Alert.alert('No soportado', 'Exportar archivos no está soportado en este dispositivo.');
      }
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo exportar el backup');
    }
  };
// Importar Backup
  const handleImport = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: 'application/json',
        copyToCacheDirectory: true,
      });

      if (result.canceled || !result.assets || result.assets.length === 0) return;

      const file = new File(result.assets[0].uri);
      const contents = await file.text();
      const parsed = JSON.parse(contents);

      if (!parsed._exportDate) {
        Alert.alert('Error', 'Archivo no válido o corrupto');
        return;
      }

      const hasData = await hasAnyData();

      if (hasData) {
        Alert.alert(
          'Datos existentes detectados',
          'Tus datos actuales serán sobreescribidos y no podrás recuperarlos. ¿Continuar?',
          [
            { text: 'Cancelar', style: 'cancel' },
            { 
              text: 'Sobreescribir', 
              style: 'destructive',
              onPress: () => performImport(parsed)
            }
          ]
        );
      } else {
        await performImport(parsed);
      }
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo importar el archivo');
    }
  };

  // Reports Logic
  const handleGenerateReport = async () => {
    setGeneratingReport(true);
    try {
      const [expenses, incomes, accounts, loans, accountBalances] = await Promise.all([
        ExpenseRepo.getAll(),
        IncomeRepo.getAll(),
        AccountRepo.getAll(),
        LoanRepo.getAll(),
        getAccountBalances(),
      ]);

      const data = {
        expenses,
        incomes,
        loans,
        accounts,
        accountBalances,
        filters: { type: reportType, period: reportPeriod },
      };

      const shared = reportFormat === 'CSV' ? await generateCSVReport(data) : await generatePDFReport(data);
      if (!shared) {
        Alert.alert('No disponible', 'Compartir archivos no está soportado en este dispositivo.');
        return;
      }
      setReportSheetOpen(false);
    } catch (error) {
      console.error(error);
      Alert.alert('Error', 'Hubo un error al generar el reporte.');
    } finally {
      setGeneratingReport(false);
    }
  };
  const performImport = async (data: any) => {
    await importAllData(data);
    await CategoryRepo.seedDefaults();
    // El backup puede traer otra moneda en su configuración.
    await initCurrency();
    Alert.alert('Éxito', 'Datos importados correctamente');
    loadSettings();
    syncAfterDataChange();
  };

  const handleWipe = async () => {
    await clearAllData();
    setWipeSheetOpen(false);
    Alert.alert('Éxito', 'Todos los datos han sido eliminados');
    loadSettings();
    syncAfterDataChange();
  };

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ padding: 16, paddingBottom: insets.bottom + 24 }}
    >
      
      {/* PREFERENCES */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>PREFERENCIAS</Text>
        <Card style={styles.listCard}>
          
          <View style={styles.listItem}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="moon" size={20} color={colors.primary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Tema de la app</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>
                  Actual: {isDark ? 'Oscuro' : 'Claro'}
                </Text>
              </View>
            </View>
            <View style={[styles.themeToggle, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainerLow }]}>
              <TouchableOpacity
                style={[styles.themeBtn, !isDark && { backgroundColor: colors.primary }]}
                onPress={() => setTheme('light')}
              >
                <Text style={[styles.themeBtnText, { color: !isDark ? colors.onPrimary : colors.onSurfaceVariant }]}>Claro</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.themeBtn, isDark && { backgroundColor: colors.primary }]}
                onPress={() => setTheme('dark')}
              >
                <Text style={[styles.themeBtnText, { color: isDark ? colors.onPrimary : colors.onSurfaceVariant }]}>Oscuro</Text>
              </TouchableOpacity>
            </View>
          </View>

          <TouchableOpacity style={styles.listItem} onPress={() => setNameSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="pencil" size={20} color={colors.primary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Nombre de la app</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>{settings.appName}</Text>
              </View>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>

          <TouchableOpacity style={styles.listItem} onPress={() => setCurrencySheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="cash" size={20} color={colors.primary} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Moneda</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
                  {currencyOption ? `${currencyOption.name} (${currencyCode})` : currencyCode} · {formatCurrency(1234.5)}
                </Text>
              </View>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>

          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={() => setGoalSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="flag" size={20} color={colors.primary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Meta de gasto mensual</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>
                  {settings.monthlyGoal ? formatCurrency(settings.monthlyGoal) : 'No definida'}
                </Text>
              </View>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </Card>
      </View>

      {/* TAXES ENGINE */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>FINANZAS</Text>
        <Card style={styles.listCard}>
          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={() => setTaxesSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.secondaryContainer }]}>
                <Ionicons name="calculator" size={20} color={colors.secondary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Motor de Impuestos</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>
                  {settings.taxes?.enabled ? `Activo (IVA ${settings.taxes.ivaRate}%, Com. ${settings.taxes.comisionRate}%)` : 'Desactivado'}
                </Text>
              </View>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </Card>
      </View>

      {/* CATEGORIES */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>CATEGORÍAS</Text>
        <Card style={styles.listCard}>
          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={() => setColorsSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.tertiaryContainer }]}>
                <Ionicons name="color-palette" size={20} color={colors.tertiary} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Colores de categorías</Text>
                <View style={styles.swatchRow}>
                  {expenseCategories.slice(0, 11).map(c => (
                    <View key={c.id} style={[styles.swatchDot, { backgroundColor: c.color }]} />
                  ))}
                </View>
              </View>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </Card>
      </View>

      {/* REPORTS */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>REPORTES</Text>
        <Card style={styles.listCard}>
          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={() => setReportSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.secondaryContainer }]}>
                <Ionicons name="document-text" size={20} color={colors.secondary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Generación de Reportes</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>
                  PDF Gráfico o exportación CSV
                </Text>
              </View>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </Card>
      </View>

      {/* DATA */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>DATOS</Text>
        <Card style={styles.listCard}>
          <TouchableOpacity style={styles.listItem} onPress={handleExport}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="cloud-download" size={20} color={colors.primary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Exportar backup</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>Exporta en formato JSON</Text>
              </View>
            </View>
            <Ionicons name="download-outline" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>

          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={handleImport}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="cloud-upload" size={20} color={colors.primary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Importar backup</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>Restaura datos desde JSON</Text>
              </View>
            </View>
            <Ionicons name="push-outline" size={20} color={colors.onSurfaceVariant} />
          </TouchableOpacity>
        </Card>
      </View>

      {/* DANGER ZONE */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.error }]}>ZONA DE PELIGRO</Text>
        <Card style={[styles.listCard, { borderColor: colors.error, borderWidth: 1 }]}>
          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={() => setWipeSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.errorContainer }]}>
                <Ionicons name="warning" size={20} color={colors.error} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.error }]}>Eliminar todos los datos</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>No se puede deshacer</Text>
              </View>
            </View>
            <Ionicons name="trash-outline" size={20} color={colors.error} />
          </TouchableOpacity>
        </Card>
      </View>

      <View style={{ height: 40 }} />

      {/* Modals */}
      <BottomSheet visible={isNameSheetOpen} onClose={() => setNameSheetOpen(false)} title="Nombre de la app">
        <TextField
          label="Nombre (máx. 30 caracteres)"
          placeholder="Ej. Mi Dinero"
          value={tempName}
          onChangeText={setTempName}
          maxLength={30}
          autoFocus
        />
        <Button title="Guardar" onPress={saveName} />
      </BottomSheet>

      <BottomSheet visible={isGoalSheetOpen} onClose={() => setGoalSheetOpen(false)} title="Meta mensual">
        <TextField
          label={`Monto máximo (${getCurrencySymbol()})`}
          placeholder="Ej. 1000.00"
          value={tempGoal}
          onChangeText={setTempGoal}
          keyboardType="decimal-pad"
          autoFocus
        />
        <Button title="Guardar" onPress={saveGoal} />
      </BottomSheet>

      <BottomSheet visible={isTaxesSheetOpen} onClose={() => setTaxesSheetOpen(false)} title="Motor de Impuestos">
        <View>
          <TouchableOpacity 
            style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: colors.outlineVariant, marginBottom: 16 }}
            onPress={() => setTaxesEnabled(!taxesEnabled)}
          >
            <Text style={{ color: colors.onSurface, fontSize: 16, fontWeight: 'bold' }}>Habilitar Cálculo de Impuestos</Text>
            <Ionicons name={taxesEnabled ? 'toggle' : 'toggle-outline'} size={32} color={taxesEnabled ? colors.primary : colors.onSurfaceVariant} />
          </TouchableOpacity>
          
          {taxesEnabled && (
            <View style={{ gap: 12 }}>
              <View style={{ flexDirection: 'row', gap: 12 }}>
                <View style={{ flex: 1 }}><TextField label="Comisión (%)" value={comisionRate} onChangeText={setComisionRate} keyboardType="decimal-pad" /></View>
                <View style={{ flex: 1 }}><TextField label="IVA (%)" value={ivaRate} onChangeText={setIvaRate} keyboardType="decimal-pad" /></View>
                <View style={{ flex: 1 }}><TextField label="ISD (%)" value={isdRate} onChangeText={setIsdRate} keyboardType="decimal-pad" /></View>
              </View>

              <Text style={{ color: colors.onSurfaceVariant, fontSize: 13, marginTop: 8 }}>¿Dónde quieres aplicar deducciones automáticas?</Text>
              
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                {['Préstamos', 'Tarjetas de Crédito', 'Inversiones'].map(t => (
                  <TouchableOpacity key={t} onPress={() => toggleTaxTarget(t)} style={{ paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: taxTargets.includes(t) ? colors.primary : colors.surfaceContainerHigh, borderWidth: 1, borderColor: taxTargets.includes(t) ? colors.primary : colors.outlineVariant }}>
                    <Text style={{ color: taxTargets.includes(t) ? colors.onPrimary : colors.onSurface, fontSize: 13 }}>{t}</Text>
                  </TouchableOpacity>
                ))}
              </View>

              <Text style={{ color: colors.onSurfaceVariant, fontSize: 13, marginTop: 16 }}>O en Gastos de categorías específicas:</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, paddingBottom: 16 }}>
                {categories.map(c => {
                  const targetName = `Gastos:${c.name}`;
                  const active = taxTargets.includes(targetName);
                  return (
                    <TouchableOpacity key={targetName} onPress={() => toggleTaxTarget(targetName)} style={{ paddingHorizontal: 16, paddingVertical: 8, borderRadius: 20, backgroundColor: active ? colors.primary : colors.surfaceContainerHigh, borderWidth: 1, borderColor: active ? colors.primary : colors.outlineVariant }}>
                      <Text style={{ color: active ? colors.onPrimary : colors.onSurface, fontSize: 13 }}>{c.name}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>
          )}

          <Button title="Guardar cambios" onPress={saveTaxes} style={{ marginTop: 16 }} />
        </View>
      </BottomSheet>

      <BottomSheet visible={isReportSheetOpen} onClose={() => setReportSheetOpen(false)} title="Generar Reporte">
        <View style={{ gap: 16 }}>
          <View>
            <Text style={{ fontSize: 13, color: colors.onSurfaceVariant, marginBottom: 8, fontWeight: 'bold' }}>Formato de Salida</Text>
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <TouchableOpacity onPress={() => setReportFormat('PDF')} style={{ flex: 1, padding: 12, borderRadius: 12, borderWidth: 1, borderColor: reportFormat === 'PDF' ? colors.primary : colors.outlineVariant, backgroundColor: reportFormat === 'PDF' ? colors.primaryContainer : 'transparent', alignItems: 'center' }}>
                <Ionicons name="document" size={24} color={reportFormat === 'PDF' ? colors.primary : colors.onSurfaceVariant} />
                <Text style={{ marginTop: 4, fontWeight: 'bold', color: reportFormat === 'PDF' ? colors.primary : colors.onSurfaceVariant }}>PDF</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => setReportFormat('CSV')} style={{ flex: 1, padding: 12, borderRadius: 12, borderWidth: 1, borderColor: reportFormat === 'CSV' ? colors.primary : colors.outlineVariant, backgroundColor: reportFormat === 'CSV' ? colors.primaryContainer : 'transparent', alignItems: 'center' }}>
                <Ionicons name="grid" size={24} color={reportFormat === 'CSV' ? colors.primary : colors.onSurfaceVariant} />
                <Text style={{ marginTop: 4, fontWeight: 'bold', color: reportFormat === 'CSV' ? colors.primary : colors.onSurfaceVariant }}>CSV</Text>
              </TouchableOpacity>
            </View>
          </View>

          <View>
            <Text style={{ fontSize: 13, color: colors.onSurfaceVariant, marginBottom: 8, fontWeight: 'bold' }}>Tipo de Datos a Incluir</Text>
            <View style={{ gap: 8 }}>
              {['all', 'ingresos', 'gastos'].map(tipo => (
                <TouchableOpacity key={tipo} onPress={() => setReportType(tipo as any)} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, borderRadius: 12, backgroundColor: colors.surfaceContainerHigh }}>
                  <Ionicons name={reportType === tipo ? 'radio-button-on' : 'radio-button-off'} size={20} color={colors.primary} />
                  <Text style={{ color: colors.onSurface, textTransform: 'capitalize' }}>{tipo === 'all' ? 'Ingresos y Gastos (Completo)' : tipo}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          <View>
            <Text style={{ fontSize: 13, color: colors.onSurfaceVariant, marginBottom: 8, fontWeight: 'bold' }}>Período</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {(Object.keys(REPORT_PERIOD_LABELS) as ReportPeriod[]).map(p => (
                <Chip key={p} label={REPORT_PERIOD_LABELS[p]} active={reportPeriod === p} onPress={() => setReportPeriod(p)} />
              ))}
            </View>
          </View>

          <Button title="Generar Reporte" icon="download" onPress={handleGenerateReport} loading={isGeneratingReport} style={{ marginTop: 8 }} />
        </View>
      </BottomSheet>

      <BottomSheet visible={isCurrencySheetOpen} onClose={() => setCurrencySheetOpen(false)} title="Moneda">
        <TouchableOpacity
          style={[styles.deviceCurrencyBtn, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainerLow }]}
          onPress={() => handleSelectCurrency(deviceCurrency.currency, deviceCurrency.locale)}
        >
          <Ionicons name="phone-portrait-outline" size={20} color={colors.primary} />
          <Text style={{ flex: 1, fontSize: 15, color: colors.onSurface }}>Usar la del dispositivo ({deviceCurrency.currency})</Text>
        </TouchableOpacity>
        <CurrencyPicker selectedCode={currencyCode} onSelect={(opt) => handleSelectCurrency(opt.code, opt.locale)} />
      </BottomSheet>

      <BottomSheet
        visible={isColorsSheetOpen}
        onClose={() => { setColorsSheetOpen(false); setEditingCategory(null); }}
        title={editingCategory ? `Color de ${editingCategory.name}` : 'Colores de categorías'}
      >
        {editingCategory ? (
          <View>
            <View style={styles.paletteGrid}>
              {CATEGORY_PALETTE.map(color => {
                const selected = editingCategory.color.toLowerCase() === color;
                return (
                  <TouchableOpacity
                    key={color}
                    style={[styles.paletteSwatch, { backgroundColor: color, borderColor: selected ? colors.onSurface : 'transparent' }]}
                    onPress={() => handleSelectCategoryColor(editingCategory, color)}
                    accessibilityLabel={`Color ${color}`}
                    accessibilityState={{ selected }}
                  >
                    {selected && <Ionicons name="checkmark" size={20} color="#ffffff" />}
                  </TouchableOpacity>
                );
              })}
            </View>
            <View style={{ flexDirection: 'row', gap: 12, marginTop: 24 }}>
              <Button style={{ flex: 1 }} variant="outlined" title="Volver" onPress={() => setEditingCategory(null)} />
              <Button style={{ flex: 1 }} variant="tonal" title="Restablecer" icon="refresh" onPress={() => handleResetCategoryColor(editingCategory)} />
            </View>
          </View>
        ) : (
          <View>
            {expenseCategories.map(cat => (
              <TouchableOpacity
                key={cat.id}
                style={[styles.categoryRow, { borderBottomColor: colors.outlineVariant }]}
                onPress={() => setEditingCategory(cat)}
              >
                <View style={[styles.iconBox, { backgroundColor: cat.color + '20' }]}>
                  <Ionicons name={cat.icon as any} size={18} color={cat.color} />
                </View>
                <Text style={{ flex: 1, fontSize: 16, color: colors.onSurface }}>{cat.name}</Text>
                {cat.isCustomColor && (
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginRight: 8 }}>Personalizado</Text>
                )}
                <View style={[styles.colorDot, { backgroundColor: cat.color }]} />
              </TouchableOpacity>
            ))}
          </View>
        )}
      </BottomSheet>

      <BottomSheet visible={isWipeSheetOpen} onClose={() => setWipeSheetOpen(false)} title="¿Eliminar todo?">
        <Card style={{ borderColor: colors.error, borderWidth: 1 }}>
          <Text style={{ color: colors.onSurface, marginBottom: 8 }}>Esta acción eliminará:</Text>
          <Text style={{ color: colors.onSurfaceVariant }}>• Gastos e ingresos</Text>
          <Text style={{ color: colors.onSurfaceVariant }}>• Todas las cuentas y tarjetas</Text>
          <Text style={{ color: colors.onSurfaceVariant }}>• Préstamos e inversiones</Text>
          <Text style={{ color: colors.error, fontWeight: 'bold', marginTop: 16 }}>Los datos NO se podrán recuperar.</Text>
        </Card>
        <View style={{ flexDirection: 'row', gap: 12, marginTop: 16 }}>
          <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={() => setWipeSheetOpen(false)} />
          <Button style={{ flex: 1 }} variant="danger" title="Eliminar" onPress={handleWipe} icon="trash" />
        </View>
      </BottomSheet>

    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 'bold',
    marginBottom: 8,
    marginLeft: 8,
    letterSpacing: 0.5,
  },
  listCard: {
    padding: 0,
    overflow: 'hidden',
  },
  listItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(150,150,150,0.1)',
  },
  noBorder: {
    borderBottomWidth: 0,
  },
  itemContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  iconBox: {
    width: 32,
    height: 32,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  itemTitle: {
    fontSize: 16,
    fontWeight: '500',
  },
  itemSubtitle: {
    fontSize: 12,
    marginTop: 2,
  },
  themeToggle: {
    flexDirection: 'row',
    borderWidth: 1,
    borderRadius: 20,
    overflow: 'hidden',
    padding: 2,
  },
  themeBtn: {
    paddingVertical: 6,
    paddingHorizontal: 16,
    borderRadius: 20,
  },
  themeBtnText: {
    fontSize: 12,
    fontWeight: '600',
  },
  swatchRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    marginTop: 6,
  },
  swatchDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  colorDot: {
    width: 20,
    height: 20,
    borderRadius: 10,
  },
  categoryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  paletteGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
    justifyContent: 'center',
  },
  paletteSwatch: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
  },
  deviceCurrencyBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 8,
  },
});
