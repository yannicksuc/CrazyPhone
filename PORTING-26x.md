# Porting to Minecraft 26.1 / 26.2 - status and next steps

Started as an overnight autonomous task (`/loop`). Real, verified progress was made, but the
task turned out to be much bigger than expected once actually attempted - this file exists so
that progress isn't lost and the next session (or a live human) can pick it up without
re-discovering everything from scratch.

## What's done

- **New Stonecutter targets registered**: `versions/26.1` (NeoForge, `neo_version=26.1.2.100`),
  `versions/26.2` (NeoForge, `neo_version=26.2.0.71`), plus `26.1-fabric`/`26.2-fabric`. 26.3 was
  deliberately skipped - as of this writing it only has snapshot builds (`26.3-snapshot-1` through
  `10`), no stable release, on both the NeoForge maven and Fabric's version metadata.
- **Java 25 toolchain selection** added to `build.gradle.kts`/`build.fabric.gradle.kts` for the
  26.x line (Minecraft 26.x requires Java 25, confirmed live: NeoForge's moddev plugin refused to
  configure a 26.1.2 project under a Java 21 daemon with an explicit "Minecraft 26.1.2 requires
  Java 25" error).
- **A wide set of vanilla/NeoForge API breaks fixed**, all confirmed by decompiling the *real*
  26.1.2 jar (`versions/26.1/build/moddev/artifacts/minecraft-patched-26.1.2.100-sources.jar`) and
  the real NeoForge 26.1.2.100 sources jar, not guessed:
  - `ResourceLocation` renamed to `Identifier` (same package, same shape) - 160+ call sites across
    50 files. Used Stonecutter's **swap** feature (see `stonecutter.gradle.kts`, and search the
    codebase for `res_loc`) instead of branching every occurrence with `//? if`. Syntax:
    `/*$ res_loc {*/ResourceLocation/*$}*/` for a normal spot, or `/^$ res_loc {^/ResourceLocation/^$}^/`
    (note `/^ ^/` instead of `/* */`) for a spot that's already nested inside another inactive
    `//? if` branch's own `/* */` comment - both forms are live-verified working.
  - `Player#displayClientMessage(Component, boolean)` split into `sendSystemMessage(Component)` /
    `sendOverlayMessage(Component)`; `Player#playNotifySound(SoundEvent, SoundSource, float, float)`
    removed outright (only `Entity#playSound(SoundEvent, float, float)`, no `SoundSource`, survives).
    Centralized into `CrazyPhoneHelper.sendClientMessage(...)` / `CrazyPhoneHelper.playNotifySound(...)`
    instead of branching ~35 call sites individually.
  - `Camera#getYRot()`/`getXRot()` -> `yRot()`/`xRot()` (swap key `cam_yaw`/`cam_pitch`).
  - `RenderType`'s static factories (`entityCutout` etc.) moved to a new sibling class
    `RenderTypes` in a new subpackage `net.minecraft.client.renderer.rendertype` (swap keys
    `render_type_import`/`render_types`).
  - `CommandSourceStack#hasPermission(int)` replaced by `Commands.hasPermission(PermissionCheck)`
    with named `Commands.LEVEL_ALL/MODERATORS/GAMEMASTERS/ADMINS/OWNERS` constants (0-4 in order).
    Added a small `permLevel(int)` helper in `ModCommands.java` instead of branching every command.
  - `SavedDataType`'s `id` param changed `String` -> `Identifier` (swap key `saved_data_id`, wraps
    `DATA_NAME` through `Crazyphone.resource(...)` on >=26 only).
  - `GameRules` moved package (`net.minecraft.world.level.GameRules` ->
    `net.minecraft.world.level.gamerules.GameRules`) and `RULE_KEEPINVENTORY`/`getBoolean(...)` ->
    `KEEP_INVENTORY`/`get(...)` (swap keys `game_rules_pkg`/`keep_inventory_call`).
  - `Inventory#selected` went from a public field to private, behind `setSelectedSlot(int)` (swap
    key `set_selected_slot_0`, only used in `CrazyPhoneGameTests.java`).
  - NeoForge's `LootModifier` gained a `priority` field threaded through its codec and
    constructor - `SoulboundLootModifier.java` updated with a version-gated constructor.
- **All 5 pre-existing targets re-verified clean** after every change above (1.20.4, 1.21.1,
  1.21.1-fabric, 1.21.10, 1.20.1-fabric) - no regressions.
- **Error count**: 327 -> 181 compile errors on `:26.1:compileJava`, and essentially all 181
  remaining errors are now concentrated in ONE area (see below), not scattered noise.

## What's NOT done - the actual blocker

**The entire `GuiGraphics`-based screen rendering API was reworked** into a retained-mode
`RenderState` system. This is the reason 26.1/26.2 don't compile yet - every one of this mod's
~15 screen/widget classes touches it. It is **not** a simple mechanical rename across the board;
some of it is, some of it is a real structural merge. Concrete findings, all confirmed against the
real decompiled/NeoForge sources (paths under the session's scratchpad, now gone, but re-derivable
the same way: extract `minecraft-patched-26.1.2.100-sources.jar` and the `neoforge-26.1.2.100-sources.jar`):

