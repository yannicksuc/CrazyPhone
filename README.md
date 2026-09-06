# 📱 CrazyPhone

A held-item smartphone: contacts, group texting, photos/albums, an optional mayor election, and an
optional Simple Voice Chat integration (calls + voice messages). Built with [Stonecutter](https://stonecutter.kikugie.dev/)
on a single shared source tree targeting both **NeoForge** and **Fabric**, across several Minecraft
versions.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%E2%80%93%2026.2-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1%20%C2%B7%2026.1-D7791E)](https://neoforged.net/)
[![Fabric](https://img.shields.io/badge/Fabric-1.21.1%20%C2%B7%2026.1-DBB69B)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-17%20%2F%2021-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](#-license)

> [!NOTE]
> CrazyPhone is a standalone, hand-written rewrite of the smartphone feature originally found in my own
> **crazythings** mod - re-architected to fix a server-crashing data growth bug. See the
> [Architecture wiki page](https://github.com/yannicksuc/CrazyPhone/wiki/Architecture) for why.

**📚 Full documentation lives on the [wiki](https://github.com/yannicksuc/CrazyPhone/wiki)** - installation,
building from source, every config option, the full command tree, and an in-depth feature breakdown. This
README stays short on purpose: a feature overview, the platform support table (the one thing that lives
here and only here, so it can't drift out of sync with the wiki), and pointers to the rest.

---

## ✨ Features

*Full write-up of every feature, with screenshots and edge cases, on the wiki - linked per feature below.*

- **Phone basics** - two-step registration + PIN sign-in, home screen launcher, lock screen. Craftable or
  given via `/crazyphone give <number>`.
- **[Messaging](https://github.com/yannicksuc/CrazyPhone/wiki/Home)** - contacts, favorites, groups,
  real-time text, sending photos, read badges, and live-converting pixel-art emoji (paste a real emoji,
  type a `:shortcode:`, or a classic `:)`/`<3` emoticon).
- **[Camera & My Photos](https://github.com/yannicksuc/CrazyPhone/wiki/Camera-and-Photos)** - a native,
  dependency-free capture overlay (three ways in: conversation, home screen, or punch-to-shoot), a flat
  photo gallery, and a physical photo item you can dye like leather armor.
- **[Sneak-presenting & selfie mode](https://github.com/yannicksuc/CrazyPhone/wiki/Camera-and-Photos#sneak-presenting)** -
  hold a photo up two-handed in front of you, or press F5 for a selfie-stick camera view.
- **[Photo frames](https://github.com/yannicksuc/CrazyPhone/wiki/Camera-and-Photos#photo-frames)** -
  place a photo on any surface as a resizable entity, with a drag-select resize GUI, rotate, and fullbright.
- **[Voice calls & voice messages](https://github.com/yannicksuc/CrazyPhone/wiki/Voice-Calls-and-Messages)**
  *(NeoForge only, optional, needs [Simple Voice Chat](https://modrepo.de/minecraft/voicechat))* - ringing
  1:1/group calls with dedicated screens, and recorded voice-message clips with waveform playback.
- **Soulbound enchantment** *(≥1.20.5)* - an Ancient City-only enchantment that survives death.
- **[Mayor election](https://github.com/yannicksuc/CrazyPhone/wiki/Mayor-Election)** *(optional)* -
  candidates with campaign posters, in-app voting.
- **[Per-feature toggles & permissions](https://github.com/yannicksuc/CrazyPhone/wiki/Configuration)** -
  calls, voice messages, images, and the election can each be switched off and/or gated behind a
  permission node.
- **Localization** - every string goes through the lang files (English + French shipped); see
  [Architecture § Localization](https://github.com/yannicksuc/CrazyPhone/wiki/Architecture#localization)
  for adding another one.
- **Built to scale** - conversation history and voice/image payloads are capped and never broadcast
  wholesale - see [Architecture § Why this exists](https://github.com/yannicksuc/CrazyPhone/wiki/Architecture#why-this-exists).

---

## 🧩 Platforms & versions

One shared `src/main/java` tree, preprocessed per target by [Stonecutter](https://stonecutter.kikugie.dev/)
(`//? if fabric` / `//? if neoforge`, plus per-version checks) into 9 build targets.

### Maintenance status

Update this table whenever a target's status actually changes - it's the quick answer to "is this version
still getting updates", separate from the detailed per-feature table below.

| Target | Status |
|---|---|
| Fabric 1.20.1 | ⚪ Not functional - phone networking needs an API absent before 1.20.5, no active work planned |
| NeoForge 1.20.4 | ⚪ Frozen - unmaintained indefinitely (since 2026-09-02), local builds only |
| NeoForge 1.21.1 | 🟢 Actively maintained - primary NeoForge target |
| Fabric 1.21.1 | 🟢 Actively maintained - primary Fabric target |
| NeoForge 1.21.10 | ⚪ Frozen - unmaintained indefinitely (since 2026-09-03) |
| NeoForge 26.1 | 🟢 Actively maintained |
| Fabric 26.1 | 🟢 Actively maintained |
| NeoForge 26.2 | 🟡 Work in progress - doesn't compile yet, see [PORTING-26x.md](PORTING-26x.md) |
| Fabric 26.2 | 🟡 Work in progress - doesn't compile yet, see [PORTING-26x.md](PORTING-26x.md) |

Only the 🟢 targets get new features and bugfixes going forward. The 26.x line is otherwise a newer,
ongoing port - see [PORTING-26x.md](PORTING-26x.md) for its detailed status and remaining work.

| | Fabric 1.20.1 | NeoForge 1.20.4 *(unmaintained)* | NeoForge 1.21.1 | Fabric 1.21.1 | NeoForge 1.21.10 *(unmaintained)* | NeoForge 26.1 | Fabric 26.1 | NeoForge 26.2 | Fabric 26.2 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Phone, messaging, contacts, groups | - | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Mayor election | - | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Native camera (capture/viewer/photo item/My Photos) | - | ✅ | ✅ | ✅ | - *(pending)* | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Sneak-presenting (hold a photo up, two-hand grip) | - | ✅ | ✅ | ✅ | - *(pending)* | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Photo frames (placeable, resizable, silk touch) | - | - | ✅ | ✅ | - | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Selfie mode (camera/arm/head on a selfie stick) | - | - | ✅ | ✅ | - | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Voice calls & voice messages | - | ✅ *(SVC)* | ✅ *(SVC)* | - | ✅ *(SVC)* | ✅ *(SVC)* | - | - *(pending)* | - |
| Soulbound enchantment | - | - | ✅ | ✅ | ✅ | ✅ | ✅ | - *(pending)* | - *(pending)* |
| Runtime-configurable settings | - | ✅ | ✅ | - | ✅ | ✅ | - | - *(pending)* | - |

- **NeoForge 1.21.10**'s camera and sneak-presenting features compile but don't work yet: Mojang reworked
  both item rendering and the screenshot/texture APIs the native pipeline uses on that version, and porting
  to the new APIs is a separate, tracked follow-up.
- **NeoForge 26.1** compiles clean and has been live-tested (native camera, sneak-presenting including
  two-hand dual-photo, selfie mode, voice calls) - the newest fully-verified target in the project.
- **NeoForge 26.2** doesn't compile yet - blocked on the same item-rendering API migration 26.1 needed,
  not yet finished for this node. See [PORTING-26x.md](PORTING-26x.md).
- **Fabric 1.20.1** is a walking skeleton for now - the item exists and registers, but the phone's
  networking layer needs an API (`CustomPacketPayload`) that doesn't exist before 1.20.5, so none of the
  screens/messaging/camera work yet on that specific version.
- **Fabric 1.21.1** has the core feature set, the native camera pipeline (including punch-to-shoot,
  standalone capture, and My Photos), sneak-presenting, selfie mode, and the Soulbound enchantment - but no
  voice calls/messages, since [Simple Voice Chat](https://modrepo.de/minecraft/voicechat) integration hasn't
  been ported to Fabric yet.
- **Fabric 26.1** compiles clean and is live-tested, with full parity with NeoForge 26.1 except voice
  calls/messages (same Fabric SVC gap as 1.21.1).
- **Fabric 26.2** doesn't compile yet - blocked on the same item-rendering API migration 26.1/26.2 needed,
  plus its own Fabric-specific registration gap (no `BuiltinItemRendererRegistry`-equivalent wired up for
  the new API yet). See [PORTING-26x.md](PORTING-26x.md).

---

## 🚀 Getting started

- **Playing or hosting a server**: see [wiki § Installation](https://github.com/yannicksuc/CrazyPhone/wiki/Installation)
  for requirements and step-by-step setup on NeoForge and Fabric.
- **Building from source**:
  ```bash
  git clone https://github.com/yannicksuc/CrazyPhone.git && cd CrazyPhone
  ./gradlew :1.21.1:build          # or :26.1:build, :1.21.1-fabric:build, etc. - see the wiki for every target
  ```
  Full Gradle task reference, the multi-loader source layout, the dev-launch testing script, and how
  release publishing works: [wiki § Building from source](https://github.com/yannicksuc/CrazyPhone/wiki/Building-from-Source).
- **Configuration & permissions**: every option, its default/range, and the permission-node system are on
  [wiki § Configuration](https://github.com/yannicksuc/CrazyPhone/wiki/Configuration).
- **Commands**: the full `/crazyphone` tree is on [wiki § Commands](https://github.com/yannicksuc/CrazyPhone/wiki/Commands).

---

## 🖼️ Screenshots

<p align="center">
  <img src="docs/screenshots/holding-the-phone.png" width="800" alt="A player holding the Crazy Phone">
</p>

<details>
<summary><b>Home, sign-in &amp; registration</b></summary>
<br>

| Home screen | Registration | Login |
|:---:|:---:|:---:|
| ![Home screen](docs/screenshots/menu-home.png) | ![Registration](docs/screenshots/menu-signin.png) | ![Login](docs/screenshots/menu-lock-password.png) |

</details>

<details>
<summary><b>Messaging (contacts, favorites, groups &amp; conversations)</b></summary>
<br>

| Messaging | Adding a contact |
|:---:|:---:|
| ![Messaging](docs/screenshots/menu-contacts.png) | ![Adding a contact](docs/screenshots/menu-add-contact.png) |

| Conversation | Sending an image |
|:---:|:---:|
| ![Conversation](docs/screenshots/menu-messages-1.png) | ![Send image tooltip](docs/screenshots/menu-messages-2.png) |
| An image sent in chat | Timestamp & zoom tooltip |
| ![Image sent](docs/screenshots/menu-messages-3a.png) | ![Timestamp tooltip](docs/screenshots/menu-messages-3b.png) |

</details>

<details>
<summary><b>Camera (outdated screenshots)</b></summary>
<br>

> [!NOTE]
> These screenshots are from the old Camera-mod-backed album UI, since replaced by the native
> capture-overlay/photo-item flow - see [wiki § Camera and Photos](https://github.com/yannicksuc/CrazyPhone/wiki/Camera-and-Photos).
> Kept here as history until fresh screenshots of the new flow are taken.

| A photo taken with the phone's camera | Albums | Album contents | Picking images to send |
|:---:|:---:|:---:|:---:|
| ![Photo taken with the phone](docs/screenshots/photo-temple.png) | ![Albums](docs/screenshots/menu-albums.png) | ![Album contents](docs/screenshots/menu-album.png) | ![Picking images to send](docs/screenshots/menu-add-image-to-conversation.png) |

</details>

---

## 🙏 Credits

- **[Simple Voice Chat](https://modrepo.de/minecraft/voicechat)** by [henkelmax](https://github.com/henkelmax): the voice engine calls and voice messages are built on top of (NeoForge only).
- **[Pixel Twemoji 9x](https://modrinth.com/resourcepack/pixel-twemoji-9x)** by [AmberW](https://modrinth.com/user/AmberW), based on [Twemoji](https://github.com/twitter/twemoji) (Copyright (c) 2018 Twitter, Inc and other contributors): pixel-art emoji glyphs bundled into the chat font. Both CC-BY-4.0 - see [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md).
- Original `crazythings` project (also mine): source of the feature set and assets this mod ports and rebuilds.

## 📄 License

`All Rights Reserved`. See the mod description in [`gradle.properties`](gradle.properties) for authorship details.
