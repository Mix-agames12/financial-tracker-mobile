import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as Print from 'expo-print';
import { formatCurrency, formatDate, getToday, roundMoney, toLocalDateStr } from './formatters';
import { Expense, Income, Loan, Account } from '../types';

export type ReportPeriod = 'thisMonth' | 'lastMonth' | 'thisYear' | 'all';
export type ReportType = 'all' | 'ingresos' | 'gastos';

export const REPORT_PERIOD_LABELS: Record<ReportPeriod, string> = {
  thisMonth: 'Este mes',
  lastMonth: 'Mes anterior',
  thisYear: 'Este año',
  all: 'Todo',
};

export interface ReportRange {
  startDate?: string; // 'YYYY-MM-DD'
  endDate?: string;
  label: string;
  fileTag: string;
}

interface ReportData {
  expenses: Expense[];
  incomes: Income[];
  loans: Loan[];
  accounts: Account[];
  accountBalances: Record<string, number>;
  filters: {
    type: ReportType;
    period: ReportPeriod;
  };
}

/** Rango de fechas (hora local) de cada período del reporte. */
export function getReportPeriod(period: ReportPeriod, now: Date = new Date()): ReportRange {
  const year = now.getFullYear();
  const month = now.getMonth();
  const monthRange = (offset: number): ReportRange => {
    const start = toLocalDateStr(new Date(year, month + offset, 1));
    const end = toLocalDateStr(new Date(year, month + offset + 1, 0));
    return { startDate: start, endDate: end, label: `${formatDate(start)} – ${formatDate(end)}`, fileTag: start.slice(0, 7) };
  };

  switch (period) {
    case 'thisMonth':
      return monthRange(0);
    case 'lastMonth':
      return monthRange(-1);
    case 'thisYear':
      return { startDate: `${year}-01-01`, endDate: `${year}-12-31`, label: `Año ${year}`, fileTag: String(year) };
    default:
      return { label: 'Todo el historial', fileTag: 'historial' };
  }
}

function inRange(date: string, range: ReportRange): boolean {
  return (!range.startDate || date >= range.startDate) && (!range.endDate || date <= range.endDate);
}

function expenseAmount(exp: Expense): number {
  return Number((exp as any).totalCharge ?? exp.amount) || 0;
}

/** "Crédito · Visa", "Débito · Ahorros" o sólo el método si no hay cuenta ni tarjeta. */
function paymentMethodLabel(exp: Expense): string {
  const source = exp.paymentMethod === 'Crédito' ? exp.cardName : exp.accountName;
  return source ? `${exp.paymentMethod} · ${source}` : exp.paymentMethod || 'Otro';
}

/** Aplica el período y el tipo elegidos; CSV y PDF usan exactamente los mismos registros. */
function selectRecords(data: ReportData) {
  const range = getReportPeriod(data.filters.period);
  const includeIncomes = data.filters.type !== 'gastos';
  const includeExpenses = data.filters.type !== 'ingresos';
  return {
    range,
    includeIncomes,
    includeExpenses,
    incomes: includeIncomes ? data.incomes.filter((i) => inRange(i.date, range)) : [],
    expenses: includeExpenses ? data.expenses.filter((e) => inRange(e.date, range)) : [],
  };
}

function topEntries<T>(items: T[], key: (item: T) => string, amount: (item: T) => number, limit = 5): [string, number][] {
  const totals: Record<string, number> = {};
  items.forEach((item) => {
    const k = key(item);
    totals[k] = (totals[k] || 0) + amount(item);
  });
  return Object.entries(totals).sort((a, b) => b[1] - a[1]).slice(0, limit);
}

