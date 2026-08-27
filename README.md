# 📱 CrazyPhone

A held-item smartphone: contacts, group texting, photos/albums, an optional mayor election, and an
optional Simple Voice Chat integration (calls + voice messages). Built with [Stonecutter](https://stonecutter.kikugie.dev/)
on a single shared source tree targeting both **NeoForge** and **Fabric**, across several Minecraft
versions.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%E2%80%93%201.21.10-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-1.20.4%20%C2%B7%201.21.1%20%C2%B7%201.21.10-D7791E)](https://neoforged.net/)
[![Fabric](https://img.shields.io/badge/Fabric-1.20.1%20%C2%B7%201.21.1-DBB69B)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-17%20%2F%2021-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](#-license)

> [!NOTE]
> CrazyPhone is a standalone, hand-written rewrite of the smartphone feature originally found in the
> **crazythings** mod. It keeps the same look and core features, but the codebase was re-architected -
> most importantly to fix a server-crashing data growth bug, see [Why this exists](#-why-this-exists).

---

## 📖 Table of Contents

- [Features](#-features)
- [Platforms & versions](#-platforms--versions)
- [Requirements](#-requirements)
- [Installation](#-installation-players--server-admins)
- [Building from source](#-building-from-source)
- [Configuration](#-configuration)
- [Commands](#-commands)
- [Screenshots](#-screenshots)
- [Localization](#-localization)
- [Why this exists](#-why-this-exists)
- [Project structure](#-project-structure)
- [Credits & License](#-credits)

---

## ✨ Features

**Phone basics** - two-step registration (number/name, then a dedicated password step with a clear warning
that the password is visible to server admins) + PIN sign-in, home screen launcher, lock screen, back/home
navigation.

**Messaging** - contacts, favorites, and groups in one scrollable, recency-sorted screen; group
conversations with their own settings (rename, custom icon, invite/exclude, admin); real-time text;
sending photos from your album; read-notification badges; hover tooltips for timestamps/senders.

**Camera** - a native, dependency-free photo feature on both loaders: click the camera icon inside a
conversation to open a full-screen capture overlay (mouse wheel to zoom, click to shoot, Escape to cancel),
then send the shot straight into that conversation. The server keeps a small thumbnail (shown by default in
chat and as the saved photo item's icon) and a larger full-size version, fetched on demand when a photo is
opened full-size. Saving a photo to your inventory gives you a physical item that re-opens the same viewer
on right-click. See the [platform table](#-platforms--versions) for which targets have this working today.

**Voice calls & voice messages** *(NeoForge only, optional, requires [Simple Voice Chat](https://modrepo.de/minecraft/voicechat))*
- 1:1 and group voice calls: ring notification, dedicated Incoming Call / Calling / In Call screens,
  auto-hangup on dropping the phone or moving it to another inventory (not on cursor-carry), auto-kick
  when left alone in a call, a "call in progress" / call summary entry posted to the conversation itself.
- Voice messages: record and send a clip, played back with a live waveform, play/pause, and a speed
  toggle (0.5x/1x/2x); audio is only ever fetched from the server when a recipient clicks play.
- Fully optional both ways: the mod loads and works normally with Simple Voice Chat absent, and every
  piece of it can be turned off independently - see [Configuration](#-configuration).

**Soulbound enchantment** *(≥1.20.5 only)* - an Ancient City-only enchantment that keeps enchanted items
out of your death drops and hands them back on respawn.

**Mayor election** *(optional)* - candidate list with campaign posters, in-app voting with a cooldown,
commands to manage candidates and toggle the feature.

**Per-feature toggles** - calls, voice messages, sending images, and the mayor election can each be turned
on/off globally (config or command) and/or restricted to specific
players/groups via a permission node, if a permission plugin is installed - see [Commands](#-commands).
Runtime-configurable on NeoForge; compiled-in defaults on Fabric for now.

**Localization** - every button, tooltip, and label goes through the lang files (English + French
shipped); no hardcoded UI strings.

**Built to scale** - conversation history and voice/image payloads are capped and never broadcast
wholesale; see [Why this exists](#-why-this-exists).

---

## 🧩 Platforms & versions

One shared `src/main/java` tree, preprocessed per target by [Stonecutter](https://stonecutter.kikugie.dev/)
(`//? if fabric` / `//? if neoforge`, plus per-version checks) into 5 build targets. NeoForge 1.21.1 is the
primary development target; everything else is kept in sync with it.

| | NeoForge 1.20.4 | NeoForge 1.21.1 | NeoForge 1.21.10 | Fabric 1.20.1 | Fabric 1.21.1 |
|---|:---:|:---:|:---:|:---:|:---:|
| Phone, messaging, contacts, groups | ✅ | ✅ | ✅ | — | ✅ |
| Mayor election | ✅ | ✅ | ✅ | — | ✅ |
| Native camera (capture/viewer/photo item) | ✅ | ✅ | — *(pending)* | — | ✅ |
| Voice calls & voice messages | — *(see below)* | ✅ *(SVC)* | ✅ *(SVC)* | — | — |
| Soulbound enchantment | — | ✅ | ✅ | — | ✅ |
| Runtime-configurable settings | ✅ | ✅ | ✅ | — | — |

- **NeoForge 1.21.10**'s camera feature compiles but doesn't work yet: Mojang reworked both item rendering
  and the screenshot/texture APIs the native pipeline uses on that version, and porting to the new APIs is
  a separate, tracked follow-up.
- **NeoForge 1.20.4**'s voice calls/messages code is present but unusable in practice: Simple Voice Chat
  itself has no NeoForge build before 1.21.1, so there's nothing to integrate with on that version yet.
- **Fabric 1.20.1** is a walking skeleton for now - the item exists and registers, but the phone's
  networking layer needs an API (`CustomPacketPayload`) that doesn't exist before 1.20.5, so none of the
  screens/messaging/camera work yet on that specific version.
- **Fabric 1.21.1** has the core feature set, the native camera pipeline, and the Soulbound enchantment -
  but no voice calls/messages, since [Simple Voice Chat](https://modrepo.de/minecraft/voicechat)
  integration hasn't been ported to Fabric yet.

---

## 📋 Requirements

**NeoForge** (1.20.4, 1.21.1, or 1.21.10):

| Dependency | Required for |
|---|---|
| [NeoForge](https://neoforged.net/), matching your Minecraft version | Runtime |
| [Simple Voice Chat](https://modrepo.de/minecraft/voicechat), matching version | Runtime only, **optional** - calls/voice messages are simply unavailable without it |

**Fabric** (1.20.1 or 1.21.1):

| Dependency | Required for |
|---|---|
| [Fabric Loader](https://fabricmc.net/) `0.16.9+`, matching your Minecraft version | Runtime |
| [Fabric API](https://modrinth.com/mod/fabric-api), matching version | Runtime |

No Simple Voice Chat on Fabric yet - see the [platform table](#-platforms--versions) for what that means
feature-wise.

---

## 🎮 Installation (players / server admins)

**NeoForge:**
1. Install [NeoForge](https://neoforged.net/) for your target Minecraft version (1.20.4, 1.21.1, or 1.21.10).
2. (Optional) Install **[Simple Voice Chat](https://modrepo.de/minecraft/voicechat)** into `mods/` for calls and voice messages.
3. Drop the built `crazyphone-*.jar` for that version into `mods/`.
4. Launch the game, craft/obtain a Crazy Phone, and register a number.

**Fabric:**
1. Install [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api) for your target Minecraft version (1.20.1 or 1.21.1).
2. Drop the built `crazyphone-*.jar` for that version into `mods/`.
3. Launch the game, craft/obtain a Crazy Phone, and register a number - see the [platform table](#-platforms--versions) for what's available on Fabric today.

---

## 🔨 Building from source

```bash
git clone https://github.com/<your-username>/CrazyPhone.git
cd CrazyPhone
```

This is a [Stonecutter](https://stonecutter.kikugie.dev/) multi-version project: every version listed in
[Platforms & versions](#-platforms--versions) is its own Gradle subproject under `versions/`, prefix every
task with its target:

```bash
# Windows
gradlew.bat :1.21.1:build
gradlew.bat :1.21.1:runClient
gradlew.bat :1.21.1-fabric:build
gradlew.bat :1.21.1-fabric:runClient

# Linux / macOS
./gradlew :1.21.1:build
./gradlew :1.21.1:runClient
./gradlew :1.21.1-fabric:build
./gradlew :1.21.1-fabric:runClient
```

Swap `1.21.1` for `1.20.4` or `1.21.10` (NeoForge), or `1.21.1-fabric` for `1.20.1-fabric` (Fabric).

Run the test suite (boots a real, headless game instance so tests can use registries/data components) -
NeoForge only, per version:

```bash
gradlew.bat :1.21.1:test
```

---

## ⚙️ Configuration

**NeoForge:** generated at `config/crazyphone-common.toml` on first run. Every `*FeatureEnabled` value
below can also be changed at runtime with `/crazyphone feature`, without editing the file or restarting -
see [Commands](#-commands).

**Fabric:** the same settings exist as compiled-in defaults (shown below) - not yet exposed as an editable
file or via `/crazyphone feature`.

| Option | Default | Range | Description |
|---|---|---|---|
| `maxStoredMessagesPerConversation` | `300` | 10-10000 | Messages kept on disk per conversation; oldest dropped first once exceeded. |
| `maxMessagesSentPerRequest` | `100` | 10-1000 | Messages sent to a client in one page load of a conversation. |
| `maxImagesStoredPerConversation` | `50` | 5-2000 | Image messages kept on disk per conversation (capped separately - heaviest text-adjacent payload). |
| `maxPhotosStoredPerOwner` | `300` | 10-5000 | Photos (both resolutions) kept on disk per owning phone number; oldest dropped first once exceeded, independent of conversation trimming. |
| `mayorElectionFeatureEnabled` | `true` | - | Global switch for the mayor election feature. |
| `callsFeatureEnabled` | `true` | - | Global switch for voice calls. No effect without Simple Voice Chat installed. (NeoForge only) |
| `voiceMessagesFeatureEnabled` | `true` | - | Global switch for recording/sending voice messages. No effect without Simple Voice Chat installed. (NeoForge only) |
| `imagesFeatureEnabled` | `true` | - | Global switch for sending images into a conversation. |
| `voicechatIntegrationEnabled` | `true` | - | Master switch for the whole Simple Voice Chat integration (both calls and voice messages). (NeoForge only) |
| `callRingTimeoutSeconds` | `30` | 5-120 | How long a call rings before an unanswered callee counts as a missed call. (NeoForge only) |
| `aloneInCallKickSeconds` | `5` | 1-60 | How long a call stays open with one participant left before they're auto-removed. (NeoForge only) |
| `maxVoiceMessagesStoredPerConversation` | `30` | 5-500 | Voice messages (with audio) kept on disk per conversation. (NeoForge only) |
| `maxVoiceMessageRecordingSeconds` | `60` | 5-600 | Maximum length of a single voice message recording. (NeoForge only) |
| `soulboundEnchantmentEnabled` | `true` | - | Whether the Soulbound enchantment actually keeps enchanted items on death. |

**Permissions** *(NeoForge only for now)*: each toggleable feature also has a permission node
(`crazyphone.feature.<calls|voice_messages|images|mayor_voting>`), for restricting a feature to
specific players/groups instead of the whole server. Allowed for everyone by default; only takes effect
if a permission plugin (e.g. LuckPerms) is installed. A feature is usable only when **both** its global
switch is on **and** the player has the permission.

---

## 🖥️ Commands

Everything lives under `/crazyphone`. Arguments with a known set of values (registered phone numbers,
mayor candidates, feature names) tab-complete. *(Command tree is ported to Fabric too, minus the
`mayor candidate program` leaf - not yet ported.)*

| Command | Permission | Description |
|---|:---:|---|
| `/crazyphone give <number>` | 4 | Give yourself the phone registered to `<number>`. |
| `/crazyphone delete <number>` | 4 | Delete the phone registered to `<number>` and scrub it from every contact list. |
| `/crazyphone list [search]` | 4 | Search registered phones by number/name. |
| `/crazyphone feature list` | 2 | Show every toggleable feature's current global state. |
| `/crazyphone feature <name> <true\|false>` | 4 | Enable/disable a feature globally, at runtime. |
| `/crazyphone mayor vote <number>` | *(any player)* | Cast a vote - what the in-phone **Vote** button runs; not meant to be typed by hand. |
| `/crazyphone mayor election <true\|false>` | 4 | Enable/disable the election feature entirely. |
| `/crazyphone mayor voting <true\|false>` | 4 | Open/close voting while keeping the feature visible. |
| `/crazyphone mayor votes show` | 2 | Print current vote tallies. |
| `/crazyphone mayor votes clear <number>` | 4 | Clear a single player's vote (e.g. to let them re-vote). |
| `/crazyphone mayor candidate add <number>` | 4 | Register a phone number as a mayor candidate. |
| `/crazyphone mayor candidate remove <number>` | 4 | Remove a mayor candidate. |
| `/crazyphone mayor candidate program <number>` | 4 | Attach the image currently held in hand as that candidate's campaign poster. *(NeoForge only)* |

Permission levels are [vanilla op levels](https://minecraft.wiki/w/Permission_level); a permission plugin
can further restrict any of these the same way it would any other command.

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
> capture-overlay/photo-item flow described under [Features](#-features). Kept here as history until
> fresh screenshots of the new flow are taken.

| A photo taken with the phone's camera | Albums | Album contents | Picking images to send |
|:---:|:---:|:---:|:---:|
| ![Photo taken with the phone](docs/screenshots/photo-temple.png) | ![Albums](docs/screenshots/menu-albums.png) | ![Album contents](docs/screenshots/menu-album.png) | ![Picking images to send](docs/screenshots/menu-add-image-to-conversation.png) |

</details>

---

## 🌍 Localization

Every piece of UI text (button labels, tooltips, placeholders, headers) is routed through the lang files,
no hardcoded strings:

```
src/main/resources/assets/crazyphone/lang/en_us.json
src/main/resources/assets/crazyphone/lang/fr_fr.json
```

Want to add another language? Copy `en_us.json` to `<your_locale>.json` and translate the values; the
keys must stay identical between files.

---

## 🐛 Why this exists

The original `crazythings` implementation stored every phone, contact, and message ever sent, in every
conversation, forever, in one `SavedData` blob - and broadcast the *entire* thing to *every online player*
on every login and every message sent. History was never pruned, so the payload only grew across the
server's lifetime; that's what eventually crashed the server on player connect.

This rewrite splits that blob by purpose:

| Data | Storage | Sync behavior |
|---|---|---|
| Phone registry, contacts, mayor state | `PhoneRegistrySavedData` | Small by construction, still synced in full on login |
| Conversation messages (incl. voice audio, images) | `ConversationSavedData`, one bucket per conversation | **Never** broadcast; fetched on demand when a conversation is opened, capped and trimmed on write |

A new message now notifies only the participants who are online, instead of the whole server, so payload
size stays bounded no matter how long the world has run or how much history has accumulated. The same
principle carries through the voice features added later, and through Fabric's own photo pipeline: call
audio, voice message audio, and image bytes are only ever sent to the players who actually need them, on
demand, never broadcast.

---

## 📁 Project structure

Stonecutter splits the codebase into a shared source tree and one subproject per target version/loader:

```
CrazyPhone/
├── src/main/java/fr/lordfinn/crazyphone/   Shared source tree - every target compiles from here
│   ├── client/gui/       Screens (one per phone page) + shared widgets
│   ├── client/picture/   Native screenshot capture + texture cache, shared by both loaders
│   ├── command/          /crazyphone command tree
│   ├── data/             SavedData + player attachments (the crash fix lives here)
│   ├── enchantment/       Soulbound enchantment (≥1.20.5 only)
│   ├── fabric/            Fabric entrypoints + per-loader registration glue
│   ├── init/              Item/menu/screen/tab/sound/permission registration
│   ├── item/              The Crazy Phone item and photo item, vanilla item models & inventory capability
│   ├── mixin/             A single vanilla cursor-recentering fix (NeoForge only)
│   ├── network/           Client-server packets
│   ├── procedures/        Gameplay logic (ported from the original mod, adapted to the new data layer)
│   ├── utils/              Shared helpers (contacts, screen navigation, NBT/registry compat)
│   ├── voicechat/         Optional Simple Voice Chat integration (NeoForge only)
│   └── world/inventory/   Container menus backing each screen
├── versions/                One Stonecutter subproject per build target (1.20.4, 1.21.1, 1.21.10,
│                            1.20.1-fabric, 1.21.1-fabric) - each has its own gradle.properties pinning
│                            that target's Minecraft/loader/dependency versions
├── build.gradle.kts          Default build script - NeoForge targets
├── build.fabric.gradle.kts    Build script used by the two "-fabric" subprojects (Fabric Loom)
└── stonecutter.gradle.kts     Wires up the //? if fabric / //? if neoforge / //? if >=X.Y preprocessor
```

Per-loader and per-version differences in the shared tree are handled with Stonecutter comment
directives (e.g. `//? if fabric { ... }`, `//? if >=1.20.5 { ... }`) rather than separate source sets or
an abstraction layer - most files are identical on every target; only the files that genuinely differ
(registries, networking, attachments, a handful of NeoForge/Fabric API divergences) carry any gating.

---

## 🙏 Credits

- **[Simple Voice Chat](https://modrepo.de/minecraft/voicechat)** by [henkelmax](https://github.com/henkelmax): the voice engine calls and voice messages are built on top of (NeoForge only).
- Original `crazythings` project: source of the feature set and assets this mod ports and rebuilds. Made by me ;)

## 📄 License

`All Rights Reserved`. See the mod description in [`gradle.properties`](gradle.properties) for authorship details.
