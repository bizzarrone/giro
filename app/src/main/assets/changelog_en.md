# Giro — Release history (Android)

Rebuilt from the git history (22/09/2026 → 04/11/2026). Dates are commit dates; from 2.0 on, the minor number goes up with every AAB.

## 2.52 (code 585) — 17/11/2026
- **My space**: in the menu, your private files kept online for 30 days: the **last 3 recorded rides** (REC) and up to **3 GPX files of your own** (2 MB max). Save, share or delete with the icons; you also find them on the website, where you upload them by dragging. From **Navigate** (☁ button, free map only) and from **Map** («GPX» button → «My space») tap a file to open it.
- **Simpler 3D on the Map**: removed video creation and the flight bar. It opens right away with **your GPX highlighted** and the whole route in view, with controls laid out like the 2D map: eyes **Z, L (builder logos), T, N, P**, «2D» to go back, layers, location and zoom. Move it with your fingers (tilt and rotate with two fingers).
- **Map**: removed ▶ and the duplicate «3D» from the GPX row («Play» stays in the ⋮ menu).
- **Home**: **Contacts** and **Workshop** now show even when logged out, greyed («Login required»). Removed «Share list (text)» from the menu.
- **Smoother**: the recording dashboard and the live track no longer slow Navigate down; saving GPS points and cleaning your online space no longer run on the main thread.
- **Since 2.48**: hazards with votes on the route and the map, Navigate with voice, junctions and offline area, recording with ride statistics, linked events from other calendars, last ride saved online (downloadable from the website) and automatic restart on an internal Firestore error.

## 2.47 (code 547) — 10/11/2026
- **New «Navigate» tab** (replaces «Record»): map right on your position, **OPEN GPX** button (calendar GPX files, local folder, single file, recordings), **fullscreen** so swiping no longer changes tab, recording auto-saved to Download/Giro.
- **GPX navigation**: dashboard (km, %, altitude, climb), vibration when you leave the route, battery always visible, keys placed left and right so they never go under the dashboard.
- **Offline map per route**: from the ☰ menu you download the tiles along the track (600 m corridor) and the map works without a connection. The menu also shows the map cache size and lets you clear it.
- **Reports** (fallen tree, damage, hazards…): shared with the whole calendar group, automatically linked to the nearest trail (≤150 m) or assignable by hand from the menu; shown on the map.
- **Emergency**: removed the «test feature» warning; fullscreen map compatible with Android 15+.

## 2.46 (code 516) — 10/11/2026
- **Android 15+ compatibility**: the fullscreen map no longer uses the deprecated system-bar colour APIs (Google Play edge-to-edge warning).

## 2.45 (code 514) — 09/11/2026
- **Emergency**: removed the «feature still in testing» notice from the tab.

## 2.44 (code 512) — 09/11/2026
- **Smoother**: the Map tab now stays in memory when you switch tabs (no reload, zoom and position kept) and jumps between distant tabs are instant, without scrolling through the ones in between. Also fixed a double load in the GPX route map and a map memory leak.
- **Cleanup**: removed the route recorder from the map (the Record tab remains) and unused code and strings.

## 2.43 (code 509) — 07/11/2026
- **Event card**: option «Show trail status for a zone»: pick the zone when editing, the card shows a summary (open / caution / closed) and, when tapped, the list of trails with their current status.
- **Calendar info**: cleaner «Admin and collaborators» (admin in red with «(Admin)», collaborators below); the first info page shows only the admin, collaborators are in «More info».
- **Preferences**: removed «Visible tabs» and «Single tab row» (tabs are managed from Home; the bar is always one row).

## 2.42 (code 498) — 05/11/2026
- **Home**: in edit mode the buttons can be **dragged** like launcher icons (long-press and drop on another one), plus ‹ › arrows. New **Reset** button (with confirmation) to restore the default order and tabs. The edit pencil is now a small faded icon at the bottom right and no longer takes a row; you can also enter edit mode by long-pressing a button, and **BACK** acts as «Done».
- **Bottom tab bar** (optional): in Preferences → «Bottom tab bar» the first 4 tabs sit at the bottom and the rest under «More». The classic top bar is still the default.
- **Faster Trails and Map**: trails are read and sorted in the background, updating only the changed ones, and the map tracks are prepared off the main thread.

## 2.41 (code 481) — 04/11/2026
- **Photos on the route without gallery permission** (Google Play requirement): pick photos with the system picker via the 📷 button on the map (with a timed GPX). With photos already on the map, **long-press the F eye** to change or add some.

