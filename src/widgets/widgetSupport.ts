import { NativeModules, Platform, TurboModuleRegistry } from 'react-native';

/**
 * El widget sólo existe en builds nativos (EAS / dev client). En Expo Go falta el módulo nativo y
 * react-native-android-widget lanza al importarse, por eso su código se carga con require() perezoso
 * y únicamente cuando este flag es true.
 */
export const isWidgetSupported =
  Platform.OS === 'android' &&
  (TurboModuleRegistry.get('AndroidWidget') != null || NativeModules.AndroidWidget != null);

export const SUMMARY_WIDGET_NAME = 'Resumen';
