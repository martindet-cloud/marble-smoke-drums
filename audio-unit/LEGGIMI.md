# Marble Smoke DRUMS 0.1 — prototipo Audio Unit

Drum machine per Logic Pro. Questo plugin è distinto da RITUAL: nome e identificatore AU sono diversi.

## Installazione in sei passi

1. Sul Mac installa gli strumenti Xcode (`xcode-select --install`), CMake 3.22 o successivo e Git. Al primo avvio serve internet per scaricare JUCE 8.0.6.
2. Estrai lo ZIP e apri Terminale nella cartella `Drums` (scrivi `cd ` e trascina la cartella nel Terminale).
3. Esegui `bash scripts/build-mac.sh`. Lo script trova anche CMake.app nella cartella Applicazioni, compila per Apple Silicon e Intel e apre la cartella dell'Audio Unit.
4. Chiudi Logic. Copia `DRUMS.component` dalla cartella aperta in `~/Library/Audio/Plug-Ins/Components/`. In Finder usa Vai → Vai alla cartella per raggiungere la destinazione.
5. Nel Terminale esegui `auval -v aumu Drm1 Mrbl`. Se non supera la validazione, conserva l'output.
6. Avvia Logic e crea una traccia Strumento software → AU Instruments → Marble Smoke → DRUMS → Stereo. Se non compare, apri Gestore plug-in e ripeti la scansione di DRUMS.

Lo script crea `build/MarbleSmokeDrums_artefacts/Release/AU/DRUMS.component`. RITUAL può restare installato.

## Come provarlo

- PLAY avvia l'anteprima interna; STOP la ferma. Il trasporto di Logic, quando attivo, ha la precedenza e sincronizza step e BPM. Clicca un pad per ascoltare la voce; le note MIDI 36–43 la suonano.
- ARTIST sceglie Martin Death, Gondi, Xande, Clay o Init; PRESET sceglie uno dei tre pattern dimostrativi del mondo selezionato. Il colore cambia e la targa dedica uno spazio al nome/firma. Le scelte attuali sono segnaposto creativi, non suoni, pattern, palette o firme approvati dagli artisti.
- Clicca una delle otto voci e accendi gli step nella fila di 16 tasti. PAGE mostra la seconda metà quando 32 STEPS è attivo. I pulsanti A–H scelgono otto pattern. SWING e BREAK avanzano a incrementi; BREAK introduce colpi casuali.
- Trascina i cinque controlli verticalmente per cambiare tune, decay, attack, filter e level della voce. LOAD WAV sostituisce il suo campione; sono accettati WAV mono o stereo fino a 30 secondi. Selezionare un altro preset ripristina i suoni dimostrativi.
- MIDI ALL salva le otto voci in un file `.mid`; MIDI LANE salva la voce selezionata. WAV STEMS sceglie una cartella e scrive otto WAV stereo e `00_FULL_MIX.wav` a 44,1 kHz/16 bit, usando il BPM letto da Logic oppure 128 BPM iniziali. I file possono essere trascinati dalla cartella in Logic. Non c'è ancora drag diretto dal plugin.
- Salva e riapri il progetto Logic per verificare la persistenza. I WAV caricati sono referenziati dal loro percorso sul disco, quindi non spostarli dopo il salvataggio.

## Limiti di questa versione

Gli otto WAV incorporati sono demo sintetiche, non un sample pack firmato dagli artisti. Il MIDI registra velocity e spostamento degli step; swing, probabilità e BREAK non sono stampati nel file. I WAV renderizzati includono swing e controlli di voce, ma non le variazioni casuali, e aggiungono una coda di un secondo. L'uscita del plugin è stereo, senza uscite audio separate: gli stem si ottengono con WAV STEMS. Non sono ancora presenti trascinamento diretto dalla UI, firma grafica autentica degli artisti o librerie preset esterne.

La sintassi dello script e l'integrità dei file si verificano qui; compilazione, validazione AU, comportamento in Logic e resa sonora sono da verificare sul tuo Mac. Per distribuire il componente ad altri serviranno firma/notarizzazione e verifica della licenza JUCE.
