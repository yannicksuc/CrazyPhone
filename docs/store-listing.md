![A player holding the Crazy Phone](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/holding-the-phone.png)

# 📱 CrazyPhone

**A real smartphone your character carries around.**
Contacts, group texting, photos and a native camera (including selfies), an optional mayor election, and
optional voice calls.

![Minecraft 1.21.1 · 26.1](https://img.shields.io/badge/Minecraft-1.21.1%20%C2%B7%2026.1-62B47A?logo=minecraft&logoColor=white) ![NeoForge and Fabric](https://img.shields.io/badge/Loader-NeoForge%20%C2%B7%20Fabric-D7791E) ![Java 21 / 25](https://img.shields.io/badge/Java-21%20%2F%2025-ED8B00?logo=openjdk&logoColor=white)

---

Register a number, add contacts, text people, take and send photos, and if the server also runs **Simple
Voice Chat**, place a real voice call or leave a recorded message. Everything lives on the phone in your
hand, no separate app, no separate menu system bolted onto vanilla UI, and **no external camera mod
dependency** - photo capture, storage, and rendering are all built in.

CrazyPhone is a standalone rewrite of the smartphone feature originally found in my **CrazyThings** mod.
Same look, same core idea, but the storage layer was rebuilt from scratch to fix a bug in the original that
could crash a server: it kept *every* message from *every* conversation forever in one blob and broadcast
the whole thing to *every* player on *every* login. This version paginates conversations, caps what's
stored, and only notifies the players who are actually in a conversation when a new message lands. Lots of
features have been added and improved since, most recently a full native camera pipeline and selfie mode.

---

## ✨ Features

### 📇 Contacts, favorites and groups

One scrollable, recency-sorted list. Group chats get their own name, icon, and invite/exclude/admin
controls.

![Contacts list](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-contacts.png) ![Adding a contact](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-add-contact.png)

### 💬 Real-time messaging

Read badges, timestamp tooltips, photos sent straight from your album, and pixel-art emoji throughout chat
(paste a real emoji, type a `:shortcode:`, or use a classic ASCII emoticon like `:)` - it converts live as
you type). Start typing a reply, get pulled away mid-sentence, come back later and it's still there.

![A conversation](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-messages-1.png) ![An image sent in chat](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-messages-3a.png)

### 📷 Native camera, photos and selfie mode

No external mod required - capture, storage, and rendering are all built directly into CrazyPhone.

Three ways to take a photo: the camera icon inside a conversation, the home screen's Photo icon for a
standalone shot, or just punching while holding the phone. **My Photos** is a flat, scrollable grid of
every photo the phone owns, with delete / save-to-inventory / send-to-conversation actions. Saving a photo
gives you a physical item you can hold up (sneak-presenting, both hands gripping it, real in-world
lighting) or right-click to reopen full-size.

**Selfie mode** (press F5 while holding the phone) extends it on a selfie stick you aim with the mouse -
your arm and head visibly pose and track the shot, both for yourself and for anyone watching.

### 📞 Voice calls

*(optional, requires [Simple Voice Chat](https://modrepo.de/minecraft/voicechat))*

1:1 and group calls, ring notification, dedicated Incoming Call / Calling / In Call screens, auto-hangup if
you drop the phone, auto-kick if you're left alone in the call.

### 🎙️ Voice messages

*(optional, requires [Simple Voice Chat](https://modrepo.de/minecraft/voicechat))*

Record and send a clip, played back with a live waveform and a 0.5x / 1x / 2x speed toggle. Audio is only
ever pulled from the server when someone actually hits play.

### 🗳️ Mayor election

*(optional)*

Candidates campaign with posters, players vote in-app with a cooldown, admins get a full command set to run
the whole thing.

### 🔐 Per-feature toggles and permissions

Calls, voice messages, images, and the mayor election can each be switched off globally or restricted to
specific players and groups with a permission node - live, no restart needed, on NeoForge.

### 🌍 Fully localized

Every button, tooltip, and label goes through the lang files. English and French are shipped out of the
box; adding another language is one JSON file to translate.

---

## 🏠 First launch

![Home screen](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-home.png) ![Registration](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-signin.png) ![Login](https://raw.githubusercontent.com/yannicksuc/CrazyPhone/master/docs/screenshots/menu-lock-password.png)

---

## 📦 Optional dependency

| Dependency | Required for |
|---|---|
| [Simple Voice Chat](https://modrepo.de/minecraft/voicechat) by henkelmax *(NeoForge only)* | Optional. Without it, calls and voice messages are simply unavailable; everything else - including the camera and selfie mode - works normally. |

CrazyPhone has **no hard dependencies**. It runs standalone; nothing else needs to be installed.

---

## 🧩 Available for

NeoForge and Fabric, on Minecraft **1.21.1** and **26.1** - see the
[platform table](https://github.com/yannicksuc/CrazyPhone#-platforms--versions) on GitHub for the full,
up-to-date per-version/per-feature breakdown (some older versions are still buildable from source but no
longer receive updates).

---

## 🔗 Links

- [Source & full documentation](https://github.com/yannicksuc/CrazyPhone)
- [Wiki (setup, config, commands, permissions)](https://github.com/yannicksuc/CrazyPhone/wiki)
- [Report a bug](https://github.com/yannicksuc/CrazyPhone/issues)

---

*Voice calls and voice messages are built on top of [Simple Voice Chat](https://modrepo.de/minecraft/voicechat)
by Max Henkel (optional, NeoForge only).*
