# Commits de la migración a Kotlin Multiplatform

> **Ya commiteado y publicado en `dev`** (`git log --oneline -5`). Al hacerlo, `PdfModel`, `NotificationPlans` y
> `WidgetSummary` quedaron en el commit 2 (con el resto de `domain/` y `reports/`), y el workflow de CI en macOS se dejó
> fuera por ahora. Lo que sigue es la guía original, útil para revisar cada commit con `git show <hash>`.

Cada bloque indica el mensaje, las rutas y qué revisar.

Antes del primer commit, si no quieres que el commit 1 arrastre las renombraciones, ejecuta `git reset -q` (no toca el
árbol de trabajo). Después, en cada grupo, usa `git add -A` sobre la ruta antigua (`app/...`) y la nueva (`shared/...`)
para que git registre la renombración.

Los grupos separan por intención, pero no son independientes: `PdfModel`, `NotificationPlans` y `WidgetSummary` se usan
desde `app` (grupo 2) y están en el grupo 4. Por eso los commits intermedios no están garantizados a compilar; sólo el
último se verifica. Si quieres que cada commit compile por sí solo, mueve esos tres archivos al grupo 2.

Revisar antes de commitear:

```bash
git status --short
git diff --cached --stat
git ls-files -s gradlew                       # debe mostrar 100755
./gradlew :shared:compileCommonMainKotlinMetadata :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Sugerencias para `.gitignore` (no aplicadas; el archivo ya tiene una línea `knowledge_context.md` que no es de este trabajo):

```gitignore
# iOS (proyecto de Xcode generado con XcodeGen)
iosApp/MiDinero.xcodeproj/
iosApp/**/Info.plist
iosApp/**/*.entitlements
xcuserdata/
*.xcuserstate
DerivedData/
.konan/
```

## 1. `build: módulo shared Kotlin Multiplatform`

Añade el módulo `:shared` con Kotlin 2.0.21 y Compose Multiplatform 1.7.3, y deja `:app` dependiendo de él. El wrapper
baja de Gradle 9.7.1 a 8.14.3, porque el plugin Multiplatform de Kotlin 2.0.21 falla en Gradle 9. Incluye los archivos
`Platform` de cada conjunto de fuentes (`expect`/`actual`).

```bash
git add build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml \
        gradle/wrapper/gradle-wrapper.properties shared/build.gradle.kts app/build.gradle.kts \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/shared \
        shared/src/androidMain/kotlin/com/mixagames666/trackerrntemp/shared \
        shared/src/iosMain/kotlin/com/mixagames666/trackerrntemp/shared
git commit -m "build: módulo shared Kotlin Multiplatform"
```

Revisar: `gradle/libs.versions.toml` (Compose Multiplatform 1.7.3 con las mismas versiones de Jetpack Compose que el BOM
2024.12.01), `shared/build.gradle.kts` (destinos Android e iOS y la tarea `embedAndSignAppleFrameworkForXcode`), y que
`Platform.kt` (commonMain) no dependa de Android ni de iOS.

## 2. `refactor(shared): dominio, datos y utilidades en commonMain`

Mueve a `shared/commonMain` el modelo, el almacén JSON, los repositorios, el dominio, el generador CSV y las utilidades
(con sus `expect`/`actual` para archivos, hilos, candados, ICU y logs). `CoreContainer` reúne las dependencias
compartidas. En `app` quedan sólo los puntos de llamada y las pruebas que ahora apuntan a `shared`.

```bash
git add -A app/src/main/java/com/mixagames666/trackerrntemp/data \
           app/src/main/java/com/mixagames666/trackerrntemp/domain \
           app/src/main/java/com/mixagames666/trackerrntemp/util \
           app/src/main/java/com/mixagames666/trackerrntemp/reports/ReportGenerator.kt \
           app/src/main/java/com/mixagames666/trackerrntemp/notifications/PaymentNotifications.kt \
           app/src/main/java/com/mixagames666/trackerrntemp/reports/PdfReport.kt \
           app/src/main/java/com/mixagames666/trackerrntemp/widget/SummaryWidget.kt \
           app/src/test/java/com/mixagames666/trackerrntemp/util/SharedPortabilityTest.kt \
           app/src/test/java/com/mixagames666/trackerrntemp/data \
           app/src/test/java/com/mixagames666/trackerrntemp/golden
