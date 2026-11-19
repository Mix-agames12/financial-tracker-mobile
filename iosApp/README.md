# Mi Dinero para iOS

App de iOS (SwiftUI) que aloja la UI de Compose Multiplatform del módulo `:shared`, más una extensión de widget
(WidgetKit) con el resumen financiero. El proyecto de Xcode **no se versiona**: se genera con
[XcodeGen](https://github.com/yonaskolb/XcodeGen) a partir de `project.yml`.

```
iosApp/
├── project.yml                     # definición del proyecto (XcodeGen)
├── MiDinero/                       # app: MiDineroApp.swift, ContentView.swift, Assets.xcassets (icono)
└── MiDineroWidget/                 # extensión del widget (Swift puro, lee widget-summary.json)
```

## Requisitos

- Un Mac con **Xcode 15 o superior** (Kotlin/Native sólo compila los destinos iOS en macOS).
- **JDK 17** (`brew install --cask temurin@17`) y `JAVA_HOME` apuntando a él.
- **XcodeGen**: `brew install xcodegen`.

## Compilar y ejecutar en el simulador

```bash
cd iosApp
xcodegen generate          # crea MiDinero.xcodeproj
open MiDinero.xcodeproj
```

En Xcode elige el esquema **MiDinero**, un simulador (iOS 15 o posterior) y pulsa Run. Antes de compilar Swift,
una fase de script ejecuta `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, que compila el módulo `shared`
y deja el framework estático `Shared` en `shared/build/xcode-frameworks/<Configuración>/<SDK>` (la primera vez
descarga el toolchain de Kotlin/Native y tarda varios minutos). Los recursos de Compose (fuentes de iconos, imagen
del splash) los copia esa misma tarea dentro de la app.

Si cambias `project.yml`, vuelve a ejecutar `xcodegen generate`.

Desde la terminal (igual que la integración continua):

```bash
cd iosApp && xcodegen generate
xcodebuild -project MiDinero.xcodeproj -scheme MiDinero -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' ARCHS=arm64 CODE_SIGNING_ALLOWED=NO build
```

## Ejecutar en un dispositivo y firmar

1. En Xcode, selecciona el target **MiDinero** > *Signing & Capabilities* y elige tu **Team** (también en el target
   **MiDineroWidget**). Alternativa: pon tu identificador de equipo en `DEVELOPMENT_TEAM` de `project.yml` y
   regenera.
2. Si el identificador `com.mixagames666.trackerrntemp` ya está tomado por otra cuenta, cámbialo en `project.yml`
   (app y `.widget`) y también el grupo de apps (ver abajo).
3. Conecta el iPhone, actívalo como destino y pulsa Run (primera vez: *Ajustes > General > VPN y gestión de
   dispositivos* para confiar en tu certificado de desarrollador).

## App Group (datos compartidos con el widget)

La app guarda sus datos y escribe `widget-summary.json` en el contenedor del grupo de apps
`group.com.mixagames666.trackerrntemp`; la extensión del widget lee ese archivo (no usa el framework de Kotlin).
Para que funcione en dispositivo:

1. En [developer.apple.com](https://developer.apple.com/account/resources/identifiers/list/applicationGroup) crea
   el *App Group* `group.com.mixagames666.trackerrntemp`.
2. En los dos targets (*Signing & Capabilities* > *+ Capability* > *App Groups*) marca ese grupo (Xcode con firma
   automática lo registra y lo añade a los App IDs; los `.entitlements` ya lo declaran).
3. Si usas otro identificador de grupo, cámbialo en `project.yml` (entitlements de ambos targets),
   en `SummaryStore.appGroup` (`MiDineroWidget.swift`) y en `IOS_APP_GROUP` (`shared/.../platform/IosAppServices.kt`).

Sin el grupo disponible (por ejemplo una compilación sin firmar), la app sigue funcionando y guarda los datos en su
carpeta Documents, pero el widget mostrará «Abre la app para ver tu resumen». Los datos guardados en Documents no se
migran al grupo si más adelante se habilita.

## Notificaciones

Los recordatorios de pago (09:00 del día anterior o del mismo día) y el aviso del límite de gastos son
notificaciones locales (`UNUserNotificationCenter`). El permiso se pide desde la app (Configuración). iOS admite
64 notificaciones pendientes; la app programa como máximo 50.

## TestFlight / App Store

1. Cambia `MARKETING_VERSION` y `CURRENT_PROJECT_VERSION` en `project.yml` (cada subida necesita un número de
   compilación nuevo) y regenera.
2. En Xcode: destino *Any iOS Device (arm64)* > *Product > Archive* > *Distribute App* > *App Store Connect*.
3. El icono (`Assets.xcassets/AppIcon.appiconset`) es una única imagen de 1024×1024 sin transparencia, compuesta con
   el fondo y el primer plano del icono de Android.
4. `ITSAppUsesNonExemptEncryption` está en `false` (la app no usa cifrado propio), así que App Store Connect no
   pregunta por cumplimiento de exportación en cada subida.

## Pendiente de verificar en Mac

- Color de la barra de estado cuando el tema de la app difiere del del sistema (`SystemBarsEffect` en
  `AppServices.ios.kt` aún no la controla).
- Ancho de pantalla tras rotar el iPad (`screenWidthDp` no se recompone al rotar); el iPhone va fijo en vertical.
- El PDF de iOS simplifica algunos detalles del de Android (líneas punteadas, texto en una sola línea en la
  cabecera); ver el comentario de `IosPdfWriter.kt`.
