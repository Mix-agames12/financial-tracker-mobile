import SwiftUI
import WidgetKit

// Widget "Resumen financiero": balance total, ingresos y gastos del mes y próximo pago. Es Swift puro: no usa el
// framework de Kotlin, sólo lee `widget-summary.json`, que la app escribe en el grupo de apps cada vez que cambian
// los datos (mismo contenido que el widget de Android).

/// Mismo contrato que `WidgetSummary` de `shared` (campos ya formateados).
struct SummaryData: Codable {
    let appName: String
    let monthLabel: String
    let balance: String
    let isNegative: Bool
    let monthIncome: String
    let monthExpenses: String
    let nextPayment: String?
    let updatedAt: String
}

enum SummaryStore {
    static let appGroup = "group.com.mixagames666.trackerrntemp"
    static let fileName = "widget-summary.json"

    static func load() -> SummaryData? {
        guard let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup) else { return nil }
        guard let data = try? Data(contentsOf: folder.appendingPathComponent(fileName)) else { return nil }
        return try? JSONDecoder().decode(SummaryData.self, from: data)
    }
}

struct SummaryEntry: TimelineEntry {
    let date: Date
    let summary: SummaryData?
}

struct SummaryProvider: TimelineProvider {
    private static let sample = SummaryData(
        appName: "Mi Dinero", monthLabel: "Enero 2025", balance: "$1,250.00", isNegative: false,
        monthIncome: "$2,000.00", monthExpenses: "$750.00", nextPayment: "Alquiler · 05 feb · $400.00", updatedAt: "09:00"
    )

    func placeholder(in context: Context) -> SummaryEntry {
        SummaryEntry(date: Date(), summary: Self.sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (SummaryEntry) -> Void) {
        completion(SummaryEntry(date: Date(), summary: SummaryStore.load() ?? Self.sample))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SummaryEntry>) -> Void) {
        let entry = SummaryEntry(date: Date(), summary: SummaryStore.load())
        // La app pide recargar al cambiar los datos; además se relee cada hora (el próximo pago cambia con la fecha).
        let next = Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date().addingTimeInterval(3600)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }
}

// Paleta clara/oscura del widget de Android (SummaryWidget.kt).
private extension Color {
    init(light: UInt32, dark: UInt32, scheme: ColorScheme) {
        let hex = scheme == .dark ? dark : light
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

struct SummaryWidgetView: View {
    let entry: SummaryEntry
    @Environment(\.colorScheme) private var scheme

    private var text: Color { Color(light: 0x0F172A, dark: 0xF1F5F9, scheme: scheme) }
    private var muted: Color { Color(light: 0x475569, dark: 0x94A3B8, scheme: scheme) }
    private var income: Color { Color(light: 0x059669, dark: 0x34D399, scheme: scheme) }
    private var expense: Color { Color(light: 0xE11D48, dark: 0xFB7185, scheme: scheme) }
    private var accent: Color { Color(light: 0x4F46E5, dark: 0x818CF8, scheme: scheme) }
    private var background: Color { Color(light: 0xFFFFFF, dark: 0x1E293B, scheme: scheme) }

    var body: some View {
        Group {
            if let data = entry.summary {
                content(data)
            } else {
                VStack(spacing: 4) {
                    Text("Mi Dinero").font(.system(size: 13, weight: .bold)).foregroundColor(accent)
                    Text("Abre la app para ver tu resumen").font(.system(size: 11)).foregroundColor(muted)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .widgetBackground(background)
    }

    private func content(_ data: SummaryData) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(data.appName).font(.system(size: 13, weight: .bold)).foregroundColor(accent).lineLimit(1)
                Spacer()
                Text(data.monthLabel).font(.system(size: 11)).foregroundColor(muted)
            }
            Spacer(minLength: 0)
            Text("Balance total").font(.system(size: 11)).foregroundColor(muted)
            Text(data.balance)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(data.isNegative ? expense : text)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Spacer(minLength: 0)
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Ingresos del mes").font(.system(size: 10)).foregroundColor(muted)
                    Text(data.monthIncome).font(.system(size: 14, weight: .bold)).foregroundColor(income).lineLimit(1)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 0) {
                    Text("Gastos del mes").font(.system(size: 10)).foregroundColor(muted)
                    Text(data.monthExpenses).font(.system(size: 14, weight: .bold)).foregroundColor(expense).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            Text(footer(data))
                .font(.system(size: 10))
                .foregroundColor(muted)
                .lineLimit(1)
        }
    }

    private func footer(_ data: SummaryData) -> String {
        if let next = data.nextPayment {
            return "Próximo pago: \(next)"
        }
        return "Actualizado \(data.updatedAt)"
    }
}

private extension View {
    /// iOS 17 exige `containerBackground`; antes, un fondo normal.
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}

@main
struct MiDineroWidget: Widget {
    let kind = "MiDineroWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SummaryProvider()) { entry in
            SummaryWidgetView(entry: entry)
        }
        .configurationDisplayName("Resumen financiero")
        .description("Balance total, ingresos y gastos del mes y próximo pago.")
        .supportedFamilies([.systemMedium])
    }
}
