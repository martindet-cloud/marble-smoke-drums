# Marble Smoke DRUMS Pocket 0.1

Prototipo iPhone, solo orizzontale, indipendente dal plugin AU. Richiede iOS 17 o successivo e Xcode 15 o successivo sul Mac. Non serve CMake.

## Compilare senza Mac (GitHub Actions → TestFlight)

Il repository contiene `.github/workflows/ios.yml`. Xcode gira sui Mac di GitHub (runner `macos-26`, Xcode 26, come richiesto da Apple per i caricamenti). Tutto il resto si fa dal browser.

### Una volta sola

1. **Apple Developer Program** attivo (99 $/anno).
2. **Bundle ID**: su developer.apple.com → Certificates, IDs & Profiles → Identifiers → **+** → App IDs → App. Scegli un identificatore tuo (ad esempio `it.tuodominio.marblesmoke.drums`).
3. **App in App Store Connect**: appstoreconnect.apple.com → App → **+** → Nuova app → iOS. Seleziona il Bundle ID del punto 2 e scegli uno SKU qualsiasi.
4. **Chiave API**: App Store Connect → Utenti e accessi → Integrazioni → API di App Store Connect → Chiavi del team → **+**, con ruolo **Admin** (serve per creare certificati e profili in automatico). Scarica il file `.p8` (si può scaricare una volta sola) e annota **Key ID** e **Issuer ID**.
5. **Team ID**: developer.apple.com → Account → Membership details.
6. Su GitHub, nel repository → Settings → Secrets and variables → Actions:
   - *Secrets*: `ASC_KEY_ID`, `ASC_ISSUER_ID`, `APPLE_TEAM_ID` e `ASC_KEY_P8`. Per `ASC_KEY_P8` incolla tutto il contenuto del file `.p8`, comprese le righe BEGIN/END.
   - *Variables*: `BUNDLE_ID` = il Bundle ID del punto 2.

Non servono certificati `.p12` né provisioning profile: li crea e rinnova `xcodebuild` tramite la chiave API.

### A ogni versione

- Ogni push su `main` compila l'app per il simulatore, senza firma: è un controllo veloce che il codice compili.
- Per mandarla sull'iPhone: scheda **Actions** → **iOS** → **Run workflow**. Dopo 5-15 minuti la build compare in App Store Connect → TestFlight. Aggiungiti come tester interno e installala dall'app **TestFlight** sull'iPhone. Il numero di build cresce da solo.
- Se qualcosa fallisce, i log completi sono negli *artifacts* dell'esecuzione.

Nota: sui repository privati i minuti macOS consumano la quota gratuita di GitHub più in fretta di quelli Linux. Sui repository pubblici sono gratuiti.

## Installazione sul tuo iPhone con Xcode (alternativa, serve un Mac)


1. Estrai lo ZIP e apri `MarbleSmokeDrums.xcodeproj` con Xcode.
2. Collega l'iPhone al Mac via cavo e sbloccalo. Se richiesto, conferma «Autorizza» sul telefono.
3. In Xcode seleziona il progetto **MarbleSmokeDrums**, poi il target omonimo → **Signing & Capabilities** → scegli il tuo Apple ID in **Team**. Se il Bundle Identifier risulta già usato, cambialo in uno personale.
4. In alto, al posto del simulatore, seleziona il tuo iPhone e premi **▶ Run**. La prima compilazione può richiedere qualche minuto.
5. Se l'iPhone chiede l'attivazione della Modalità sviluppatore, abilitala in **Impostazioni → Privacy e sicurezza → Modalità sviluppatore** e ripeti Run.
6. Apri **Marble Smoke DRUMS** sull'iPhone e ruotalo in orizzontale. Tocca un pad, premi Play e modifica gli step. Con EXPORT MIDI/WAV puoi condividere il file o salvarlo nell'app File.

## Funzioni della prima versione

- Otto pad con gli stessi WAV sintetici dimostrativi del prototipo AU, selezionabili e suonabili al tocco.
- Cinque temi artista (Martin Det, GONDII, Xande, Clay, Init), tre pattern demo per artista, otto banchi A–H, sequencer a 16 step, BPM 60–220 e swing.
- Importazione WAV per la voce selezionata dall'app File. I campioni vengono copiati nello spazio locale dell'app; scegliendo un altro preset si torna ai WAV demo.
- Salvataggio automatico locale di pattern e impostazioni. Esportazione MIDI del pattern completo o della voce selezionata; esportazione WAV mono del mix o della voce selezionata.

Questi temi e preset sono segnaposto: non contengono materiale fornito o approvato dagli artisti. L'app non sincronizza ancora i progetti con il plugin AU e non esporta gli otto stem in un solo gesto. Il sequencer usa il timer dell'interfaccia, quindi il timing va verificato su iPhone e migliorato con una schedulazione audio dedicata prima di un rilascio pubblico. L'icona è un segnaposto.

In questo ambiente non sono disponibili Xcode e un iPhone: ho verificato file, risorse, sintassi XML e struttura dell'archivio, ma non ho potuto compilare né provare l'app sul dispositivo.

## Versione Android (cartella `android/`)

La stessa app riscritta in Kotlin con Jetpack Compose, per telefoni e tablet Android 8.0 o successivi, in orizzontale. Funzioni identiche alla versione iPhone. L'audio passa da un mixer a bassa latenza: il sequencer è sincronizzato sui campioni audio e non su un timer dell'interfaccia, quindi tempo e swing sono precisi.

- **Compilazione**: il workflow `.github/workflows/android.yml` gira su un server Linux di GitHub a ogni modifica della cartella `android/`. Si può avviare anche a mano da Actions → Android → Run workflow. Non serve un Mac.
- **Download**: l'APK compare nella pagina *Releases* del repository come `MarbleSmokeDrums.apk`.
- **Installazione**: apri il link dell'APK dal browser del telefono o del tablet, scaricalo e toccalo. La prima volta Android chiede di consentire l'installazione di app da quel browser o dall'app File.
- **Aggiornamenti**: ogni nuova build si installa sopra la precedente e conserva pattern e campioni, perché è firmata sempre con la stessa chiave (`android/app/sideload.keystore`). Quella chiave serve solo per l'installazione diretta. Per il Play Store va creata una chiave privata da tenere fuori dal repository.
- **Android Studio** (facoltativo): apri la cartella `android/`.
