import AVFoundation
import Combine
import Foundation

struct DrumState: Codable {
    var artist = 0
    var preset = 0
    var bank = 0
    var selected = 0
    var bpm = 128
    var swing = 0.0
    var patterns = Array(repeating: Array(repeating: Array(repeating: false, count: 16), count: 8), count: 8)
    var customSamples = Array(repeating: "", count: 8)
}

final class DrumMachine: ObservableObject {
    static let artists = ["Martin Det", "GONDII", "Xande", "Clay", "Init"]
    static let presets = [
        ["Afterhours", "Glitch Vein", "Leftfield Garage"],
        ["Tunnel", "Runs and Hits", "Pressure Room"],
        ["Static Motion", "Blue Circuit", "Fragments"],
        ["Warm Dust", "Raw Shuffle", "Low Tide"],
        ["Phase One", "Offset", "Night Shift"]
    ]
    static let names = ["KICK", "SNARE", "CLAP", "C.HAT", "O.HAT", "RIM", "TOM", "PERC"]
    static let files = ["Kick", "Snare", "Clap", "ClosedHat", "OpenHat", "Rim", "Tom", "Perc"]

    @Published private(set) var state = DrumState()
    @Published private(set) var playing = false
    @Published private(set) var playStep = -1
    @Published var exportURL: URL?
    @Published var error: String?
    private var timer: Timer?
    private var activePlayers: [AVAudioPlayer] = []
    private let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]

    init() {
        if let data = try? Data(contentsOf: documents.appendingPathComponent("drums-state.json")),
           let saved = try? JSONDecoder().decode(DrumState.self, from: data),
           saved.patterns.count == 8, saved.patterns.allSatisfy({ $0.count == 8 && $0.allSatisfy({ $0.count == 16 }) }),
           saved.customSamples.count == 8 {
            state = saved
            state.artist = min(4, max(0, state.artist))
            state.preset = min(2, max(0, state.preset))
            state.bank = min(7, max(0, state.bank))
            state.selected = min(7, max(0, state.selected))
            state.bpm = min(220, max(60, state.bpm))
            state.swing = min(0.6, max(0, state.swing))
        } else {
            makePreset(artist: 0, preset: 0)
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    var accent: ColorToken { ColorToken(rawValue: state.artist) ?? .lime }

    func selectArtist(_ index: Int) { makePreset(artist: index, preset: 0) }
    func selectPreset(_ index: Int) { makePreset(artist: state.artist, preset: index) }
    func selectBank(_ index: Int) { state.bank = index; persist() }
    func selectLane(_ index: Int) { state.selected = index; audition(index); persist() }
    func setBPM(_ value: Int) { state.bpm = min(220, max(60, value)); if playing { restartTimer() }; persist() }
    func setSwing(_ value: Double) { state.swing = min(0.6, max(0, value)); persist() }
    func toggle(_ step: Int) { state.patterns[state.bank][state.selected][step].toggle(); persist() }

    private func makePreset(artist: Int, preset: Int) {
        guard (0..<5).contains(artist), (0..<3).contains(preset) else { return }
        stop()
        state.artist = artist; state.preset = preset; state.bank = 0
        state.customSamples = Array(repeating: "", count: 8)
        for b in 0..<8 { for t in 0..<8 { for s in 0..<16 {
            let x = (s * 19 + b * 7 + artist * 13 + preset * 11) % 37
            state.patterns[b][t][s] = switch t {
            case 0: s % 4 == 0 || (artist == 2 && x == 1)
            case 1: s % 8 == 4
            case 3: s % 2 == 0 || x < 3
            case 4: s % 8 == 6
            case 2, 5, 7: x < (artist == 3 ? 8 : 4) && t == (artist == 1 ? 5 : artist == 4 ? 7 : 2)
            default: false
            }
        } } }
        persist()
    }

    func togglePlay() { playing ? stop() : start() }
    func start() { guard !playing else { return }; playing = true; playStep = -1; restartTimer() }
    func stop() { timer?.invalidate(); timer = nil; playing = false; playStep = -1 }

    private func restartTimer() {
        timer?.invalidate()
        guard playing else { return }
        let interval = 60.0 / Double(state.bpm) / 4.0
        tick()
        timer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in self?.tick() }
    }
    private func tick() {
        playStep = (playStep + 1) % 16
        let pattern = state.patterns[state.bank]
        let step = playStep
        for lane in 0..<8 where pattern[lane][step] {
            let delay = step.isMultiple(of: 2) ? 0 : state.swing * 60.0 / Double(state.bpm) / 8.0
            if delay == 0 { audition(lane) }
            else { DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                guard let self, self.playing else { return }
                self.audition(lane)
            } }
        }
    }

    func audition(_ lane: Int) {
        guard let url = sampleURL(lane) else { return }
        do {
            activePlayers.removeAll { !$0.isPlaying }
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay(); player.play(); activePlayers.append(player)
        } catch { self.error = "Campione non riproducibile: \(error.localizedDescription)" }
    }

    private func sampleURL(_ lane: Int) -> URL? {
        let name = state.customSamples[lane]
        if !name.isEmpty {
            let url = documents.appendingPathComponent(name)
            if FileManager.default.fileExists(atPath: url.path) { return url }
        }
        return Bundle.main.url(forResource: Self.files[lane], withExtension: "wav")
    }

    func importSample(_ url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            guard data.count < 30_000_000 else { error = "Campione troppo grande (massimo 30 MB)."; return }
            let name = "sample-\(state.selected).wav"
            let destination = documents.appendingPathComponent(name)
            try data.write(to: destination, options: .atomic)
            _ = try AVAudioPlayer(contentsOf: destination)
            state.customSamples[state.selected] = name
            persist()
            audition(state.selected)
        } catch { self.error = "Caricamento WAV non riuscito: \(error.localizedDescription)" }
    }

    func exportMIDI(lane: Int?) {
        var events: [(Int, Data)] = []
        for step in 0..<16 { for t in 0..<8 where (lane == nil || lane == t) && state.patterns[state.bank][t][step] {
            let at = step * 240 + (step.isMultiple(of: 2) ? 0 : Int(state.swing * 120))
            events.append((at, Data([0x99, UInt8(36 + t), 105])))
            events.append((at + 110, Data([0x89, UInt8(36 + t), 0])))
        } }
        events.sort { $0.0 == $1.0 ? $0.1.first! < $1.1.first! : $0.0 < $1.0 }
        var track = Data()
        let bpm = 60_000_000 / state.bpm
        track.append(contentsOf: [0, 0xFF, 0x51, 3, UInt8((bpm >> 16) & 255), UInt8((bpm >> 8) & 255), UInt8(bpm & 255)])
        var previous = 0
        for (time, message) in events { track.append(varLength(time - previous)); track.append(message); previous = time }
        track.append(varLength(3840 - previous)); track.append(contentsOf: [0xFF, 0x2F, 0])
        var file = Data([0x4D,0x54,0x68,0x64, 0,0,0,6, 0,0, 0,1, 0x03,0xC0,
                         0x4D,0x54,0x72,0x6B])
        file.appendBE(UInt32(track.count)); file.append(track)
        saveExport(file, named: lane.map { "DRUMS_\(Self.files[$0]).mid" } ?? "DRUMS_ALL.mid")
    }

    func exportWAV(lane: Int?) {
        let rate = 44100
        let length = Int(16.0 * 60.0 / Double(state.bpm) / 4.0 * Double(rate)) + rate
        var mix = Array(repeating: Float.zero, count: length)
        for t in 0..<8 where lane == nil || lane == t {
            guard let url = sampleURL(t), let samples = readSamples(url) else { continue }
            for step in 0..<16 where state.patterns[state.bank][t][step] {
                let offset = Int((Double(step) * 15.0 / Double(state.bpm) +
                                  (step.isMultiple(of: 2) ? 0 : state.swing * 7.5 / Double(state.bpm))) * Double(rate))
                for i in 0..<min(samples.count, length - offset) { mix[offset + i] += samples[i] * 0.65 }
            }
        }
        var wav = Data([0x52,0x49,0x46,0x46]); wav.appendLE(UInt32(36 + length * 2))
        wav.append(contentsOf: [0x57,0x41,0x56,0x45, 0x66,0x6D,0x74,0x20]); wav.appendLE(UInt32(16))
        wav.appendLE(UInt16(1)); wav.appendLE(UInt16(1)); wav.appendLE(UInt32(rate))
        wav.appendLE(UInt32(rate * 2)); wav.appendLE(UInt16(2)); wav.appendLE(UInt16(16))
        wav.append(contentsOf: [0x64,0x61,0x74,0x61]); wav.appendLE(UInt32(length * 2))
        for value in mix { wav.appendLE(UInt16(bitPattern: Int16((min(1, max(-1, value)) * 32767).rounded()))) }
        saveExport(wav, named: lane.map { "DRUMS_\(Self.files[$0]).wav" } ?? "DRUMS_MIX.wav")
    }

    private func readSamples(_ url: URL) -> [Float]? {
        guard let file = try? AVAudioFile(forReading: url), file.length > 0,
              file.length < 44100 * 30,
              let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: AVAudioFrameCount(file.length)) else { return nil }
        do { try file.read(into: buffer) } catch { return nil }
        guard let channels = buffer.floatChannelData else { return nil }
        let count = Int(buffer.frameLength), channelCount = Int(buffer.format.channelCount)
        let inputRate = buffer.format.sampleRate
        let outputCount = Int(Double(count) * 44100 / inputRate)
        return (0..<outputCount).map { i in
            let index = min(count - 1, Int(Double(i) * inputRate / 44100))
            return (0..<channelCount).reduce(Float.zero) { $0 + channels[$1][index] } / Float(channelCount)
        }
    }

    private func saveExport(_ data: Data, named name: String) {
        let url = documents.appendingPathComponent(name)
        do { try data.write(to: url, options: .atomic); exportURL = url }
        catch { self.error = "Esportazione non riuscita: \(error.localizedDescription)" }
    }
    private func persist() {
        if let data = try? JSONEncoder().encode(state) { try? data.write(to: documents.appendingPathComponent("drums-state.json"), options: .atomic) }
    }
}

enum ColorToken: Int { case lime, copper, blue, amber, violet }

private func varLength(_ value: Int) -> Data {
    var bytes = [UInt8(value & 0x7F)], n = value >> 7
    while n > 0 { bytes.insert(UInt8(n & 0x7F) | 0x80, at: 0); n >>= 7 }
    return Data(bytes)
}
private extension Data {
    mutating func appendBE(_ x: UInt32) { append(contentsOf: [UInt8((x >> 24) & 255), UInt8((x >> 16) & 255), UInt8((x >> 8) & 255), UInt8(x & 255)]) }
    mutating func appendLE(_ x: UInt32) { append(contentsOf: [UInt8(x & 255), UInt8((x >> 8) & 255), UInt8((x >> 16) & 255), UInt8((x >> 24) & 255)]) }
    mutating func appendLE(_ x: UInt16) { append(contentsOf: [UInt8(x & 255), UInt8((x >> 8) & 255)]) }
}
