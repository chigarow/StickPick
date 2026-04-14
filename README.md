<div align="center">

<img src="Assets/logo.png" width="200" height="200" alt="StickPick" style="border-radius: 20px;" />

# StickPick

**Telegram → WhatsApp Sticker Converter**

Fetch any Telegram sticker pack, convert animated & static stickers, and export directly to WhatsApp — all from one app.

<br/>

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![FFmpeg](https://img.shields.io/badge/FFmpeg-Kit-007808?style=flat-square&logo=ffmpeg&logoColor=white)
![Android](https://img.shields.io/badge/Android-26+-3DDC84?style=flat-square&logo=android&logoColor=white)
![WhatsApp](https://img.shields.io/badge/WhatsApp-Sticker_API-25D366?style=flat-square&logo=whatsapp&logoColor=white)

</div>

## [+] How It Works

> Paste a Telegram sticker pack link → Download → Preview → Convert → Add to WhatsApp

The app fetches sticker metadata via the Telegram Bot API, downloads all stickers, converts `.webm` video stickers to animated WebP using FFmpeg, and serves them to WhatsApp through a ContentProvider following the [official WhatsApp Sticker API](https://github.com/WhatsApp/stickers).

## [+] Features

- **Animated & Static** : handles video (`.webm`) and image (`.webp`) stickers from Telegram
- **Smart Pack Splitting** : auto-separates animated (max 30) and static (max 50) into valid WhatsApp packs
- **WhatsApp + Business** : export to both WhatsApp and WhatsApp Business
- **Gallery Import** : add your own images to any pack before converting
- **Dark Mode** : light, dark, and system-default themes
- **Transparent Padding** : non-square stickers keep transparency, no black bars
- **Configurable** : pack size limit, author name, bot token — all from settings

## [+] Setup

<details>
<summary><strong>1. Get a Telegram Bot Token</strong></summary>

<br/>

1. Open Telegram and search for **@BotFather**
2. Send `/newbot` and follow the prompts
3. Copy the token (looks like `123456789:ABCDefGhI...`)

The bot is only used to access the Telegram API — it doesn't need to be added to any group.

</details>

<details>
<summary><strong>2. Build & Run</strong></summary>

<br/>

```bash
git clone https://github.com/AvishkarPatil/StickPick.git
cd StickPick
```

Open in Android Studio → Build → Run on device.

> **Note:** FFmpeg conversion requires a real ARM device. Emulator (x86) will be extremely slow.

</details>

<details>
<summary><strong>3. First Launch</strong></summary>

<br/>

The app walks you through a two-step onboarding:

1. Paste your Telegram Bot Token
2. Enter your name (appears as publisher on WhatsApp packs)

Both can be changed later in Settings.

</details>

## [+] Building the APK

```bash
# Debug (faster, larger ~130MB with x86)
./gradlew assembleDebug

# Release (ARM only, ~87MB)
./gradlew assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

> APK size is ~87MB due to FFmpeg native libraries. An AAB for Play Store would be ~45MB per device via ABI splitting.

## [+] License

This project is licensed under the [MIT License](LICENSE).

<br/>

<div align="center">

Built with ❤️ by **Avishkar Patil**

</div>
