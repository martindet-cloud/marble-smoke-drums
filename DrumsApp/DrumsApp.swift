import SwiftUI

@main
struct DrumsApp: App {
    @StateObject private var machine = DrumMachine()

    var body: some Scene {
        WindowGroup {
            DrumView().environmentObject(machine)
                .preferredColorScheme(.dark)
        }
    }
}
