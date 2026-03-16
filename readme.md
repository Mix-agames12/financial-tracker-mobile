<h1 align="center">Financial Tracker</h1>

Una aplicación móvil desarrollada en React Native diseñada para ofrecerte un control total sobre tus finanzas personales. Permite registrar, analizar y resguardar tus movimientos de dinero de manera eficiente e intuitiva en la palma de tu mano

## ✨ Funcionalidades Principales

### 📊 Dashboard Inteligente

- **Vista unificada** de tu situación financiera actual
- **Indicadores en tiempo real** de gastos, ingresos y saldo neto
- **Resumen visual** de tendencias financieras período a período
- Acceso rápido a funciones críticas desde una sola pantalla

### 💰 Gestión de Gastos

- **Registro granular** de transacciones con categorización automática
- **Interfaz de entrada optimizada** con TextField personalizado
- **Almacenamiento persistente** mediante AsyncStorage
- **Edición y eliminación** de registros históricos
- Validación en tiempo real de montos y conceptos

### 💵 Seguimiento de Ingresos

- **Múltiples fuentes** de ingresos in a single interface
- **Proyecciones de ingresos** basadas en patrones históricos
- **Desglose por categoría** de ingresos
- **Comparativas período sobre período**

### 📈 Analytics Avanzados

- **Gráficos interactivos** basados en react-native-chart-kit
- **Análisis de tendencias** de corto y largo plazo
- **Distribución de gastos** por categoría (pie charts, bar charts)
- **Predicciones inteligentes** de gastos futuros basadas en histórico
- **Reportes descargables** en formato PDF/CSV

### 🏦 Gestión de Préstamos

- **Seguimiento de deudas** personales
- **Cálculo automático** de intereses
- **Historial de pagos** detallado
- **Alertas de vencimiento** configurable

### 🎨 Experiencia de Usuario Premium

- **Tema oscuro/claro** adaptativo con persistencia
- **Interfaz nativa** con SafeAreaContext para máxima compatibilidad
- **Componentes reutilizables** optimizados (Button, Card, Chip, ProgressBar)
- **Bottom Tab Navigation** para navegación fluida entre módulos
- **BottomSheet moderno** para acciones secundarias

### ⚙️ Configuración Personalizada

- **Preferencias de usuario** persistentes
- **Moneda personalizable** según localización
- **Categorías custom** de gastos e ingresos
- **Alertas y notificaciones** configurables

## Como instalarlo y probarlo

Clona el repositorio

```bash
git clone https://github.com/Mix-agames12/financial-tracker-mobile.git
cd financial-tracker-mobile
```

Instala las dependencias

```bash
npm install
```

Para poder probarlo tienes que tener instalado Expo go en tu telefono

```bash
npx expo start -c
```

Se levantara el servidor en local para ello tendras que abrir la app Expo go en tu telefono y escanear el codigo QR que se muestra en la terminal, esto te permitira probar la app en tu telefono, puede tomar unos segundos que se compile la app y ya podras probar la app en local

### Como desplegarlo

Para poder desplegar la App se necesita de una cuenta de Expo, puedes crearla en https://expo.dev/ , una vez creada la cuenta debes seguir los siguientes pasos ya que la app te pedira que inicies sesion

```bash
npm install -g eas-cli
```

Luego inicias sesion con el siguiente comando

```bash
npx eas login
```

antes de construir el apk debes configurar el archivo **eas.json**, para ello debes tener una estructura similar

```json
{
  "cli": {
    "version": ">= 3.8.2"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal"
    },
    "preview": {
      "android": {
        "buildType": "apk"
      }
    },
    "production": {}
  },
  "submit": {
    "production": {}
  }
}
```

Ahora vincularemos nuestro proyecto local con el panel de control de Expo

```bash
eas build:configure
```

y debes seleccionar la opcion de android, ahora los servidores de Expo construiran el apk y lo subiran a la app

```bash
eas build -p android --profile preview
```

La terminal te pedirá generar un Keystore (las credenciales de firma de tu app), si es tu primera vez, presiona "Y" (Yes) para que Expo lo genere y lo guarde automáticamente por ti (es la opción más cómoda)

Ahora tu código se subirá a los servidores de Expo, te aparecerá un enlace en la terminal en el que puedes darle clic para ver el progreso en la web

> Hay que esperar, Dependiendo de la cola de Expo, puede tardar entre 5 y 15 minutos, y al finalizar, la misma terminal te dará un enlace directo para descargar el archivo .apk o un código QR. Lo descargas, lo pasas a tu teléfono, lo instalas ¡y listo!

## Estrucutura del proyecto

```text
financial-tracker-mobile/
├── assets/         # Imágenes, iconos, fuentes y recursos visuales estáticos
├── src/            # Código fuente principal de la aplicación (componentes, vistas, lógica)
├── App.tsx         # Punto de entrada principal de la aplicación
├── app.json        # Configuración general de Expo
├── tsconfig.json   # Configuración estricta de TypeScript
└── package.json    # Dependencias y scripts del proyecto
```

### Arquitectura del proyecto

- **Patrón de componentes** reutilizables y tipados
- **Context API** para gestión global de temas
- **Storage layer** abstracción para persistencia
- **Type-safe navigation** con tipos personalizados
- **Utilities formatters** para consistencia en presentación de datos

### Ventajas competitivas

✅ **Tipo Seguro**: Desarrollado completamente en TypeScript para evitar bugs en tiempo de ejecución
✅ **Rendimiento Optimizado**: Arquitectura nativa compilada que genera APK/IPA de máximo rendimiento
✅ **Multiplataforma**: Una sola base de código para iOS, Android y Web
✅ **Datos Seguros**: Almacenamiento local encriptado sin sincronización a servidores externos
✅ **UI/UX Moderna**: Componentes diseñados con principios de Material Design y HIG
✅ **Escalable**: Estructura preparada para extensiones futuras y nuevas funcionalidades
✅ **Cloud-Ready**: Arquitectura lista para integración con backends (Firebase, Supabase, etc.)

## Proximos pasos (Features)

- [ ] Desarrollar un módulo de exportación y serialización de datos para la generación de reportes financieros locales en formatos estandarizados (PDF y CSV).
- [ ] Integrar un servicio de notificaciones Push (ej. Expo Notifications o FCM) para la calendarización y emisión de alertas sobre pagos próximos.
- [ ] Implementar detección del Locale o geolocalización del dispositivo durante el flujo de Onboarding (primer inicio) para configurar automáticamente el tipo de moneda por defecto.
- [ ] Ampliar la lógica del motor de búsqueda en el listado de gastos para soportar el filtrado condicional por parámetros de categoría.
- [ ] Implementar tematización dinámica en la UI para asignar y renderizar colores personalizables según la categoría del gasto.
- [ ] Implementar gestos de arrastre (PanResponder o componente Swipeable Bottom Sheet) en la tarjeta de ingreso de gastos para mejorar la ergonomía de la interfaz.
- [ ] Aplicar renderizado condicional en los estilos (Conditional Styling) del balance de ahorros para aplicar feedback visual (cambio de color) cuando el valor sea negativo.
---
<p align="center">Construido con ❤️ para tu tranquilidad financiera</p>
