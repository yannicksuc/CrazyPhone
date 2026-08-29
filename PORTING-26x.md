# Porting to Minecraft 26.1 / 26.2 - status and next steps

Started as an overnight autonomous task (`/loop`). Real, verified progress was made, but the
task turned out to be much bigger than expected once actually attempted - this file exists so
that progress isn't lost and the next session (or a live human) can pick it up without
re-discovering everything from scratch.

## Update: `:26.1-fabric:compileJava` down to ONE known blocker (from 188, then 51, now 1)

Fabric API did its OWN sweeping API rework for 26.x, in parallel with (and mirroring) vanilla's own
GuiGraphics/GameTest rework - confirmed by diffing the actual resolved fabric-api dependency tree
and jar contents for 26.1.2 against what this codebase's pre-26 Fabric nodes use. All of the
following turned out to be pure renames (same method/constructor shapes, javap-verified against the
real 26.1.2-resolved jars) and are now fixed, mostly via new Stonecutter swaps so the rename doesn't
need a full version-gated code duplication:

- `net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType(.ExtendedFactory)` ->
  `net.fabricmc.fabric.api.menu.v1.ExtendedMenuType(.ExtendedFactory)` (module itself renamed
  `fabric-screen-handler-api-v1` -> `fabric-menu-api-v1`) - new swaps `fabric_ext_menu_type_import`/
  `fabric_ext_menu_type` in `stonecutter.gradle.kts`, applied in `ModMenus.java`.
- `net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory` ->
  `net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider` (same `getScreenOpeningData(ServerPlayer)`
  method) - new swap `fabric_ext_menu_provider`, applied at all 5 anonymous-class sites in
  `ScreenMenuUtils.java`.
- `ScreenEvents#afterRender(Screen)` -> `#afterExtract(Screen)` (same callback shape, just typed
  with `GuiGraphicsExtractor` now) - new swap `fabric_screen_events_after_render`, applied in
  `PhoneClickableCursorHandler.java`.
- `PayloadTypeRegistry#playS2C()/playC2S()` -> `#clientboundPlay()/#serverboundPlay()` (vanilla's own
  clientbound/serverbound terminology replacing S2C/C2S) - new swaps `fabric_payload_registry_s2c`/
  `_c2s`, applied in `FabricNetworking.java`.
