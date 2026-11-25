# Giro — CHANGELOG (Android)

Ricostruito dalla cronologia git (22/09/2026 → 04/11/2026). Le date sono quelle dei commit;
i numeri di versione sono quelli indicati nei messaggi di commit (dalla 2.0 la minor cresce a ogni AAB).

## 2.52 (code 585) — 17/11/2026
- **Il mio spazio**: nel menu, i tuoi file privati online per 30 giorni: gli **ultimi 3 giri registrati** (REC) e fino a **3 file GPX tuoi** (max 2 MB). Salvi, condividi o elimini con le icone; li ritrovi anche sul sito, dove si caricano trascinandoli. Da **Naviga** (pulsante ☁, solo a mappa libera) e da **Mappa** (pulsante «GPX» → «Il mio spazio») tocchi un file e si apre.
- **3D della Mappa, più semplice**: tolti la generazione del video e la barra del volo. Si apre subito con il **tuo GPX evidenziato** e tutto il percorso inquadrato, con i comandi disposti come nella mappa 2D: occhi **Z, L (loghi dei costruttori), T, N, P**, «2D» per tornare indietro, livelli, posizione e zoom. Si muove con le dita (inclinazione e rotazione con due dita).
- **Mappa**: nella fila del GPX tolti ▶ e il «3D» doppio («Riproduci» resta nel menu ⋮).
- **Home**: **Contatti** e **Officina** compaiono anche senza login, in grigio («Login richiesto»). Tolta la voce «Condividi lista (testo)» dal menu.
- **Più fluida**: il cruscotto della registrazione e la traccia in tempo reale non appesantiscono più Naviga; il salvataggio dei punti GPS e la pulizia dello spazio online non girano più sul thread principale.
- **Dalla 2.48**: pericoli con voti sul percorso e sulla mappa, Naviga con voce, bivi e zona offline, registrazione con statistiche del giro, eventi collegati da altri calendari, ultimo giro salvato online (scaricabile dal sito) e riavvio automatico su errore interno di Firestore.

## 2.47 (code 547) — 10/11/2026
- **Nuova scheda «Naviga»** (al posto di «Registra»): mappa subito sulla tua posizione, pulsante **APRI GPX** (GPX delle schede del calendario, cartella locale, file singolo, registrazioni), **schermo intero** per non cambiare scheda con lo swipe, registrazione con salvataggio automatico in Download/Giro.
- **Navigazione GPX**: cruscotto (km, %, quota, dislivello), vibrazione quando esci dal percorso, batteria sempre visibile, tasti disposti a sinistra e a destra senza finire sotto il cruscotto.
- **Mappa offline per percorso**: dal menu ☰ scarichi le tessere lungo la traccia (fascia di 600 m) e la mappa funziona senza rete. Nel menu anche dimensione e svuotamento della cache delle mappe.
- **Segnalazioni** (albero caduto, danni, pericoli…): condivise con tutto il gruppo del calendario, collegate automaticamente al trail più vicino (≤150 m) o assegnabili a mano dal menu; si vedono sulla mappa.
- **Emergenza**: tolto l'avviso «funzione in fase di test»; mappa a schermo intero compatibile con Android 15+.

## 2.46 (code 516) — 10/11/2026
- **Compatibilità Android 15+**: la mappa a schermo intero non usa più le API deprecate per il colore delle barre di sistema (avviso di Google Play su edge-to-edge).

## 2.45 (code 514) — 09/11/2026
- **Emergenza**: tolto l'avviso «funzione in fase di test» dalla scheda.

## 2.44 (code 512) — 09/11/2026
- **Più fluida**: la scheda Mappa resta in memoria quando cambi scheda (niente ricaricamento, zoom e posizione conservati) e i salti tra schede lontane sono immediati, senza far scorrere quelle in mezzo. Corretto anche un caricamento doppio nella mappa del percorso GPX e una perdita di memoria delle mappe.
- **Pulizia**: tolto il registratore di percorso dalla mappa (resta la scheda Registra) e il codice e le stringhe inutilizzati.

## 2.43 (code 509) — 07/11/2026
- **Scheda evento**: opzione «Mostra lo stato dei trail di una zona»: in modifica scegli la zona, nella scheda compare il riepilogo (aperti / attenzione / chiusi) e, toccandolo, l'elenco dei trail con lo stato attuale.
- **Info calendario**: «Admin e collaboratori» più pulito (admin in rosso con «(Admin)», sotto i collaboratori); la prima pagina di info mostra solo l'admin, i collaboratori sono in «Altre info».
- **Preferenze**: tolte «Tabs visibili» e «Singola riga schede» (le schede si gestiscono dalla Home; la barra è sempre su una riga).

