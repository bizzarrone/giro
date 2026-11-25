# GiroCalendar

App Android (Kotlin + Jetpack Compose) per calendari di uscite in bicicletta di gruppo
(MTB, strada, gravel, cicloturismo): eventi, partecipanti, trail e mappa, navigazione GPX,
contatti, mercatino, SOS. Backend **Firebase Firestore + Auth** sul piano gratuito Spark
(nessuna Cloud Function). Sito web collegato: <https://girocalendar.pages.dev>.

## Come compilare

1. **Android Studio** recente (o JDK 17+ e la Android SDK da riga di comando).
2. **Firebase:** crea un tuo progetto, registra l'app Android (`simplylucaapps.girocalendar`
   e la variante debug `simplylucaapps.girocalendar.debug`), abilita Firestore e
   l'accesso con Google, e scarica `google-services.json` in `app/`
   (vedi `app/google-services.json.example`). Il file è ignorato da git.
3. **Regole Firestore:** pubblica `firestore/firestore.rules` e `firestore/firestore.indexes.json`
   (`firebase deploy --only firestore`). Sostituisci l'email del super-admin nelle regole e in
   `CalendarRepository.kt` con la tua.
4. **Build di prova:** `./gradlew assembleBeta`
5. **Release firmata:** crea `release-keystore/keystore.properties` partendo da
   `release-keystore.example/keystore.properties.example`, poi `./gradlew bundleRelease`.
   Senza questo file la release viene firmata con la chiave di debug.

Il progetto Firebase e il sito `girocalendar.pages.dev` usati dall'app pubblicata sono dell'autore:
per un tuo fork cambia gli URL e gli identificativi.

## Sicurezza

Non committare mai `google-services.json`, i keystore, `keystore.properties` o chiavi di account
di servizio. La sicurezza dei dati è nelle Firestore Security Rules (`firestore/firestore.rules`).

## Licenza

GNU General Public License v3.0 — vedi [LICENSE](LICENSE).