- `Gui#render(GuiGraphics, DeltaTracker)` -> `Gui#extractRenderState(GuiGraphicsExtractor,
  DeltaTracker)` (matches the whole vanilla render->extractRenderState rework, just missed in the
  earlier GuiGraphics batch since it's a mixin file, not a screen) - fixed directly in
  `CrazyPhoneCaptureGuiMixin.java` with a 3-way version split (mixin `@Inject` "method" strings are
  raw JVM descriptors, not Java signatures, so both the method name and the type's binary name
  needed updating together - not a candidate for the swap mechanism since the descriptor STRING
  itself changes shape, not just a type name inside otherwise-identical code).
- `net.fabricmc.fabric.api.client.command.v2.ClientCommandManager` (the `literal(...)`/`argument(...)`
  convenience wrapper) is gone entirely (`ClientCommandRegistrationCallback`/`FabricClientCommandSource`
  are untouched) - fixed in `CrazyPhonePresentDebugCommand.java` (a dev-only `/presentdebug` command)
  by calling brigadier's own `LiteralArgumentBuilder.literal(...)`/`RequiredArgumentBuilder.argument(...)`
  directly, which is all `ClientCommandManager` ever did internally.
- `net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup` (module `fabric-item-group-api-v1`) isn't
  even a transitive fabric-api dependency anymore - it was only ever a thin `CreativeModeTab.Builder`
  wrapper missing `withSearchBar()` (a NeoForge-only builder addition), so `ModTabs.java`'s `>=26`
  Fabric branch now calls vanilla's own `CreativeModeTab.builder(Row, int)` directly. Note the
  overload difference from the neoforge branch's `CreativeModeTab.builder()` (no-arg): that no-arg
  overload only exists on NeoForge's own *patched* Minecraft jar (`versions/*/build/moddev/artifacts/
  minecraft-patched-*-sources.jar`) - true/plain vanilla, what Fabric Loom actually compiles against,
  only ever had the `(Row, int)` overload. **Worth remembering for future 26.x work: NeoForge's
  "patched" decompiled sources are not 1:1 with what Fabric sees** - always double-check a
  surprising vanilla API shape against Fabric's own resolved jar before assuming it's identical.
- Two unrelated, pre-existing latent bugs newly exposed simply because 26.1-fabric is the FIRST
  Fabric node in this project to ever cross the `>=1.21.10` boundary (every prior Fabric node -
  1.20.1-fabric, 1.21.1-fabric - stayed below it): `ModRecipes.java`'s and
  `PhoneRegistrySavedData.java`/`ConversationSavedData.java`/`PhotoSavedData.java`'s Fabric branches
  were gated `fabric && >=1.20.5` with **no upper bound**, silently relying on there never being a
  Fabric node past 1.21.10. Fixed: `ModRecipes` gained a proper `<1.21.10` cap (matching
  `CrazyPhoneDuplicatePhotoRecipe.java`'s own existing `<1.21.10` scope) plus a `fabric && >=1.21.10`
  no-op stub; the three SavedData classes' `>=1.21.10` `computeIfAbsent(TYPE)` call (already written,
  previously neoforge-only) turned out to be plain vanilla with no loader-specific types involved at
  all - widened from `neoforge && >=1.21.10` to a shared `>=1.21.10`, and the old
  `SavedData.Factory`-based fabric branch capped at `<1.21.10` to match.
- `CrazyPhoneItemProperties.java`'s NeoForge-only `RegisterConditionalItemModelPropertyEvent` branch
  had the exact same missing-loader-gate bug (`>=1.21.10` with no `neoforge &&`) - see "Known,
  documented gap" below, since this ONE genuinely has no Fabric equivalent yet.

**One blocker remains, deliberately not guessed at**: `CrazyPhonePhotoItem.java`'s
`net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry` (the old-style
PoseStack+MultiBufferSource custom item renderer hook) doesn't exist in the 26.1.2-resolved
fabric-rendering-v1 jar at all - replaced by an unfamiliar declarative set of classes
(`PictureInPictureRendererRegistry`, `FabricModel`, `TransformCopyingModel`, `FabricRenderState`,
`ExtractItemDecorationsCallback`) that clearly mirror vanilla's own new render-state-extraction
architecture, not a renamed version of the old immediate-mode callback. **This is the exact same
underlying problem as `:26.2:compileJava`'s already-flagged `MultiBufferSource` removal below - not
a second, separate issue.** Confirmed by checking `:26.2-fabric:compileJava` (24 errors, spanning
`CrazyPhonePhotoItemRenderer.java`, `CrazyPhoneCaptureMode.java`, `CrazyPhoneCaptureHandMixin.java`
and 9 screen/message files that reference `RenderType`/`MultiBufferSource` for text/item rendering)
- the SAME custom-item-rendering pipeline needs one unified redesign that works for both loaders on
both 26.1 (Fabric only, since NeoForge 26.1 still has the old APIs) and 26.2 (both loaders). Treat
`CrazyPhonePhotoItemRenderer.java`'s rewrite as the single highest-value remaining task - fixing it
once likely resolves 26.1-fabric's last error AND most of 26.2's 25 errors on both loaders at once.

**Known, documented gap (not a compile error, a silent functionality gap)**:
`CrazyPhoneItemProperties.java`'s phone icon states (lit/calling/called_in/in_call) do not update on
Fabric >=1.21.10 (26.1-fabric/26.2-fabric). The registration mechanism
(`ConditionalItemModelProperty`) is populated via a plain static `ExtraCodecs.LateBoundIdMapper` in
vanilla's own `ConditionalItemModelProperties.bootstrap()` - NeoForge patches an event-post call
directly into that vanilla method (confirmed against the real decompiled 26.1.2 source), which is
why `RegisterConditionalItemModelPropertyEvent` only exists on NeoForge. Searched every fabric-api
submodule jar resolved for 26.1.2 for any mention of `ConditionalItemModelProperty` - no matches
anywhere. A real fix needs a Fabric-side Mixin into that bootstrap method (or reflection onto its
private `ID_MAPPER` field) - genuine investigation/design work, intentionally not guessed at here.

## Update: `:26.1:compileJava` is fully clean - 26.2 needs its own separate investigation

**`:26.1:compileJava` now compiles with zero errors.** Both previously-open files are resolved:

- **`CrazyPhoneGameTests.java`** - restructured from `@GameTest`-annotated methods into the new
  registration model (see "GameTest finding" below for the full mechanism). The `<26` branch keeps
  the original annotation-based code entirely unchanged; the `>=26` branch registers the same 9
  test bodies (untouched logic, just referenced via method reference) through
  `RegisterEvent(Registries.TEST_FUNCTION, ...)` + `RegisterGameTestsEvent`, with one
  `TestEnvironmentDefinition` per old batch name (empty `AllOf(List.of())`, since the old "batch"
  concept was purely about test isolation, not actual environment effects) preserving the same
  test-isolation grouping as before. Wired into `Crazyphone.java`'s mod-bus listener registration.
- **`CrazyPhoneMayorCandidateScreenScreen.java`** - turned out NOT to need a real design decision
  after all, once actually read closely: it was never using `imageWidth`/`imageHeight` for the
  screen's own panel size (that's fixed at 122x195 by the base class constructor and never
  referenced again - `drawScreenBackground` blits a hardcoded-size texture regardless). It was
  reusing those inherited fields purely as ad-hoc scratch storage for the candidate poster photo's
  own lazily-computed render size, which happened to work pre-26 only because the fields were
  freely mutable after construction. Fixed by giving the poster its own `posterWidth`/`posterHeight`
  fields, leaving the inherited (now-final) `imageWidth`/`imageHeight` untouched - no behavior
  change, no design tradeoff needed. Also picked up the same `GuiGraphics`/`renderBg`/`renderLabels`
  rework already applied to every sibling screen (this file was skipped in that earlier batch
  specifically because of the imageWidth blocker).

Every file in the entire GUI rendering rework - the full `GuiGraphics` -> `GuiGraphicsExtractor`
migration across ~26 screen/widget files, `GuiCompat.java`'s own signature breaks (including a
proper fix for `renderEntityInInventory`, not just a flag), and now the GameTest registration model
- is done and compiles clean, on all 5 pre-existing targets too (regression-checked). **26.1 NeoForge
is code-complete pending live testing** (not yet done - see "Pending" at the bottom).

**`:26.2:compileJava` has 25 MORE errors on top of those same two files - a real, separate
rendering change that only shows up on 26.2, not 26.1.** `net.minecraft.client.renderer.MultiBufferSource`
- the class this mod's whole custom item-rendering pipeline is built on (`CrazyPhonePhotoItemRenderer.java`
obtains a `VertexConsumer` from it via `bufferSource.getBuffer(RenderType...)` to draw the photo's
raw quads) - does not exist anywhere in the decompiled 26.2.0.71 tree. Neither does
`net.minecraft.client.renderer.entity.ItemRenderer` itself, nor any other `*BufferSource` class.
What IS present instead, under a reorganized `net.minecraft.client.renderer.rendertype` package: new
classes named `PreparedRenderType`, `RenderSetup`, `OutputTarget`, `LayeringTransform` - names that
suggest a genuinely different, more declarative vertex-submission model, not a renamed version of
the old "get a mutable VertexConsumer and push vertices into it yourself" pattern. **This has not
been investigated beyond confirming the old classes are gone** - it's a separate rendering-pipeline
change from the `GuiGraphics` rework (that one *is* present and already fixed on 26.2, inherited
from the 26.1 work), roughly comparable in likely scope to redoing that whole investigation again,
just for item/entity rendering instead of GUI rendering. Given how much ground the GUI rework alone
took, this deserves its own dedicated pass rather than guessing at 25 errors blind.

**Practical implication**: 26.1 NeoForge's compile work is done; what's left is live testing (and
the Fabric side, see below). 26.2 needs someone to sit down with the decompiled
`net.minecraft.client.renderer.rendertype` package and `CrazyPhonePhotoItemRenderer.java` together
and work out the new vertex-submission model from scratch - treat it as its own project, not a
quick follow-up to the GUI work above.

**GameTest finding (resolved, see above for the fix actually applied)**: vanilla's
`net.minecraft.gametest.framework.GameTest` annotation is genuinely gone from the decompiled tree -
the whole annotation-scanning model is replaced by two registries a mod must populate explicitly:
`Registries.TEST_FUNCTION` (a "simple" built-in registry, same kind SoundEvents already use in this
mod, holding the actual `Consumer<GameTestHelper>` test bodies - moddable via the standard
`RegisterEvent`) and `Registries.TEST_INSTANCE` (the per-test metadata: which function, which
structure, which `TestEnvironmentDefinition`/batch - populated via NeoForge's own
`RegisterGameTestsEvent`, confirmed present in the real NeoForge 26.1.2.100 sources, firing only
when `GameTestHooks.isGametestEnabled()` is true, same conditions the old scanner ran under). This
is the same "annotation -> event registration" shift already seen for `ItemProperties`/
`RegisterConditionalItemModelPropertyEvent`. `FunctionGameTestInstance` (vanilla, in
`net.minecraft.gametest.framework`) is the built-in `GameTestInstance` implementation that just
wraps a `TEST_FUNCTION` key + `TestData` - no custom `GameTestInstance` subclass was needed.

## Update: Java 25 installed, Fabric 26.x infrastructure unblocked

A Java 25 JDK is now installed on this machine (`C:\Users\yanni\.jdks\ms-25.0.4.1`, Microsoft
Build of OpenJDK 25.0.4.1) - live testing of 26.1/26.2 is possible again once compilation
succeeds. Set `JAVA_HOME` to this path before running `./gradlew` against any 26.x target.

While getting `:26.1-fabric`/`:26.2-fabric` to even *configure* (previously blocked outright,
"Minecraft 26.1.2 requires Java 25 but Gradle is using 21"), a much bigger discovery surfaced:

**Minecraft 26.x ships fully unobfuscated.** The official Mojang version manifest for 26.1/26.1.2
has no `client_mappings`/`server_mappings` download entries at all (confirmed directly against the
raw JSON - grepping for "mappings" in either the base `26.1.json` or the patch `26.1.2.json` finds
nothing). Fabric Loom's usual `mappings(loom.officialMojangMappings())` mechanism has nothing to
resolve and fails with "Failed to find official mojang mappings for 26.1.2" - true even on the
newest Loom releases (tried 1.17.20 and the 1.18.0-alpha.19 prerelease, same error both times).
This is a known, already-fixed situation upstream: Fabric Loom's GitHub issue #1541 ("Loom still
asks mappings after upgrading from 1.21.11 to 26.1") confirms the fix is a **separate Gradle
plugin id** for this unobfuscated mode: `net.fabricmc.fabric-loom` instead of the regular
`fabric-loom` - same Loom version line (`1.17-SNAPSHOT` per Fabric's own updated
`fabric-example-mod` 26.1 branch), no `mappings(...)` dependency at all, and plain
`implementation(...)` instead of `modImplementation(...)` for mod dependencies (no remapping step
left to need it).

**Concrete changes made:**
- New `build.fabric26.gradle.kts` - a near-copy of `build.fabric.gradle.kts` (same walking-skeleton
  source-set scope, same resource/testing/publishing setup) but using the new plugin id and no
  mappings call. Only `26.1-fabric`/`26.2-fabric` use it (wired in `settings.gradle.kts`);
  `1.20.1-fabric`/`1.21.1-fabric` are untouched and still use the original `build.fabric.gradle.kts`
  with the original `fabric-loom` (Loom `1.10-SNAPSHOT`) - they're genuinely obfuscated versions
  and still need real remapping.
- Gradle wrapper bumped `9.4.0` -> `9.7.1` (the new Loom plugin line needs a newer Gradle plugin API
  than 9.4.0 exposes - confirmed via a "no matching variant... required org.gradle.plugin.api-version
  9.5.0" error). Re-verified all 5 pre-existing targets still compile clean under the new Gradle
  version + Java 25 daemon.
- `versions/26.1-fabric/gradle.properties`'s `fabric_api_version` corrected from `0.158.3+26.1.2`
  (does not exist - a bad transcription from an earlier lookup, never actually exercised until
  dependency resolution was attempted) to `0.155.2+26.1.2` (verified against the real maven
  metadata). `26.2-fabric`'s `0.158.0+26.2` was already correct.

**Result:** `:26.1-fabric:compileJava` now genuinely resolves every dependency and reaches real
compilation - 188 errors, a mix of the already-documented `GuiGraphics` rework (shared source tree,
same findings as the NeoForge side below) **and** a new, Fabric-API-specific layer: Fabric API
0.155.2+26.1.2 dropped several v1 modules entirely and replaced them with renamed ones, matching
vanilla's own 26.x terminology shift:

| Old | New | Confirmed via |
|---|---|---|
| `net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType` | `net.fabricmc.fabric.api.menu.v1.ExtendedMenuType` (module `fabric-menu-api-v1`) | Listing classes inside the real 0.155.2+26.1.2 jar - `fabric-screen-handler-api-v1` isn't bundled at all anymore. |
| `net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory` | Likely `net.fabricmc.fabric.api.menu.v1.FabricMenuProvider` (same module) | Same jar listing - not yet cross-checked method-for-method against our actual usage in `ScreenMenuUtils.java`. |
| `net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup` | `net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab` (module `fabric-creative-tab-api-v1`) | Same jar listing - `fabric-item-group-api-v1` isn't bundled anymore either. |
| `PayloadTypeRegistry.playS2C()` / `.playC2S()` | `PayloadTypeRegistry.clientboundPlay()` / `.serverboundPlay()` | `javap` on the real class in `fabric-networking-api-v1-6.3.1`. |
| `ScreenEvents.afterRender(Screen)` | `ScreenEvents.AfterExtract` (an inner interface/event, not a static method - shape likely changed too, not just the name) | Class listing in `fabric-screen-api-v1-5.1.0` - matches the same render->extract rename already seen on the vanilla side. |
| `net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry` | **Not yet found** - not present under that package in the 26.1.2 jar. May be tied to the same item-render architecture rework already flagged as an open TODO for NeoForge >=1.21.10 (`CrazyPhonePhotoItem.java`'s own comment: "1.21.10 removed BlockEntityWithoutLevelRenderer... item rendering reworked into a data-driven special model system"). Needs its own investigation - don't assume it's a simple rename like the others. |

Files hit by this Fabric-specific layer (on top of whatever `GuiGraphics` files they already share):
`init/ModMenus.java`, `init/ModTabs.java`, `init/ModRecipes.java` (a `fr.lordfinn.crazyphone.recipe`
package-not-found error - likely just a stale/incomplete source-set include list in
`build.fabric26.gradle.kts`, check that first before assuming it's a real break),
`utils/ScreenMenuUtils.java`, `client/PhoneClickableCursorHandler.java`,
`fabric/FabricNetworking.java`, `fabric/CrazyphoneFabricClient.java` (calls
`CrazyPhoneItemProperties.register()`, which doesn't exist - check whether that method only exists
behind a NeoForge-only stonecutter branch and Fabric needs its own equivalent),
`item/CrazyPhonePhotoItem.java`, `item/CrazyPhoneItemProperties.java` (this one's error was a
NeoForge-only import - `net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent`
- resolving while compiling a *Fabric* target, which smells like a real stonecutter
loader-condition bug worth checking first rather than a genuine new API break).

None of this Fabric-API-specific layer has been fixed yet - found via one compile attempt, not
methodically worked through file-by-file the way the NeoForge/vanilla API changes were.

**A real pre-existing gap, not a new 26.x break**: `item/CrazyPhoneItemProperties.java`'s top-level
`//? if >=1.21.10 { ... } else { ... }` block is gated on Minecraft version only, **not loader** -
the `>=1.21.10` branch is 100% NeoForge-specific (imports
`net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent`, no Fabric
equivalent inside that branch at all). This has silently never mattered before because this
project never had a Fabric node at `>=1.21.10` until `26.1-fabric`/`26.2-fabric` were added tonight
- it's not something 26.x itself broke, it's untested Fabric-side work that predates this session's
26.x effort entirely. Confirmed vanilla's own `ItemProperties` class (the pre-1.21.10 registration
API, still used in the `else` branch) is **completely gone** on 26.1 (`find` across the full
decompiled source tree turns up nothing) - so Fabric genuinely needs its own `>=1.21.10` path here,
most likely through Fabric API's `fabric-model-loading-api-v1` module (bundled in
0.155.2+26.1.2 - not yet opened/inspected) rather than reusing the NeoForge event. This needs the
same "add a loader gate inside the existing version gate" treatment as everything else, but with a
real Fabric-side implementation to write, not just a rename.

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

