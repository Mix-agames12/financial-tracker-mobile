import SwiftUI
import Shared

/// Aloja el `UIViewController` de Compose Multiplatform que crea el módulo `shared`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose dibuja bajo las barras del sistema y aplica por su cuenta los márgenes de zona segura y teclado.
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