## 2.40 (code 480) — 04/11/2026
- **Home**: tapping the star of the startup tab removes it, and the app opens on Home again.

## 2.39 (code 479) — 04/11/2026
- **Release history updated**: now includes versions 2.33 to 2.38.

## 2.38 (code 478) — 04/11/2026
- **Grid Home**: buttons only, height fitted to the screen. **Edit tabs** shows the **eye** (show/hide a tab) and the **star** (tab opened at startup).
- **Home**: **CHANGE** button to pick the main tab, **Open at startup** switch and monochrome icons instead of emoji.
- **Event card**: shows the duration when longer than 1 day.
- **Calendar info**: scrollable collaborator list, with the full identifier when there is no email.
- **Contacts**: **Copy from…** and **Clone to…** buttons (clone your card into another calendar).
- **More compact text fields** across the app and a **leaner ride form** (GPX, background and audio without redundant labels).
- **Faster startup and lighter app**: slimmer icon and font, Italian and English only.

## 2.37 (code 456) — 03/11/2026
- **Home with one button per tab** and a suggested tab at startup (Preferences).
- **Instant Home**: the app can open straight on Home. Vector Trail icon.

## 2.36 (code 455) — 03/11/2026
- **Record tab**: dashboard, reports and SOS.
- **Sky theme**, the name **Giro** in the app and **Calendars** with a large title and details.
- **Help**: swipe between tabs.
- **Tips**: rotating per user, numbered; 3 taps on the light bulb show them all.
- **"Maybe" answer** for rides.

## 2.35 (code 454) — 02/11/2026
- **Update**: opens Google Play explicitly (no longer Mi Store on Xiaomi/POCO).
- **Calendar info**: a second tap on the logo opens "More info".

## 2.34 (code 446) — 02/11/2026
- **Comments on ride cards** (logged-in users only).
- **Update popup** at startup, personal-area notice in Workshop and test notice in Emergency.

## 2.33 (code 440) — 01/11/2026
- **Faster startup**: file cache for events and decoding off the UI thread.
- **Tips** as random popups ("Enough!" to stop them), menu entry after Preferences and an on/off switch in Preferences.
- **Trail suggestions**: status, name and position editable by logged-in users.
- **Event editor**: add and rename participants.

## 2.32 (code 429) — 29/10/2026
- **Admin padlock removed**: editors always see the edit controls. Deleting a notice now asks for confirmation.
- **Event photo**: double-tap to zoom; after downloading, "Image saved" appears with an **Open** button.
- **Past**: the 10 most recent rides, the rest behind "N more rides" (and "Show less").
- **Contacts**: "Create your card" banner if you haven't made one yet.
- "Cancel" translated in three dialogs (event sharing, calendar info, audio trim).

## 2.31 (code 428) — 29/10/2026
- **Download the event image**: in the full-screen photo viewer, a download button (saves the original to Pictures/GiroCalendar).

## 2.30 (code 427) — 29/10/2026
- **Contacts tab for everyone**: visible to anyone signed in (no longer editors only); everyone creates and edits their own card. Help updated.

## 2.27 (code 424) — 27/10/2026
- **Points of interest (POI)**: meeting point, summit, picnic area, other, with a name and description. **P** eye on the map; in edit mode "📍 POI" and a tap on the map; bubble with description, "added by / edited by" and a **Navigate** button (Google Maps, Waze, other app). In **3D** they appear on the terrain by themselves and shrink with zoom. Entries in the LOG. Meeting point: white flag on a purple background.
- **Compact map edit bar** (left column: Draw, Path, Zone, POI); layers and magnifier lower down; "?" guide updated (IT/EN).
- **Info** compact on one screen, borderless logo and **Release history** (IT/EN).
- **Map preferences**: *Zone colour opacity* (0–70%) and *Circled trail name* on/off. A POI bubble is no longer covered by the edit bar.

## 2.26 (code 418) — 26/10/2026
- **Duration (days)** on the event card (default 1): field in the editor, "N days" in the detail and in the shared text (web too, v2.14).
- **"My location" button**: when already on, one tap re-centres the map on the dot (it used to switch it off); a long press switches it off.
- **Full-screen map**: the + / − buttons stay above Android's navigation bar.
- **Menu → Info** made compact (everything on one screen) with a borderless logo and **Release history**.

