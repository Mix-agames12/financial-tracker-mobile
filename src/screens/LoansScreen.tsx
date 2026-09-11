import React, { useState, useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';

import { useTheme } from '../theme/ThemeContext';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { TextField } from '../components/TextField';
import { BottomSheet } from '../components/BottomSheet';
import { ProgressBar } from '../components/ProgressBar';
import { ToastManager } from '../components/ActionFeedback';
import { Chip } from '../components/Chip';
import { DatePickerModal } from '../components/DatePickerModal';

import { formatCurrency, formatDate, formatDateShort, daysUntil, getToday, roundMoney, toAmountInput } from '../utils/formatters';
import { syncAfterDataChange } from '../utils/dataSync';
import {
  applyLoanPaymentToCard, availabilityLevel, CardCycleSummary, getCardCycleSummary, releaseLoanFromCard, syncLoanEditWithCard,
} from '../utils/cardPurchases';
import { LoanRepo, ExpenseRepo, AccountRepo, CreditCardRepo, getAccountBalances, getTotalBalance, SettingsRepo } from '../db/storage';
import { Loan, Account, CreditCard, Expense, TaxesConfig } from '../types';

export default function LoansScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  const [refreshing, setRefreshing] = useState(false);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountBalances, setAccountBalances] = useState<Record<string, number>>({});
  const [paymentAccount, setPaymentAccount] = useState('');
  const [taxesConfig, setTaxesConfig] = useState<TaxesConfig | null>(null);

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
  const [isDatePickerOpen, setDatePickerOpen] = useState(false);

  // Selected for Details
  const [selectedLoan, setSelectedLoan] = useState<Loan | null>(null);

  // Tarjetas: sus compras se agrupan y se abren desde la vista de cada tarjeta.
  const [creditCards, setCreditCards] = useState<CreditCard[]>([]);
  const [expensesById, setExpensesById] = useState<Map<string, Expense>>(new Map());
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null);

  const loadData = async () => {
    try {
      const data = await LoanRepo.getAll();
      data.sort((a, b) => {
        if ((a as any).status === 'paid' && (b as any).status !== 'paid') return 1;
        if ((a as any).status !== 'paid' && (b as any).status === 'paid') return -1;
        return ((a as any).nextPaymentDate || '').localeCompare((b as any).nextPaymentDate || '');
      });
      setLoans(data);
      setAccounts(await AccountRepo.getAll());
      setCreditCards(await CreditCardRepo.getAll());
      setExpensesById(new Map((await ExpenseRepo.getAll()).map((e) => [e.id, e] as const)));
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

  const openForm = (loan: Loan | null = null) => {
    setEditingData(loan);
    setName(loan?.name || '');
    setTotalAmount(toAmountInput(loan?.totalAmount));
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

    if (paidInst < 0 || paidInst > inst) {
      Alert.alert('Error', 'Las cuotas pagadas no pueden ser negativas ni superan el total de cuotas');
      return;
    }

    const payload = {
      name: name.trim(),
      totalAmount: tAmount,
      interestRate: iRate,
      installments: inst,
      paidInstallments: paidInst,
      monthlyQuota: Math.round(installmentAmount * 100) / 100,
      
      // Dynamic fields preserved explicitly to match legacy logic 
      totalWithInterest: roundMoney(totalWithInterest),
      nextPaymentDate,
      status: paidInst >= inst ? 'paid' : 'active',
    };

    try {
      if (editingData) {
        const edited = { ...editingData, ...payload } as Loan;
        // Préstamo de una compra con tarjeta: la tarjeta pasa a reflejar lo que queda por pagar.
        const cardOutstanding = await syncLoanEditWithCard(editingData, edited);
        await LoanRepo.update(cardOutstanding === undefined ? edited : { ...edited, cardOutstanding });
      } else {
        await LoanRepo.add(payload as any);
      }
      setFormOpen(false);
      ToastManager.show('Préstamo guardado con éxito');
      loadData();
      syncAfterDataChange();
    } catch (e) {
      console.error(e);
      Alert.alert('Error', 'No se pudo guardar el préstamo');
    }
  };

  const handleDelete = () => {
    if (!selectedLoan) return;
    const message = selectedLoan.cardId
      ? '¿Estás seguro de eliminar este préstamo? Lo que siga pendiente de la compra se quitará del saldo de la tarjeta.'
      : '¿Estás seguro de eliminar este préstamo?';
    Alert.alert('Eliminar', message, [
      { text: 'Cancelar', style: 'cancel' },
      { 
        text: 'Eliminar', style: 'destructive', 
        onPress: async () => {
          await releaseLoanFromCard(selectedLoan);
          await LoanRepo.delete(selectedLoan.id);
          // Desde una tarjeta se vuelve a su lista de compras.
          if (selectedCardId) setSelectedLoan(null); else setDetailOpen(false);
          loadData();
          syncAfterDataChange();
        } 
      }
    ]);
  };

  const handlePayInstallment = async () => {
    if (!selectedLoan) return;
    
    if (!paymentAccount) return Alert.alert('Error', 'Debes seleccionar una cuenta origen para pagar la cuota');
    const acc = accounts.find(a => a.name === paymentAccount);
    if (!acc) return Alert.alert('Error', 'Cuenta inválida');

    const amount = Math.round(selectedLoan.monthlyQuota * 100) / 100;

    let taxComision = 0;
    let taxIva = 0;
    let taxIsd = 0;
    let appliesTaxes = false;

    if (taxesConfig?.enabled && taxesConfig.applyTo.includes('Préstamos')) {
      appliesTaxes = true;
      taxComision = (amount * taxesConfig.comisionRate) / 100;
      taxIva = (taxComision * taxesConfig.ivaRate) / 100;
      taxIsd = (amount * taxesConfig.isdRate) / 100;
    }
    const totalTax = taxComision + taxIva + taxIsd;
    const totalCharge = amount + totalTax;

    const tb = await getTotalBalance();
    if (tb.balance < totalCharge) {
      return Alert.alert('Balance Negativo', `No se permite un balance total negativo. Disponible global: ${formatCurrency(tb.balance)}`);
    }

    if ((accountBalances[acc.id] || 0) < totalCharge) {
      return Alert.alert('Saldo Insuficiente', `La cuenta ${paymentAccount} no tiene fondos suficientes para cubrir la cuota de ${formatCurrency(totalCharge)}. Disponible: ${formatCurrency(accountBalances[acc.id] || 0)}`);
    }
    
    const newPaidCount = selectedLoan.paidInstallments + 1;
    const isNowPaid = newPaidCount >= selectedLoan.installments;

    const advancePaymentDate = (dateStr: string): string => {
      if (!dateStr) return '';
      const [y, m, d] = dateStr.split('-').map(Number);
      if (!y || !m || !d) return '';
      let newM = m + 1;
      let newY = y;
      if (newM > 12) { newM = 1; newY++; }
      const maxDays = new Date(newY, newM, 0).getDate();
      const newD = Math.min(d, maxDays);
      return `${newY}-${String(newM).padStart(2, '0')}-${String(newD).padStart(2, '0')}`;
    };

    const newNextDate = advancePaymentDate((selectedLoan as any).nextPaymentDate || '');

    try {
      // Compra con tarjeta: la cuota pagada deja de estar pendiente en la tarjeta.
      const cardOutstanding = await applyLoanPaymentToCard(selectedLoan, amount, isNowPaid);
      const updatedLoan = {
        ...selectedLoan,
        paidInstallments: newPaidCount,
        status: isNowPaid ? 'paid' : 'active',
        nextPaymentDate: isNowPaid ? '' : ((selectedLoan as any).nextPaymentDate ? newNextDate : ''),
        ...(cardOutstanding === undefined ? {} : { cardOutstanding }),
      } as any;

      await LoanRepo.update(updatedLoan);

      await ExpenseRepo.add({
        amount,
        category: 'Préstamo',
        detail: `Cuota ${newPaidCount}/${selectedLoan.installments} — ${selectedLoan.name}`,
        accountName: paymentAccount,
        paymentMethod: 'Débito',
        date: getToday(),
        ...( { isLoanPayment: true, loanId: selectedLoan.id, time: new Date().toTimeString().slice(0, 5), tags: ['préstamo', 'cuota'] } as any )
      });

      if (appliesTaxes && totalTax > 0) {
        await ExpenseRepo.add({
          amount: Math.round(totalTax * 100) / 100,
          category: 'Otros',
          detail: `Cuota ${newPaidCount}/${selectedLoan.installments} (Retenciones) — ${selectedLoan.name}`,
          accountName: paymentAccount,
          paymentMethod: 'Débito',
          date: getToday(),
          ...( { time: new Date().toTimeString().slice(0, 5), tags: ['impuestos', 'préstamo'] } as any )
        });
      }

      if (selectedCardId) setSelectedLoan(null); else setDetailOpen(false);
      Alert.alert('Éxito', selectedLoan.cardId
        ? `Cuota de ${formatCurrency(selectedLoan.monthlyQuota)} descontada del balance general y del saldo de la tarjeta`
        : `Cuota de ${formatCurrency(selectedLoan.monthlyQuota)} descontada del balance general`);
      loadData();
      syncAfterDataChange();

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

  // Compras con tarjeta: una tarjeta por TC; el resto de préstamos se lista como siempre.
  const cardIds = new Set(creditCards.map(c => c.id));
  const cardGroups = creditCards
    .map(card => ({ card, summary: getCardCycleSummary(card, loans, expensesById) }))
    .filter(group => group.summary.purchases.length > 0);
  const otherLoans = loans.filter(l => !l.cardId || !cardIds.has(l.cardId));
  const activeCount = otherLoans.filter(l => (l as any).status !== 'paid').length
    + cardGroups.filter(group => group.summary.totalPending > 0).length;
  const selectedCard = creditCards.find(c => c.id === selectedCardId) || null;
  const selectedSummary = selectedCard ? getCardCycleSummary(selectedCard, loans, expensesById) : null;

  const openLoanDetail = (loan: Loan) => {
    setSelectedCardId(null);
    setSelectedLoan(loan);
    setDetailOpen(true);
  };

  const openCardDetail = (cardId: string) => {
    setSelectedLoan(null);
    setSelectedCardId(cardId);
    setDetailOpen(true);
  };

  const renderCardGroup = (card: CreditCard, summary: CardCycleSummary) => {
    const pendingCount = summary.purchases.filter(p => !p.isPaid).length;
    const dueDays = summary.nextDueDate ? daysUntil(summary.nextDueDate) : null;
    let statusColor = colors.secondary;
    let statusText = summary.totalPending > 0 ? 'Al día' : 'Sin deuda';
    if (summary.hasOverdue) {
      statusColor = colors.error;
      statusText = 'Vencido';
    } else if (summary.dueAmount > 0 && dueDays !== null && dueDays <= 5) {
      statusColor = colors.tertiary;
      statusText = 'Próximo a vencer';
    }
    const availableRatio = summary.available !== null ? summary.available / card.creditLimit : null;

    return (
      <TouchableOpacity key={card.id} onPress={() => openCardDetail(card.id)}>
        <Card style={[styles.cardOverrides, { opacity: summary.totalPending > 0 ? 1 : 0.6 }]}>
          <View style={styles.cardHeaderRow}>
            <View style={{ flex: 1 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                <Ionicons name="card" size={18} color={colors.primary} />
                <Text style={[styles.loanName, { color: colors.onSurface, flexShrink: 1 }]} numberOfLines={1}>{card.name}</Text>
              </View>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
                {card.bankName} · {pendingCount === 1 ? '1 compra pendiente' : `${pendingCount} compras pendientes`}
              </Text>
            </View>
            <View style={[styles.badge, { backgroundColor: statusColor + '20' }]}>
              <Text style={[styles.badgeText, { color: statusColor }]}>{statusText}</Text>
            </View>
          </View>

          <View style={styles.grid2}>
            <View>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
                {summary.nextDueDate ? `Por pagar el ${formatDateShort(summary.nextDueDate)}` : 'Por pagar'}
              </Text>
              <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.error }}>{formatCurrency(summary.dueAmount)}</Text>
            </View>
            <View style={{ alignItems: 'flex-end' }}>
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Deuda activa</Text>
              <Text style={{ fontSize: 14, fontWeight: 'bold', color: colors.tertiary }}>{formatCurrency(summary.activeDebt)}</Text>
            </View>
          </View>

          {availableRatio !== null && (
            <View style={{ marginTop: 12, gap: 4 }}>
              <ProgressBar progress={availableRatio} colorVariant={availabilityLevel(availableRatio)} />
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text style={{ fontSize: 11, color: colors.onSurfaceVariant }}>Cupo disponible: {formatCurrency(summary.available ?? 0)}</Text>
                <Text style={{ fontSize: 11, color: colors.onSurfaceVariant }}>Límite: {formatCurrency(card.creditLimit)}</Text>
              </View>
            </View>
          )}
        </Card>
      </TouchableOpacity>
    );
  };

  const renderCardDetail = (card: CreditCard, summary: CardCycleSummary) => (
    <View>
      <View style={[styles.detailGrid, { borderTopColor: colors.outlineVariant, borderTopWidth: 1, paddingTop: 16 }]}>
        <View style={styles.detailItem}>
          <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>
            {summary.nextDueDate ? `Por pagar el ${formatDateShort(summary.nextDueDate)}` : 'Por pagar'}
          </Text>
          <Text style={{ fontSize: 16, fontWeight: 'bold', color: colors.error }}>{formatCurrency(summary.dueAmount)}</Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Deuda activa</Text>
          <Text style={{ fontSize: 16, fontWeight: 'bold', color: colors.tertiary }}>{formatCurrency(summary.activeDebt)}</Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Último corte</Text>
          <Text style={{ fontSize: 16, color: colors.onSurface }}>{summary.lastCutOff ? formatDate(summary.lastCutOff) : '-'}</Text>
        </View>
        <View style={styles.detailItem}>
          <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Cupo disponible</Text>
          <Text style={{ fontSize: 16, color: colors.onSurface }}>{summary.available !== null ? formatCurrency(summary.available) : '-'}</Text>
        </View>
      </View>
      <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginTop: 16 }}>
        "Por pagar" son las compras hasta el último corte; "Deuda activa", las posteriores al corte y las cuotas siguientes.
      </Text>

      <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant, marginTop: 24 }]}>COMPRAS</Text>
      {summary.purchases.length === 0 ? (
        <Text style={{ color: colors.onSurfaceVariant, textAlign: 'center', paddingVertical: 16 }}>Sin compras registradas</Text>
      ) : summary.purchases.map(p => (
        <TouchableOpacity
          key={p.loan.id}
          style={[styles.purchaseRow, { borderBottomColor: colors.outlineVariant, opacity: p.isPaid ? 0.6 : 1 }]}
          onPress={() => setSelectedLoan(p.loan)}
        >
          <View style={{ flex: 1, marginRight: 8 }}>
            <Text style={{ fontSize: 15, fontWeight: '500', color: colors.onSurface }} numberOfLines={1}>{p.title}</Text>
            <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }} numberOfLines={1}>
              {[
                p.expense?.date ? formatDate(p.expense.date) : null,
                p.expense?.category,
                `${p.loan.paidInstallments}/${p.loan.installments} ${p.loan.installments === 1 ? 'cuota' : 'cuotas'}`,
              ].filter(Boolean).join(' · ')}
            </Text>
          </View>
          <View style={{ alignItems: 'flex-end', marginRight: 4 }}>
            <Text style={{ fontSize: 15, fontWeight: 'bold', color: p.isPaid ? colors.secondary : colors.error }}>
              {p.isPaid ? 'Pagada' : formatCurrency(p.pending)}
            </Text>
            {!p.isPaid && p.loan.nextPaymentDate ? (
              <Text style={{ fontSize: 11, color: colors.onSurfaceVariant }}>Vence {formatDateShort(p.loan.nextPaymentDate)}</Text>
            ) : null}
          </View>
          <Ionicons name="chevron-forward" size={18} color={colors.onSurfaceVariant} />
        </TouchableOpacity>
      ))}
    </View>
  );

  return (
    <View style={[styles.container, { backgroundColor: colors.background, paddingTop: insets.top }]}>
      
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
              <Text style={{ fontSize: 12, color: colors.onSurfaceVariant }}>Activos</Text>
              <Text style={{ fontSize: 24, fontWeight: 'bold', color: colors.onSurface }}>{activeCount}</Text>
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
          <>
            {cardGroups.length > 0 && (
              <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>TARJETAS DE CRÉDITO</Text>
            )}
            {cardGroups.map(({ card, summary }) => renderCardGroup(card, summary))}
            {cardGroups.length > 0 && otherLoans.length > 0 && (
              <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>PRÉSTAMOS</Text>
            )}
          {otherLoans.map(loan => {
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
                onPress={() => openLoanDetail(loan)}
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
          })}
          </>
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
      <BottomSheet
        visible={isDetailOpen}
        onClose={() => setDetailOpen(false)}
        title={selectedLoan ? selectedLoan.name : selectedCard ? `${selectedCard.name} · ${selectedCard.bankName}` : 'Detalle'}
      >
        {selectedLoan ? (
          <View>
            {selectedCard && (
              <TouchableOpacity style={styles.backRow} onPress={() => setSelectedLoan(null)}>
                <Ionicons name="chevron-back" size={18} color={colors.primary} />
                <Text style={{ color: colors.primary, fontWeight: '600' }}>Volver a {selectedCard.name}</Text>
              </TouchableOpacity>
            )}
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

            {(selectedLoan as any).status !== 'paid' && accounts.length > 0 && (
              <View style={{ marginBottom: 16 }}>
                <Text style={{ fontSize: 12, color: colors.onSurfaceVariant, marginBottom: 8, fontFamily: 'sans-serif-medium' }}>Cuenta origen para el pago *</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
                  {accounts.map(a => (
                    <Chip key={a.name} label={`${a.name} (${formatCurrency(accountBalances[a.id] || 0)})`} active={paymentAccount === a.name} onPress={() => setPaymentAccount(a.name)} />
                  ))}
                </ScrollView>
              </View>
            )}

            {(() => {
              if (!selectedLoan || (selectedLoan as any).status === 'paid') return null;
              
              const amountParsed = Math.round(selectedLoan.monthlyQuota * 100) / 100;
              let taxComision = 0;
              let taxIva = 0;
              let taxIsd = 0;
              let appliesTaxes = false;

              if (taxesConfig?.enabled && taxesConfig.applyTo.includes('Préstamos')) {
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
        ) : selectedCard && selectedSummary ? (
          renderCardDetail(selectedCard, selectedSummary)
        ) : null}
      </BottomSheet>


      {/* ==== FORM MODAL ==== */}
      <BottomSheet visible={isFormOpen} onClose={() => setFormOpen(false)} title={editingData ? 'Editar préstamo' : 'Nuevo préstamo'}>
        <View style={{ gap: 16 }}>
          <TextField label="Nombre / Descripción *" placeholder="Ej. Préstamo Auto" value={name} onChangeText={setName} />
          
          <TextField 
            label="Monto total original *" 
            placeholder="Ej. 15000"
            keyboardType="decimal-pad" 
            value={totalAmount} 
            onChangeText={setTotalAmount} 
          />

          <View style={{ flexDirection: 'row', gap: 16 }}>
            <View style={{ flex: 1 }}>
              <TextField label="Interés (%)" placeholder="Ej. 10.5" keyboardType="decimal-pad" value={interestRate} onChangeText={setInterestRate} />
            </View>
            <View style={{ flex: 1 }}>
              <TextField label="N° Cuotas *" placeholder="Ej. 24" keyboardType="number-pad" value={installments} onChangeText={setInstallments} />
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
            placeholder="Ej. 5"
            keyboardType="number-pad" 
            value={paidInstallments} 
            onChangeText={setPaidInstallments} 
          />

          <TouchableOpacity onPress={() => setDatePickerOpen(true)}>
            <View pointerEvents="none">
              <TextField 
                label="Fecha máxima de próximo pago (Opcional)" 
                placeholder="YYYY-MM-DD" 
                value={nextPaymentDate} 
                onChangeText={() => {}} 
              />
            </View>
          </TouchableOpacity>

          <View style={{ flexDirection: 'row', gap: 12 }}>
             <Button style={{ flex: 1 }} variant="outlined" title="Cancelar" onPress={() => setFormOpen(false)} />
             <Button style={{ flex: 1 }} title={editingData ? 'Actualizar' : 'Guardar préstamo'} onPress={handleSaveLoan} />
          </View>
        </View>
      </BottomSheet>

      <DatePickerModal 
        visible={isDatePickerOpen} 
        onClose={() => setDatePickerOpen(false)} 
        value={nextPaymentDate} 
        onSelect={setNextPaymentDate} 
      />

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
  sectionLabel: { fontSize: 12, fontWeight: 'bold', letterSpacing: 0.5, marginBottom: 8, marginTop: 4 },
  cardHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  purchaseRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 12, borderBottomWidth: 1 },
  backRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 12 },
});