## 2.42 (code 498) — 05/11/2026
- **Home**: in modifica i pulsanti si **trascinano** come le icone di un launcher (tieni premuto e rilascia su un altro), con in più le frecce ‹ ›. Nuovo pulsante **Reimposta** (con conferma) per tornare a ordine e schede predefiniti. La matita di modifica è una piccola icona sfumata in basso a destra, non occupa più una riga; si entra in modifica anche con un tocco lungo su un pulsante e **BACK** equivale a «Fine».
- **Barra schede in basso** (opzionale): in Preferenze → «Barra schede in basso» le prime 4 schede stanno in basso e le altre sotto «Altro». Di default resta la barra classica in alto.
- **Trail e Mappa più veloci**: i trail si leggono e ordinano in background aggiornando solo quelli modificati, e i tracciati della mappa si preparano fuori dal thread principale.

## 2.41 (code 481) — 04/11/2026
- **Foto sul percorso senza permesso sulla galleria** (richiesta di Google Play): le foto si scelgono col selettore di sistema, con il tasto 📷 sulla mappa (con un GPX con i tempi). Con le foto già sulla mappa, **tocco lungo sull'occhio F** per cambiarle o aggiungerne.

## 2.40 (code 480) — 04/11/2026
- **Home**: un tap sulla stella della scheda di apertura la toglie e l'app torna ad aprirsi sulla Home.

## 2.39 (code 479) — 04/11/2026
- **Storia rilasci aggiornata**: ora comprende anche le versioni dalla 2.33 alla 2.38.

## 2.38 (code 478) — 04/11/2026
- **Home a griglia**: solo pulsanti, con altezza adattata allo schermo. Il pulsante **Modifica schede** mostra l'**occhio** (mostra/nascondi una scheda) e la **stella** (scheda che si apre all'avvio).
- **Home**: pulsante **CAMBIA** per scegliere la scheda principale, interruttore **Apri all'avvio** e icone monocromatiche al posto delle emoji.
- **Card evento**: mostra la durata se supera 1 giorno.
- **Info calendario**: elenco collaboratori scorrevole, con l'identificativo intero se manca l'email.
- **Contatti**: pulsanti **Copia da…** e **Clona in…** (la scheda viene clonata in un altro calendario).
- **Campi di testo più compatti** in tutta l'app e **form uscita più snello** (GPX, sfondo e audio senza etichette ridondanti).
- **Avvio più veloce e app più leggera**: icona e font alleggeriti, solo italiano e inglese.

## 2.37 (code 456) — 03/11/2026
- **Home con un tasto per ogni scheda** e scheda suggerita all'avvio (Preferenze).
- **Home immediata**: l'app può aprirsi direttamente sulla Home. Icona Trail vettoriale.

## 2.36 (code 455) — 03/11/2026
- **Scheda Registra**: cruscotto, segnalazioni e SOS.
- **Tema Cielo**, nome **Giro** nell'app e **Calendari** con titolo grande e dettagli.
- **Aiuto**: scorrimento col dito tra le schede.
- **Suggerimenti**: a rotazione per utente, numerati; 3 tocchi sulla lampadina li mostrano tutti.
- **Risposta "Forse"** alle uscite.

## 2.35 (code 454) — 02/11/2026
- **Aggiornamento**: apre Google Play in modo esplicito (non più Mi Store su Xiaomi/POCO).
- **Info calendario**: un secondo tocco sul logo apre "Altre info".

## 2.34 (code 446) — 02/11/2026
- **Commenti sulle schede delle uscite** (solo utenti loggati).
- **Popup di aggiornamento** all'avvio, avviso area personale in Officina e avviso test in Emergenza.

## 2.33 (code 440) — 01/11/2026
- **Avvio più veloce**: cache su file degli eventi e decodifica fuori dal thread UI.
- **Suggerimenti** a popup casuali ("Basta!" per non vederli più), voce nel menu dopo Preferenze e interruttore on/off in Preferenze.
- **Suggerimenti sui trail**: stato, nome e posizione modificabili da chi è loggato.
- **Editor evento**: aggiungere e rinominare i partecipanti.

