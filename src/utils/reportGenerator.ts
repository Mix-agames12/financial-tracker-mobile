import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as Print from 'expo-print';
import { formatCurrency, formatDate, getToday } from './formatters';
import { Expense, Income, Loan, Account } from '../types';

interface ReportData {
  expenses: Expense[];
  incomes: Income[];
  loans: Loan[];
  accounts: Account[];
  accountBalances: Record<string, number>;
  filters: Partial<{
    startDate: string;
    endDate: string;
    type: 'all' | 'ingresos' | 'gastos';
  }>;
}

export async function generateCSVReport(data: ReportData) {
  const { expenses, incomes, filters } = data;
  let csvString = 'Fecha,Tipo,Categoría/Fuente,Cuenta,Monto,Detalle\n';

  if (filters.type === 'ingresos' || filters.type === 'all' || !filters.type) {
    incomes.forEach(inc => {
      csvString += `${inc.date},Ingreso,${sanitizeCSV(inc.source || '')},${sanitizeCSV(inc.bankAccount || '')},${inc.amount},${sanitizeCSV(inc.detail || '')}\n`;
    });
  }

  if (filters.type === 'gastos' || filters.type === 'all' || !filters.type) {
    expenses.forEach(exp => {
      const charge = (exp as any).totalCharge || exp.amount;
      csvString += `${exp.date},Gasto,${sanitizeCSV(exp.category || '')},${sanitizeCSV(exp.accountName || '')},${charge},${sanitizeCSV(exp.detail || '')}\n`;
    });
  }

  const fs = FileSystem as any;
  const fileUri = fs.documentDirectory + `reporte_${getToday()}.csv`;
  await fs.writeAsStringAsync(fileUri, csvString, { encoding: fs.EncodingType.UTF8 });
  
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(fileUri, { mimeType: 'text/csv', dialogTitle: 'Compartir reporte CSV' });
  }
}

function sanitizeCSV(text: string) {
  if (!text) return '';
  return `"${text.replace(/"/g, '""')}"`;
}

