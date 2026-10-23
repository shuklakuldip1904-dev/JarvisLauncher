# JarvisLauncher

An Android home launcher with a searchable app drawer, local voice/command handling,
and optional Gemini or Groq chat.

## Build

Install JDK 17 and Android SDK Platform 34, then run:

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
The GitHub Actions workflow builds this APK on every push and uploads it as the
`app-debug` workflow artifact.

## Use

Set JarvisLauncher as the device's default Home app to use it as the launcher.
Choose an AI provider and save its API key in **Settings** to enable AI chat.
Keys are stored in the app's local preferences. Voice input requests microphone
permission when first used.

Commands such as `Open Settings` or `Open Mech Arena` are handled on-device.
To use `scroll up`, `scroll down`, or `tap x y`, enable **Jarvis Assistant** in
Android's Accessibility settings. The service reads visible window text and can
perform gestures; it does not send screen content to the configured AI provider.
