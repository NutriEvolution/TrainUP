# Ripresa Forza

App Android per il programma di allenamento "Ripresa Forza" (forza e ipertrofia, 19 settimane a cicli, con routine posturale).

## Installazione
1. Apri la pagina **Releases** di questo repository dal telefono.
2. Scarica il file `RipresaForza-x.y.z.apk` dell'ultima versione.
3. Aprilo e consenti l'installazione da questa fonte quando Android lo chiede.

Gli aggiornamenti successivi si installano dall'app: **Programma → App e dati → Cerca aggiornamenti** (l'app controlla anche da sola all'avvio).

## I dati
Restano solo sul telefono. Usa **Salva backup** in *Programma → App e dati* prima di disinstallare o cambiare telefono.

## Come si pubblica una nuova versione
1. Modifica `web/app.html` (l'app) o `web/android.js` (collegamento con Android).
2. Scrivi le novità in `NOVITA.md`.
3. Crea un tag `vX.Y.Z` (es. `v1.0.1`) e fai push: GitHub Actions compila, firma e pubblica l'APK nelle Releases.

La firma usa due secret: `KEYSTORE_BASE64` e `KEYSTORE_PASSWORD`. La chiave non è nel repository: senza la stessa chiave gli aggiornamenti non si installano sopra la versione esistente.

## Struttura
- `web/app.html`: l'app (HTML/CSS/JS in un file)
- `web/android.js`: notifiche del timer, schermo acceso, backup, aggiornamenti
- `tools/build_www.py`: unisce i due file in `app/src/main/assets/www/index.html`
- `app/`: contenitore Android (Kotlin, WebView)

Carattere: Geist (SIL Open Font License, vedi `app/src/main/assets/www/fonts/OFL.txt`).
