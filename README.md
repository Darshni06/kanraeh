# Kanraeh

Offline-first PCOS + migraine companion for Android. No cloud, no account, data encrypted on the phone.
Native Kotlin + Jetpack Compose. One Gradle module (`app`) organised into self-contained packages ("modules").

## Run it (first time)
1. Install **Android Studio** (current stable). Open it, choose **Open**, select this `Kanraeh` folder.
2. Wait for **Gradle sync** (needs internet the first time; it downloads Gradle 8.7 and libraries). If it offers to upgrade Gradle/AGP/Kotlin, choose **Don't remind me** for now.
3. On your Vivo: Settings > About phone > tap *Build number* 7 times > Settings > System > Developer options > turn on **USB debugging**.
4. Plug the phone in by USB, accept the "Allow USB debugging" prompt, pick your phone at the top of Android Studio, press the green **Run** button.
5. First launch: open **Me** and walk through *Permissions it needs* (overlay, usage access, notification access, battery, notifications) and **Vivo: keep Kanraeh running**.

## Build a shareable APK
Android Studio > **Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file lands in `app/build/outputs/apk/debug/app-debug.apk`.
Send it to a phone; the person must allow "Install unknown apps" once.

## Optional: online avatar creation
Copy the two lines from `local.properties.example` into `local.properties` and fill `AVATAR_API_URL` / `AVATAR_API_KEY`,
then adapt `avatar/AvatarCreation.kt > OnlineAvatarClient.generate()` to the provider you choose. Without them the avatar is built offline from the photo.

## Knowledge base
`app/src/main/assets/knowledge.json` is a PLACEHOLDER showing the format (`docs/KNOWLEDGE_FORMAT.md`).
Send the real research content to Claude to get the real file, then in the app: **Me > Knowledge > Import knowledge file**. No rebuild needed.

## If the build fails
Copy the first red error from the **Build** tab and send it back. Common ones: SDK not installed (Android Studio offers a one-click install), JDK mismatch (use the bundled JDK 17+).

## Map
See `docs/ARCHITECTURE.md`, `docs/SCREEN_CONTRACTS.md`, `docs/ANTIGRAVITY_PROMPT.md`.
