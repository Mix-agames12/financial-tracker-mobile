// Operaciones que ejecutan la lógica ORIGINAL de la app (TypeScript compilado) sobre un estado sembrado.
//
// Las funciones de src/utils y src/db se llaman tal cual. La lógica que en la app vive dentro de los
// componentes (handlers de guardado, pago de cuota, análisis...) se copia aquí casi literalmente
// (los Alert.alert se devuelven como { alert: [título, mensaje] }) para que la versión Kotlin se compare
// contra el comportamiento real del original. Cada op tiene su equivalente en GoldenRunner.kt.
'use strict';

const alertOf = (title, message) => ({ alert: [title, message] });
const nowHHmm = () => new Date().toTimeString().slice(0, 5);

// ---- Copia literal de ExpensesScreen.calculateCreditCardNextPaymentDate ----
function calculateCreditCardNextPaymentDate(expenseDateStr, cutOffDay, paymentDueDay) {
  if (!expenseDateStr || !cutOffDay || !paymentDueDay) return '';
  const expDate = new Date(expenseDateStr + 'T12:00:00Z');
  const expDay = expDate.getUTCDate();
  const expMonth = expDate.getUTCMonth();
  const expYear = expDate.getUTCFullYear();

  let dueMonth = expMonth + 1;
  let dueYear = expYear;

  if (expDay > cutOffDay) {
    dueMonth += 1;
  }

  if (dueMonth > 11) {
    dueMonth -= 12;
    dueYear += 1;
  }

  const mStr = String(dueMonth + 1).padStart(2, '0');
  const lastDayOfDueMonth = new Date(dueYear, dueMonth + 1, 0).getDate();
  const dStr = String(Math.min(paymentDueDay, lastDayOfDueMonth)).padStart(2, '0');
  return `${dueYear}-${mStr}-${dStr}`;
}

function taxBreakdown(taxesConfig, amount, enabledFor) {
  let taxComision = 0;
  let taxIva = 0;
  let taxIsd = 0;
  let appliesTaxes = false;
  if (taxesConfig?.enabled && enabledFor(taxesConfig)) {
    appliesTaxes = true;
    taxComision = (amount * taxesConfig.comisionRate) / 100;
    taxIva = (taxComision * taxesConfig.ivaRate) / 100;
    taxIsd = (amount * taxesConfig.isdRate) / 100;
  }
  const totalTax = taxComision + taxIva + taxIsd;
  return { appliesTaxes, taxComision, taxIva, taxIsd, totalTax, totalCharge: amount + totalTax };
}

const loanOf = async (ctx, a) => {
  if (a.loanId) return ctx.storage.LoanRepo.get(a.loanId);
  const loans = await ctx.storage.LoanRepo.getAll();
  return loans.find((l) => l.sourceExpenseId === a.loanOfExpense);
};

const cardById = async (ctx, id) => (id ? (await ctx.storage.CreditCardRepo.getAll()).find((c) => c.id === id) : undefined);

