# 📱 CrazyPhone

**A fully-featured in-game smartphone for NeoForge 1.21.1:** contacts, texting, photos & albums, and a mayor election system, all running from a single held item.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.248-D7791E)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](#-license)

> [!NOTE]
> CrazyPhone is a standalone, hand-written rewrite of the smartphone feature originally found in the **crazythings** mod. It keeps the same in-game features and look, but the entire codebase was re-architected, most importantly to fix a server-crashing data growth bug (see [Why this exists](#-why-this-exists)).

---

## 📖 Table of Contents

- [Features](#-features)
- [Screenshots](#-screenshots)
- [Requirements](#-requirements)
- [Installation (players / server admins)](#-installation-players--server-admins)
- [Building from source](#-building-from-source)
- [Configuration](#-configuration)
- [Commands](#-commands)
- [Localization](#-localization)
- [Why this exists](#-why-this-exists)
- [Project structure](#-project-structure)
- [Credits](#-credits)
- [License](#-license)

---

## ✨ Features

<table>
<tr><td width="33%" valign="top">

### 📲 Phone basics
- Registration + PIN-locked sign-in
- Home screen launcher with Photo / Albums / Contacts (and Mayor, if enabled)
- Lock screen, back/home navigation
- Per-page title header throughout the UI

</td><td width="33%" valign="top">

### 💬 Contacts & messaging
- Contact list with add/open actions
- Real-time text conversations
- Send photos straight from your albums
- Read-notification badges
- Hover tooltips for timestamps & senders

</td><td width="33%" valign="top">

### 📷 Camera & albums
- Take photos with the [Camera mod](https://github.com/henkelmax/camera)
- Organize photos into albums
- Zoomable image viewer
- Give / save-to-album / multi-select delete

</td></tr>
<tr><td width="33%" valign="top">

### 🗳️ Mayor election *(optional)*
- Candidate list with campaign posters
- In-app voting, cooldown-protected
- Admin commands to manage candidates & toggle the whole feature

</td><td width="33%" valign="top">

### 🌍 Localization
- Full English & French translations
- Every button, tooltip, and label goes through the lang file, no hardcoded strings

</td><td width="33%" valign="top">

### 🛠️ Built to scale
- Bounded, capped data storage (see [Why this exists](#-why-this-exists))
- Configurable limits via a mod config file
- Covered by a JUnit test suite

</td></tr>
</table>

---

## 🖼️ Screenshots

<p align="center">
  <img src="docs/screenshots/holding-the-phone.png" width="800" alt="A player holding the Crazy Phone">
</p>

<details open>
<summary><b>Home, sign-in &amp; registration</b></summary>
<br>

| Home screen | Registration | Login |
|:---:|:---:|:---:|
| ![Home screen](docs/screenshots/menu-home.png) | ![Registration](docs/screenshots/menu-signin.png) | ![Login](docs/screenshots/menu-lock-password.png) |

</details>

<details open>
<summary><b>Contacts</b></summary>
<br>

| Contacts | Viewing / adding a contact |
|:---:|:---:|
| ![Contacts](docs/screenshots/menu-contacts.png) | ![Contact](docs/screenshots/menu-add-contact.png) |

</details>

<details open>
<summary><b>Messaging</b></summary>
<br>

| Conversation | Sending an image |
|:---:|:---:|
| ![Conversation](docs/screenshots/menu-messages-1.png) | ![Send image tooltip](docs/screenshots/menu-messages-2.png) |
| An image sent in chat | Timestamp & zoom tooltip |
| ![Image sent](docs/screenshots/menu-messages-3a.png) | ![Timestamp tooltip](docs/screenshots/menu-messages-3b.png) |

</details>

<details open>
<summary><b>Photos &amp; albums</b></summary>
<br>

Photos are taken with (and rendered through) [Camera](https://github.com/henkelmax/camera); the phone just wraps it in its own UI:

| A photo taken with the phone's camera | Albums | Album contents | Picking images to send |
|:---:|:---:|:---:|:---:|
| ![Photo taken with the phone](docs/screenshots/photo-temple.png) | ![Albums](docs/screenshots/menu-albums.png) | ![Album contents](docs/screenshots/menu-album.png) | ![Picking images to send](docs/screenshots/menu-add-image-to-conversation.png) |

</details>

---

## 📋 Requirements

| Dependency | Version | Required at |
|---|---|---|
| Minecraft | `1.21.1` | Runtime |
| [NeoForge](https://neoforged.net/) | `21.1.248+` | Runtime |
| [GeckoLib](https://github.com/bernie-g/geckolib) | `4.7.5.1` | Runtime, resolved automatically from Maven |
| [Camera by Max Henkel](https://github.com/henkelmax/camera) | `1.21.1-1.0.21` | Runtime **and** build, see below |

> [!IMPORTANT]
> The Camera mod jar is **not bundled in this repository** (it isn't ours to redistribute). You need to grab it yourself for both playing and building, see [Building from source](#-building-from-source).

---

## 🎮 Installation (players / server admins)

1. Install [NeoForge `21.1.248`](https://neoforged.net/) or newer for Minecraft `1.21.1`.
2. Download and install **[Camera](https://github.com/henkelmax/camera)** and **GeckoLib** into your `mods/` folder. CrazyPhone will not load without them.
3. Drop the built `crazyphone-*.jar` into `mods/` alongside them.
4. Launch the game, craft/obtain a Crazy Phone, and register a number.

---

## 🔨 Building from source

```bash
git clone https://github.com/<your-username>/CrazyPhone.git
cd CrazyPhone
```

Grab the Camera mod jar (`camera-neoforge-1.21.1-1.0.21.jar`) from the [releases page of henkelmax/camera](https://github.com/henkelmax/camera/releases) and place it at:

```
libs/camera-neoforge-1.21.1-1.0.21.jar
```

> [!NOTE]
> `libs/` is gitignored on purpose: the jar belongs to a separate project with its own license, so it's kept out of this repository. `build.gradle` expects it at exactly that path.

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

Generated at `config/crazyphone-common.toml` on first run.

| Option | Default | Range | Description |
|---|---|---|---|
| `maxStoredMessagesPerConversation` | `300` | 10 to 10000 | Messages kept on disk per conversation; older ones are dropped when this is exceeded. |
| `maxMessagesSentPerRequest` | `100` | 10 to 1000 | Messages sent to a client in one page load of a conversation. |
| `maxImagesStoredPerConversation` | `50` | 5 to 2000 | Image messages kept on disk per conversation (capped separately, images are the heaviest payload). |
| `maxAlbumSlotsPerPhone` | `27` | 1 to 97 | Album/photo storage slots in a phone's internal inventory. |
| `mayorElectionFeatureEnabled` | `true` | n/a | Master switch for the mayor election feature. |

---

## 🖥️ Commands

All commands require the listed [permission level](https://minecraft.wiki/w/Permission_level) unless noted otherwise.

| Command | Permission | Description |
|---|:---:|---|
| `/phoneGive <number>` | 4 | Give yourself the phone registered to `<number>`. |
| `/phoneDelete <number>` | 4 | Delete the phone registered to `<number>` and scrub it from every contact list. |
| `/phoneList <search>` | 4 | Search registered phones by number/name. |
| `/phoneAddMayorCandidate <number>` | 4 | Register a phone number as a mayor candidate. |
| `/phoneRemoveMayorCandidate <number>` | 4 | Remove a mayor candidate. |
| `/phoneAddCandidateProgramFromHand <number>` | 4 | Attach the image currently held in hand as that candidate's campaign poster. |
| `/phoneToggleMayorElection <true\|false>` | 4 | Enable/disable the election feature entirely. |
| `/phoneToggleVotingElection <true\|false>` | 4 | Open/close voting while keeping the feature visible. |
| `/phoneremovevotebynumber <number>` | 4 | Clear a single player's vote (e.g. to let them re-vote). |
| `/phoneshowvotes` | 2 | Print current vote tallies. |
| `/phoneVoteForMayor <number>` | *(any player)* | Cast a vote, this is what the in-phone **Vote** button runs; not meant to be typed by hand. |

---

## 🌍 Localization

Every piece of UI text (button labels, tooltips, placeholders, headers) is routed through the lang files, no hardcoded strings:

```
src/main/resources/assets/crazyphone/lang/en_us.json
src/main/resources/assets/crazyphone/lang/fr_fr.json
```

Want to add another language? Copy `en_us.json` to `<your_locale>.json` and translate the values; the keys must stay identical between files.

---

## 🐛 Why this exists

The original `crazythings` implementation stored **every phone, every contact, and every message ever sent, in every conversation, forever** in one giant `SavedData` blob, and broadcast the *entire* thing to *every online player* on every login and every message sent. Message history was never pruned, so the payload only grew across the server's lifetime, and every join/send re-serialized and re-broadcast all of it. On an aged world, that's what crashed the server on player connect.

This rewrite splits that blob by purpose:

| Data | Storage | Sync behavior |
|---|---|---|
| Phone registry, contacts, mayor state | `PhoneRegistrySavedData` | Small by construction, still synced in full on login |
| Conversation messages | `ConversationSavedData`, one bucket per conversation | **Never** broadcast; fetched on demand when a conversation is opened, capped and trimmed on write |

A new message now notifies only the participants who are online, instead of the whole server, so payload size stays bounded no matter how long the world has been running or how much history has accumulated.

---

## 📁 Project structure

```
src/main/java/fr/lordfinn/crazyphone/
├── client/gui/       Screens (one per phone page) + shared widgets
├── command/          /phone* admin & gameplay commands
├── data/              SavedData + player attachments (the crash fix lives here)
├── init/               Item/menu/screen/tab registration
├── item/               The Crazy Phone item, its GeckoLib model & inventory capability
├── mixin/             Integration points into the Camera mod
├── network/           Client-server packets
├── procedures/        Ported gameplay logic (1:1 from the original mod, adapted to the new data layer)
├── utils/              Shared helpers (contacts, screen navigation, camera-mod bridging)
└── world/inventory/   Container menus backing each screen
```

---

## 🙏 Credits

- **[Camera](https://github.com/henkelmax/camera)** by [Max Henkel](https://github.com/henkelmax): the photography engine CrazyPhone builds its camera/album features on top of.
- **[GeckoLib](https://github.com/bernie-g/geckolib)**: item model & animation library.
- Original `crazythings` project: source of the feature set and assets this mod ports and rebuilds.

## 📄 License

`All Rights Reserved`. See the mod description in [`gradle.properties`](gradle.properties) for authorship details.
