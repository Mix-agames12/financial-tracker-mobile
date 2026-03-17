import { useEffect, useCallback } from 'react';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { ExpenseRepo, SettingsRepo, LoanRepo } from '../db/storage';

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  } as any),
});

export const useNotifications = () => {

  useEffect(() => {
    const requestPermissions = async () => {
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      let finalStatus = existingStatus;
      
      if (existingStatus !== 'granted') {
        const { status } = await Notifications.requestPermissionsAsync();
        finalStatus = status;
      }
      
      if (Platform.OS === 'android') {
        Notifications.setNotificationChannelAsync('default', {
          name: 'default',
          importance: Notifications.AndroidImportance.MAX,
          vibrationPattern: [0, 250, 250, 250],
          lightColor: '#FF231F7C',
        });
      }
    };

    requestPermissions();
  }, []);

  const checkAndScheduleAlerts = useCallback(async () => {
    try {
      // 1. Budget Alerts
      const settings = await SettingsRepo.get();
      if (settings.monthlyGoal && settings.monthlyGoal > 0) {
        const now = new Date();
        const start = new Date(now.getFullYear(), now.getMonth(), 1).toISOString();
        const end = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59).toISOString();
        
        const expenses = await ExpenseRepo.getByDateRange(start, end);
        const totalSpent = expenses.reduce((sum, e) => sum + e.amount, 0);
        
        const isNearLimit = totalSpent >= settings.monthlyGoal * 0.9 && totalSpent <= settings.monthlyGoal;
        const isOverLimit = totalSpent > settings.monthlyGoal;

        if (isNearLimit || isOverLimit) {
          // Schedule an immediate notification for budget warnings (if not already sent recently)
          await Notifications.scheduleNotificationAsync({
            content: {
              title: isOverLimit ? '¡Presupuesto superado!' : 'Atención al presupuesto',
              body: isOverLimit 
                ? `Has gastado $${totalSpent.toFixed(2)} y superado tu límite de $${settings.monthlyGoal.toFixed(2)}.`
                : `Has gastado el ${(totalSpent / settings.monthlyGoal * 100).toFixed(1)}% de tu límite y estás cerca del tope.`,
              badge: isOverLimit ? 1 : 0,
            },
            trigger: { type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds: 2 }, // Trigger almost immediately
          });
        }
      }

      // 2. Upcoming Loans
      const loans = await LoanRepo.getActive();
      for (const loan of loans) {
        if (loan.paidInstallments < loan.installments) {
          // Here we could schedule a notification if we had a nextPaymentDate field.
        }
      }

      // 3. Recurring Expenses
      const allExpenses = await ExpenseRepo.getAll();
      const recurring = allExpenses.filter(e => e.isRecurring);
      for (const exp of recurring) {
        if (exp.recurringDay) {
          const now = new Date();
          let targetDate = new Date(now.getFullYear(), now.getMonth(), exp.recurringDay, 9, 0, 0); // 9:00 AM
          
          if (targetDate.getTime() < now.getTime()) {
            targetDate.setMonth(targetDate.getMonth() + 1);
          }

          // Schedule for next occurrence
          await Notifications.scheduleNotificationAsync({
            content: {
              title: 'Recordatorio de Gasto Recurrente',
              body: `Tu pago de ${exp.detail} (${exp.category}) es próximo a cobrarse.`,
            },
            trigger: {
              type: Notifications.SchedulableTriggerInputTypes.DATE,
              date: targetDate,
            },
          });
        }
      }

    } catch (error) {
      console.log('Error scheduling alerts:', error);
    }
  }, []);

  return { checkAndScheduleAlerts };
};