const ops = {
  // ---------- Altas directas en los repositorios ----------
  async addExpense(ctx, a) {
    const e = await ctx.storage.ExpenseRepo.add(a.payload);
    if (a.as) ctx.refs[a.as] = e.id;
    return null;
  },
  async addLoan(ctx, a) {
    const l = await ctx.storage.LoanRepo.add(a.payload);
    if (a.as) ctx.refs[a.as] = l.id;
    return null;
  },
  async addIncome(ctx, a) {
    const i = await ctx.storage.IncomeRepo.add(a.payload);
    if (a.as) ctx.refs[a.as] = i.id;
    return null;
  },

  // ---------- src/utils/cardPurchases.ts ----------
  async registerCardPurchase(ctx, a) {
    const expense = await ctx.storage.ExpenseRepo.get(a.expenseId);
    const card = await cardById(ctx, a.cardId);
    await ctx.cardPurchases.registerCardPurchase(expense, card, a.totalCharge, a.nextPaymentDate);
    return null;
  },
  async updateCardPurchase(ctx, a) {
    const before = await ctx.storage.ExpenseRepo.get(a.expenseId);
    const updated = await ctx.storage.ExpenseRepo.update({ id: a.expenseId, ...a.overrides });
    await ctx.cardPurchases.updateCardPurchase(before, updated, await cardById(ctx, a.cardId), a.nextPaymentDate || '');
    return null;
  },
  async deleteExpenseWithCardSync(ctx, a) {
    await ctx.cardPurchases.deleteExpenseWithCardSync(a.expenseId);
    return null;
  },
  async applyLoanPaymentToCard(ctx, a) {
    const loan = await loanOf(ctx, a);
    const r = await ctx.cardPurchases.applyLoanPaymentToCard(loan, a.quota, a.isFinalPayment);
    return r === undefined ? null : r;
  },
  async releaseLoanFromCard(ctx, a) {
    await ctx.cardPurchases.releaseLoanFromCard(await loanOf(ctx, a));
    return null;
  },
  async syncLoanEditWithCard(ctx, a) {
    const before = await loanOf(ctx, a);
    const after = { ...before, ...a.overrides };
    const r = await ctx.cardPurchases.syncLoanEditWithCard(before, after);
    return r === undefined ? null : r;
  },
  async linkLegacyCardLoans(ctx) {
    await ctx.cardPurchases.linkLegacyCardLoans();
    return null;
  },
  async getCardCycleSummary(ctx, a) {
    const [loans, expenses, cards] = await Promise.all([
      ctx.storage.LoanRepo.getAll(), ctx.storage.ExpenseRepo.getAll(), ctx.storage.CreditCardRepo.getAll(),
    ]);
    const card = cards.find((c) => c.id === a.cardId);
    const summary = ctx.cardPurchases.getCardCycleSummary(card, loans, new Map(expenses.map((e) => [e.id, e])));
    return {
      ...summary,
      purchases: summary.purchases.map((p) => ({ loanId: p.loan.id, expenseId: p.expense?.id ?? null, title: p.title, pending: p.pending, isPaid: p.isPaid })),
    };
  },
  async loanDerived(ctx, a) {
    const loan = await loanOf(ctx, a);
    return { outstanding: ctx.cardPurchases.loanCardOutstanding(loan), remainingDebt: ctx.cardPurchases.loanRemainingDebt(loan) };
  },

  // ---------- src/utils/paymentReminders.ts ----------
  async getUpcomingPayments(ctx, a) {
    return ctx.paymentReminders.getUpcomingPayments(a.horizonDays);
  },

  // ---------- src/db/storage.ts ----------
  async getTotalBalance(ctx) { return ctx.storage.getTotalBalance(); },
  async getAccountBalances(ctx) { return ctx.storage.getAccountBalances(); },
  async getMonthlySummary(ctx, a) { return ctx.storage.getMonthlySummary(a.year, a.month); },
  async seedDefaults(ctx) { await ctx.storage.CategoryRepo.seedDefaults(); return null; },
  async saveSettings(ctx, a) { await ctx.storage.SettingsRepo.save(a.patch); return null; },
  async getSettings(ctx) { return ctx.storage.SettingsRepo.get(); },
  async saveSalary(ctx, a) { await ctx.storage.SalaryRepo.save(a.data); return null; },
  async clearAllData(ctx) { await ctx.storage.clearAllData(); return null; },
  async hasAnyData(ctx) { return ctx.storage.hasAnyData(); },
  async exportAllData(ctx) {
    const data = await ctx.storage.exportAllData();
    data._exportDate = '<exportDate>';
    return data;
  },
  async importAllData(ctx, a) { await ctx.storage.importAllData(a.data); return null; },

  // ---------- src/utils/reportGenerator.ts ----------
  async generateCsv(ctx, a) {
    const data = await reportData(ctx, a);
    ctx.captured.files.length = 0;
    const shared = await ctx.reportGenerator.generateCSVReport(data);
    const file = ctx.captured.files[0];
    return { shared, fileName: file.uri.replace('document/', ''), content: file.content };
  },
  async reportPeriod(ctx, a) {
    return ctx.reportGenerator.getReportPeriod(a.period);
  },
  async pdfModel(ctx, a) {
    const data = await reportData(ctx, a);
    ctx.captured.html = null;
    const shared = await ctx.reportGenerator.generatePDFReport(data);
    return { shared, ...parsePdfHtml(ctx.captured.html) };
  },

  // ---------- Referencias a registros generados (para escenarios) ----------
  async refExpense(ctx, a) {
    const all = await ctx.storage.ExpenseRepo.getAll();
    const found = all.find((x) => Object.entries(a.match).every(([k, v]) => x[k] === v));
    ctx.refs[a.as] = found ? found.id : '<no-encontrado>';
    return null;
  },
  async refLoan(ctx, a) {
    const all = await ctx.storage.LoanRepo.getAll();
    const found = all.find((x) => Object.entries(a.match).every(([k, v]) => x[k] === v));
    ctx.refs[a.as] = found ? found.id : '<no-encontrado>';
    return null;
  },
  async updateCategoryByName(ctx, a) {
    const cats = await ctx.storage.CategoryRepo.getAll();
    const cat = cats.find((c) => c.name === a.name);
    await ctx.storage.CategoryRepo.update({ ...cat, ...a.patch });
    return null;
  },

  // ---------- Semántica de JavaScript que JsCompat.kt debe reproducir ----------
  async js(ctx, a) {
    return a.values.map((v) => {
      switch (a.kind) {
        case 'toFixed': return v.toFixed(a.digits);
        case 'toString': return String(v);
        case 'parseFloat': return Number.isNaN(parseFloat(v)) ? 'NaN' : parseFloat(v);
        case 'parseInt': return Number.isNaN(parseInt(v)) ? 'NaN' : parseInt(v);
        case 'round': return Math.round(v);
        default: throw new Error(`kind desconocido ${a.kind}`);
      }
    });
  },

  // ---------- Funciones puras de src/utils/formatters.ts ----------
  async fn(ctx, a) {
    const f = ctx.formatters[a.name];
    const args = (a.args || []).map((x) => (x && typeof x === 'object' && x.$date ? new Date(x.$date) : x));
    const r = f(...args);
    return r === undefined ? null : r;
  },

  // ---------- Handlers de pantalla (copia literal de la lógica, sin UI) ----------
  async saveExpense(ctx, a) {
    const { ExpenseRepo, CreditCardRepo, AccountRepo, getTotalBalance, getAccountBalances } = ctx.storage;
    const { registerCardPurchase, updateCardPurchase } = ctx.cardPurchases;
    const { formatCurrency, getToday, roundMoney } = ctx.formatters;
    const f = a.form;
    const editingId = a.editingId || null;
    const taxesConfig = a.taxesConfig || null;
    const creditCards = await CreditCardRepo.getAll();
    const accounts = await AccountRepo.getAll();
    const selectedExpense = editingId ? await ExpenseRepo.get(editingId) : null;

    const amount = parseFloat(f.amountStr.replace(',', '.'));
    if (isNaN(amount) || amount <= 0) return alertOf('Error', 'Ingresa un monto válido');
    if (!f.categoryType) return alertOf('Error', 'Selecciona una categoría');
    if (!f.detail.trim()) return alertOf('Error', 'Ingresa el detalle del gasto');

    const { appliesTaxes, totalTax, totalCharge } = taxBreakdown(
      taxesConfig, amount, (t) => t.applyTo.includes(`Gastos:${f.categoryType}`));

    const [, annualMonth, annualDay] = (f.recurringDate || f.dateStr || getToday()).split('-').map(Number);

    const payload = {
      amount: Math.round(amount * 100) / 100,
      category: f.categoryType,
      detail: f.detail.trim(),
      paymentMethod: f.paymentMethod,
      accountName: (f.paymentMethod === 'Débito' || f.paymentMethod === 'Efectivo') ? f.selectedDebit : '',
      cardName: f.paymentMethod === 'Crédito' ? f.selectedCredit : '',
      isDeferred: f.paymentMethod === 'Crédito' ? f.isDeferred : false,
      deferredMonths: f.paymentMethod === 'Crédito' && f.isDeferred ? parseInt(f.deferredMonths) || 0 : 0,
      date: f.dateStr || getToday(),
      ...({
        time: f.timeStr,
        tags: f.tags.split(',').map((t) => t.trim()).filter(Boolean),
        isRecurring: f.isRecurring,
        recurringFrequency: f.isRecurring ? f.recurringFrequency : undefined,
        recurringDay: !f.isRecurring
          ? undefined
          : f.recurringFrequency === 'specific'
            ? Math.min(31, Math.max(1, parseInt(f.recurringDay) || 1))
            : f.recurringFrequency === 'yearly' ? annualDay : undefined,
        recurringMonth: f.isRecurring && f.recurringFrequency === 'yearly' ? annualMonth : undefined,
        subscriptionType: f.categoryType === 'Suscripción' ? f.subscriptionType : '',
      }),
    };

    try {
      if (payload.paymentMethod === 'Crédito' && payload.cardName) {
        const card = creditCards.find((c) => c.name === payload.cardName);
        if (card && card.creditLimit > 0) {
          let balanceAfter = card.currentBalance + totalCharge;
          let available = card.creditLimit - card.currentBalance;
          if (editingId && selectedExpense?.paymentMethod === 'Crédito' && selectedExpense.cardName === card.name) {
            balanceAfter = card.currentBalance - selectedExpense.amount + payload.amount;
            available += selectedExpense.amount;
          }
          if (balanceAfter > card.creditLimit) {
            return alertOf('Cupo excedido', `El gasto supera el límite. Disponible estimado: ${formatCurrency(Math.max(0, available))}`);
          }
        }
      } else if ((payload.paymentMethod === 'Débito' || payload.paymentMethod === 'Efectivo') && payload.accountName) {
        const accBals = await getAccountBalances();
        const acc = accounts.find((x) => x.name === payload.accountName);
        if (acc) {
          let balanceAfter = accBals[acc.id] || 0;
          if (editingId && selectedExpense) balanceAfter += selectedExpense.amount;
          if (balanceAfter < totalCharge) {
            return alertOf('Saldo Insuficiente', `La cuenta ${payload.accountName} no tiene fondos suficientes. Disponible estimado: ${formatCurrency(balanceAfter)}`);
          }
        }
      }

      const chargeToDeductGlobal = editingId ? payload.amount : totalCharge;
      if (payload.paymentMethod !== 'Crédito') {
        const tb = await getTotalBalance();
        let globalAfter = tb.balance;
        if (editingId && selectedExpense) globalAfter += selectedExpense.amount;
        if (globalAfter < chargeToDeductGlobal) {
          return alertOf('Balance Negativo', `No se permite un balance total negativo. Disponible global estimado: ${formatCurrency(globalAfter)}`);
        }
      }

      const card = payload.paymentMethod === 'Crédito' && payload.cardName
        ? creditCards.find((c) => c.name === payload.cardName)
        : undefined;
      const nextPaymentDate = card
        ? calculateCreditCardNextPaymentDate(payload.date, card.cutOffDay, card.paymentDueDay)
        : '';

      if (editingId) {
        const before = await ExpenseRepo.get(editingId);
        const updated = await ExpenseRepo.update({ id: editingId, ...payload });
        if (before) await updateCardPurchase(before, updated, card, nextPaymentDate);
      } else {
        const expense = await ExpenseRepo.add(payload);
        if (a.as) ctx.refs[a.as] = expense.id;
        if (appliesTaxes && totalTax > 0) {
          await ExpenseRepo.add({
            ...payload,
            detail: `${payload.detail} (Impuestos/Comisiones)`,
            amount: roundMoney(totalTax),
            parentExpenseId: expense.id,
          });
        }
        if (card) {
          await registerCardPurchase(expense, card, totalCharge, nextPaymentDate);
        }
      }
      return { saved: true };
    } catch (e) {
      return alertOf('Error', 'No se pudo guardar el gasto');
    }
  },

  async payInstallment(ctx, a) {
    const { LoanRepo, ExpenseRepo, AccountRepo, getAccountBalances, getTotalBalance } = ctx.storage;
    const { applyLoanPaymentToCard } = ctx.cardPurchases;
    const { formatCurrency, getToday } = ctx.formatters;
    const selectedLoan = await loanOf(ctx, a);
    const accounts = await AccountRepo.getAll();
    const accountBalances = await getAccountBalances();
    const taxesConfig = a.taxesConfig || null;
    const paymentAccount = a.paymentAccount || '';

    if (!paymentAccount) return alertOf('Error', 'Debes seleccionar una cuenta origen para pagar la cuota');
    const acc = accounts.find((x) => x.name === paymentAccount);
    if (!acc) return alertOf('Error', 'Cuenta inválida');

    const amount = Math.round(selectedLoan.monthlyQuota * 100) / 100;
    const { appliesTaxes, totalTax, totalCharge } = taxBreakdown(taxesConfig, amount, (t) => t.applyTo.includes('Préstamos'));

    const tb = await getTotalBalance();
    if (tb.balance < totalCharge) {
      return alertOf('Balance Negativo', `No se permite un balance total negativo. Disponible global: ${formatCurrency(tb.balance)}`);
    }

    if ((accountBalances[acc.id] || 0) < totalCharge) {
      return alertOf('Saldo Insuficiente', `La cuenta ${paymentAccount} no tiene fondos suficientes para cubrir la cuota de ${formatCurrency(totalCharge)}. Disponible: ${formatCurrency(accountBalances[acc.id] || 0)}`);
    }

    const newPaidCount = selectedLoan.paidInstallments + 1;
    const isNowPaid = newPaidCount >= selectedLoan.installments;

    const advancePaymentDate = (dateStr) => {
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

    const newNextDate = advancePaymentDate(selectedLoan.nextPaymentDate || '');

    try {
      const cardOutstanding = await applyLoanPaymentToCard(selectedLoan, amount, isNowPaid);
      const updatedLoan = {
        ...selectedLoan,
        paidInstallments: newPaidCount,
        status: isNowPaid ? 'paid' : 'active',
        nextPaymentDate: isNowPaid ? '' : (selectedLoan.nextPaymentDate ? newNextDate : ''),
        ...(cardOutstanding === undefined ? {} : { cardOutstanding }),
      };

      await LoanRepo.update(updatedLoan);

      await ExpenseRepo.add({
        amount,
        category: 'Préstamo',
        detail: `Cuota ${newPaidCount}/${selectedLoan.installments} — ${selectedLoan.name}`,
        accountName: paymentAccount,
        paymentMethod: 'Débito',
        date: getToday(),
        ...({ isLoanPayment: true, loanId: selectedLoan.id, time: nowHHmm(), tags: ['préstamo', 'cuota'] }),
      });

      if (appliesTaxes && totalTax > 0) {
        await ExpenseRepo.add({
          amount: Math.round(totalTax * 100) / 100,
          category: 'Otros',
          detail: `Cuota ${newPaidCount}/${selectedLoan.installments} (Retenciones) — ${selectedLoan.name}`,
          accountName: paymentAccount,
          paymentMethod: 'Débito',
          date: getToday(),
          ...({ time: nowHHmm(), tags: ['impuestos', 'préstamo'] }),
        });
      }

      return alertOf('Éxito', selectedLoan.cardId
        ? `Cuota de ${formatCurrency(selectedLoan.monthlyQuota)} descontada del balance general y del saldo de la tarjeta`
        : `Cuota de ${formatCurrency(selectedLoan.monthlyQuota)} descontada del balance general`);
    } catch (e) {
      return alertOf('Error', 'No se pudo registrar la cuota');
    }
  },

  async saveLoan(ctx, a) {
    const { LoanRepo } = ctx.storage;
    const { syncLoanEditWithCard } = ctx.cardPurchases;
    const { roundMoney } = ctx.formatters;
    const f = a.form;
    const editingData = a.editingLoanId ? await LoanRepo.get(a.editingLoanId) : null;

    const tAmount = parseFloat(f.totalAmount.replace(',', '.'));
    const iRate = parseFloat(f.interestRate.replace(',', '.')) || 0;
    const inst = parseInt(f.installments);

    if (!f.name.trim() || isNaN(tAmount) || tAmount <= 0 || isNaN(inst) || inst <= 0) {
      return alertOf('Error', 'Completa los campos obligatorios correctamente');
    }

    const totalWithInterest = tAmount * (1 + iRate / 100);
    const installmentAmount = totalWithInterest / inst;
    const paidInst = parseInt(f.paidInstallments) || 0;

    if (paidInst < 0 || paidInst > inst) {
      return alertOf('Error', 'Las cuotas pagadas no pueden ser negativas ni superan el total de cuotas');
    }

    const payload = {
      name: f.name.trim(),
      totalAmount: tAmount,
      interestRate: iRate,
      installments: inst,
      paidInstallments: paidInst,
      monthlyQuota: Math.round(installmentAmount * 100) / 100,
      totalWithInterest: roundMoney(totalWithInterest),
      nextPaymentDate: f.nextPaymentDate,
      status: paidInst >= inst ? 'paid' : 'active',
    };

    try {
      if (editingData) {
        const edited = { ...editingData, ...payload };
        const cardOutstanding = await syncLoanEditWithCard(editingData, edited);
        await LoanRepo.update(cardOutstanding === undefined ? edited : { ...edited, cardOutstanding });
      } else {
        const l = await LoanRepo.add(payload);
        if (a.as) ctx.refs[a.as] = l.id;
      }
      return { saved: true };
    } catch (e) {
      return alertOf('Error', 'No se pudo guardar el préstamo');
    }
  },

  async deleteLoan(ctx, a) {
    const loan = await loanOf(ctx, a);
    await ctx.cardPurchases.releaseLoanFromCard(loan);
    await ctx.storage.LoanRepo.delete(loan.id);
    return null;
  },

  async saveCreditCard(ctx, a) {
    const { CreditCardRepo } = ctx.storage;
    const { roundMoney } = ctx.formatters;
    const f = a.form;
    const editingData = a.editingCardId ? (await CreditCardRepo.getAll()).find((c) => c.id === a.editingCardId) : null;
    if (!f.name.trim() || !f.bankName.trim()) return alertOf('Error', 'Nombre y banco son obligatorios');
    const limit = parseFloat(f.creditLimit) || 0;
    const available = parseFloat(f.available);
    if (limit <= 0) return alertOf('Error', 'Ingresa el límite de crédito de la tarjeta');
    if (isNaN(available) || available < 0) return alertOf('Error', 'Ingresa el cupo disponible (0 si ya no te queda cupo)');
    if (available > limit) return alertOf('Error', 'El cupo disponible no puede ser mayor al límite de crédito');
    const cutOff = parseInt(f.cutOffDay) || 0;
    const dueDay = parseInt(f.paymentDay) || 0;
    if (cutOff > 31 || dueDay > 31) return alertOf('Error', 'Los días de corte y de pago deben estar entre 1 y 31');

    const payload = {
      name: f.name.trim(), bankName: f.bankName.trim(),
      cutOffDay: cutOff,
      paymentDueDay: dueDay,
      creditLimit: roundMoney(limit),
      currentBalance: roundMoney(limit - available),
    };
    if (editingData) await CreditCardRepo.update({ ...editingData, ...payload });
    else { const c = await CreditCardRepo.add(payload); if (a.as) ctx.refs[a.as] = c.id; }
    return { saved: true };
  },

  async investmentDeposit(ctx, a) {
    const { ExpenseRepo, InvestmentRepo, AccountRepo, getAccountBalances } = ctx.storage;
    const { formatCurrency, getToday, roundMoney } = ctx.formatters;
    const inv = await InvestmentRepo.get(a.investmentId);
    const accounts = await AccountRepo.getAll();
    const accountBalances = await getAccountBalances();
    const taxesConfig = a.taxesConfig || null;
    const depositSourceAccount = a.sourceAccount || '';

    const acc = accounts.find((x) => x.name === depositSourceAccount);
    if (!acc) return alertOf('Error', 'Cuenta origen inválida');

    const amount = inv.monthlyDeposit;
    const { appliesTaxes, totalTax, totalCharge } = taxBreakdown(taxesConfig, amount, (t) => t.applyTo.includes('Inversiones'));

    if ((accountBalances[acc.id] || 0) < totalCharge) {
      return alertOf('Saldo Insuficiente', `La cuenta ${depositSourceAccount} no tiene fondos suficientes para descontar ${formatCurrency(totalCharge)}. Disponible: ${formatCurrency(accountBalances[acc.id] || 0)}`);
    }

    await ExpenseRepo.add({
      amount: inv.monthlyDeposit,
      category: 'Inversión',
      detail: `Depósito mensual — ${inv.entity}`,
      accountName: depositSourceAccount,
      paymentMethod: 'Débito',
      date: getToday(),
      ...({
        isInvestmentDeposit: true,
        investmentId: inv.id,
        time: nowHHmm(),
        tags: ['inversión', 'depósito'],
      }),
    });

    if (appliesTaxes && totalTax > 0) {
      await ExpenseRepo.add({
        amount: Math.round(totalTax * 100) / 100,
        category: 'Otros',
        detail: `Depósito mensual (Retenciones) — ${inv.entity}`,
        accountName: depositSourceAccount,
        paymentMethod: 'Débito',
        date: getToday(),
        ...({ time: nowHHmm(), tags: ['impuestos', 'inversión'] }),
      });
    }

    await InvestmentRepo.update({
      ...inv,
      totalDeposited: roundMoney((inv.totalDeposited || 0) + inv.monthlyDeposit),
      remainingMonths: Math.max(0, (inv.remainingMonths || 0) - 1),
    });

    return alertOf('Éxito', `Depósito de ${formatCurrency(inv.monthlyDeposit)} registrado como gasto`);
  },

  // DashboardScreen.loadData: aviso de "Día de sueldo", deuda activa y últimas transacciones.
  async dashboard(ctx) {
    const { SalaryRepo, SettingsRepo, IncomeRepo, LoanRepo, ExpenseRepo } = ctx.storage;
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;
    const settings = await SettingsRepo.get();
    const activeLoans = await LoanRepo.getActive();
    const totalLoanDebt = activeLoans.reduce((acc, l) => acc + ((l.installments - l.paidInstallments) * l.monthlyQuota), 0);

    const allExpenses = await ExpenseRepo.getAll();
    const allIncomes = await IncomeRepo.getAll();
    const allTx = [
      ...allExpenses.map((e) => ({ ...e, txType: 'expense', sortKey: `${e.date}T${e.time || '00:00:00'}` })),
      ...allIncomes.map((i) => ({ ...i, txType: 'income', sortKey: `${i.date}T23:59:59` })),
    ].sort((x, y) => y.sortKey.localeCompare(x.sortKey)).slice(0, 8);

    let salaryPrompt = null;
    const needsWelcome = !settings.onboardingCompleted;
    const salaries = await SalaryRepo.getAll();
    const salaryConfig = salaries[0];
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
        const alreadyPaid = currentIncomes.some((i) => i.isSalary);
        if (!alreadyPaid) {
          const postponeUntil = settings.salaryPromptPostponedUntil || 0;
          if (Date.now() > postponeUntil) {
            salaryPrompt = { amount: salaryConfig.amount, account: salaryConfig.bankAccount || 'Efectivo', configId: salaryConfig.id };
          }
        }
      }
    }
    return {
      totalLoanDebt,
      recentTx: allTx.map((t) => ({ id: t.id, txType: t.txType, sortKey: t.sortKey })),
      salaryPrompt,
    };
  },

  // AnalyticsScreen.loadData (sin los apaños de react-native-chart-kit) y handleComparison.
  async analytics(ctx, a) {
    const { ExpenseRepo, IncomeRepo, AccountRepo, CategoryRepo } = ctx.storage;
    const { getMonthName, getWeekNumber, toLocalDateStr } = ctx.formatters;
    const period = a.period;
    const allExpenses = await ExpenseRepo.getAll();
    const allIncomes = await IncomeRepo.getAll();
    const allAccounts = await AccountRepo.getAll();
    const allCategories = await CategoryRepo.getAll();
    const accNames = new Set(allAccounts.map((x) => x.name));

    const n = new Date();
    const year = n.getFullYear();
    const month = n.getMonth() + 1;

    const labels = [];
    const expData = [];
    const incData = [];
    let tSpent = 0;
    let tIncome = 0;

    if (period === 'daily') {
      for (let i = 29; i >= 0; i--) {
        const d = new Date(); d.setDate(d.getDate() - i);
        const dateStr = toLocalDateStr(d);
        labels.push(`${d.getDate()}/${d.getMonth() + 1}`);
        const dayExp = allExpenses.filter((e) => e.date === dateStr).reduce((s, e) => s + Number(e.amount), 0);
        const dayInc = allIncomes.filter((inc) => inc.date === dateStr).reduce((s, inc) => s + Number(inc.amount), 0);
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
        const weekExp = allExpenses.filter((e) => e.date >= startStr && e.date <= endStr).reduce((s, e) => s + Number(e.amount), 0);
        const weekInc = allIncomes.filter((inc) => inc.date >= startStr && inc.date <= endStr).reduce((s, inc) => s + Number(inc.amount), 0);
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
        const monthExp = allExpenses.filter((e) => e.date >= start && e.date <= end).reduce((s, e) => s + Number(e.amount), 0);
        const monthInc = allIncomes.filter((inc) => inc.date >= start && inc.date <= end).reduce((s, inc) => s + Number(inc.amount), 0);
        expData.push(monthExp); incData.push(monthInc);
        tSpent += monthExp; tIncome += monthInc;
      }
    }

    const catColors = ['#f43f5e', '#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#06b6d4', '#ec4899', '#64748b'];
    const byCategory = {};
    allExpenses.forEach((e) => {
      const cat = e.category || 'Sin categoría';
      byCategory[cat] = (byCategory[cat] || 0) + e.amount;
    });
    const pieCategory = Object.entries(byCategory)
      .sort((x, y) => y[1] - x[1])
      .map(([name, val], i) => ({
        name: name.length > 18 ? name.slice(0, 16) + '..' : name,
        population: Math.round(val * 100) / 100,
        color: allCategories.find((c) => c.name === name)?.color || catColors[i % catColors.length],
      }));

    const byMethod = {};
    allExpenses.forEach((e) => {
      const m = e.paymentMethod || 'Otro';
      byMethod[m] = (byMethod[m] || 0) + e.amount;
    });
    const pieMethod = Object.entries(byMethod).map(([name, val], i) => ({
      name: name.length > 18 ? name.slice(0, 16) + '..' : name,
      population: Math.round(val * 100) / 100,
      color: catColors[(i + 3) % catColors.length],
    }));

    const byAccountExp = {};
    allExpenses.forEach((e) => {
      if (e.paymentMethod === 'Débito' && e.accountName && accNames.has(e.accountName)) {
        byAccountExp[e.accountName] = (byAccountExp[e.accountName] || 0) + e.amount;
      }
    });

    const byIncomeSrcObj = {};
    allIncomes.forEach((i) => {
      const src = i.source || 'Otro';
      byIncomeSrcObj[src] = (byIncomeSrcObj[src] || 0) + i.amount;
    });

    return {
      labels, expData, incData, tSpent, tIncome, pieCategory, pieMethod,
      byAccountExp: Object.entries(byAccountExp),
      optCategories: Object.keys(byCategory),
      optMethods: Object.keys(byMethod),
      optIncomeSources: Object.keys(byIncomeSrcObj),
    };
  },

  async comparison(ctx, a) {
    const { getMonthlySummary } = ctx.storage;
    const { compareP1, compareP2, compCategory = '', compMethod = '', compIncomeSource = '' } = a;
    if (!compareP1 || !compareP2) return null;
    const [y1, m1] = compareP1.split('-').map(Number);
    const [y2, m2] = compareP2.split('-').map(Number);
    if (!y1 || !m1 || !y2 || !m2) return alertOf('Error', 'Usa el formato YYYY-MM para comparar');

    const s1 = await getMonthlySummary(y1, m1);
    const s2 = await getMonthlySummary(y2, m2);
    const fE = (s) => s.expenses.filter((e) => (!compCategory || (e.category || '').toLowerCase().includes(compCategory.toLowerCase())) && (!compMethod || (e.paymentMethod || '').toLowerCase().includes(compMethod.toLowerCase())));
    const fI = (s) => s.incomes.filter((i) => (!compIncomeSource || (i.source || '').toLowerCase().includes(compIncomeSource.toLowerCase())));
    const s1Exp = fE(s1).reduce((s, e) => s + e.amount, 0);
    const s2Exp = fE(s2).reduce((s, e) => s + e.amount, 0);
    const s1Inc = fI(s1).reduce((s, i) => s + i.amount, 0);
    const s2Inc = fI(s2).reduce((s, i) => s + i.amount, 0);
    const diff = s1Exp - s2Exp;
    const diffPercent = s2Exp > 0 ? ((diff / s2Exp) * 100).toFixed(1) : 0;
    return { y1, m1, y2, m2, s1Exp, s1Inc, s2Exp, s2Inc, diff, diffPercent: Number(diffPercent) };
  },
};

