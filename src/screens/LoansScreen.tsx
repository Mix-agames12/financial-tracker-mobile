import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { BottomSheet } from '../components/BottomSheet';
import { ProgressBar } from '../components/ProgressBar';

import { formatCurrency, formatDate, daysUntil, getToday } from '../utils/formatters';
import { LoanRepo, ExpenseRepo } from '../db/storage';
import { Loan } from '../types';

export default function LoansScreen() {
  const { colors } = useTheme();

  const [refreshing, setRefreshing] = useState(false);
  const [loans, setLoans] = useState<Loan[]>([]);

  // Modals
  const [isFormOpen, setFormOpen] = useState(false);
  const [isDetailOpen, setDetailOpen] = useState(false);

  // Form State
  const [editingData, setEditingData] = useState<Loan | null>(null);
  const [name, setName] = useState('');
  const [totalAmount, setTotalAmount] = useState('');
  const [interestRate, setInterestRate] = useState('');
  const [installments, setInstallments] = useState('');
  const [paidInstallments, setPaidInstallments] = useState('');
  const [nextPaymentDate, setNextPaymentDate] = useState('');

  // Selected for Details
  const [selectedLoan, setSelectedLoan] = useState<Loan | null>(null);

  const loadData = async () => {
    try {
      const data = await LoanRepo.getAll();
      data.sort((a, b) => {
        if ((a as any).status === 'paid' && (b as any).status !== 'paid') return 1;
        if ((a as any).status !== 'paid' && (b as any).status === 'paid') return -1;
        return ((a as any).nextPaymentDate || '').localeCompare((b as any).nextPaymentDate || '');
      });
      setLoans(data);
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

  const openForm = (loan: Loan | null = null) => {
    setEditingData(loan);
    setName(loan?.name || '');
    setTotalAmount(loan?.totalAmount?.toString() || '');
    setInterestRate(loan?.interestRate?.toString() || '');
    setInstallments(loan?.installments?.toString() || '');
    setPaidInstallments(loan?.paidInstallments?.toString() || '0');
    setNextPaymentDate((loan as any)?.nextPaymentDate || '');
    
    setDetailOpen(false);
    setFormOpen(true);
  };

  const handleSaveLoan = async () => {
    const tAmount = parseFloat(totalAmount.replace(',', '.'));
    const iRate = parseFloat(interestRate.replace(',', '.')) || 0;
    const inst = parseInt(installments);

    if (!name.trim() || isNaN(tAmount) || tAmount <= 0 || isNaN(inst) || inst <= 0) {
      Alert.alert('Error', 'Completa los campos obligatorios correctamente');
      return;
    }

    const totalWithInterest = tAmount * (1 + iRate / 100);
    const installmentAmount = totalWithInterest / inst;
    const paidInst = parseInt(paidInstallments) || 0;

    const payload = {
      name: name.trim(),
      totalAmount: tAmount,
      interestRate: iRate,
      installments: inst,
      paidInstallments: paidInst,
      monthlyQuota: Math.round(installmentAmount * 100) / 100,
      
      // Dynamic fields preserved explicitly to match legacy logic 
      totalWithInterest,
      nextPaymentDate,
      status: paidInst >= inst ? 'paid' : 'active',
    };

    try {
      if (editingData) {
        await LoanRepo.update({ ...editingData, ...payload } as any);
      } else {
        await LoanRepo.add(payload as any);
      }
      setFormOpen(false);
      loadData();
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo guardar el préstamo');
    }
  };

  const handleDelete = () => {
    if (!selectedLoan) return;
    Alert.alert('Eliminar', '¿Estás seguro de eliminar este préstamo?', [
      { text: 'Cancelar', style: 'cancel' },
      { 
        text: 'Eliminar', style: 'destructive', 
        onPress: async () => {
          await LoanRepo.delete(selectedLoan.id);
          setDetailOpen(false);
          loadData();
        } 
      }
    ]);
  };

  const handlePayInstallment = async () => {
    if (!selectedLoan) return;
    
    const newPaidCount = selectedLoan.paidInstallments + 1;
    const isNowPaid = newPaidCount >= selectedLoan.installments;

    try {
      const updatedLoan = {
        ...selectedLoan,
        paidInstallments: newPaidCount,
        status: isNowPaid ? 'paid' : 'active'
      } as any;

      await LoanRepo.update(updatedLoan);

      await ExpenseRepo.add({
        amount: Math.round(selectedLoan.monthlyQuota * 100) / 100,
        category: 'Préstamo',
        detail: `Cuota ${newPaidCount}/${selectedLoan.installments} — ${selectedLoan.name}`,
        paymentMethod: 'Débito', // Fallback, could prompt for an account if desired
        date: getToday(),
        ...( { isLoanPayment: true, loanId: selectedLoan.id, time: new Date().toTimeString().slice(0, 5), tags: ['préstamo', 'cuota'] } as any )
      });

      setDetailOpen(false);
      Alert.alert('Éxito', `Cuota de ${formatCurrency(selectedLoan.monthlyQuota)} descontada del balance general`);
      loadData();

    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo registrar la cuota');
    }
  };

  // Preview computations
  const pTotalAm = parseFloat(totalAmount.replace(',', '.')) || 0;
  const pIntR = parseFloat(interestRate.replace(',', '.')) || 0;
  const pInst = parseInt(installments) || 0;
  
  const pTotalWithInterest = pTotalAm > 0 && pInst > 0 ? pTotalAm * (1 + pIntR / 100) : 0;
  const pInstallmentAm = pInst > 0 ? pTotalWithInterest / pInst : 0;

  // Stats
  const activeLoans = loans.filter(l => (l as any).status !== 'paid');
  const totalDebt = activeLoans.reduce((s, l) => s + ((l.installments - l.paidInstallments) * l.monthlyQuota), 0);

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      
      {/* Header */}
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.onSurface }]}>Préstamos</Text>
      </View>

      <View style={{ paddingHorizontal: 16 }}>
        <Card elevated>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <View>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Deuda pendiente</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.tertiary }}>{formatCurrency(totalDebt)}</Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Préstamos activos</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.onSurface }}>{activeLoans.length}</Text>
            </View>
          </View>
        </Card>
      </View>

      <ScrollView 
        contentContainerStyle={{ padding: 16, paddingBottom: 100 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      >
        {loans.length === 0 ? (
          <View style={styles.emptyState}>
            <Ionicons name="business-outline" size={48} color={colors.outline} />
            <Text style={[styles.emptyTitle, { color: colors.onSurfaceVariant }]}>Sin préstamos</Text>
            <Text style={[styles.emptySubtitle, { color: colors.onSurfaceVariant }]}>Toca el botón + para registrar uno</Text>
          </View>
        ) : (
          loans.map(loan => {
            const progress = loan.installments > 0 ? (loan.paidInstallments / loan.installments) : 0;
            const remaining = (loan.installments - loan.paidInstallments) * loan.monthlyQuota;
            
            const nextPDate = (loan as any).nextPaymentDate;
            let days = nextPDate ? daysUntil(nextPDate) : null;
            const isPaid = (loan as any).status === 'paid';

            let statusColor = '#10b981'; // Green for success
            let statusText = 'Al día';
            
            if (isPaid) { 
              statusText = 'Pagado'; 
            } else if (days !== null && days < 0) { 
              statusColor = colors.error; 
              statusText = 'Vencido'; 
            } else if (days !== null && days <= 5) { 
              statusColor = colors.tertiary; 
              statusText = 'Próximo a vencer'; 
            }

            return (
              <TouchableOpacity
                key={loan.id}
                onPress={() => { setSelectedLoan(loan); setDetailOpen(true); }}
              >
                <Card style={[styles.cardOverrides, { opacity: isPaid ? 0.6 : 1 }]}>
                  
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <View style={{ flex: 1 }}>
                      <Text style={[styles.loanName, { color: colors.onSurface }]} numberOfLines={1}>{loan.name}</Text>
                      <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
                        {loan.paidInstallments}/{loan.installments} cuotas
                      </Text>
                    </View>
                    <View style={[styles.badge, { backgroundColor: statusColor + '20' }]}>
                      <Text style={[styles.badgeText, { color: statusColor }]}>{statusText}</Text>
                    </View>
                  </View>

                  <ProgressBar 
                    progress={progress} 
                    colorVariant={progress >= 1 ? 'success' : progress > 0.5 ? 'warning' : 'primary'} 
                    style={{ marginBottom: 12 }}
                  />

                  <View style={styles.grid2}>
                    <View>
                      <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cuota:</Text>
                      <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.onSurface }}>{formatCurrency(loan.monthlyQuota)}</Text>
                    </View>
                    <View style={{ alignItems: 'flex-end' }}>
                      <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Restante:</Text>
                      <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.error }}>{formatCurrency(remaining)}</Text>
                    </View>
                    {(loan as any).totalWithInterest != null && (
                      <View>
                        <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total c/interés:</Text>
                        <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.onSurface }}>{formatCurrency((loan as any).totalWithInterest)}</Text>
                      </View>
                    )}
                    {nextPDate && !isPaid && (
                      <View style={{ alignItems: 'flex-end' }}>
                        <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Próximo pago:</Text>
                        <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.onSurface }}>{formatDate(nextPDate)}</Text>
                      </View>
                    )}
                  </View>
                </Card>
              </TouchableOpacity>
            )
          })
        )}
      </ScrollView>

      {/* FAB */}
      <TouchableOpacity 
        style={[styles.fab, { backgroundColor: colors.tertiary }]} 
        activeOpacity={0.8}
        onPress={() => openForm(null)}
      >
        <Ionicons name="add" size={32} color={colors.onTertiary} />
      </TouchableOpacity>


      {/* ==== DETAILS MODAL ==== */}
      <BottomSheet visible={isDetailOpen} onClose={() => setDetailOpen(false)} title={selectedLoan?.name || 'Detalle'}>
        {selectedLoan && (
          <View>
            <View style={{ alignItems: 'center', marginBottom: 24 }}>
              <Text style={{ color: colors.onSurfaceVariant }}>Monto Original</Text>
              <Text style={{ fontSize: 32, fontWeight: 'bold', color: colors.primary }}>
                {formatCurrency(selectedLoan.totalAmount)}
              </Text>
            </View>

            <View style={[styles.detailGrid, { borderTopColor: colors.outlineVariant, borderTopWidth: 1, paddingTop: 16 }]}>
              <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total c/interés</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatCurrency((selectedLoan as any).totalWithInterest || selectedLoan.totalAmount)}</Text></View>
              <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Tasa interés</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedLoan.interestRate || 0}%</Text></View>
              <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cuota mensual</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatCurrency(selectedLoan.monthlyQuota)}</Text></View>
              <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Progreso</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{selectedLoan.paidInstallments}/{selectedLoan.installments} cuotas</Text></View>
              <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Monto restante</Text><Text style={{ fontSize: 16, color: colors.error }}>{formatCurrency((selectedLoan.installments - selectedLoan.paidInstallments) * selectedLoan.monthlyQuota)}</Text></View>
              {(selectedLoan as any).nextPaymentDate && (
                <View style={styles.detailItem}><Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Próximo pago</Text><Text style={{ fontSize: 16, color: colors.onSurface }}>{formatDate((selectedLoan as any).nextPaymentDate)}</Text></View>
              )}
            </View>

            <View style={{ marginVertical: 24 }}>
               <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, textAlign: 'center' }}>Progreso de pago</Text>
               <ProgressBar progress={selectedLoan.installments > 0 ? selectedLoan.paidInstallments / selectedLoan.installments : 0} colorVariant="success" />
               <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginTop: 8, textAlign: 'center' }}>
                 {selectedLoan.installments > 0 ? ((selectedLoan.paidInstallments / selectedLoan.installments) * 100).toFixed(1) : 0}% completado
               </Text>
            </View>

            {(selectedLoan as any).status !== 'paid' && (
              <Button 
                title="Registrar pago de cuota" 
                icon="cash-outline" 
                onPress={handlePayInstallment} 
                style={{ marginBottom: 16 }}
              />
            )}
            
            <View style={{ flexDirection: 'row', gap: 12 }}>
              <Button style={{ flex: 1 }} variant="outlined" title="Editar" icon="pencil" onPress={() => openForm(selectedLoan)} />
              <Button style={{ flex: 1 }} variant="danger" title="Eliminar" icon="trash" onPress={handleDelete} />
            </View>
          </View>
        )}
      </BottomSheet>


      {/* ==== FORM MODAL ==== */}
      <BottomSheet visible={isFormOpen} onClose={() => setFormOpen(false)} title={editingData ? 'Editar préstamo' : 'Nuevo préstamo'}>
        <View style={{ gap: 16 }}>
          <TextField label="Nombre / Descripción *" value={name} onChangeText={setName} />
          
          <TextField 
            label="Monto total original *" 
            keyboardType="decimal-pad" 
            value={totalAmount} 
            onChangeText={setTotalAmount} 
          />

          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}>
              <TextField label="Interés (%)" keyboardType="decimal-pad" value={interestRate} onChangeText={setInterestRate} />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="N° Cuotas *" keyboardType="number-pad" value={installments} onChangeText={setInstallments} />
            </View>
          </View>

          {pTotalWithInterest > 0 && (
            <Card style={{ backgroundColor: colors.surfaceContainerHighest }}>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Total a pagar con interés</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.primary, marginBottom: 8 }}>{formatCurrency(pTotalWithInterest)}</Text>
              
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cuota mensual estimada</Text>
              <Text style={{ fontSize: 18, fontWeight: 'bold', color: colors.onSurface }}>{formatCurrency(pInstallmentAm)}</Text>
            </Card>
          )}

          <TextField 
            label="Cuotas ya pagadas" 
            keyboardType="number-pad" 
            value={paidInstallments} 
            onChangeText={setPaidInstallments} 
          />

          <TextField 
            label="Fecha máximo de próximo pago" 
            placeholder="YYYY-MM-DD" 
            value={nextPaymentDate} 
            onChangeText={setNextPaymentDate} 
          />

          <Button title={editingData ? 'Actualizar' : 'Guardar préstamo'} onPress={handleSaveLoan} />
        </View>
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
  emptyState: { alignItems: 'center', paddingVertical: 60 },
  emptyTitle: { fontSize: 18, fontWeight: 'bold', marginTop: 16 },
  emptySubtitle: { fontSize: 14, marginTop: 4 },
  
  cardOverrides: { padding: 16, marginBottom: 12 },
  loanName: { fontSize: 16, fontWeight: '600' },
  badge: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 12 },
  badgeText: { fontSize: 12, fontWeight: 'bold' },
  grid2: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, rowGap: 8, justifyContent: 'space-between' },

  fab: {
    position: 'absolute', bottom: 24, right: 24,
    width: 56, height: 56, borderRadius: 28,
    alignItems: 'center', justifyContent: 'center',
    elevation: 4, shadowColor: '#000', shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4,
  },

  detailGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 16, rowGap: 24 },
  detailItem: { width: '45%' },
});
