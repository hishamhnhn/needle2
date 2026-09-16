# Cactus Terminal

A black terminal-style Android app that runs Cactus's **Needle 2** (14MB on-device
agentic model) to call real phone functions — battery, flashlight, vibration,
opening other apps, device info, time — entirely offline after the model's first
download.

## Get an installable APK with no installs on your end

This project includes `.github/workflows/build.yml`, which makes GitHub's own
servers compile the APK for you — you only need a free GitHub account, no
Android Studio, no local tools.

1. Go to https://github.com/new and create a new **public** repository (any name).
2. On the new repo's page, click **"uploading an existing file"** (or Add file →
   Upload files), then drag in every file/folder from this unzipped project
   (including the hidden `.github` folder — if your file browser hides it, use
   `git` locally, or a tool that shows hidden files, to make sure `.github/workflows/build.yml`
   gets uploaded too).
3. Commit the upload to the `main` branch.
4. Go to the repo's **Actions** tab — a "Build APK" run should start automatically
   (takes ~3-5 minutes).
5. When it finishes (green check), click into the run, scroll to **Artifacts**,
   and download `CactusTerminal-debug-apk` — it's a zip containing `app-debug.apk`.
6. Transfer that `.apk` to your Galaxy A15 (email it to yourself, Google Drive,
   USB cable — any way you'd move a file).
7. On the phone, tap the `.apk` file. Android will ask to allow installs from
   that source (Settings → allow this once) — approve it, then tap **Install**.
8. Open "Cactus Terminal" from your app drawer. First launch downloads the
   Needle 2 model over your data/Wi-Fi (~14MB), then it's ready.

This produces a **debug** APK, which is unsigned for distribution but installs
and runs completely fine on your own phone via "install from unknown sources."

## Alternative: build it yourself with Android Studio

1. Install **Android Studio** (Iguana or newer) if you don't have it.
2. Open this folder (`CactusTerminal/`) as a project — File → Open → select this directory.
3. Let Gradle sync (first sync pulls the Cactus SDK from Maven Central; needs internet).
4. Connect your Galaxy A15 with USB debugging on, or use an emulator (API 26+).
5. Click Run ▶. On first launch the app downloads the Needle 2 model (~14MB) over
   Wi-Fi/data — you'll see the download progress as status lines in the terminal.
6. Once it says `Ready.`, type things like:
   - `what's my battery level?`
   - `turn on the flashlight`
   - `open camera`
   - `what time is it`

## How it's wired together

- `MainActivity.kt` — the black terminal UI (Jetpack Compose): a scrolling log and
  a single input line, styled green-on-black.
- `CactusManager.kt` — loads Needle 2 via `CactusLM`, sends your text plus the list
  of available tools, and if the model responds with a tool call, executes it and
  feeds the result back to the model automatically so you only see the final answer.
- `ToolExecutor.kt` — the actual Android API calls (`CameraManager` for the torch,
  `BatteryManager`, `Vibrator`, `PackageManager` for launching apps). **This is
  where you add new capabilities.**

## Adding a new tool

In `ToolExecutor.kt`:
1. Add a `createTool(name, description, parameters)` entry to `allTools`.
2. Add a `when` branch in `execute()` that does the real Android work and returns
   a short string result.

That's it — `CactusManager` automatically offers every tool in `allTools` to the
model on every turn.

## Sensitive tools (SMS, contacts, calendar) — not included yet

Those need Android's runtime permission flow (a permission dialog, not just a
manifest entry) plus `ContentResolver`/`SmsManager` calls, which is a bigger
enough chunk of code that it's worth doing as its own pass. Ask and I'll add:
- `read_contacts` / `find_contact`
- `send_sms` (requires `SEND_SMS` runtime permission — Google Play also restricts
  default-SMS-app-only access to this permission, so this one has real deployment
  constraints worth knowing about before you build it)
- `create_calendar_event`
- `get_clipboard` / `set_clipboard`

## Notes on Needle 2

The code looks up the model by name (`"Needle"`) via `CactusLM().getModels()`
rather than hardcoding a slug, since Cactus's model slugs can change between SDK
releases. If lookup fails for some reason it falls back to the literal slug
`"needle"` — check Cactus's model list (https://cactuscompute.com/docs/kotlin) if
that ever needs adjusting.
