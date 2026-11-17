# Mi Dinero — versión nativa Android (Kotlin + Jetpack Compose)

Traslado 1:1 de la app Expo / React Native de este repositorio (`App.tsx` y `src/`) a un proyecto Android
nativo que se abre y compila con Android Studio. El código React Native **no se toca**: sigue en su sitio como
referencia para comparar lado a lado.

- Paquete: `com.mixagames666.trackerrntemp` (el mismo que la app React Native).
- `minSdk` 24 · `compileSdk`/`targetSdk` 35 · Kotlin 2.0.21 · AGP 8.6.1 · Gradle 8.8 · Compose BOM 2024.12.01.
- Sin Hilt ni Room: inyección manual (`AppContainer`) y datos en documentos JSON (ver [Datos](#datos)).

## Qué incluye

Todo lo que hace la app React Native, con los mismos textos en español, validaciones y mensajes:

| Área | Contenido |
|---|---|
| Navegación | 5 pestañas (Inicio, Gastos, Ingresos, Préstamos, Análisis) + Configuración apilada, tema claro/oscuro, splash animado, toasts, hojas inferiores arrastrables, pull-to-refresh, recarga al enfocar |
| Gastos | alta/edición/borrado, filtros (fechas, categorías, método), recurrentes (mensual / día / anual), diferidos, motor de impuestos, validaciones de cupo/saldo/balance, compras con tarjeta ↔ préstamo ↔ saldo de tarjeta |
| Ingresos | sueldo (con aviso «Día de sueldo»), cuentas, tarjetas de crédito, fondos de inversión (depósito mensual → gasto), ingresos extra |
| Préstamos | cuotas, pago de cuota (genera gasto y baja el saldo de la tarjeta), tarjetas agrupadas por ciclo de corte |
| Análisis | línea / pastel / barras, comparativa mensual con filtros |
| Configuración | tema, nombre, moneda (18 + detección por región), meta mensual, impuestos, colores de categorías, reportes PDF/CSV, backup JSON (exportar/importar), borrar todo |
| Plataforma | notificaciones locales (recordatorios de pago 09:00 + alerta de presupuesto 90 %/100 %), widget «Resumen financiero» 4×2 |

## Abrir y compilar

Requisitos: **Android Studio Koala (2024.1.2) o posterior**, JDK 17+ (el JBR que trae el IDE sirve) y el SDK de
Android 35 (platform + build-tools).

1. *File → Open* y elegir la carpeta **`android-kotlin`** (no la raíz del repositorio).
2. Dejar que Gradle sincronice (Android Studio crea `local.properties` con `sdk.dir`; no se versiona).
3. *Run ▶* sobre un emulador o dispositivo (API 24 o superior).

Desde la terminal, dentro de `android-kotlin/`:

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # pruebas JVM (vectores dorados + utilidades)
./gradlew lintDebug
./gradlew assembleRelease      # APK sin firmar (ver "Publicar")
```

En Linux/macOS (o en CI) el wrapper necesita permiso de ejecución: `chmod +x gradlew` (si el proyecto se versionó
desde Windows, `git update-index --chmod=+x gradlew` lo deja guardado en el repositorio).

**Windows y rutas largas.** Si el repositorio está en una ruta larga y `LongPathsEnabled` está en 0, Gradle
falla al escribir en `build/`. Define `ANDROID_KOTLIN_BUILD_ROOT` con una carpeta corta (p. ej. `C:\tgb`) y el
`build.gradle.kts` raíz redirigirá allí todas las salidas (el APK queda en `C:\tgb\app\outputs\apk\debug`).
En otros sistemas no hace falta.

El build *debug* usa el sufijo `.debug` (`com.mixagames666.trackerrntemp.debug`) y la etiqueta
«tracker-rn-temp (Kotlin)», así convive con la app React Native instalada.

## Mapa React Native → Kotlin

Raíz del código: `app/src/main/java/com/mixagames666/trackerrntemp/`.

| React Native | Kotlin |
|---|---|
| `index.ts`, `App.tsx` | `MainActivity`, `TrackerApp` (Application), `AppContainer` (dependencias), `ui/AppRoot` (init de moneda/categorías/préstamos legacy, splash animado, sincronización al volver a primer plano) |
| `src/types/index.ts` | `data/Models.kt` (`@Serializable`, mismos campos opcionales que los JSON) |
| `src/db/storage.ts` | `data/JsonStore`, `data/Repositories`, `data/Aggregates`, `data/Backup`, `data/BackupFiles` |
| `utils/formatters.ts`, `currency.ts` | `util/Formatters`, `util/Currency`, `util/JsCompat` (semántica de JavaScript), `util/Time` |
| `utils/cardPurchases.ts`, `paymentReminders.ts`, `dataSync.ts` | `domain/CardPurchases`, `domain/PaymentReminders`, `domain/DataSync` |
| lógica dentro de las pantallas (guardar gasto, pagar cuota, análisis…) | `domain/Services` (gastos, préstamos, tarjetas, inversiones), `domain/Dashboard`, `domain/Analytics` |
| `utils/localNotifications.ts`, `hooks/useNotifications.ts` | `notifications/PaymentNotifications`, `notifications/ReminderWorker` |
| `utils/reportGenerator.ts` | `reports/ReportGenerator` (rango, CSV), `reports/PdfReport` (PDF), `reports/ShareFile` |
| `src/widgets/*` | `widget/SummaryWidget` (Jetpack Glance) + `res/xml/summary_widget_info.xml` |
| `theme/colors.ts`, `ThemeContext.tsx` | `ui/theme/Colors`, `Theme`, `ThemeController` |
| `components/*` | `ui/components/*` (`BottomSheet`, `Button`, `Card`, `Chip`, `ProgressBar`, `AppTextField`, `DatePickerSheet`, `CurrencyPicker`, `WelcomeSheet`, `ActionFeedback`) + `Ionicon`, `AlertHost` (≙ `Alert.alert`), `AutoSizeText` (≙ `adjustsFontSizeToFit`) |
| `navigation/*` | `ui/navigation/AppNavigation` |
| `screens/*Screen.tsx` | `ui/screens/<x>/<X>Screen` + `<X>ViewModel`; gráficos en `ui/charts` |

## Datos

- Cada colección es un archivo `files/stores/<nombre>.json` con un arreglo, igual que cada clave
  `@tracker_gastos_<nombre>` de AsyncStorage. Mismos nombres de campo, mismos ids y misma semántica
  (`add`, `update` con fusión, `getByDateRange`…). Las escrituras son atómicas (archivo temporal + rename) y cada
  colección tiene su propio candado.
- **El backup JSON es idéntico** al de la app React Native (`_exportDate` + una clave por colección): un backup
  exportado en una versión se importa en la otra.
- **Migración desde la app React Native.** Si esta versión se instala *sobre* la de React Native (mismo paquete y
  misma firma), `LegacyAsyncStorageImporter` copia una sola vez las colecciones de `databases/RKStorage`
  (la base SQLite de AsyncStorage) al primer arranque, antes de leer o escribir nada. Nunca pisa datos ya
  guardados por esta app. El receptor del widget se llama `Resumen` como en React Native, de modo que el
  widget ya colocado en la pantalla de inicio sigue funcionando.

## Cómo se comprobó la paridad

`tools/golden/` compila con `tsc` la lógica **original** en TypeScript (`formatters`, `cardPurchases`,
`paymentReminders`, `storage`, `reportGenerator`, `currency`) con *stubs* de AsyncStorage y de los módulos
`expo-*`, reloj congelado (2026-11-17 12:00, `America/Guayaquil`) e ids normalizados. Ejecuta 18 escenarios
(compras a crédito diferidas, cambio de monto/tarjeta/método, impuestos, pago de todas las cuotas, vinculación
legacy, próximos pagos, agregados, dashboard y aviso de sueldo, análisis en martes/domingo/enero, CSV y modelo
del PDF, categorías y ajustes, round-trip del backup…) y guarda el resultado en
`app/src/test/resources/golden/*.json`. Los tests de Kotlin (`golden/GoldenTest`) repiten cada escenario con el
código portado y exigen el mismo resultado en cada paso y el mismo estado final de las colecciones.

```bash
# Regenerar los vectores desde el código TypeScript (requiere `npm install` en la raíz o RN_NODE_MODULES)
node android-kotlin/tools/golden/run.js
# Compararlos con Kotlin
./gradlew testDebugUnitTest
```

La semántica de JavaScript que el resto del código necesita (`toFixed`, `parseFloat`, `Math.round`, número→texto,
desborde de fechas) también se compara contra Node en el escenario `js-and-formatters`. Aparte hay pruebas del
almacén JSON y de los serializadores tolerantes (tipos distintos, archivo corrupto, altas concurrentes, registros
ilegibles que se conservan al guardar) y del importador de AsyncStorage.

## Decisiones y diferencias deliberadas

| Tema | Decisión |
|---|---|
| UI | Jetpack Compose + Material 3 con los tokens de `colors.ts` como tema propio (sin colores dinámicos). Tema oscuro por defecto, como el original. |
| Iconos | `Ionicons.ttf` y el glifo `piggy-bank` de MaterialCommunityIcons embebidos: los nombres guardados en las categorías (`restaurant`, `car`…) siguen funcionando. |
| Gráficos | Canvas propios que replican `react-native-chart-kit` (línea bezier con eje Y fijo, pastel con leyenda, barras). |
| PDF | `android.graphics.pdf.PdfDocument` dibuja el mismo contenido que el HTML de `reportGenerator.ts` (el modelo de datos del PDF se compara con el HTML original en las pruebas). |
| Notificaciones | Canal «Recordatorios y alertas» + WorkManager (un trabajo único por recordatorio, 09:00 del día anterior o del mismo día, máx. 50 en 60 días) + permiso `POST_NOTIFICATIONS` en Android 13+. |
| Widget | Jetpack Glance, mismo tamaño (4×2), textos y paleta clara/oscura; refresco cada 30 min y tras cada cambio de datos. |
| Formatos | `formatCurrency`/`formatDate*` usan `android.icu` (la misma base ICU que el `Intl` de Hermes), con 2 decimales y `es-EC` fijo para fechas. |
| Nombre | El launcher se llama `tracker-rn-temp` (`strings.xml → app_name`); el nombre que se ve dentro de la app («Mi Dinero») es un ajuste del usuario. |
| Versión | `versionCode = 1`, `versionName = "1.0.0"`: EAS gestionaba el código remotamente, súbelo por encima del último publicado antes de actualizar una instalación existente. |

## Quirks del original que se conservan

Se portaron tal cual para no cambiar el comportamiento; quedan listados por si quieres corregir alguno más adelante.

- El atajo «Hace 1 mes» del selector de fecha (y los meses de Análisis) resta un mes con el desborde de `Date` de JavaScript: `setMonth(-1)` un día 31 cae el 3 del mes siguiente.
- La semana de Análisis empieza en domingo (`getDay()`).
- El período 2 de la comparativa de Análisis vale por defecto el mes anterior, pero en enero se queda en enero (`Math.max(1, mes)`) en vez de saltar a diciembre.
- El tema por defecto es oscuro aunque el sistema esté en claro; un ajuste `system` deja el tema claro hasta que cambie el esquema del sistema.
- `clearAllData` vacía todas las colecciones menos los ajustes, categorías incluidas, hasta el siguiente arranque (que vuelve a sembrarlas).
- `formatDate` y `formatDateShort` usan siempre `es-EC`, aunque se cambie la moneda/región.
- `update()` de un id inexistente lanza una excepción, como en `storage.ts`.

## Lo que no se porta

- Código muerto del original: `BarChart`/`barData`/`barIncomeAcc`/`chartConfig` de Análisis (se calculan pero nunca se dibujan), `spacing`/`radius`, `SalaryRepo.get/delete`, `ExpenseRepo.getByCategory`.
- Los apaños de `react-native-chart-kit` (`forceVariation`: con datos planos el original dibuja un punto de 1,00 en el último día; valor centinela `0.01` en las barras) — con gráficos propios no hacen falta.

## Publicar

No hay *keystore* en el repositorio. Para **actualizar** una instalación de la app React Native hay que firmar con la
misma clave (EAS la guarda: `eas credentials` permite descargarla):

1. Crea `keystore.properties` (fuera del control de versiones) y añade un `signingConfigs.release` en
   `app/build.gradle.kts`, o usa *Build → Generate Signed Bundle / APK* en Android Studio.
2. Sube `versionCode` por encima del último publicado.
3. `isMinifyEnabled` está en `false`; si lo activas, `app/proguard-rules.pro` ya conserva lo necesario y
   `kotlinx.serialization`, WorkManager y Glance publican sus propias reglas.

## Verificado y pendiente

Verificado en esta rama: compilación *debug* y *release* sin errores, 29 pruebas JVM en verde (18 escenarios
dorados + 11 de almacén e importador), `lintDebug` sin errores, y recorrido en un emulador Pixel 7a con Android 15 de: Inicio
(incl. aviso de sueldo), Gastos (alta, detalle, borrado), Ingresos, Préstamos (grupos de tarjeta, pago de cuota con
impuestos), Análisis, Configuración (tema, reportes PDF y CSV con el selector de compartir, exportar/importar
backup, borrar todo), notificaciones (canal, permiso, recordatorios programados, alerta de presupuesto), widget en el
launcher (render y toque que abre la app) y la migración desde una base `RKStorage` simulada.

No se pudo comprobar aquí:

- La apariencia pixel a pixel frente a la app React Native en un dispositivo real (no hay una compilación RN instalable en este entorno).
- La actualización sobre un APK firmado por EAS (requiere el *keystore*); la migración de datos se probó con una base SQLite del mismo esquema que AsyncStorage.
- Android 7–12 (sólo se probó API 35): en ellos no hay permiso de notificaciones en tiempo de ejecución y el widget usa el diseño por defecto de Glance.
- Pruebas instrumentadas de UI (no hay).