/** Escapa texto ingresado por el usuario antes de insertarlo en el HTML del PDF. */
function escapeHtml(text: string | undefined): string {
  return (text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function sanitizeCSV(text: string) {
  if (!text) return '';
  return `"${text.replace(/"/g, '""')}"`;
}

/** Genera y comparte el CSV. Devuelve false si el dispositivo no permite compartir archivos. */
export async function generateCSVReport(data: ReportData): Promise<boolean> {
  const { range, incomes, expenses } = selectRecords(data);

  // BOM (Byte Order Mark) para que Excel lea UTF-8 con tildes y ñ
  let csvString = '\uFEFFFecha,Tipo,Categoría/Fuente,Cuenta,Monto,Detalle\n';

  type RecordRow = { date: string; type: string; category: string; account: string; amount: number; detail: string };
  const rows: RecordRow[] = [
    ...incomes.map((inc) => ({
      date: inc.date,
      type: 'Ingreso',
      category: inc.source || '',
      account: inc.bankAccount || '',
      amount: Number(inc.amount) || 0,
      detail: inc.detail || '',
    })),
    ...expenses.map((exp) => ({
      date: exp.date,
      type: 'Gasto',
      category: exp.category || '',
      account: exp.accountName || exp.cardName || '',
      amount: expenseAmount(exp),
      detail: exp.detail || '',
    })),
  ];

  // Ordenar descendente (fecha más actual -> más antigua)
  rows.sort((a, b) => (b.date || '').localeCompare(a.date || ''));

  rows.forEach((r) => {
    csvString += `${r.date},${r.type},${sanitizeCSV(r.category)},${sanitizeCSV(r.account)},${roundMoney(r.amount).toFixed(2)},${sanitizeCSV(r.detail)}\n`;
  });

  const file = new File(Paths.document, `reporte_${range.fileTag}_${getToday()}.csv`);
  file.write(csvString);

  if (!(await Sharing.isAvailableAsync())) return false;
  await Sharing.shareAsync(file.uri, { mimeType: 'text/csv', dialogTitle: 'Compartir reporte CSV' });
  return true;
}

/** Genera y comparte el PDF. Devuelve false si el dispositivo no permite compartir archivos. */
export async function generatePDFReport(data: ReportData): Promise<boolean> {
  const { accounts, accountBalances } = data;
  const { range, incomes, expenses, includeIncomes, includeExpenses } = selectRecords(data);

  const totalIncome = incomes.reduce((sum, inc) => sum + (Number(inc.amount) || 0), 0);
  const totalExpense = expenses.reduce((sum, exp) => sum + expenseAmount(exp), 0);
  const balance = totalIncome - totalExpense;

  const expPercentage = totalIncome > 0 ? Math.min(Math.round((totalExpense / totalIncome) * 100), 100) : 0;
  const balPercentage = totalIncome > 0 ? Math.max(0, Math.round((balance / totalIncome) * 100)) : 0;

  const typeLabel = includeIncomes && includeExpenses ? 'Ingresos y gastos' : includeIncomes ? 'Sólo ingresos' : 'Sólo gastos';
  const balanceColor = balance < 0 ? '#E74C3C' : '#2E7D32';

  const barRows = (entries: [string, number][], total: number, color: string) =>
    entries.length === 0
      ? '<p class="empty">Sin movimientos en el período</p>'
      : entries.map(([label, amount]) => {
          const perc = total > 0 ? (amount / total) * 100 : 0;
          return `
            <div class="bar-row">
              <div class="bar-label">${escapeHtml(label)}</div>
              <div class="bar-wrap"><div class="bar-fill" style="width: ${perc.toFixed(1)}%; background: ${color};"></div></div>
              <div class="bar-val">${formatCurrency(amount)}</div>
            </div>`;
        }).join('');

  // Todas las categorías y fuentes con movimientos en el período, de mayor a menor.
  const topCategories = topEntries(expenses, (e) => e.category || 'Sin categoría', expenseAmount, Infinity)
    .filter(([, amount]) => amount > 0);
  const topSources = topEntries(incomes, (i) => i.source || 'Otros', (i) => Number(i.amount) || 0, Infinity)
    .filter(([, amount]) => amount > 0);

  // Detalle de movimientos: del más reciente al más antiguo (la hora sólo desempata).
  const movements = [
    ...incomes.map((inc) => ({
      date: inc.date,
      time: '',
      detail: inc.detail || inc.source || 'Ingreso',
      category: inc.source || 'Ingreso',
      method: inc.bankAccount ? `Depósito · ${inc.bankAccount}` : 'Depósito',
      amount: Number(inc.amount) || 0,
      isIncome: true,
    })),
    ...expenses.map((exp) => ({
      date: exp.date,
      time: String((exp as any).time || ''),
      detail: exp.detail || exp.category || 'Gasto',
      category: exp.category || 'Sin categoría',
      method: paymentMethodLabel(exp),
      amount: expenseAmount(exp),
      isIncome: false,
    })),
  ].sort((a, b) => (b.date || '').localeCompare(a.date || '') || b.time.localeCompare(a.time));

  const htmlContent = `
    <!DOCTYPE html>
    <html lang="es">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Reporte financiero</title>
      <style>
        body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 0; padding: 20px; color: #333; background: #fff; }
        .header { border-bottom: 2px solid #2B9396; padding-bottom: 16px; margin-bottom: 24px; }
        h1 { margin: 0 0 8px 0; color: #1C3E49; font-size: 26px; }
        p { margin: 0; color: #666; font-size: 13px; line-height: 1.5; }
        .section-title { background: #EEF2F6; padding: 8px 12px; font-size: 16px; font-weight: bold; color: #1C3E49; margin: 24px 0 12px; border-radius: 4px; }
        .summary { display: flex; gap: 12px; flex-wrap: wrap; }
        .stat { flex: 1; min-width: 120px; border: 1px solid #ddd; border-radius: 6px; padding: 12px; background: #FAFAFA; }
        .stat span { display: block; font-size: 12px; color: #888; margin-bottom: 4px; }
        .stat strong { font-size: 18px; }
        .donuts-container { display: flex; justify-content: space-around; margin: 24px 0 8px; }
        .donut-wrapper { text-align: center; }
        .donut-wrapper p { margin-top: 8px; font-weight: bold; color: #444; }
        .donut { width: 110px; height: 110px; border-radius: 50%; display: flex; align-items: center; justify-content: center; position: relative; }
        .donut::before { content: ""; position: absolute; width: 74px; height: 74px; background: white; border-radius: 50%; }
        .donut-val { position: relative; font-size: 22px; font-weight: bold; color: #333; z-index: 1; }
        .exp-donut { background: conic-gradient(#FF5722 ${expPercentage}%, #E0E0E0 0); }
        .bal-donut { background: conic-gradient(#2196F3 ${balPercentage}%, #E0E0E0 0); }
        .card { border: 1px solid #ddd; border-radius: 6px; padding: 14px; background: #FAFAFA; }
        .balance-item { display: flex; justify-content: space-between; font-size: 14px; padding: 6px 0; border-bottom: 1px dashed #ddd; }
        .balance-item:last-child { border-bottom: none; }
        .bar-row { display: flex; align-items: center; margin-bottom: 10px; }
        .bar-label { width: 130px; font-size: 12px; text-align: right; padding-right: 10px; color: #555; }
        .bar-wrap { flex: 1; background: #EDEDED; height: 14px; border-radius: 7px; overflow: hidden; }
        .bar-fill { height: 100%; border-radius: 7px; }
        .bar-val { font-size: 11px; margin-left: 8px; color: #333; min-width: 80px; }
        .empty { text-align: center; color: #999; }
        .movements { width: 100%; border-collapse: collapse; font-size: 11px; }
        .movements th { background: #1C3E49; color: #fff; text-align: left; padding: 6px 8px; }
        .movements td { padding: 6px 8px; border-bottom: 1px solid #e5e5e5; vertical-align: top; }
        .movements thead { display: table-header-group; }
        .movements tr { page-break-inside: avoid; }
        .movements .amount { text-align: right; white-space: nowrap; }
        .movements .nowrap { white-space: nowrap; }
      </style>
    </head>
    <body>
      <div class="header">
        <h1>Reporte financiero</h1>
        <p><strong>Período:</strong> ${escapeHtml(range.label)} · <strong>Contenido:</strong> ${typeLabel}</p>
        <p>Generado el ${formatDate(getToday())}</p>
      </div>

      <div class="section-title">Resumen del período</div>
      <div class="summary">
        ${includeIncomes ? `<div class="stat"><span>Ingresos</span><strong style="color: #2E7D32;">${formatCurrency(totalIncome)}</strong></div>` : ''}
        ${includeExpenses ? `<div class="stat"><span>Gastos</span><strong style="color: #E74C3C;">${formatCurrency(totalExpense)}</strong></div>` : ''}
        ${includeIncomes && includeExpenses ? `<div class="stat"><span>Balance del período</span><strong style="color: ${balanceColor};">${formatCurrency(balance)}</strong></div>` : ''}
        <div class="stat"><span>Transacciones</span><strong>${incomes.length + expenses.length}</strong></div>
      </div>

      ${includeIncomes && includeExpenses ? `
      <div class="donuts-container">
        <div class="donut-wrapper">
          <div class="donut exp-donut"><span class="donut-val">${expPercentage}%</span></div>
          <p>Gastos sobre ingresos</p>
        </div>
        <div class="donut-wrapper">
          <div class="donut bal-donut"><span class="donut-val">${balPercentage}%</span></div>
          <p>Margen de ahorro</p>
        </div>
      </div>` : ''}

      ${includeExpenses ? `
      <div class="section-title">Gastos por categoría</div>
      <div class="card">${barRows(topCategories, totalExpense, '#E74C3C')}</div>` : ''}

      ${includeIncomes ? `
      <div class="section-title">Ingresos por fuente</div>
      <div class="card">${barRows(topSources, totalIncome, '#2E7D32')}</div>` : ''}

      <div class="section-title">Saldos actuales en cuentas</div>
      <div class="card">
        ${accounts.length === 0 ? '<p class="empty">Sin cuentas registradas</p>' : accounts.map((acc) => {
          const accBalance = accountBalances[acc.id] || 0;
          return `
            <div class="balance-item">
              <span>${escapeHtml(acc.name)} · ${escapeHtml(acc.bankName)}</span>
              <span style="color: ${accBalance < 0 ? '#E74C3C' : '#2196F3'};">${formatCurrency(accBalance)}</span>
            </div>`;
        }).join('')}
      </div>

      <div class="section-title">Detalle de movimientos</div>
      ${movements.length === 0 ? '<p class="empty">Sin movimientos en el período</p>' : `
      <table class="movements">
        <thead>
          <tr><th>Fecha</th><th>Detalle</th><th>Categoría</th><th>Medio de pago</th><th class="amount">Monto</th></tr>
        </thead>
        <tbody>
          ${movements.map((m) => `
            <tr>
              <td class="nowrap">${formatDate(m.date)}</td>
              <td>${escapeHtml(m.detail)}</td>
              <td>${escapeHtml(m.category)}</td>
              <td>${escapeHtml(m.method)}</td>
              <td class="amount" style="color: ${m.isIncome ? '#2E7D32' : '#E74C3C'};">${m.isIncome ? '+' : '-'}${formatCurrency(m.amount)}</td>
            </tr>`).join('')}
        </tbody>
      </table>`}
    </body>
    </html>
  `;

  const { uri } = await Print.printToFileAsync({ html: htmlContent });

  // expo-print usa un nombre aleatorio; se renombra para que el archivo compartido sea reconocible.
  let shareUri = uri;
  try {
    const target = new File(Paths.cache, `reporte_${range.fileTag}_${getToday()}.pdf`);
    if (target.exists) target.delete();
    new File(uri).move(target);
    shareUri = target.uri;
  } catch (e) {
    console.warn('No se pudo renombrar el PDF', e);
  }

  if (!(await Sharing.isAvailableAsync())) return false;
  await Sharing.shareAsync(shareUri, { mimeType: 'application/pdf', UTI: 'com.adobe.pdf', dialogTitle: 'Compartir reporte PDF' });
  return true;
}
