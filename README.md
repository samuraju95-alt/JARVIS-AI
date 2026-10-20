# JARVIS AI — Android starter project

Tamil/English AI chat, Android speech recognition, Android text-to-speech, futuristic HUD, and local conversation history.

## Build an APK

### Easiest path: GitHub Actions (no Android Studio required on your phone)
1. Create a GitHub account if needed.
2. Create a **private** repository, e.g. `jarvis-ai-android`.
3. Upload all files from this project, preserving folder structure.
4. Open **Actions** → **Build JARVIS APK** → **Run workflow** (or push to `main`).
5. Open the completed workflow run → Artifacts → download `jarvis-ai-debug-apk`.
6. Extract the ZIP and install `app-debug.apk` on your Android phone. Android may ask you to allow installs from that source.

Do not upload API keys or secrets into the repository.

### Build locally with Android Studio
- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3 and Gradle 8.9
- Open this folder in Android Studio, sync, then run `gradle assembleDebug`.
- APK path: `app/build/outputs/apk/debug/app-debug.apk`

## First launch
1. Open JARVIS AI.
2. Tap **API SETTINGS** and enter your own OpenAI API key.
3. Ensure your API account has API access and billing/credits as required.
4. Choose Tamil or English, then type a message or tap the microphone.
5. Allow speech recognition if Android prompts.

## Important security notes
- The API key is entered on-device and stored in app-private SharedPreferences; it is not hardcoded in the APK or included in this source project.
- This is a personal-use starter, not a hardened production app. For a public release, use a backend proxy with authentication, request limits, and secure key management rather than shipping a client-side API key.
- Do not expose the API key, and do not commit it to Git.
- This starter intentionally does not execute arbitrary Android commands. Phone-control features need explicit permissions and narrow, user-confirmed actions.
- Speech recognition availability and Tamil voice quality depend on installed Android speech services and language data.
- Internet is required for AI replies.
- The default model is `gpt-4.1-mini`. Change it in `MainActivity.kt` if your API account uses a different available model.
- API usage is billed separately from ChatGPT subscriptions.
