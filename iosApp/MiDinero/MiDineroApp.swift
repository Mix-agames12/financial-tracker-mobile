import SwiftUI
import WidgetKit
import Shared

/// Recarga las líneas de tiempo del widget cuando el código Kotlin termina de escribir `widget-summary.json`
/// (`WidgetCenter` sólo existe en Swift, por eso Kotlin lo pide a través de este puente).
private final class WidgetReloaderImpl: NSObject, WidgetReloader {
    func reload() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}

@main
struct MiDineroApp: App {
    // El puente se registra antes de que se cree la pantalla (y con ella el contenedor de servicios de Kotlin).
    private let widgetReloader = WidgetReloaderImpl()

    init() {
        IosBridge.shared.widgetReloader = widgetReloader
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