| Old (through 1.21.10) | New (26.1/26.2) | Nature |
|---|---|---|
| `GuiGraphics` (class) | `GuiGraphicsExtractor` (same package) | Pure rename. Method shapes for the actual draw primitives (`fill`, `blit`, matrix-stack ops) are otherwise **unchanged** from the 1.21.10 rework already handled in `GuiCompat.java` - verified the exact overloads `GuiCompat.java`'s `>=1.21.10` branches call still exist byte-for-byte on `GuiGraphicsExtractor`. **`GuiCompat.java` has already been updated for this** (swap key `gui_graphics_type`) - it's the one file that needed it and is done. |
| `Screen#render(GuiGraphics, int, int, float)` | `Screen#extractRenderState(GuiGraphicsExtractor, int, int, float)` | Rename, same param order. |
| `Renderable#render(...)` | `Renderable#extractRenderState(...)` | Same rename, it's the same interface method. |
| `AbstractButton#renderWidget(GuiGraphics, int, int, float)` | `AbstractButton#extractContents(GuiGraphicsExtractor, int, int, float)` | Rename, same param order. Every anonymous `new ImageButton(...) { @Override public void renderWidget(...) {...} }` in this codebase needs this. |
| `AbstractContainerScreen#renderLabels(GuiGraphics, int, int)` | `AbstractContainerScreen#extractLabels(GuiGraphicsExtractor, int, int)` | Rename, same order. |
| `AbstractContainerScreen#renderTooltip(GuiGraphics, int, int)` | `AbstractContainerScreen#extractTooltip(...)` | Rename, same order (internal - we don't override this one, just call it). |
| `Screen#init(Minecraft, int, int)` (public, engine-invoked) | `Screen#init(int, int)` | `Minecraft` param dropped. Only matters where **our own code** explicitly re-invokes it (search for `this.init(this.minecraft`). |
| `Screen#resize(Minecraft, int, int)` | `Screen#resize(int, int)` | `Minecraft` param dropped. Matters anywhere we call `super.resize(minecraft, width, height)`. |
| `AbstractContainerScreen#renderBackground(GuiGraphics,...)` (dim overlay) **+** `#renderBg(GuiGraphics, float, int, int)` (screen's own texture), two separate hooks | **Merged into one**: `Screen#extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)` - note `partialTick` moved from 2nd position to *last*. A screen now overrides this ONE method to do both the dim fill and its own texture (see vanilla's `InventoryScreen#extractBackground`, which calls `super.extractBackground(...)` then blits its own texture, in one method). | **Real structural merge, not a rename, and it's worse than it first looked.** `CrazyPhoneDefaultScreenScreen.java`'s current `renderBackground()` + `renderBg()` pair needs to become one `extractBackground()` override - AND **6 subclasses also override `renderBg()` themselves** (confirmed via `grep -rl "renderBg\|renderBackground" client/gui/`): `CrazyPhoneContactsScreenScreen`, `CrazyPhoneGroupSettingsScreenScreen`, `CrazyPhoneMayorCandidateScreenScreen`, `CrazyPhoneMyPhotosScreenScreen`, `CrazyPhonePasswordScreenScreen`, `CrazyPhonePhotoViewerScreen` - each presumably calling `super.renderBg(...)` then drawing something extra on top. Since vanilla no longer exposes a hook with that exact name/shape for a container screen to layer onto, the base class needs **its own, version-stable protected method** (not tied to vanilla's naming) that both the old and new entry points call internally, and all 6 subclasses need to move their override onto that instead - a small, coordinated, multi-file rename of an internal contract, not just a vanilla-API swap. Not yet done - flagged rather than rushed, since getting the new hook's exact placement/timing wrong (e.g. calling it before vs. after the dim fill) would silently misrender every affected screen in a way that's easy to miss without actually looking at it. |
| `AbstractContainerScreen`'s `imageWidth`/`imageHeight` fields | Went from mutable to `protected final int`, set via a new 5-arg constructor `AbstractContainerScreen(menu, inventory, title, imageWidth, imageHeight)` | **Real structural change.** Most of our screens currently set `this.imageWidth = N;` inside `init()` after the 3-arg `super(...)` call - needs moving into the `super(...)` call itself. `CrazyPhoneMayorCandidateScreenScreen.java` is the hard case: it currently computes `imageWidth`/`imageHeight` **dynamically at runtime** based on an async-loaded texture's aspect ratio, which doesn't fit "fixed at construction time" at all - this one specifically needs a real design decision, not a mechanical fix (defer screen construction until the texture loads? use a fixed max size and letterbox? something else?). |

Files that reference `GuiGraphics` and haven't been touched yet (26 total, `GuiCompat.java` is the
only one already done):

```
client/CrazyPhoneCaptureMode.java
client/gui/components/CallBustPreview.java
client/gui/components/CrazyPhoneColors.java
client/gui/components/MessageDisplayManager.java
client/gui/components/MessageWidget.java
client/gui/components/PasswordEditBox.java
client/gui/components/ScrollingText.java
client/gui/components/SmallTextEditBox.java
client/gui/components/WrappedTextWidget.java
client/gui/CrazyPhoneCallingScreenScreen.java
client/gui/CrazyPhoneContactInfoScreenScreen.java
client/gui/CrazyPhoneContactsScreenScreen.java
client/gui/CrazyPhoneConversationScreen.java
client/gui/CrazyPhoneDefaultScreenScreen.java   <- base class, do this one first, everything else extends it
client/gui/CrazyPhoneGroupSettingsScreenScreen.java
client/gui/CrazyphoneHomeScreenScreen.java
client/gui/CrazyPhoneInCallScreenScreen.java
client/gui/CrazyPhoneIncomingCallScreenScreen.java
client/gui/CrazyPhoneMayorCandidateScreenScreen.java   <- has the dynamic imageWidth problem, see above
client/gui/CrazyPhoneMayorsCandidatesListScreen.java
client/gui/CrazyPhoneMyPhotosScreenScreen.java
client/gui/CrazyPhonePasswordScreenScreen.java
client/gui/CrazyPhonePhotoViewerScreen.java
client/gui/CrazyPhoneSignInScreenScreen.java
mixin/CrazyPhoneCaptureGuiMixin.java
```

Also unrelated to the GuiGraphics rework but still broken: `MojangProfileLookup.java:70` -
`cannot find symbol: variable Util` (`Util.backgroundExecutor()`) - `Util` itself likely moved
package or the method was renamed too; not yet investigated.

## Why this stopped here instead of pushing through

1. **The GUI rework is a real design task in places** (the `renderBg`/`renderBackground` merge,
   and especially `CrazyPhoneMayorCandidateScreenScreen`'s dynamic sizing), not purely mechanical -
   pushing through all 25 remaining files blind risked introducing subtle, hard-to-spot bugs
   (wrong draw order, misaligned text, a screen that silently doesn't draw its background) across
   nearly every screen in the mod.
2. **No way to runtime-verify anything on this machine right now**: Minecraft 26.x requires Java
   25, and only Java 21 (`C:\Users\yanni\.jdks\ms-21.0.12`) is installed. This session deliberately
   did **not** download/install a JDK unilaterally - that's a real download-and-install action, and
   outside what an unattended session should decide on its own. This project's whole development
   discipline so far has been "compile, launch, look at it, iterate" - continuing to push rendering
   changes with zero ability to actually look at the result felt like the wrong tradeoff for an
   unattended run, however much time was left.
3. Given both of the above, the highest-value thing to do with the rest of the night was to lock
   in everything that's genuinely done and verified (committed to `dev`, not merged to `main`), and
   leave a concrete, accurate map of what's left instead of a vague "GUI broke, TODO" note.

## Recommended next steps

1. Install a Java 25 JDK (e.g. via IntelliJ's JDK downloader, the same way
   `C:\Users\yanni\.jdks\ms-21.0.12` presumably got there) and point `JAVA_HOME` at it. This
   unblocks two independent things: `:26.1-fabric`/`:26.2-fabric` can then even *configure* (Fabric
   Loom's Minecraft-provisioning step checks the driving JVM's own version, not just a per-project
   toolchain setting - confirmed live, `--configure-on-demand` was the workaround used throughout
   this session to keep testing `:26.1`/`:26.2` NeoForge without touching the Fabric nodes), and
   actual live testing of 26.1 becomes possible again.
2. Start with `CrazyPhoneDefaultScreenScreen.java` (the shared base class) - get its
   `render`/`renderBackground`/`renderBg`/`renderLabels`/`init` overrides and its three anonymous
   `ImageButton`s' `renderWidget` overrides converted using the table above. Compile
   `:26.1:compileJava` after, expect it to surface how much (if anything) changes for subclasses.
3. Work outward to the other 24 files. Given how many of them likely share very similar shapes
   (most are probably a `render` override + a handful of `renderWidget` anonymous buttons, per the
   error patterns already seen), once 2-3 are done by hand the rest may be mechanical enough to
   delegate to parallel agents with a concrete worked example - the way the `ResourceLocation` swap
   rollout was done earlier in this same session (5 parallel agents, ~50 files, clean result).
4. Handle `CrazyPhoneMayorCandidateScreenScreen.java`'s dynamic-imageWidth case as its own small
   design decision once the mechanical part of the rest is done and there's a clearer picture of
   what "normal" looks like in this codebase post-port.
5. Fix `MojangProfileLookup.java`'s `Util.backgroundExecutor()` break (small, unrelated, not yet
   investigated).
6. Once `:26.1:compileJava` (and then `:26.2:compileJava`) succeed, do a full regression pass
   (`:1.20.4:compileJava :1.21.1:compileJava :1.21.1-fabric:compileJava :1.21.10:compileJava
   :1.20.1-fabric:compileJava`) before considering this close to done, and then actually launch
   `:26.1:runClient` and live-test the way every other version in this project has been tested.
7. This file should be deleted once the port is actually complete and merged.
