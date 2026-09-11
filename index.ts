import { registerRootComponent } from 'expo';

import App from './App';
import { isWidgetSupported } from './src/widgets/widgetSupport';

// registerRootComponent calls AppRegistry.registerComponent('main', () => App);
// It also ensures that whether you load the app in Expo Go or in a native build,
// the environment is set up appropriately
registerRootComponent(App);

// Widget de Android: sólo en builds nativos. En Expo Go el módulo nativo no existe y el paquete
// lanza al importarse, por eso se carga con require() detrás de isWidgetSupported.
if (isWidgetSupported) {
  const { registerWidgetTaskHandler } = require('react-native-android-widget');
  const { widgetTaskHandler } = require('./src/widgets/widgetTaskHandler');
  registerWidgetTaskHandler(widgetTaskHandler);
}
