# Mithril

App Android personale per allenarsi a casa con i video di YouTube: **costanza, non perfezione**.
Libreria di video (dal tasto Condividi di YouTube), planner settimanale con intenzioni *quando, dove, cosa*,
sessioni con player incorporato, trend del peso, revisione della domenica, widget e promemoria.

Kotlin · Jetpack Compose · Room · Glance · Health Connect. Nessun backend: i dati restano sul telefono.

## Build e rilascio

Non serve un ambiente locale: compila tutto **GitHub Actions** (`.github/workflows/android.yml`).

- Ogni push compila, esegue i test e il lint e conserva gli APK come artifact.
- Un push su `main` con `versionCode` più alto dell'ultima release **firma e pubblica** da solo la GitHub Release
  (`mithril-x.y.z.apk`, `update.json` firmato, `SHA256SUMS`). Servono `versionCode`/`versionName` in
  `app/build.gradle.kts` e le note in `docs/CHANGELOG-<versione>.md`.
- L'app controlla `releases/latest/download/update.json`, verifica la firma RSA del descrittore, scarica l'APK,
  ne controlla SHA-256, pacchetto, versione e certificato, poi lo installa.

Le chiavi (keystore PKCS12 e chiave privata del descrittore) stanno solo nei Secrets del repository
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `UPDATE_PRIVATE_KEY_PEM`) e in una copia locale
fuori da Git. **Non rigenerarle**: con chiavi nuove gli aggiornamenti non si installano più.

Schermate di controllo senza telefono: workflow **Schermate UI** (Robolectric), artifact `ui-screenshots`.