## Historical note: why this stopped mid-GUI-rework the first time (now resolved)

Earlier in this task, `:26.1:compileJava` was paused at 26 remaining errors (this file's "Files
that reference GuiGraphics" list above, plus the imageWidth/GameTest questions) because Java 25
wasn't installed yet, so nothing could be runtime-verified, and the mayor-candidate screen's
constructor looked like it needed a real design call. Both concerns turned out to be resolvable:
Java 25 got installed (`C:\Users\yanni\.jdks\ms-25.0.4.1`), and the imageWidth question dissolved
once the file was actually read closely (see the top of this file - it wasn't a design decision at
all, just a field-reuse pattern that no longer compiles). The GuiGraphics migration table and file
list above are kept as reference for how each rename/merge was actually handled, in case a similar
question comes up in the still-open 26.2 investigation.

## Remaining work (as of this update)

Current compile status, freshly verified: `:26.1:compileJava` (NeoForge) - **0 errors**.
`:26.1-fabric:compileJava` - **1 error** (`BuiltinItemRendererRegistry`, see above).
`:26.2:compileJava`/`:26.2-fabric:compileJava` - the shared custom-item-rendering pipeline rework
below is the only known remaining gap for both (25 and 24 errors respectively, before that rework).
All 5 pre-existing targets (`1.20.4`, `1.21.1`, `1.21.1-fabric`, `1.21.10`, `1.20.1-fabric`)
regression-checked clean alongside `:26.1:compileJava` in the same pass.

1. **The unified custom-item-rendering pipeline rework** - the one real remaining piece of design
   work, and now understood to be a SINGLE task rather than two separate ones (see above): rewrite
   `CrazyPhonePhotoItemRenderer.java`'s vertex-submission logic around whatever the new
   `PictureInPictureRendererRegistry`/`FabricModel`/`FabricRenderState` (Fabric) and
   `PreparedRenderType`/`RenderSetup`/`OutputTarget`/`LayeringTransform` (vanilla/NeoForge) API
   actually wants, then re-wire `CrazyPhonePhotoItem.java`'s two registration branches
   (`BuiltinItemRendererRegistry` on Fabric, the NeoForge `IClientItemExtensions`/
   `BlockEntityWithoutLevelRenderer` path - itself still a `<1.21.10`-only TODO on NeoForge, see that
   file's own comment) accordingly. Also touches `CrazyPhoneCaptureMode.java`,
   `CrazyPhoneCaptureHandMixin.java`, and ~9 screen/message files that reference `RenderType`/
   `MultiBufferSource` for text/item rendering on 26.2 specifically (per the `:26.2-fabric` error
   list). Not started beyond confirming the old classes are gone on both loaders - budget this as
   comparable in scope to the whole GuiGraphics rework already done, not a quick follow-up.
2. **The Fabric `ConditionalItemModelProperty` gap** (phone icon states not updating on Fabric
   >=1.21.10) - needs a Mixin into vanilla's `ConditionalItemModelProperties.bootstrap()`, see above.
   Lower priority than #1 (cosmetic - a static "dark" phone icon, no functional breakage) but a real,
   documented gap, not silently left broken.
3. **Live-test `:26.1:runClient`** - compiles clean now but has not actually been launched and
   clicked through yet. Do this once #1 is resolved enough to get a client actually rendering (a
   broken item renderer might crash on first render of the item, not just look wrong).
4. This file should be deleted once the port is actually complete and merged.