## 2.32 (code 429) — 29/10/2026
- **Via il lucchetto admin**: gli editor vedono sempre i controlli di modifica. Eliminare un avviso ora chiede conferma.
- **Foto dell'evento**: doppio tap per lo zoom; dopo il download compare "Immagine salvata" con il pulsante **Apri**.
- **Passate**: le 10 uscite più recenti, il resto dietro "Altre N uscite" (e "Mostra meno").
- **Contatti**: banner "Crea la tua scheda" se non l'hai ancora fatta.
- "Annulla" tradotto in tre dialog (condivisione evento, info calendario, taglio audio).

## 2.31 (code 428) — 29/10/2026
- **Scarica l'immagine dell'evento**: nel visualizzatore a schermo intero della foto, pulsante di download (salva l'originale in Immagini/GiroCalendar).

## 2.30 (code 427) — 29/10/2026
- **Scheda Contatti per tutti**: visibile a chiunque sia loggato (non serve più essere editor); ognuno crea e modifica la propria. Help aggiornato.

## 2.27 (code 424) — 27/10/2026
- **Punti di interesse (POI)**: ritrovo, vetta, area picnic, altro, con nome e descrizione. Occhio **P** sulla mappa; in modifica "📍 POI" e tocco sulla mappa; fumetto con descrizione, "aggiunto da / modificato da" e pulsante **Naviga** (Google Maps, Waze, altra app). Nel **3D** compaiono da soli sul rilievo e si rimpiccioliscono con lo zoom. Voci nel LOG. Ritrovo: bandierina bianca su fondo viola.
- **Barra di modifica della mappa** compatta (colonna a sinistra: Traccia, Sentiero, Zona, POI); layer e lente più in basso; guida "?" aggiornata (IT/EN).
- **Info** compatta in una schermata, logo senza bordo e **Storia rilasci** (IT/EN).
- **Preferenze mappa**: *Opacità colore Zona* (0–70%) e *Nome trail cerchiato* sì/no. Il fumetto di un POI non è più coperto dalla barra di modifica.

## 2.26 (code 418) — 26/10/2026
- **Durata giorni** nella scheda dell'evento (default 1): campo nell'editor, "N giorni" nel dettaglio e nel testo condiviso (anche sul web, v2.14).
- **Pulsante "La mia posizione"**: se già acceso un tocco ricentra la mappa sul pallino (prima lo spegneva); il tocco lungo lo spegne.
- **Mappa a schermo intero**: i tasti + / − restano sopra la barra di navigazione di Android.
- **Menu → Info** compatto (tutto in una schermata) con logo senza bordo e **Storia rilasci**.

## 2.25 (code 392) — 19/10/2026
- **Volo 3D** (MapLibre GL nel WebView, stessa pagina del sito): rilievo reale, layer OSM/Topografica/CyclOSM/Chiara/**Satellite**, bussola, camera libera con pannello 🎥 (inclinazione, zoom, rotazione), "Segui" on/off, **Localizzami** 📍, apre sul tracciato intero senza partire; ▶ anima solo il puntino.
- **Tasto 3D fisso** sulla Mappa: la zona in 3D con trail (colore = difficoltà) e zone; con un GPX caricato mostra il GPX con la barra ▶ sopra trail e zone.
- Il **GPX caricato resta** cambiando scheda; gli **occhi** Z, L, T, N, G, F si ricordano.
- Preferenze → **Spessore traccia GPX** (prima di Lingua): molto sottile … molto spesso, per mappa, riproduzione e 3D.
- Layout del 3D pensato per il telefono (barra in alto, statistiche compatte, tempo con ore).

## 2.24 (code 382) — 19/10/2026
- **Riproduzione del GPX**: tasto ▶ accanto a "GPX", velocità 1–60× (default 20×), tracciato grigio che si colora al passaggio, altimetria nascondibile (toccabile per saltare), mappa libera (◎ Segui opzionale), cursori senza "lotta" con la riproduzione.
- **Foto sul percorso**: dalla galleria (permesso foto), miniature che scalano con lo zoom, tap per vederle a schermo intero, occhio F; posizione da GPS o stimata dall'orario.
- Tasto **LOG** piccolo in basso a sinistra sulla Mappa (solo admin); filtro `-parola` nella ricerca del LOG; conferma esplicita per Annulla/Ripristina.
- Riga GPX compatta, layer e lente più in basso, "Tuo GPX" → "GPX", linee più sottili, layer Satellite nelle mappe.

## 2.23 (code 362) — 18/10/2026
- **Ricerca luogo** sulla mappa (lente, Nominatim); **GPX da altre app** (Apri con / Condividi); Aiuto aggiornato IT/EN.
- Il LOG sotto la mappa e il filtro `-parola` (poi sostituiti dal tasto LOG).

## 2.22 (code 350) — 18/10/2026
- **Annulla operazioni dal LOG** (solo admin): rinomina trail/zona, difficoltà, stato, posizione, tracciato, creazione ed eliminazione di un singolo trail (con commenti). Ogni annullamento è una voce di log a sua volta (Ripristina). Avviso se il trail è stato modificato dopo.
- **Rinomina zona** anche per zone senza trail (sposta perimetro e costruttori).
- **Scala difficoltà Trailforks UK/EU**: White, Green, Blue, **Red**, Black, Proline.
- **Mappa a schermo intero**: barra dei pulsanti più in alto, selettore layer sotto la barra (non più coperto).
- Regole Firestore: un admin può ricreare trail/commenti eliminati.

## 2.21 (code 345) — 18/10/2026
- **Convertitore di tracciati** nel menu ⋮ del "Tuo GPX": **Converti in…** KML, GeoJSON, TCX, CSV (Salva o Condividi). Il "Tuo GPX" apre anche KML, GeoJSON, TCX, CSV.

## 2.20 (code 343) — 17/10/2026
- **Strumenti GPX** nel menu ⋮ del tuo GPX in Mappa: statistiche, ritaglia, inverti, semplifica, salva, condividi. (La scheda GPX dedicata, aggiunta e tolta lo stesso giorno, è stata assorbita qui.)
- Frecce di direzione sul GPX personale.
- Voce **Donate** nel menu.
- Info: riquadro "Versione Android" con Copia link/Condividi, riquadri iOS/web e Android verdi, link "Altre app SimplyLucaApps" in fondo.
- Font: tolto Nunito, aggiunto **Lexend**.
- Mappa: avviso "passa a schermo intero" al centro con icona.

## 2.19 (16/10/2026) — Mercatino
- **Scheda Mercatino**: annunci (solo calendario o per tutti) con fino a 8 foto a piena qualità, editor foto (ritaglio, rotazione), viewer a schermo intero, foto principale (stella), prezzo in €, categoria, contatti separati (telefono + WhatsApp, email, altro), nome venditore in evidenza.
- Condivisione dell'annuncio con link a pagina pubblica, copia link.
- Emergenza resta sempre l'ultima scheda.

## 2.18 (16/10/2026)
- Versione di passaggio (bump).

## 2.17 (code 315, 15/10/2026)
- **Push per nuove proposte** (FCM + funzione Cloudflare), puntino sempre visibile per gli editor, avviso con azione "Vedi" a app aperta.
- **Lista nera per account/email**: "Escludi modifica" o accesso negato; "Modifica negata" sulla matita della mappa; collaboratori elencati dal logo.
- **Proposte solo da loggati**.
- Mappa del percorso di un'uscita completa (layer, occhi Z/L/T/N, posizione, partenza/arrivo), parte sempre da OSM; statistiche sotto la mappa.
- Spunta "annullata" solo in modifica di un'uscita esistente.

## 2.16 (code 303, 15/10/2026)
- **Uscita annullata**: flag, barra diagonale rossa sulla scheda, nascosta dal Mese, clone la azzera.
- **Clona anche nel calendario corrente**; lucchetto spento = chiuso grigio.
- **"Aggiornamento disponibile"** nel menu (Play In-App Updates + fallback `version.json`).
- **Traduzione inglese completa** (~330 stringhe in risorse, log, Emergenza, mappa e JS); sigle dei giorni in inglese.

## 2.12 – 2.15 (15/10/2026)
- Avvisi ordinabili (frecce) e testo copiabile; titolo avviso.
- Preferenze: cache mappa a tendina, "Autoplay audio", scheda Calendari spenta e Emergenza attiva di default.
- Guida della mappa ("?"), X sul GPX personale.
- Aiuto/Info aggiornati (schede Mese, Mappa, Emergenza).
- **Emergenza**: massimo 5 contatti.

## 2.5 – 2.11 (14/10/2026)
- **Emergenza**: rilevamento cadute, SMS automatico anche senza dati, WhatsApp/Telegram con tocco, allarme sonoro e conto alla rovescia a schermo intero, messaggi con segnaposto, registro in tempo reale, modalità prova.
- **Tab ordinabili/visibili** (Preferenze → Tabs visibili).
- Registrazione GPX (implementata, nascosta da flag), rinomina trail, elimina zona, nuova zona dal perimetro.
- Preferenze: dimensione cache mappa; area zone più evidente; Importa GPX in basso.
- Impostazioni Officina spostate in Officina.

## 2.0 – 2.4 (13–14/10/2026) — Mappa evoluta
- **Nuova regola di versione**: dalla 2.0 la minor cresce a ogni AAB.
- Zone: **costruttori dei trail** (logo, nome, link social) sulla mappa; occhio L; loghi scalati con lo zoom; modifica costruttori.
- Zone senza trail; elimina zona (admin); sfondo "Chiara" senza chiavi API.
- Occhi Z/L/T/N/G, ultima vista salvata, X su "Tuo GPX", zone modificabili.
- Scelta dell'app di navigazione per il Parcheggio (Google Maps, Waze, altra).

## 1.3 – 1.9 (13/10/2026) — Mappa
- Modalità occhio/matita (sola lettura di default), nomi trail opzionali, "Vedi tuo GPX su mappa", "Vedi sulla mappa".
- **La mia posizione**, "Tuo GPX" compatto con X, layer compatti con X.
- **Tocca un sentiero**, zone disegnate (poligoni informativi), modifica/cancella tracciati (tenendo premuto), tocco in vista con nome+km.
- Segmenti colorati per difficoltà, la mappa non salta dopo i salvataggi, versione in fondo al menu.

## Metà ottobre — Mappa, trail, LOG (12/10/2026)
- **Scheda MAPPA** (OpenStreetMap, Topografica, CyclOSM, overlay MTB), cache tile su disco (150 MB), schermo intero, tracciati dei trail, "Importa GPX" con abbinamento per nome, "Rimuovi dalla mappa".
- **Tracciamento col dito** con aggancio ai sentieri OSM.
- **LOG modifiche trail** (solo admin, ricerca, ultime 20); stato/difficoltà modificabili; zona da elenco.
- Preferenza "Singola riga schede".
- Cache Firestore su disco 500 MB.

## 1.4x – 1.7x (04–11/10/2026)
- **Proponi evento**: allegati GPX, immagine di sfondo e audio.
- **Super-admin**: modera calendari (privato/elimina), email dei collaboratori.
- **Scheda Contatti**: scheda per collaboratore (social, email, telefono), foto profilo con zoom e ritaglio circolare, foto dall'account Google, clonabile tra calendari, condivisibile con link web.
- Trail: modifica ed elimina dei propri commenti; rinomina area (admin); difficoltà dal dettaglio.
- **Scheda MESE** (09/10): griglia mensile con titoli e immagine di sfondo del giorno.
- Info: copia/condividi link sito; condividi evento come testo o link; **App Links** per gli eventi; condividi calendario (link web); avvisi con titolo.
- Aiuto: voce Officina.

## 1.19 – 1.42 (29/09 – 01/10/2026) — Officina
- **Scheda Officina**: manutenzione bici (bici, lavori, tabella compatta, 14 colori), export CSV/PDF/JPEG/testo/ODS, icone lavoro (automatiche da testo + manuali) con colori, popup dettaglio con modifica.
- Elimina calendario, fix lingua Preferenze, nuova icona (icona debug con etichetta DEBUG), CSV con `;`.

## 1.0 – 1.12 (26–28/09/2026)
- **Versione 1.0** (26/09): owner sempre in tab Calendari, splash, "Proponi" compattato, giorno della settimana dopo la data.
- Nuovo tema chiaro "Alba Viola" (3 scuri + 3 chiari), voce Temi.
- Download GPX (salva/condividi) da evento/epico; fix tab Prossime/Passate.
- Email dell'owner nel quick-info; tagline aggiornata; versione web in evidenza.
- Aiuto: dialog rifatto (righe separate, punti elenco), fix a-capo.

## 0.x (22–25/09/2026) — Costruzione
- **Scaffold iniziale** (22/09): Compose + Firestore + Credential Manager, stesso modello di VolleyCalendar.
- Nuova icona, Preferenze, Comunicazioni (avvisi), splash logo.
- **Proponi evento** e **Partecipa**, meteo (su richiesta), font multipli, campi PWA completi.
- Icone contestuali, "+calendario", condividi, form compatti; archivio eventi, foto intera nel dettaglio.
- **Tab Trail** (stato di percorribilità, commenti), GPX/audio sugli eventi, clona evento (anche da calendari altrui), calendari pubblici, i18n inglese (25/09).
- **Tab Epici** (giri da ripetere ogni anno) con "Pianifica".
- Dialog **Aiuto** nel menu, statistiche rimosse da Info.

---
Web (girocalendar.pages.dev) ha una numerazione propria: oggi v1.94 in repo (ultima deployata: v1.92).
