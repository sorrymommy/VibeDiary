# VibeDiary

A map-based diary for capturing a moment as a **photo, memo or voice recording** the instant it comes to mind.
Voice-to-text and tag extraction both run **entirely on the phone**. Data is stored on the phone and synced
to a **hidden app-only folder in your Google Drive (appDataFolder)**, so running costs are zero.

## Features
| Feature | Implementation |
|---|---|
| Capture | Camera / gallery photo, memo (`#tags` supported), 16 kHz WAV recording, GPS saved automatically |
| Voice → text | Android built-in `SpeechRecognizer` (prefers on-device, Korean), Android 13+ |
| Tag extraction | `#hashtags` + topic/mood keyword rules + ML Kit photo labels (on-device) + place (neighbourhood) tag |
| Timeline | Cards grouped by date, filter by tag |
| Map | Google Maps markers → tap for card → detail |
| Sync | WorkManager: 5 s after a change + every 6 hours, last write wins, deletes travel as tombstones |

## Code structure
```
app/src/main/java/com/vibediary/app/
├─ data/      Room(Entry), EntryRepository, AppPrefs
├─ capture/   AudioRecorder(WAV), Locator(GPS·reverse geocoding)
├─ ai/        Transcriber(STT), ImageTagger(ML Kit), TagExtractor(rules), AnalysisWorker
├─ sync/      DriveAuth, DriveApi(REST), SyncWorker, SyncScheduler
└─ ui/        home(timeline·sync menu), map, newentry, detail
```

## Required setup (one-time, free)

### 1. Google Maps API key
1. [Google Cloud Console](https://console.cloud.google.com/) → create a project
2. **APIs & Services → Library → enable "Maps SDK for Android"**
3. **Credentials → API key**. Restriction: Android apps, package `com.vibediary.app` + SHA-1
4. Add it to `local.properties` in the project root:
   ```
   MAPS_API_KEY=AIza...
   ```
   > Displaying the map in a mobile app (Maps SDK for Android) costs nothing.

### 2. Google Drive sync (OAuth)
1. In the same project, **enable "Google Drive API"**
2. **OAuth consent screen**: External, add your Gmail address as a test user, add scope `.../auth/drive.appdata`
3. **Credentials → OAuth client ID → type: Android**
   - Package: `com.vibediary.app`
   - SHA-1: output of the command below (debug key)
   ```bash
   keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
4. In the app, tap the cloud icon at the top → choose a Google account → allow. Sync starts after that.

> If you build a release with a different signing key, register that key's SHA-1 too.

### 3. Korean voice recognition
Settings → System → Language → On-device speech recognition → download **Korean**.
(The app also asks for the download on its own when the language pack is missing; after that, use "Re-analyse" on the detail screen.)

## Build
```bash
gradlew.bat assembleDebug
```
Requires JDK 17 or later (Android Studio's bundled JBR works). If `JAVA_HOME` points to 1.8, change it.

## Known limitations / next steps
- Voice → text works only on **Android 13+**. On lower versions the recording is kept and only the transcription is skipped.
- Tags are rule-based. A good next step is plugging an on-device LLM (Gemini Nano, ML Kit GenAI) in behind the same signature as `TagExtractor.extract()`.
- Media is uploaded with Drive multipart upload, so very long recordings (several MB or more) may need switching to resumable upload.
- Compiled against SDK 36, which is why some libraries are pinned one step below their latest versions (the newest ones need compileSdk 37).
