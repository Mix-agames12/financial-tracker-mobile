# Commits propuestos para revisar los cambios (rama `dev`)

Nada de esto está commiteado: son cambios en el árbol de trabajo. Cada bloque indica el mensaje, los archivos y qué
revisar. Las pantallas (`DashboardScreen`, `ExpensesScreen`, `IncomeScreen`, `LoansScreen`, `SettingsScreen`,
`AnalyticsScreen`) contienen a la vez cambios de UX (commit 2) y de diseño (commit 3); como no se pueden separar por
archivo, van en el commit 3. Si quieres separarlos usa `git add -p`.

Revisar antes de commitear:

```bash
git status --short
git diff --stat
./gradlew testDebugUnitTest lintDebug assembleDebug   # 41 pruebas, lint sin errores
```

## 1. `fix(ui): hojas inferiores sin hueco bajo la tarjeta`

Causa: el IME reservaba 63 px (altura de la barra de navegación) sin dibujar teclado (emulador con teclado físico) y
Android recentraba la ventana del diálogo, dejando una franja sin oscurecer. Ahora la ventana se ancla arriba y la
hoja reserva el espacio del teclado dentro de sí misma. Probado con y sin teclado real.

```bash
git add app/src/main/java/com/mixagames666/trackerrntemp/ui/components/BottomSheet.kt
git commit -m "fix(ui): hojas inferiores sin hueco bajo la tarjeta"
```

Revisar: `BottomSheet.kt` (gravedad TOP, `Spacer(windowInsetsBottomHeight(ime))`, parámetro `scrollState`).

## 2. `feat(ux): límite de gastos mensual y formularios más claros`

- «Meta» pasa a «Límite de gastos mensual» en Dashboard, Configuración y notificación (el campo `monthlyGoal` no cambia).
- Errores en línea (`FieldError`), prefijo de moneda, texto de apoyo, selección del texto al editar.
- `FormColumn`: elimina el margen doble entre campos (~44 dp → 16 dp).
- Barra de filtros activos, estados vacíos con acción, avisos destructivos en rojo, botones de 48 dp.

```bash
git add app/src/main/java/com/mixagames666/trackerrntemp/util/Formatters.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/notifications/PaymentNotifications.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/AlertHost.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/AppTextField.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/FormParts.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/Button.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/Chip.kt \
        app/src/test/java/com/mixagames666/trackerrntemp/util/PercentFormatTest.kt
git commit -m "feat(ux): límite de gastos mensual y formularios más claros"
```

Revisar: `formatPercentLocalized` (separador decimal de la moneda; `formatPercent` original no se toca por las pruebas
doradas), `FormColumn`/`LocalFieldBottomPadding`, `AppTextField` (TextFieldValue).

## 3. `feat(ui): rediseño visual y contraste accesible`

- Tokens de texto con contraste ≥ 4,5:1 (`primaryText`, `successText`, `warningText`, `errorText`) y ajustes mínimos de
  paleta (`primary` ~3 % más oscuro, `onSurfaceVariant` más claro en oscuro).
- Tarjetas con borde y degradado, píldora en la pestaña activa, barra de progreso animada, `EmptyState`, cifras
  tabulares (`MoneyStyle`), íconos decorativos fuera del árbol de accesibilidad.
- Pantallas: formularios, filtros, estados vacíos, tarjetas de resumen.

```bash
git add app/src/main/java/com/mixagames666/trackerrntemp/ui/theme \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/Card.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/ProgressBar.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/Ionicon.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/AutoSizeText.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/components/EmptyState.kt \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/navigation \
        app/src/main/java/com/mixagames666/trackerrntemp/ui/screens \
        app/src/test/java/com/mixagames666/trackerrntemp/ui
git commit -m "feat(ui): rediseño visual y contraste accesible"
```

Revisar: `Colors.kt` (`readableOn`, tokens nuevos), `ThemeContrastTest`, y en `SettingsScreen` el selector PDF/CSV
(`onPrimaryContainer` sobre `primaryContainer`).

## 4. `docs: galería funcional con capturas en el README`

- Cabecera, galería por secciones y notas de usabilidad/contraste en `README.md`.
- 24 imágenes en `docs/screenshots/` (~1 MB, datos de demostración ficticios) y `*.png binary` en `.gitattributes`.

```bash
git add README.md .gitattributes docs/screenshots docs/commits-propuestos.md
git commit -m "docs: galería funcional con capturas en el README"
```

Si prefieres no versionar este archivo, quítalo del `git add` y bórralo.
