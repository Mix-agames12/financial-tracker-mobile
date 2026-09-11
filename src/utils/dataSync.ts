import { checkBudgetAlert, schedulePaymentReminders } from '../hooks/useNotifications';
import { updateSummaryWidget } from '../widgets/updateWidget';

/** Mantiene al día recordatorios, alerta de presupuesto y widget tras cualquier cambio en los datos. */
export function syncAfterDataChange(): void {
  schedulePaymentReminders().catch((e) => console.log('Error programando recordatorios:', e));
  checkBudgetAlert().catch((e) => console.log('Error en la alerta de presupuesto:', e));
  updateSummaryWidget().catch((e) => console.log('Error actualizando el widget:', e));
}