export async function generatePDFReport(data: ReportData) {
  const { expenses, incomes, accounts, accountBalances } = data;

  const totalIncome = incomes.reduce((sum, inc) => sum + inc.amount, 0);
  const totalExpense = expenses.reduce((sum, exp) => sum + ((exp as any).totalCharge || exp.amount), 0);
  const balance = totalIncome - totalExpense;

  const incPercentage = totalIncome > 0 ? 100 : 0;
  const expPercentage = totalIncome > 0 ? Math.min(Math.round((totalExpense / totalIncome) * 100), 100) : 0;
  const balPercentage = totalIncome > 0 ? Math.max(0, Math.round((balance / totalIncome) * 100)) : 0;

  // Group expenses by category
  const expensesByCategory: Record<string, number> = {};
  expenses.forEach(exp => {
    if (!expensesByCategory[exp.category]) expensesByCategory[exp.category] = 0;
    expensesByCategory[exp.category] += ((exp as any).totalCharge || exp.amount);
  });
  
  const sortedCategories = Object.entries(expensesByCategory).sort((a, b) => b[1] - a[1]);
  const topCategories = sortedCategories.slice(0, 5);

  const htmlContent = `
    <!DOCTYPE html>
    <html lang="es">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Reporte Financiero General</title>
      <style>
        body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 0; padding: 20px; color: #333; background: #fff; }
        .header { display: flex; align-items: center; border-bottom: 2px solid #2B9396; padding-bottom: 20px; margin-bottom: 30px; }
        .header-content { margin-left: 20px; }
        .header-bg { background-color: #2F323A; width: 100px; height: 100px; transform: rotate(45deg); position: absolute; top: -50px; left: -50px; display: none; }
        h1 { margin: 0 0 10px 0; color: #333; font-size: 28px; }
        p { margin: 0; color: #666; font-size: 14px; line-height: 1.5; }
        
        .section-title { background: #E6E1CB; padding: 10px; text-align: center; font-size: 18px; font-weight: bold; color: #7F9FB3; margin: 20px 0; border-radius: 4px; }
        
        .donuts-container { display: flex; justify-content: space-around; margin-bottom: 30px; }
        .donut-wrapper { text-align: center; }
        .donut-wrapper p { margin-top: 10px; font-weight: bold; color: #444; }
        
        .donut { width: 120px; height: 120px; border-radius: 50%; display: flex; align-items: center; justify-content: center; position: relative; }
        .donut::before { content: ""; position: absolute; width: 80px; height: 80px; background: white; border-radius: 50%; }
        .donut-val { position: relative; font-size: 24px; font-weight: bold; color: #333; z-index: 1; }
        
        /* CSS Hack for simplistic pie slices */
        .inc-donut { background: conic-gradient(#4CAF50 ${incPercentage}%, #E0E0E0 0); }
        .exp-donut { background: conic-gradient(#FF5722 ${expPercentage}%, #E0E0E0 0); }
        .bal-donut { background: conic-gradient(#2196F3 ${balPercentage}%, #E0E0E0 0); }

        .flex-row { display: flex; justify-content: space-between; gap: 20px; }
        .card { border: 1px solid #ccc; border-radius: 4px; flex: 1; padding: 15px; background: #FAFAFA; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
        .card-header { background: #1C3E49; color: white; padding: 8px 15px; border-radius: 20px; text-align: center; font-weight: bold; margin: -25px auto 15px auto; width: fit-content; }
        
        .balance-item { display: flex; justify-content: space-between; font-size: 18px; margin-bottom: 10px; font-weight: bold; }
        .balance-item span:first-child { color: #888; }
        .val-total { color: #E74C3C; font-size: 24px; }
        .val-in { color: #333; }
        .val-out { color: #333; }

        .bar-chart { margin-top: 20px; }
        .bar-row { display: flex; align-items: center; margin-bottom: 12px; }
        .bar-label { width: 120px; font-size: 12px; text-align: right; padding-right: 10px; color: #555;}
        .bar-wrap { flex: 1; background: #EDEDED; height: 14px; border-radius: 7px; overflow: hidden; position: relative;}
        .bar-fill { height: 100%; background: #E74C3C; border-radius: 7px; }
        .bar-val { font-size: 11px; margin-left: 8px; color: #333;}
      </style>
    </head>
    <body>
      <div class="header">
        <div class="header-content">
          <h1>Informe financiero mensual empresarial de una página</h1>
          <p>This report covers the financial performance. It involves details such as gross incomes, operating expenses, and net profit margins based on recorded transactions.</p>
        </div>
      </div>

      <div class="section-title">Financial Report : ${formatDate(getToday())}</div>

      <div class="donuts-container">
        <div class="donut-wrapper">
          <div class="donut inc-donut"><span class="donut-val">${incPercentage}%</span></div>
          <p>Ingresos Totales</p>
        </div>
        <div class="donut-wrapper">
          <div class="donut exp-donut"><span class="donut-val">${expPercentage}%</span></div>
          <p>Margen de Gastos</p>
        </div>
        <div class="donut-wrapper">
          <div class="donut bal-donut"><span class="donut-val">${balPercentage}%</span></div>
          <p>Margen Neto</p>
        </div>
      </div>

      <div class="section-title">Posición de Efectivo</div>
      <div class="flex-row">
        <div class="card" style="margin-top: 15px;">
          <div class="card-header">Balance Total</div>
          <div class="balance-item"><span style="color:#FF5722; font-size:24px;">$</span> <span class="val-total">${formatCurrency(balance).replace('$', '')}</span></div>
          <div class="balance-item" style="font-size:16px;"><span>IN</span> <span>${formatCurrency(totalIncome)}</span></div>
          <div class="balance-item" style="font-size:16px;"><span>OUT</span> <span>${formatCurrency(totalExpense)}</span></div>
        </div>
        
        <div class="card" style="margin-top: 15px;">
          <div class="card-header">Saldos en Cuentas</div>
          ${accounts.map(acc => `
            <div class="balance-item" style="font-size:14px; border-bottom: 1px dashed #ddd; padding-bottom: 5px;">
              <span style="color:#333;">${acc.name}</span>
              <span style="color:#2196F3;">${formatCurrency(accountBalances[acc.id] || 0)}</span>
            </div>
          `).join('')}
        </div>
      </div>

      <div class="section-title">Desglose de Costos</div>
      <div class="flex-row">
        <div class="card" style="margin-top: 15px;">
          <div class="card-header">Top 5 Categorías</div>
          <div class="bar-chart">
            ${topCategories.map(([cat, amount]) => {
              const perc = totalExpense > 0 ? (amount / totalExpense) * 100 : 0;
              return `
                <div class="bar-row">
                  <div class="bar-label">${cat}</div>
                  <div class="bar-wrap">
                    <div class="bar-fill" style="width: ${perc}%"></div>
                  </div>
                  <div class="bar-val">${formatCurrency(amount)}</div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
        <div class="card" style="margin-top: 15px; text-align: center; display: flex; flex-direction: column; justify-content: center;">
             <p style="font-size:16px; margin-bottom: 10px;">Total Registros</p>
             <h2 style="color: #1C3E49; margin:0; font-size:42px;">${expenses.length + incomes.length}</h2>
             <p style="margin-top: 10px;">Transacciones analizadas</p>
        </div>
      </div>

    </body>
    </html>
  `;

  const { uri } = await Print.printToFileAsync({ html: htmlContent });
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(uri, { dialogTitle: 'Compartir reporte PDF' });
  }
}