## 2.25 (code 392) — 19/10/2026
- **3D flight** (MapLibre GL in the WebView, same page as the website): real terrain, OSM/Topographic/CyclOSM/Light/**Satellite** layers, compass, free camera with a 🎥 panel (tilt, zoom, rotation), "Follow" on/off, **Locate me** 📍, opens on the whole track without starting; ▶ animates only the dot.
- **Fixed 3D button** on the Map: the zone in 3D with trails (colour = difficulty) and zones; with a GPX loaded it shows the GPX with the ▶ bar above trails and zones.
- The **loaded GPX stays** when switching tabs; the **eyes** Z, L, T, N, G, F are remembered.
- Preferences → **GPX track thickness** (before Language): very thin … very thick, for the map, playback and 3D.
- 3D layout designed for phones (top bar, compact stats, time with hours).

## 2.24 (code 382) — 19/10/2026
- **GPX playback**: ▶ button next to "GPX", speed 1–60× (default 20×), grey track that turns coloured as it passes, hideable elevation profile (tap to jump), free map (optional ◎ Follow), sliders that no longer fight playback.
- **Photos on the route**: from the gallery (photo permission), thumbnails that scale with zoom, tap to view full screen, eye F; position from GPS or estimated from the time.
- Small **LOG** button at the bottom left of the Map (admins only); `-word` filter in LOG search; explicit confirmation for Undo/Redo.
- Compact GPX row, layers and magnifier lower down, "Your GPX" → "GPX", thinner lines, Satellite layer in the maps.

## 2.23 (code 362) — 18/10/2026
- **Place search** on the map (magnifier, Nominatim); **GPX from other apps** (Open with / Share); Help updated IT/EN.
- LOG below the map and the `-word` filter (later replaced by the LOG button).

## 2.22 (code 350) — 18/10/2026
- **Undo operations from the LOG** (admins only): trail/zone rename, difficulty, status, position, track, creation and deletion of a single trail (with comments). Each undo is itself a log entry (Redo). Warning if the trail was changed afterwards.
- **Rename zone** also for zones without trails (moves perimeter and builders).
- **Trailforks UK/EU difficulty scale**: White, Green, Blue, **Red**, Black, Proline.
- **Full-screen map**: button bar higher up, layer selector below the bar (no longer covered).
- Firestore rules: an admin can re-create deleted trails/comments.

## 2.21 (code 345) — 18/10/2026
- **Track converter** in the ⋮ menu of "Your GPX": **Convert to…** KML, GeoJSON, TCX, CSV (Save or Share). "Your GPX" also opens KML, GeoJSON, TCX, CSV.

## 2.20 (code 343) — 17/10/2026
- **GPX tools** in the ⋮ menu of your GPX on the Map: statistics, crop, reverse, simplify, save, share. (The dedicated GPX tab, added and removed on the same day, was absorbed here.)
- Direction arrows on your personal GPX.
- **Donate** item in the menu.
- Info: "Android version" box with Copy link/Share, green iOS/web and Android boxes, "More SimplyLucaApps apps" link at the bottom.
- Fonts: Nunito removed, **Lexend** added.
- Map: "switch to full screen" notice in the centre with an icon.

## 2.19 (16/10/2026) — Marketplace
- **Marketplace tab**: listings (calendar only or for everyone) with up to 8 full-quality photos, photo editor (crop, rotate), full-screen viewer, main photo (star), price in €, category, separate contacts (phone + WhatsApp, email, other), seller name highlighted.
- Listing sharing with a public page link, copy link.
- Emergency stays the last tab.

## 2.18 (16/10/2026)
- Interim version (bump).

## 2.17 (code 315, 15/10/2026)
- **Push for new proposals** (FCM + Cloudflare function), dot always visible for editors, notice with a "View" action while the app is open.
- **Blacklist per account/email**: "Exclude editing" or access denied; "Editing denied" on the map pencil; collaborators listed from the logo.
- **Proposals only from logged-in users**.
- Complete route map for a ride (layers, eyes Z/L/T/N, position, start/finish), always starts from OSM; statistics below the map.
- "Cancelled" checkbox only when editing an existing ride.

## 2.16 (code 303, 15/10/2026)
- **Cancelled ride**: flag, red diagonal bar on the card, hidden from Month, clone resets it.
- **Clone into the current calendar too**; lock off = grey closed.
- **"Update available"** in the menu (Play In-App Updates + `version.json` fallback).
- **Complete English translation** (~330 strings in resources, log, Emergency, map and JS); English weekday abbreviations.

## 2.12 – 2.15 (15/10/2026)
- Sortable notices (arrows) and copyable text; notice title.
- Preferences: map cache dropdown, "Audio autoplay", Calendars tab off and Emergency on by default.
- Map guide ("?"), X on the personal GPX.
- Help/Info updated (Month, Map, Emergency tabs).
- **Emergency**: maximum 5 contacts.

## 2.5 – 2.11 (14/10/2026)
- **Emergency**: fall detection, automatic SMS even without data, WhatsApp/Telegram with a tap, siren and full-screen countdown, messages with placeholders, real-time log, test mode.
- **Sortable/visible tabs** (Preferences → Visible tabs).
- GPX recording (implemented, hidden by a flag), trail rename, delete zone, new zone from the perimeter.
- Preferences: map cache size; more visible zone area; Import GPX at the bottom.
- Workshop settings moved into Workshop.

## 2.0 – 2.4 (13–14/10/2026) — Advanced map
- **New version rule**: from 2.0 the minor number goes up with every AAB.
- Zones: **trail builders** (logo, name, social link) on the map; eye L; logos scaled with zoom; edit builders.
- Zones without trails; delete zone (admin); "Light" background with no API keys.
- Eyes Z/L/T/N/G, last view saved, X on "Your GPX", editable zones.
- Choice of navigation app for the Parking spot (Google Maps, Waze, other).

## 1.3 – 1.9 (13/10/2026) — Map
- Eye/pencil mode (read-only by default), optional trail names, "See your GPX on map", "See on map".
- **My location**, compact "Your GPX" with X, compact layers with X.
- **Tap a path**, drawn zones (informational polygons), edit/delete tracks (long press), tap in view with name + km.
- Segments coloured by difficulty, the map no longer jumps after saves, version at the bottom of the menu.

## Mid October — Map, trails, LOG (12/10/2026)
- **MAP tab** (OpenStreetMap, Topographic, CyclOSM, MTB overlay), tile disk cache (150 MB), full screen, trail tracks, "Import GPX" matched by name, "Remove from map".
- **Finger tracing** snapped to OSM paths.
- **Trail change LOG** (admins only, search, last 20); editable status/difficulty; zone from a list.
- "Single-row tabs" preference.
- 500 MB Firestore disk cache.

## 1.4x – 1.7x (04–11/10/2026)
- **Propose event**: GPX, background image and audio attachments.
- **Super-admin**: moderate calendars (private/delete), collaborators' emails.
- **Contacts tab**: one card per collaborator (social, email, phone), profile photo with zoom and circular crop, photo from the Google account, cloneable between calendars, shareable with a web link.
- Trails: edit and delete your own comments; rename area (admin); difficulty from the detail.
- **MONTH tab** (09/10): monthly grid with titles and the day's background image.
- Info: copy/share site link; share event as text or link; event **App Links**; share calendar (web link); notices with a title.
- Help: Workshop entry.

## 1.19 – 1.42 (29/09 – 01/10/2026) — Workshop
- **Workshop tab**: bike maintenance (bikes, jobs, compact table, 14 colours), export CSV/PDF/JPEG/text/ODS, job icons (automatic from text + manual) with colours, detail popup with editing.
- Delete calendar, Preferences language fix, new icon (debug icon with DEBUG label), CSV with `;`.

## 1.0 – 1.12 (26–28/09/2026)
- **Version 1.0** (26/09): owner always in the Calendars tab, splash, compact "Propose", weekday after the date.
- New light theme "Violet Dawn" (3 dark + 3 light), Themes entry.
- GPX download (save/share) from event/epic; Upcoming/Past tab fix.
- Owner's email in quick-info; updated tagline; web version highlighted.
- Help: dialog redone (separate rows, bullet points), line-wrap fix.

## 0.x (22–25/09/2026) — Construction
- **Initial scaffold** (22/09): Compose + Firestore + Credential Manager, same model as VolleyCalendar.
- New icon, Preferences, Announcements (notices), splash logo.
- **Propose event** and **Join**, weather (on request), multiple fonts, full PWA fields.
- Contextual icons, "+calendar", share, compact forms; event archive, full photo in the detail.
- **Trail tab** (rideability status, comments), GPX/audio on events, clone event (also from other people's calendars), public calendars, English i18n (25/09).
- **Epics tab** (rides to repeat every year) with "Plan".
- **Help** dialog in the menu, statistics removed from Info.