git add shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/CoreContainer.kt \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/data \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/domain/{Analytics,CardPurchases,Dashboard,DataSync,PaymentReminders,Services}.kt \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/reports/ReportGenerator.kt \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/util \
        shared/src/androidMain/kotlin/com/mixagames666/trackerrntemp/util
git commit -m "refactor(shared): dominio, datos y utilidades en commonMain"
```

Revisar: `app/.../reports/ReportGenerator.kt` aparece como archivo nuevo en `app` además de la renombración a `shared`;
confirma qué contiene antes de commitear. `SharedPortabilityTest` comprueba que el código movido no usa APIs de Android.
Las pruebas doradas (`golden/`) deben seguir con las mismas expectativas que antes del cambio.

## 3. `refactor(shared): UI en Compose Multiplatform`

Mueve toda la interfaz (`ui/`, pantallas, componentes, tema, navegación y gráficos) a `shared/commonMain`, junto con los
recursos (fuentes de iconos, mapa de glifos, icono del splash). Añade `AppServices` como interfaz que la plataforma
implementa, y los `actual` de la interfaz para Android. `AppContainer` pasa a implementar `AppServices`.

```bash
git add -A app/src/main/java/com/mixagames666/trackerrntemp/ui \
           app/src/main/assets/ionicons_glyphmap.json \
           app/src/main/res/font
git add shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/ui \
        shared/src/commonMain/composeResources \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/platform/AppServices.kt \
        shared/src/androidMain/kotlin/com/mixagames666/trackerrntemp/ui \
        shared/src/androidMain/kotlin/com/mixagames666/trackerrntemp/platform \
        app/src/main/java/com/mixagames666/trackerrntemp/AppContainer.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/MainActivity.kt
git commit -m "refactor(shared): UI en Compose Multiplatform"
```

Revisar: `SheetDialog` y `WindowMetrics` (commonMain y actuals de Android), `AppContainer` (implementa `AppServices`) y
`MainActivity`. Las pantallas de `ui/` incluyen cambios de diseño y de UX de otros commits; si el diff muestra cambios de
estilo que no son de la migración, sepáralos con `git add -p`.

## 4. `feat(ios): app iOS y widget`

App SwiftUI «MiDinero» que aloja la UI de Compose (`MainViewController`), extensión WidgetKit que lee
`widget-summary.json` del App Group, implementaciones iOS (`IosAppServices`, archivos, notificaciones, PDF, presentación),
y pruebas comunes que también se ejecutan en el simulador. El workflow de CI en macOS quedó fuera por ahora.

```bash
git add shared/src/iosMain \
        shared/src/commonTest \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/domain/{NotificationPlans,WidgetSummary}.kt \
        shared/src/commonMain/kotlin/com/mixagames666/trackerrntemp/reports/PdfModel.kt \
        iosApp
git commit -m "feat(ios): app iOS y widget"
```

Revisar: `iosApp/project.yml` (identificadores y App Group), `iosApp/README.md` (pasos de
configuración, enlazado desde el README principal). Este grupo nunca se ha
compilado: el primer resultado real llega al compilarlo en un Mac.

## 5. `docs: Kotlin Multiplatform`

Sección «Kotlin Multiplatform (Android + iOS)» en el README, con la estructura de módulos, qué es compartido, cómo compilar
cada plataforma y el estado honesto de iOS. Corrige las rutas, la versión de Gradle y el recuento de pruebas de la
sección «Proyecto y stack» y «Verificado y pendiente».

```bash
git add README.md docs/commits-kmp.md
git commit -m "docs: Kotlin Multiplatform"
```

Si prefieres no versionar este archivo, quítalo del `git add` y bórralo.
