/**
 * APIs de notificaciones locales de expo-notifications, importadas módulo por módulo.
 *
 * El índice del paquete ('expo-notifications') avisa en Expo Go que la librería no está completa y
 * carga el registro automático de tokens push, que en Android imprime un error aunque la app sólo
 * programe notificaciones locales. Estos son los mismos módulos que reexporta ese índice; si una
 * actualización de Expo los mueve, `tsc` fallará aquí.
 */
export { setNotificationHandler } from 'expo-notifications/build/NotificationsHandler';
export { getPermissionsAsync, requestPermissionsAsync } from 'expo-notifications/build/NotificationPermissions';
export { default as setNotificationChannelAsync } from 'expo-notifications/build/setNotificationChannelAsync';
export { default as getAllScheduledNotificationsAsync } from 'expo-notifications/build/getAllScheduledNotificationsAsync';
export { default as cancelScheduledNotificationAsync } from 'expo-notifications/build/cancelScheduledNotificationAsync';
export { default as scheduleNotificationAsync } from 'expo-notifications/build/scheduleNotificationAsync';
export { AndroidImportance } from 'expo-notifications/build/NotificationChannelManager.types';
export { SchedulableTriggerInputTypes } from 'expo-notifications/build/Notifications.types';
