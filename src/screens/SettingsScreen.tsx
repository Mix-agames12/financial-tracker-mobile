import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Alert, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as DocumentPicker from 'expo-document-picker';

import { useTheme } from '../theme/ThemeContext';
import { 
  SettingsRepo, 
  exportAllData, 
  importAllData, 
  clearAllData, 
  hasAnyData,
  CategoryRepo
} from '../db/storage';
import { Settings } from '../types';

import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { BottomSheet } from '../components/BottomSheet';

export default function SettingsScreen() {
  const { colors, isDark, setTheme } = useTheme();
  
  const [settings, setLocalSettings] = useState<Settings>({ 
    id: 'default', appName: 'Mi Dinero', monthlyGoal: 0, theme: 'dark' 
  });
  
  const [isNameSheetOpen, setNameSheetOpen] = useState(false);
  const [tempName, setTempName] = useState('');

  const [isGoalSheetOpen, setGoalSheetOpen] = useState(false);
  const [tempGoal, setTempGoal] = useState('');

  const [isWipeSheetOpen, setWipeSheetOpen] = useState(false);

  useEffect(() => {
    loadSettings();
  }, [isDark]);

  const loadSettings = async () => {
    const s = await SettingsRepo.get();
    setLocalSettings(s);
    setTempName(s.appName);
    setTempGoal(s.monthlyGoal.toString());
  };

  const saveName = async () => {
    if (!tempName.trim()) {
      Alert.alert('Error', 'Ingresa un nombre válido');
      return;
    }
    await SettingsRepo.save({ appName: tempName.trim() });
    setNameSheetOpen(false);
    loadSettings();
  };

  const saveGoal = async () => {
    const parsed = parseFloat(tempGoal);
    if (isNaN(parsed) || parsed < 0) {
      Alert.alert('Error', 'Monto inválido');
      return;
    }
    await SettingsRepo.save({ monthlyGoal: parsed });
    setGoalSheetOpen(false);
    loadSettings();
  };

  const handleExport = async () => {
    try {
      const data = await exportAllData();
      const jsonStr = JSON.stringify(data, null, 2);
      const fileName = `tracker-backup-${new Date().toISOString().slice(0, 10)}.json`;
      const fileUri = `${FileSystem.documentDirectory}${fileName}`;
      
      await FileSystem.writeAsStringAsync(fileUri, jsonStr, { encoding: FileSystem.EncodingType.UTF8 });
      
      const canShare = await Sharing.isAvailableAsync();
      if (canShare) {
        await Sharing.shareAsync(fileUri);
      } else {
        Alert.alert('No soportado', 'Exportar archivos no está soportado en este dispositivo.');
      }
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo exportar el backup');
    }
  };

  const handleImport = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: 'application/json',
        copyToCacheDirectory: true,
      });

      if (result.canceled || !result.assets || result.assets.length === 0) return;

      const file = result.assets[0];
      const contents = await FileSystem.readAsStringAsync(file.uri);
      const parsed = JSON.parse(contents);

      if (!parsed._version) {
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

  const performImport = async (data: any) => {
    await importAllData(data);
    await CategoryRepo.seedDefaults();
    Alert.alert('Éxito', 'Datos importados correctamente');
    loadSettings();
  };

  const handleWipe = async () => {
    await clearAllData();
    setWipeSheetOpen(false);
    Alert.alert('Éxito', 'Todos los datos han sido eliminados');
    loadSettings();
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]}>
      
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

          <TouchableOpacity style={[styles.listItem, styles.noBorder]} onPress={() => setGoalSheetOpen(true)}>
            <View style={styles.itemContent}>
              <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}>
                <Ionicons name="flag" size={20} color={colors.primary} />
              </View>
              <View>
                <Text style={[styles.itemTitle, { color: colors.onSurface }]}>Meta de gasto mensual</Text>
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>
                  {settings.monthlyGoal ? `$${settings.monthlyGoal.toFixed(2)}` : 'No definida'}
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
                <Text style={[styles.itemSubtitle, { color: colors.onSurfaceVariant }]}>Descarga en formato JSON</Text>
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
          value={tempName}
          onChangeText={setTempName}
          maxLength={30}
          autoFocus
        />
        <Button title="Guardar" onPress={saveName} />
      </BottomSheet>

      <BottomSheet visible={isGoalSheetOpen} onClose={() => setGoalSheetOpen(false)} title="Meta mensual">
        <TextField
          label="Monto máximo ($)"
          value={tempGoal}
          onChangeText={setTempGoal}
          keyboardType="numeric"
          autoFocus
        />
        <Button title="Guardar" onPress={saveGoal} />
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
    padding: 16,
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
});
