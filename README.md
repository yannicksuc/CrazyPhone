# 📱 CrazyPhone

A held-item smartphone for NeoForge 1.21.1: contacts, group texting, photos/albums, an optional mayor
election, and an optional Simple Voice Chat integration (calls + voice messages).

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.248-D7791E)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](#-license)

> [!NOTE]
> CrazyPhone is a standalone, hand-written rewrite of the smartphone feature originally found in the
> **crazythings** mod. It keeps the same look and core features, but the codebase was re-architected -
> most importantly to fix a server-crashing data growth bug, see [Why this exists](#-why-this-exists).

---

## 📖 Table of Contents

- [Features](#-features)
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

**Phone basics** - registration + PIN sign-in, home screen launcher, lock screen, back/home navigation.

**Messaging** - contacts, favorites, and groups in one scrollable, recency-sorted screen; group
conversations with their own settings (rename, custom icon, invite/exclude, admin); real-time text;
sending photos from your album; read-notification badges; hover tooltips for timestamps/senders.

**Camera & albums** (requires the [Camera mod](https://github.com/henkelmax/camera)) - take photos,
organize them into albums, zoomable viewer, give/save/multi-select-delete.

**Voice calls & voice messages** *(optional, requires [Simple Voice Chat](https://modrepo.de/minecraft/voicechat))*
- 1:1 and group voice calls: ring notification, dedicated Incoming Call / Calling / In Call screens,
  auto-hangup on dropping the phone or moving it to another inventory (not on cursor-carry), auto-kick
  when left alone in a call, a "call in progress" / call summary entry posted to the conversation itself.
- Voice messages: record and send a clip, played back with a live waveform, play/pause, and a speed
  toggle (0.5x/1x/2x); audio is only ever fetched from the server when a recipient clicks play.
- Fully optional both ways: the mod loads and works normally with Simple Voice Chat absent, and every
  piece of it can be turned off independently - see [Configuration](#-configuration).

**Mayor election** *(optional)* - candidate list with campaign posters, in-app voting with a cooldown,
commands to manage candidates and toggle the feature.

**Per-feature toggles** - calls, voice messages, sending images, camera photo insertion, and the mayor
election can each be turned on/off globally (config or command) and/or restricted to specific
players/groups via a permission node, if a permission plugin is installed - see [Commands](#-commands).

**Localization** - every button, tooltip, and label goes through the lang files (English + French
shipped); no hardcoded UI strings.

**Built to scale** - conversation history and voice/image payloads are capped and never broadcast
wholesale; see [Why this exists](#-why-this-exists).

---

## 📋 Requirements

| Dependency | Version | Required for |
|---|---|---|
| Minecraft | `1.21.1` | Runtime |
| [NeoForge](https://neoforged.net/) | `21.1.248+` | Runtime |
| [Camera by Max Henkel](https://github.com/henkelmax/camera) | `1.21.1-1.0.21` | Runtime **and** build (hard dependency - see below) |
| [Simple Voice Chat](https://modrepo.de/minecraft/voicechat) | any 1.21.1 build | Runtime only, **optional** - calls/voice messages are simply unavailable without it |

> [!IMPORTANT]
> The Camera mod jar is **not bundled in this repository** (it isn't ours to redistribute). Grab it
> yourself for both playing and building - see [Building from source](#-building-from-source).

---

## 🎮 Installation (players / server admins)

1. Install [NeoForge `21.1.248`](https://neoforged.net/) or newer for Minecraft `1.21.1`.
2. Install **[Camera](https://github.com/henkelmax/camera)** into `mods/` - CrazyPhone will not load without it.
3. (Optional) Install **[Simple Voice Chat](https://modrepo.de/minecraft/voicechat)** into `mods/` for calls and voice messages.
4. Drop the built `crazyphone-*.jar` into `mods/`.
5. Launch the game, craft/obtain a Crazy Phone, and register a number.

---

## 🔨 Building from source

```bash
git clone https://github.com/<your-username>/CrazyPhone.git
cd CrazyPhone
```

Grab the Camera mod jar (`camera-neoforge-1.21.1-1.0.21.jar`) from the
[releases page of henkelmax/camera](https://github.com/henkelmax/camera/releases) and place it at:

```
libs/camera-neoforge-1.21.1-1.0.21.jar
```

> [!NOTE]
> `libs/` is gitignored on purpose: the jar belongs to a separate project with its own license, so it's
> kept out of this repository. `build.gradle` expects it at exactly that path.

Then build and/or run:

```bash
# Windows
gradlew.bat build
gradlew.bat runClient

# Linux / macOS
./gradlew build
./gradlew runClient
```

Run the test suite (boots a real, headless game instance so tests can use registries/data components):

```bash
gradlew.bat test
```

---

## ⚙️ Configuration

Generated at `config/crazyphone-common.toml` on first run. Every `*FeatureEnabled` value below can also be
changed at runtime with `/crazyphone feature`, without editing the file or restarting - see
[Commands](#-commands).

| Option | Default | Range | Description |
|---|---|---|---|
| `maxStoredMessagesPerConversation` | `300` | 10-10000 | Messages kept on disk per conversation; oldest dropped first once exceeded. |
| `maxMessagesSentPerRequest` | `100` | 10-1000 | Messages sent to a client in one page load of a conversation. |
| `maxImagesStoredPerConversation` | `50` | 5-2000 | Image messages kept on disk per conversation (capped separately - heaviest text-adjacent payload). |
| `maxAlbumSlotsPerPhone` | `27` | 1-97 | Album/photo storage slots in a phone's internal inventory. |
| `mayorElectionFeatureEnabled` | `true` | - | Global switch for the mayor election feature. |
| `callsFeatureEnabled` | `true` | - | Global switch for voice calls. No effect without Simple Voice Chat installed. |
| `voiceMessagesFeatureEnabled` | `true` | - | Global switch for recording/sending voice messages. No effect without Simple Voice Chat installed. |
| `imagesFeatureEnabled` | `true` | - | Global switch for sending images from the album into a conversation. |
| `cameraFeatureEnabled` | `true` | - | Global switch for inserting a Camera-mod photo into the phone. |
| `voicechatIntegrationEnabled` | `true` | - | Master switch for the whole Simple Voice Chat integration (both calls and voice messages). |
| `callRingTimeoutSeconds` | `30` | 5-120 | How long a call rings before an unanswered callee counts as a missed call. |
| `aloneInCallKickSeconds` | `5` | 1-60 | How long a call stays open with one participant left before they're auto-removed. |
| `maxVoiceMessagesStoredPerConversation` | `30` | 5-500 | Voice messages (with audio) kept on disk per conversation. |
| `maxVoiceMessageRecordingSeconds` | `60` | 5-600 | Maximum length of a single voice message recording. |

**Permissions:** each of the 5 toggleable features also has a permission node
(`crazyphone.feature.<calls|voice_messages|images|camera|mayor_voting>`), for restricting a feature to
specific players/groups instead of the whole server. Allowed for everyone by default; only takes effect
if a permission plugin (e.g. LuckPerms) is installed. A feature is usable only when **both** its global
switch is on **and** the player has the permission.

---

## 🖥️ Commands

Everything lives under `/crazyphone`. Arguments with a known set of values (registered phone numbers,
mayor candidates, feature names) tab-complete.

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
| `/crazyphone mayor candidate program <number>` | 4 | Attach the image currently held in hand as that candidate's campaign poster. |

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
<summary><b>Photos &amp; albums</b></summary>
<br>

Photos are taken with (and rendered through) [Camera](https://github.com/henkelmax/camera); the phone
just wraps it in its own UI:

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
| Conversation messages (incl. voice audio) | `ConversationSavedData`, one bucket per conversation | **Never** broadcast; fetched on demand when a conversation is opened, capped and trimmed on write |

A new message now notifies only the participants who are online, instead of the whole server, so payload
size stays bounded no matter how long the world has run or how much history has accumulated. The same
principle carries through the voice features added later: call audio is never sent to anyone but the two
people on the call, and a voice message's audio is fetched only when its recipient clicks play.

---

## 📁 Project structure

```
src/main/java/fr/lordfinn/crazyphone/
├── client/gui/       Screens (one per phone page) + shared widgets
├── command/          /crazyphone command tree
├── data/              SavedData + player attachments (the crash fix lives here)
├── init/               Item/menu/screen/tab/sound/permission registration
├── item/               The Crazy Phone item, its vanilla item model & inventory capability
├── mixin/             Integration points into the Camera mod
├── network/           Client-server packets
├── procedures/        Gameplay logic (ported from the original mod, adapted to the new data layer)
├── utils/              Shared helpers (contacts, screen navigation, camera-mod bridging)
├── voicechat/         Optional Simple Voice Chat integration (calls, voice message recording/playback)
└── world/inventory/   Container menus backing each screen
```

---

## 🙏 Credits

- **[Camera](https://github.com/henkelmax/camera)** by [Max Henkel](https://github.com/henkelmax): the photography engine CrazyPhone builds its camera/album features on top of.
- **[Simple Voice Chat](https://modrepo.de/minecraft/voicechat)** by [henkelmax](https://github.com/henkelmax): the voice engine calls and voice messages are built on top of.
- Original `crazythings` project: source of the feature set and assets this mod ports and rebuilds.

## 📄 License

`All Rights Reserved`. See the mod description in [`gradle.properties`](gradle.properties) for authorship details.