async function reportData(ctx, a) {
  const { ExpenseRepo, IncomeRepo, AccountRepo, LoanRepo, getAccountBalances } = ctx.storage;
  const [expenses, incomes, accounts, loans, accountBalances] = await Promise.all([
    ExpenseRepo.getAll(), IncomeRepo.getAll(), AccountRepo.getAll(), LoanRepo.getAll(), getAccountBalances(),
  ]);
  return { expenses, incomes, loans, accounts, accountBalances, filters: { type: a.type, period: a.period } };
}

// Extrae del HTML del PDF original los datos que el PDF Kotlin debe reproducir.
function parsePdfHtml(html) {
  const text = (s) => s.replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;/g, "'").trim();
  const stats = [...html.matchAll(/<div class="stat"><span>([^<]*)<\/span><strong[^>]*>([^<]*)<\/strong><\/div>/g)]
    .map((m) => [text(m[1]), text(m[2])]);
  const donuts = [...html.matchAll(/<span class="donut-val">([^<]*)<\/span>/g)].map((m) => text(m[1]));
  const bars = [...html.matchAll(/<div class="bar-label">([^<]*)<\/div>\s*<div class="bar-wrap"><div class="bar-fill" style="width: ([0-9.]+)%; background: (#[0-9A-Fa-f]+);"><\/div><\/div>\s*<div class="bar-val">([^<]*)<\/div>/g)]
    .map((m) => ({ label: text(m[1]), width: m[2], color: m[3], value: text(m[4]) }));
  const balances = [...html.matchAll(/<div class="balance-item">\s*<span>([^<]*)<\/span>\s*<span[^>]*>([^<]*)<\/span>/g)]
    .map((m) => [text(m[1]), text(m[2])]);
  const rows = [...html.matchAll(/<tr>\s*<td class="nowrap">([^<]*)<\/td>\s*<td>([^<]*)<\/td>\s*<td>([^<]*)<\/td>\s*<td>([^<]*)<\/td>\s*<td class="amount"[^>]*>([^<]*)<\/td>/g)]
    .map((m) => [text(m[1]), text(m[2]), text(m[3]), text(m[4]), text(m[5])]);
  const header = [...html.matchAll(/<p><strong>Período:<\/strong>([^<]*)<strong>Contenido:<\/strong>([^<]*)<\/p>/g)]
    .map((m) => [text(m[1]).replace(/·\s*$/, '').trim(), text(m[2])])[0] || null;
  return {
    header, stats, donuts, bars, balances, movements: rows,
    emptyMarkers: (html.match(/class="empty"/g) || []).length,
  };
}

module.exports = { ops };
