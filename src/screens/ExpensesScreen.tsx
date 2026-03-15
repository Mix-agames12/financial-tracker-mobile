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
import { formatCurrency, formatDate, getToday, getNow } from '../utils/formatters';
import { ExpenseRepo, CategoryRepo, AccountRepo, CreditCardRepo, LoanRepo } from '../db/storage';
import { Expense, Category, Account, CreditCard } from '../types';

export default function ExpensesScreen() {
  const { colors } = useTheme();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [creditCards, setCreditCards] = useState<CreditCard[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  // Filters State
  const [isFilterOpen, setFilterOpen] = useState(false);
  const [filterFrom, setFilterFrom] = useState('');
  const [filterTo, setFilterTo] = useState('');
  const [filterCat, setFilterCat] = useState('');
  const [filterMethod, setFilterMethod] = useState('');
  const [activeFilters, setActiveFilters] = useState(false);

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
  const [timeStr, setTimeStr] = useState(getNow());
  const [tags, setTags] = useState('');
  
  const [isRecurring, setIsRecurring] = useState(false);
  const [recurringDay, setRecurringDay] = useState('');
  const [subscriptionType, setSubscriptionType] = useState('');

  const loadData = async (applyFilters = activeFilters) => {
    try {
      let data = await ExpenseRepo.getAll();
      
      if (applyFilters && filterFrom && filterTo) {
        data = data.filter(e => e.date >= filterFrom && e.date <= filterTo);
        if (filterCat) data = data.filter(e => e.category === filterCat);
        if (filterMethod) data = data.filter(e => e.paymentMethod === filterMethod);
      }

      data.sort((a, b) => (b.date || '').localeCompare(a.date || ''));
      setExpenses(data);

      const cats = await CategoryRepo.getAll();
      setCategories(cats.filter(c => c.type === 'expense'));
      setAccounts(await AccountRepo.getAll());
      setCreditCards(await CreditCardRepo.getAll());
    } catch (e) {
      console.error(e);
    }
  };

  useFocusEffect(
    useCallback(() => {
      loadData();
    }, [activeFilters, filterFrom, filterTo, filterCat, filterMethod])
  );

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const handleApplyFilters = () => {
    if (!filterFrom || !filterTo) {
      Alert.alert('Aviso', 'Las fechas Desde y Hasta son obligatorias para filtrar.');
      return;
    }
    setActiveFilters(true);
    setFilterOpen(false);
    loadData(true);
  };

  const clearFilters = () => {
    setFilterFrom('');
    setFilterTo('');
    setFilterCat('');
    setFilterMethod('');
    setActiveFilters(false);
    setFilterOpen(false);
    loadData(false);
  };

  const openNewForm = () => {
    setEditingId(null);
    setAmountStr('');
    setCategoryType('');
    setDetail('');
    setPaymentMethod('Débito');
    setSelectedDebit('');
    setSelectedCredit('');
    setIsDeferred(false);
    setDeferredMonths('');
    setDateStr(getToday());
    setTimeStr(getNow());
    setTags('');
    setIsRecurring(false);
    setRecurringDay('');
    setSubscriptionType('');
    setDetailOpen(false);
    setFormOpen(true);
  };

  const openEditForm = (exp: Expense) => {
    setEditingId(exp.id);
    setAmountStr(exp.amount.toString());
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
    setIsRecurring((exp as any).isRecurring || false);
    setRecurringDay((exp as any).recurringDay?.toString() || '');
    setSubscriptionType((exp as any).subscriptionType || '');
    setDetailOpen(false);
    setFormOpen(true);
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

    const payload: Omit<Expense, 'id'> = {
      amount: Math.round(amount * 100) / 100,
      category: categoryType,
      detail: detail.trim(),
      paymentMethod,
      accountName: paymentMethod === 'Débito' ? selectedDebit : '',
      cardName: paymentMethod === 'Crédito' ? selectedCredit : '',
      isDeferred: paymentMethod === 'Crédito' ? isDeferred : false,
      deferredMonths: paymentMethod === 'Crédito' && isDeferred ? parseInt(deferredMonths) || 0 : 0,
      date: dateStr || getToday(),
      ...( { 
        time: timeStr, 
        tags: tags.split(',').map(t => t.trim()).filter(Boolean),
        isRecurring,
        recurringDay: isRecurring ? parseInt(recurringDay) || 0 : 0,
        subscriptionType: categoryType === 'Suscripción' ? subscriptionType : '',
      } as any )
    };

    try {
      if (editingId) {
        await ExpenseRepo.update({ id: editingId, ...payload });
      } else {
        await ExpenseRepo.add(payload);

        if (payload.paymentMethod === 'Crédito' && payload.cardName) {
          const card = creditCards.find(c => c.name === payload.cardName);
          if (card) {
            await CreditCardRepo.update({ ...card, currentBalance: (card.currentBalance || 0) + payload.amount });
            if (payload.isDeferred && payload.deferredMonths! > 0) {
              await LoanRepo.add({
                name: `Diferido: ${payload.detail}`,
                totalAmount: payload.amount,
                installments: payload.deferredMonths,
                paidInstallments: 0,
                monthlyQuota: Math.round((payload.amount / payload.deferredMonths!) * 100) / 100,
                interestRate: 0,
                ...( { nextPaymentDate: '', status: 'active' } as any )
              });
            }
          }
        }
      }

      setFormOpen(false);
      loadData();
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo guardar el gasto');
    }
  };

  const handleDeleteExpense = () => {
    if (!selectedExpense) return;
    Alert.alert('Eliminar Gasto', '¿Estás seguro de eliminar este gasto?', [
      { text: 'Cancelar', style: 'cancel' },
      { 
        text: 'Eliminar', style: 'destructive', 
        onPress: async () => {
          await ExpenseRepo.delete(selectedExpense.id);
          setDetailOpen(false);
          loadData();
        }
      }
    ]);
  };

  const totalSpent = useMemo(() => expenses.reduce((s, e) => s + e.amount, 0), [expenses]);
  let lastDateRendered = '';

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      
      {/* Header */}
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Gastos</Text>
        <TouchableOpacity 
          style={[styles.iconBtn, { backgroundColor: activeFilters ? colors.primaryContainer : 'transparent' }]}
          onPress={() => setFilterOpen(true)}
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
                  <View style={[styles.listIcon, { backgroundColor: colors.errorContainer }]}>
                    <Ionicons name="receipt" size={20} color={colors.error} />
                  </View>
                  <View style={styles.listContent}>
                    <Text style={[styles.listTitle, { color: colors.onSurface }]} numberOfLines={1}>{exp.detail}</Text>
                    <Text style={[styles.listSubtitle, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
                      {exp.category} 
                      {exp.paymentMethod === 'Débito' && exp.accountName ? ` · ${exp.accountName}` : ''}
                      {exp.paymentMethod === 'Crédito' && exp.cardName ? ` · ${exp.cardName}` : ''}
                      {exp.isDeferred ? ` · Diferido ${exp.deferredMonths}m` : ''}
                      {(exp as any).isRecurring ? ' · Recurrente' : ''}
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
              {(selectedExpense as any).isRecurring && (
                <View style={styles.detailItem}>
                  <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Recurrente</Text>
                  <Text style={{ fontSize: 16, color: colors.onSurface }}>Día {(selectedExpense as any).recurringDay}</Text>
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
              <TextField label="Desde (YYYY-MM-DD)" placeholder="YYYY-MM-DD" value={filterFrom} onChangeText={setFilterFrom} />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="Hasta (YYYY-MM-DD)" placeholder="YYYY-MM-DD" value={filterTo} onChangeText={setFilterTo} />
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
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Categoría</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              <Chip label="Todas" active={filterCat === ''} onPress={() => setFilterCat('')} />
              {categories.map((c) => (
                <Chip key={c.name} label={c.name} active={filterCat === c.name} onPress={() => setFilterCat(c.name)} />
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
            placeholder="0.00" 
            keyboardType="decimal-pad" 
            value={amountStr} 
            onChangeText={setAmountStr} 
          />

          <View>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Categoría *</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              {categories.map((c) => (
                <Chip key={c.name} label={c.name} active={categoryType === c.name} onPress={() => setCategoryType(c.name)} icon={c.icon as any} />
              ))}
            </View>
          </View>

          <TextField 
            label="Detalle *" 
            placeholder="Descripción del gasto" 
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

          {paymentMethod === 'Débito' && accounts.length > 0 && (
            <View>
               <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Cuenta de débito</Text>
               <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
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
                 <TextField label="Meses diferidos" placeholder="3" keyboardType="number-pad" value={deferredMonths} onChangeText={setDeferredMonths} />
               )}
            </View>
          )}

          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}>
              <TextField label="Fecha" placeholder="YYYY-MM-DD" value={dateStr} onChangeText={setDateStr} />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="Hora (Opcional)" placeholder="HH:MM" value={timeStr} onChangeText={setTimeStr} />
            </View>
          </View>

          <Button title={editingId ? 'Actualizar gasto' : 'Guardar gasto'} onPress={handleSaveExpense} />
        </View>
      </BottomSheet>

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
