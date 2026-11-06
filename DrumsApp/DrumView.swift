import SwiftUI
import UniformTypeIdentifiers

private extension ColorToken {
    var color: Color {
        switch self {
        case .lime: Color(red: 0.88, green: 0.96, blue: 0.29)
        case .copper: Color(red: 0.94, green: 0.49, blue: 0.23)
        case .blue: Color(red: 0.40, green: 0.66, blue: 0.99)
        case .amber: Color(red: 0.87, green: 0.69, blue: 0.43)
        case .violet: Color(red: 0.84, green: 0.43, blue: 0.91)
        }
    }
}

struct DrumView: View {
    @EnvironmentObject private var machine: DrumMachine
    @State private var importing = false
    private var accent: Color { machine.accent.color }
    private var state: DrumState { machine.state }

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.height < 360
            VStack(spacing: compact ? 7 : 11) {
                header
                padRow(height: compact ? 63 : 80)
                controls
                sequencer(height: compact ? 65 : 83)
                footer
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(Color(red: 0.075, green: 0.082, blue: 0.080))
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.wav, .audio], allowsMultipleSelection: false) { result in
            if case .success(let urls) = result, let url = urls.first { machine.importSample(url) }
        }
        .sheet(item: Binding(get: { machine.exportURL.map(ShareItem.init) }, set: { _ in machine.exportURL = nil })) { item in
            ShareSheet(items: [item.url])
        }
        .alert("DRUMS", isPresented: Binding(get: { machine.error != nil }, set: { if !$0 { machine.error = nil } })) {
            Button("OK") { machine.error = nil }
        } message: { Text(machine.error ?? "") }
    }

    private var header: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text("MARBLE SMOKE").font(.system(size: 17, weight: .black, design: .rounded)).tracking(1)
                Text("DRUMS · POCKET EDITION").font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundStyle(accent)
            }.frame(width: 181, alignment: .leading)
            Spacer(minLength: 2)
            Menu {
                ForEach(0..<DrumMachine.artists.count, id: \.self) { index in
                    Button(DrumMachine.artists[index]) { machine.selectArtist(index) }
                }
            } label: { selector("ARTIST", DrumMachine.artists[state.artist]) }
            Menu {
                ForEach(0..<3, id: \.self) { index in
                    Button(DrumMachine.presets[state.artist][index]) { machine.selectPreset(index) }
                }
            } label: { selector("PRESET", DrumMachine.presets[state.artist][state.preset]) }
            Text(DrumMachine.artists[state.artist]).font(.system(size: 14, weight: .semibold, design: .serif)).italic()
                .foregroundStyle(accent).lineLimit(1).minimumScaleFactor(0.7)
                .frame(maxWidth: 130, alignment: .trailing)
        }.frame(height: 44)
    }

    private func selector(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundStyle(.gray)
            Text(value.uppercased() + "  ▾").font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundStyle(accent)
                .lineLimit(1).minimumScaleFactor(0.65)
        }.padding(.horizontal, 10).frame(width: 152, height: 42, alignment: .leading)
            .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 5))
            .overlay(RoundedRectangle(cornerRadius: 5).stroke(accent.opacity(0.45)))
    }

    private func padRow(height: CGFloat) -> some View {
        HStack(spacing: 5) {
            ForEach(0..<8, id: \.self) { lane in
                Button { machine.selectLane(lane) } label: {
                    VStack(alignment: .leading) {
                        HStack { Text(String(format: "%02d", lane + 1)); Spacer(); Circle().fill(lane == state.selected ? accent : .gray).frame(width: 5, height: 5) }
                            .font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundStyle(.gray)
                        Spacer()
                        Text(DrumMachine.names[lane]).font(.system(size: 11, weight: .heavy, design: .rounded))
                            .lineLimit(1).minimumScaleFactor(0.6).foregroundStyle(.white)
                        Capsule().fill(lane == state.selected ? accent : accent.opacity(0.35)).frame(height: 3)
                    }.padding(7).frame(maxWidth: .infinity).frame(height: height)
                        .background(Color(red: 0.17, green: 0.19, blue: 0.18), in: RoundedRectangle(cornerRadius: 6))
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(lane == state.selected ? accent : .gray.opacity(0.35), lineWidth: lane == state.selected ? 2 : 1))
                }.buttonStyle(.plain)
            }
        }
    }

    private var controls: some View {
        HStack(spacing: 9) {
            Text("PATTERN").font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundStyle(.gray)
            ForEach(0..<8, id: \.self) { bank in
                Button(String(UnicodeScalar(65 + bank)!)) { machine.selectBank(bank) }
                    .font(.system(size: 11, weight: .black, design: .monospaced))
                    .frame(width: 25, height: 25)
                    .background(bank == state.bank ? accent : Color.white.opacity(0.13), in: RoundedRectangle(cornerRadius: 3))
                    .foregroundStyle(bank == state.bank ? .black : .white)
            }
            Spacer(minLength: 0)
            Button("−") { machine.setBPM(state.bpm - 1) }.font(.title3)
            Text("\(state.bpm) BPM").font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundStyle(accent)
            Button("+") { machine.setBPM(state.bpm + 1) }.font(.title3)
            Spacer(minLength: 0)
            Button("SWING \(Int(state.swing * 100))%") { machine.setSwing(state.swing >= 0.6 ? 0 : state.swing + 0.1) }
                .font(.system(size: 10, weight: .bold, design: .monospaced))
        }.buttonStyle(.plain).frame(height: 26)
    }

    private func sequencer(height: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack {
                Text("STEP SEQUENCER / \(DrumMachine.names[state.selected])")
                Spacer()
                Text("16 STEPS · 1 BAR")
            }.font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundStyle(.gray)
            HStack(spacing: 4) {
                ForEach(0..<16, id: \.self) { step in
                    let active = state.patterns[state.bank][state.selected][step]
                    Button { machine.toggle(step) } label: {
                        VStack(spacing: 4) {
                            Text(String(format: "%02d", step + 1)).font(.system(size: 8, weight: .bold, design: .monospaced))
                            RoundedRectangle(cornerRadius: 3)
                                .fill(active ? accent : Color(red: 0.38, green: 0.39, blue: 0.36))
                                .overlay(RoundedRectangle(cornerRadius: 3).stroke(step == machine.playStep ? .white : .clear, lineWidth: 2))
                        }.foregroundStyle(.white).frame(maxWidth: .infinity)
                    }.buttonStyle(.plain)
                }
            }.frame(height: height - 18)
        }.padding(8).frame(height: height + 12)
            .background(Color(red: 0.12, green: 0.14, blue: 0.13), in: RoundedRectangle(cornerRadius: 6))
    }

    private var footer: some View {
        HStack(spacing: 10) {
            Button(machine.playing ? "■ STOP" : "▶ PLAY") { machine.togglePlay() }
                .foregroundStyle(.black).padding(.horizontal, 18).frame(height: 35)
                .background(accent, in: RoundedRectangle(cornerRadius: 5))
            Button("LOAD WAV") { importing = true }
            Spacer()
            Menu("EXPORT MIDI ▾") {
                Button("Pattern completo") { machine.exportMIDI(lane: nil) }
                Button("Solo \(DrumMachine.names[state.selected])") { machine.exportMIDI(lane: state.selected) }
            }
            Menu("EXPORT WAV ▾") {
                Button("Mix completo") { machine.exportWAV(lane: nil) }
                Button("Solo \(DrumMachine.names[state.selected])") { machine.exportWAV(lane: state.selected) }
            }
        }.font(.system(size: 11, weight: .heavy, design: .monospaced)).buttonStyle(.plain)
            .frame(height: 38)
    }
}

private struct ShareItem: Identifiable { let url: URL; var id: String { url.absoluteString } }
private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
