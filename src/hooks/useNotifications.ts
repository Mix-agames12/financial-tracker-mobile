import { useEffect } from 'react';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { ExpenseRepo, SettingsRepo } from '../db/storage';
import { formatCurrency, formatDate, toLocalDateStr } from '../utils/formatters';
import { getUpcomingPayments, UpcomingPayment } from '../utils/paymentReminders';

const CHANNEL_ID = 'payments';
const REMINDER_KIND = 'payment-reminder';
const REMINDER_HOUR = 9;
const REMINDER_HORIZON_DAYS = 60;
const MAX_SCHEDULED = 50; // iOS admite como máximo 64 notificaciones locales programadas

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/** Ejecuta las llamadas una detrás de otra para que dos reprogramaciones no se intercalen. */
function serialized(task: () => Promise<void>): () => Promise<void> {
  let queue: Promise<void> = Promise.resolve();
  return () => {
    queue = queue.catch(() => undefined).then(task);
    return queue;
  };
}

async function hasPermission(): Promise<boolean> {
  const { status } = await Notifications.getPermissionsAsync();
  return status === 'granted';
}

async function ensureNotificationSetup(): Promise<boolean> {
  // En Android 13+ el canal debe existir antes de pedir el permiso para que se muestre el diálogo.
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: 'Recordatorios y alertas',
      importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250],
      lightColor: '#6366f1',
    });
  }
  if (await hasPermission()) return true;
  const { status } = await Notifications.requestPermissionsAsync();
  return status === 'granted';
}

/** El día anterior a las 09:00; si ya pasó, el mismo día a las 09:00; si también pasó, ninguno. */
function reminderDateFor(payment: UpcomingPayment): { date: Date; isDueDay: boolean } | null {
  const [y, m, d] = payment.dueDate.split('-').map(Number);
  if (!y || !m || !d) return null;
  const now = Date.now();
  const dayBefore = new Date(y, m - 1, d - 1, REMINDER_HOUR, 0, 0);
  if (dayBefore.getTime() > now) return { date: dayBefore, isDueDay: false };
  const dueDay = new Date(y, m - 1, d, REMINDER_HOUR, 0, 0);
  if (dueDay.getTime() > now) return { date: dueDay, isDueDay: true };
  return null;
}

/** Reprograma los recordatorios de pagos próximos: préstamos, tarjetas y gastos recurrentes. */
export const schedulePaymentReminders = serialized(async () => {
  if (!(await hasPermission())) return;

  const scheduled = await Notifications.getAllScheduledNotificationsAsync();
  await Promise.all(
    scheduled
      .filter((n) => n.content.data?.kind === REMINDER_KIND)
      .map((n) => Notifications.cancelScheduledNotificationAsync(n.identifier))
  );

  const payments = (await getUpcomingPayments(REMINDER_HORIZON_DAYS)).filter((p) => p.daysLeft >= 0);
  let count = 0;
  for (const payment of payments) {
    if (count >= MAX_SCHEDULED) break;
    const when = reminderDateFor(payment);
    if (!when) continue;
    await Notifications.scheduleNotificationAsync({
      identifier: `${REMINDER_KIND}-${payment.id}`,
      content: {
        title: when.isDueDay ? 'Hoy vence un pago' : 'Pago próximo',
        body: `${when.isDueDay ? 'Hoy' : 'Mañana'} vence ${payment.title}: ${formatCurrency(payment.amount)} (${formatDate(payment.dueDate)}).`,
        data: { kind: REMINDER_KIND, paymentId: payment.id },
      },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.DATE,
        date: when.date,
        channelId: CHANNEL_ID,
      },
    });
    count++;
  }
});

/** Avisa una sola vez por mes y nivel cuando el gasto del mes llega al 90 % de la meta o la supera. */
export const checkBudgetAlert = serialized(async () => {
  const settings = await SettingsRepo.get();
  const goal = Number(settings.monthlyGoal) || 0;
  if (goal <= 0 || !(await hasPermission())) return;

  const now = new Date();
  const start = toLocalDateStr(new Date(now.getFullYear(), now.getMonth(), 1));
  const end = toLocalDateStr(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  const expenses = await ExpenseRepo.getByDateRange(start, end);
  const totalSpent = expenses.reduce((sum, e) => sum + (Number(e.amount) || 0), 0);

  const level = totalSpent > goal ? 'over' : totalSpent >= goal * 0.9 ? 'near' : null;
  if (!level) return;
  const marker = `${start.slice(0, 7)}:${level}`;
  if (settings.lastBudgetAlert === marker) return;

  await Notifications.scheduleNotificationAsync({
    content: {
      title: level === 'over' ? '¡Presupuesto superado!' : 'Atención al presupuesto',
      body: level === 'over'
        ? `Has gastado ${formatCurrency(totalSpent)} y superado tu límite de ${formatCurrency(goal)}.`
        : `Has gastado el ${((totalSpent / goal) * 100).toFixed(1)}% de tu límite de ${formatCurrency(goal)}.`,
    },
    trigger: { channelId: CHANNEL_ID },
  });
  await SettingsRepo.save({ lastBudgetAlert: marker });
});

/** Configura el canal y pide permiso al montar la app; con permiso, programa los avisos. */
export const useNotifications = () => {
  useEffect(() => {
    ensureNotificationSetup()
      .then((granted) => {
        if (!granted) return;
        schedulePaymentReminders().catch((e) => console.log('Error programando recordatorios:', e));
        checkBudgetAlert().catch((e) => console.log('Error en la alerta de presupuesto:', e));
      })
      .catch((e) => console.log('Error configurando notificaciones:', e));
  }, []);
};
