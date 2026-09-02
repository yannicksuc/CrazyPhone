# Porting to Minecraft 26.1 / 26.2 - status and next steps

Started as an overnight autonomous task (`/loop`). Real, verified progress was made, but the
task turned out to be much bigger than expected once actually attempted - this file exists so
that progress isn't lost and the next session (or a live human) can pick it up without
re-discovering everything from scratch.

## Update: `:26.1:runServer` boots cleanly - first live 26.x dedicated-server boot, four real runtime bugs found and fixed

Compiling clean and live-testing are different things. Launching `:26.1:runServer` for the first
time surfaced four genuinely separate runtime bugs, none visible at compile time, fixed one at a
time across four boot attempts until the server reached `Done (...)! For help, type "help"` with
zero ERROR log lines - the first live-verified 26.x server boot in this whole porting effort.

### 1. `NoClassDefFoundError: LocalPlayer` - a real regression, not a pre-existing bug

The dedicated server crashed outright during mod construction. Initially misdiagnosed as
"pre-existing, unrelated to tonight's work" - the user firmly corrected this (they'd tested a
dedicated server across multiple NeoForge/Fabric versions before, launched earlier in this same
overall session), which was then confirmed empirically: a `git worktree` checkout of the last
commit before any 26.x work began (`ed56944`) booted `:1.21.1:runServer` successfully. This WAS a
real regression introduced by this session's own porting work, and the user was right to push back
on the initial assessment.

**Root cause, confirmed via NeoForge's own `OnlyInWarningsHandler` log line** ("the mod crazyphone
uses the @OnlyIn annotation; the runtime member-stripping behaviour of this annotation is no longer
present"): NeoForge 26.x completely removed `@OnlyIn`'s bytecode-stripping mechanism. On older
NeoForge (1.21.1, `neo_version=21.1.248`), `@OnlyIn(Dist.CLIENT)` on a method actually strips/
replaces that method's body on the wrong dist at classload time - this genuinely worked there. On
26.x it's now a no-op annotation with no runtime effect at all. Standard JVM classloading fully
verifies every method's bytecode the moment a class is loaded (not lazily per-method) - so any
class containing even one method referencing a client-only type (`Minecraft`, `LocalPlayer`, etc.)
fails to load entirely on a dedicated server the instant anything triggers loading it, most commonly
NeoForge's own `AutomaticEventSubscriber` scanning every `@EventBusSubscriber`-annotated class via
`Class.forName(...)`.

**The fix has to be structural, not annotation-based**: move the risky method into a genuinely
separate nested class carrying its OWN class-level `@EventBusSubscriber(value = Dist.CLIENT)`
annotation. That annotation's `Dist.CLIENT` value is read via ASM-based metadata inspection
*before* `Class.forName()` runs - if the dist doesn't match, the scanner skips loading the class
entirely, sidestepping verification altogether. This pattern already existed once in the codebase
(`CrazyPhonePhotoItemClientBinding.java`), just not applied everywhere it needed to be. A subtlety
that cost a second round of testing: nesting a nested `Registration` class alone is **not** enough
if the risky method still lives on the outer class - `Registration.register()`'s own body creates a
`OuterClass::handleData` method reference, which forces full verification of `OuterClass`
regardless. The risky method itself has to live in the separate class, not just the registration
call.

Fixed across 5 files by extracting the client-only method into a new nested `ClientHandler` class:
[CrazyPhoneGroupMembershipNotificationPacket.java](src/main/java/fr/lordfinn/crazyphone/network/CrazyPhoneGroupMembershipNotificationPacket.java),
[CrazyPhoneNewMessageNotificationPacket.java](src/main/java/fr/lordfinn/crazyphone/network/CrazyPhoneNewMessageNotificationPacket.java),
[UpdateContactInfoMessage.java](src/main/java/fr/lordfinn/crazyphone/network/UpdateContactInfoMessage.java),
[CrazyPhoneNewCallDurationNotificationPacket.java](src/main/java/fr/lordfinn/crazyphone/network/CrazyPhoneNewCallDurationNotificationPacket.java),
[CrazyPhoneIncomingCallNotificationPacket.java](src/main/java/fr/lordfinn/crazyphone/network/CrazyPhoneIncomingCallNotificationPacket.java).
Two more files (`PhoneRegistrySyncPacket.java`, `PlayerPhoneStateSyncPacket.java`) were checked and
found already safe for our target versions - their `Minecraft.getInstance()` usage was already
confined to a `<1.20.5`-only branch, inactive on every version we actually ship.

### 2. GameTest registration: invalid `Identifier` path from camelCase batch names

`net.minecraft.IdentifierException: Non [a-z0-9/._-] character in path of location:
crazyphone:unregisteredPhone`. Own bug from earlier in this session's `CrazyPhoneGameTests.java`
`>=26` rework (see "GameTest finding" further down): the old `@GameTest(batch = "...")` camelCase
batch-name strings (`"unregisteredPhone"`, `"mayorVoteCooldown"`, etc.) got reused directly as
`TestEnvironmentDefinition` resource-path identifiers via `Crazyphone.resource(batchName)` in the
new `>=26` registration path - `Identifier`/`ResourceLocation` path validation requires
lowercase-only `[a-z0-9/._-]`. Fixed by converting the 9 batch-name arguments passed to
`registerTest(event, testId, batchName)` in `registerGameTests(...)` to snake_case
(`"unregistered_phone"`, `"mayor_vote_cooldown"`, etc.) - the `<26` annotation values themselves
were untouched, since those are plain Java strings, not resource-path identifiers.

### 3. NeoForge >=26 removed the global-loot-modifier index file entirely

`Couldn't parse data file 'neoforge:global_loot_modifiers'... No key type in MapLike[...]`.
Confirmed by diffing `LootModifierManager.java` between the real NeoForge 21.1.248 and 26.1.2.100
sources jars: on 1.21.1, `LootModifierManager` reads a `neoforge:loot_modifiers/
global_loot_modifiers.json` index file (`{"replace": bool, "entries": [...]}`) to decide which
files under `loot_modifiers/` are actually enabled - anything not listed there is ignored. On 26.1,
that whole indirection is gone: `LootModifierManager` now scans every file under `loot_modifiers/`
directly via a type-keyed dispatch codec (`IGlobalLootModifier.DIRECT_CODEC`), same pattern as
recipes/enchantments. `soulbound_ancient_city.json`/`soulbound_ancient_city_ice_box.json` already
carry their own `"type"` key and load fine standalone - the old index file itself is what breaks on
26.x, since it has no `"type"` key and gets scanned like any other file in the folder. Fixed by
deleting `data/neoforge/loot_modifiers/global_loot_modifiers.json` from the **build output only**
on `minecraftVersion.startsWith("26.")`, in `build.gradle.kts`'s `processResources` task - same
"patch the build output, not the tracked shared resource" approach already used there for
`crazy_phone_photo.json`.

### 4. `duplicate_photo` recipe JSON orphaned on versions where its serializer isn't registered

`Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]:
crazyphone:crafting_special_duplicate_photo`. Not a new bug - `ModRecipes.java`/
`CrazyPhoneDuplicatePhotoRecipe.java` already deliberately don't register this recipe's serializer
on `>=1.21.10` for either loader (the Recipe/CraftingRecipe API rework there isn't backported yet -
see their own doc comments). But the recipe JSON itself (`data/crazyphone/recipe/
duplicate_photo.json` and the older `data/crazyphone/recipes/duplicate_photo.json` - the datapack
folder was singularized at some point in this version range, both copies exist) is a shared
resource with no per-version gating, so it still ships and fails to load once the serializer stops
existing. Fixed the same way as #3: delete both files from the build output when
`minecraftVersion == "1.21.10" || minecraftVersion.startsWith("26.")`.

**Result**: `:26.1:runServer` now reaches `Done (0.853s)! For help, type "help"` with zero ERROR
log lines. Full compile regression re-verified clean across all 6 previously-passing targets
(`1.20.4`, `1.21.1`, `1.21.1-fabric`, `1.21.10`, `1.20.1-fabric`, `26.1`) after all four fixes.
**Not yet done**: joining as a client to verify the item-rendering pipeline visually (see the next
section - `:26.1:runClient` has been launched but not yet driven through an actual join/render
check), and the equivalent server-boot check for `26.1-fabric`/`26.2`/`26.2-fabric` once their own
remaining compile errors are resolved.

## Update: the NeoForge >=26 item-rendering rewrite is WRITTEN and COMPILES, but is NOT YET REACHABLE - one real decision left

Implemented the recipe from the section below: `CrazyPhonePhotoItemRenderer.java` now has a
`>=26`-gated `ModelImpl`/`SpecialRendererImpl` pair (a real `ItemModel` + `SpecialModelRenderer`,
NOT `SpecialModelWrapper`) whose transform logic is a direct, unchanged transcription of the
existing `render()` method (same math, just building a fresh `PoseStack` and finishing with
`layer.setLocalTransform(...)` instead of drawing immediately), and whose vertex-drawing logic
reuses the exact same `quad`/`slice`/`sliceBack`/`doubleSidedQuad` helpers via
`SubmitNodeCollector#submitCustomGeometry(...)`. Registered via a new `onRegisterItemModels`
handler in `CrazyPhonePhotoItemClientBinding.java` (the existing `Dist.CLIENT`-restricted class -
**not** `Crazyphone.java`'s common constructor, which would crash a dedicated server the same way
this exact class's own doc comment already warns about). Two more small, real 26.2-only breaks
found and fixed along the way: `GameRenderer#getMainCamera()` → `#mainCamera()` (new swap
`gr_main_camera`) - this one also affects the OLD, still-Fabric-used `render()` method, not just
the new code.

**Scoped to `neoforge && >=26` only, not `>=1.21.10`** (where this problem actually starts): 1.21.10
has a meaningfully different `ItemModel.Unbaked`/`SpecialModelRenderer` API shape (`bake()`'s
parameter list, `getExtents()`'s callback type) - confirmed by literally trying to compile this
exact code against it and reading the resulting errors, not guessed. Reconciling that would have
been a second research pass; 1.21.10 NeoForge still has zero custom photo rendering, exactly as
before this work - a real, tracked follow-up, not forgotten.

**Verified: compiles clean on `:1.20.4`, `:1.21.1`, `:1.21.1-fabric`, `:1.21.10`, `:1.20.1-fabric`,
`:26.1`. Zero regressions - `:26.2`/`:26.2-fabric`/`:26.1-fabric`'s error counts are UNCHANGED**
(still isolated to exactly the same already-documented old-code MultiBufferSource/
BuiltinItemRendererRegistry gap, since Fabric still needs the OLD `render()` method to keep
compiling on every version until its own separate fix lands - this new code doesn't replace or
remove that method, only adds a parallel NeoForge-only path).

**NOT live-tested - cannot be, from this session.** The transform/vertex math is an unchanged
transcription, so the geometry itself should be correct if reached at all, but real unknowns
remain: does `ItemModel#update()` actually get called fresh every frame for a held item the way
the old `render()` was (needed for the live camera-tracking presenting logic), does the
registration actually get picked up correctly, is `setLocalTransform` + a `SpecialModelRenderer`
with no `base` model genuinely sufficient (no `ModelRenderProperties`/particle material set - may
be fine, may need a minimal base model reference for GUI-light/particle behavior). All real
questions a live client would answer in minutes; none answerable by reading code.

**Update: the item model JSON gap is now closed too.** `build.gradle.kts` gained a small
`processResources`-level `doLast` step (right after `generateModMetadata`'s own existing
`neoforge.mods.toml` → `mods.toml` rename for 1.20.x, same "generate per-version file content in
Kotlin, not the tracked resource itself" idea, just a content overwrite instead of a rename):
when `minecraftVersion.startsWith("26.")`, it overwrites the BUILT
`assets/crazyphone/models/item/crazy_phone_photo.json` with `{"type":
"crazyphone:photo_card_model"}`; every other version's build output is untouched. Verified directly
by running `processResources` for both `:26.1`/`:26.2` (correctly rewritten) and `:1.20.4`/
`:1.21.1`/`:1.21.10` (correctly left as `{"parent": "builtin/entity"}`), plus a full
`compileJava` regression pass afterward (unchanged - this only touches resource output, not
compilation). **This means NeoForge `>=26`'s new `ModelImpl`/`SpecialRendererImpl` pair (see below)
is now actually wired end-to-end and should be reachable at runtime on `:26.1` - still not
live-tested (see this file's own caveats on that below), but no longer blocked by a missing
decision.** 26.2 itself still won't compile until its separate old-code `MultiBufferSource` gap is
fixed, but its resource output is already correct and waiting.

## Update: `Minecraft#screen`/`#setScreen` fixed (26.2-only) - both 26.2 targets now isolated to the rendering pipeline

Found and fixed one more small, real, 26.2-*specific* break while investigating the rendering
pipeline item below: `Minecraft`'s public `screen` field and `setScreen(Screen)` method are gone
entirely on 26.2 (confirmed present, unchanged, on 26.1 - checked both decompiled sources side by
side), moved onto the `Minecraft#gui` (`Gui`) instance as `screen()`/`setScreen(Screen)`.
`Minecraft#setScreenAndShow(Screen)` (present on both versions) is the closer behavioral
replacement for the old setter - its body forces an immediate render frame the same way the old
field-setting setter's callers likely relied on, whereas the low-level `gui.setScreen(...)` is just
the raw field write `Gui` itself uses internally. Fixed via two new swaps (`mc_get_screen`,
`mc_set_screen` in `stonecutter.gradle.kts`, keyed `>=26.2` specifically) applied across 21 call
sites in 15 files (screen-history lookups, capture-mode enter/exit, "open the photo viewer"
call sites). **Result: `:26.2:compileJava` and `:26.2-fabric:compileJava` are now isolated to
exactly the item-rendering pipeline work below** - every other error on both is gone. All 5
pre-existing targets + `:26.1`/`:26.1-fabric` regression-checked clean (no change to their error
counts - this was purely additive, 26.2-gated).

## Update: the item-rendering pipeline replacement, investigated and now a concrete recipe

This is the one remaining real piece of work (blocks `:26.1-fabric`'s last error and all of
`:26.2`/`:26.2-fabric`'s ~25 errors each). It looked like a scary unknown at first - vanilla's
`MultiBufferSource`/`net.minecraft.client.renderer.entity.ItemRenderer` and Fabric's
`BuiltinItemRendererRegistry` are BOTH just gone, with no renamed drop-in replacement - but a real
investigation (decompiled 26.2.0.71 vanilla source + the actual NeoForge 26.1.2.100 jar) turned up
a complete, concrete, mechanically-followable path. Writing it down here in full since actually
implementing it is a substantial rewrite (~600 lines in `CrazyPhonePhotoItemRenderer.java` alone)
that deserves its own dedicated pass with real live-testing (3D geometry correctness can't be
verified by reading code), not a blind guess during an unattended run.

**What actually changed, architecturally**: item rendering moved to the same "extract render
state, then submit" split already seen everywhere else in 26.x (GuiGraphics, GameTest, entity
render states). There is no more "get a `VertexConsumer` from a `MultiBufferSource` and push
vertices into it immediately" - instead:

1. A per-item `net.minecraft.client.renderer.item.ItemModel` (this interface already existed
   before 26.x, tied to the same >=1.21.10 rework `CrazyPhonePhotoItem.java`'s own TODO already
   referenced) has one method, `update(ItemStackRenderState output, ItemStack item,
   ItemModelResolver resolver, ItemDisplayContext displayContext, ClientLevel level, ItemOwner
   owner, int seed)`, called fresh on every render (the `seed` param confirms this isn't a
   bake-once cache) to populate `output` - this is where **all of this file's existing
   `render()`-method transform logic belongs now** (the whole isHand/GROUND/FIXED/presenting
   poseStack.translate/mulPose/scale chain, completely unchanged math-wise).
2. `output.newLayer()` returns an `ItemStackRenderState.LayerRenderState` with two methods that
   matter here: `setLocalTransform(Matrix4fc)` (an arbitrary transform matrix - build a scratch
   `PoseStack`, run the exact same translate/mulPose/scale calls this file already has, then pass
   `poseStack.last().pose()` here) and `setupSpecialModel(SpecialModelRenderer<T> renderer, T
   argument)` (registers the actual drawing logic for this layer, `T` being any small
   data-carrier - e.g. an enum/record for "which of the current render() branches applies" plus
   the resolved photo texture).
3. `net.minecraft.client.renderer.special.SpecialModelRenderer<T>.submit(T argument, PoseStack
   poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean
   hasFoil, int outlineColor)` is called later (render phase), with `poseStack` **already carrying
   the `localTransform` from step 2 baked in** - this is where the ACTUAL vertex-drawing code
   belongs, and it needs almost no changes: `SubmitNodeCollector.submitCustomGeometry(PoseStack,
   RenderType, SubmitNodeCollector.CustomGeometryRenderer)` takes a
   `CustomGeometryRenderer.render(PoseStack.Pose pose, VertexConsumer buffer)` callback - **the
   exact same `(PoseStack.Pose pose, VertexConsumer buffer)` shape every existing helper method in
   this file already uses** (`quad`, `slice`, `sliceBack`, `doubleSidedQuad`, `tintedQuad`,
   `vertex`/`tintedVertex`). Every current `bufferSource.getBuffer(RenderType.entityCutout(tex))`
   call site becomes `submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tex),
   (pose, buffer) -> { /* same quad(...)/slice(...) calls as today, just using pose/buffer here */ })`.

**Net effect**: `renderFramedCard`/`renderHandFramedCard`/`renderPresentingCandidates` and every
quad/vertex helper below them survive almost verbatim - only their outer signature (take
`SubmitNodeCollector` instead of `MultiBufferSource`, wrap each texture's draw calls in one
`submitCustomGeometry` block instead of calling `bufferSource.getBuffer(...)` directly) changes.
The `render()` method itself needs to actually split into two: the transform-computation half
(-> `ItemModel.update()`) and the draw half (-> `SpecialModelRenderer.submit()`), connected by
whatever small argument type carries "which case, which texture" across that boundary.

**Registration (NeoForge)** - both are real, standard mod-bus events, confirmed present in the
real NeoForge 26.1.2.100 jar (`net.neoforged.neoforge.client.event`):
- `RegisterItemModelsEvent#register(Identifier, MapCodec<? extends ItemModel.Unbaked>)` - registers
  a custom `ItemModel.Unbaked` "type" (referenced by id from the item's own model JSON, e.g.
  `assets/crazyphone/models/item/crazy_phone_photo.json` would need `{"type":
  "crazyphone:photo_card"}` instead of whatever it references today - check that file).
- `RegisterSpecialModelRendererEvent#register(Identifier, MapCodec<? extends
  SpecialModelRenderer.Unbaked<?>>)` - registers the actual renderer type the `ItemModel.Unbaked`'s
  own `bake(...)` method would reference/construct.
- Same "simple built-in registry populated via a LateBoundIdMapper, NeoForge patches an event-post
  call into vanilla's own bootstrap method" shape as `ConditionalItemModelProperties`/
  `TEST_FUNCTION` (see this file's other sections) - expect the same kind of vanilla bootstrap
  class to exist for these two (not yet located by name - search the decompiled source for
  `ItemModels.bootstrap`/`SpecialModelRenderers.bootstrap` or similar).

**Registration (Fabric) - confirmed, and it's the SAME gap as `ConditionalItemModelProperty`**:
checked the real decompiled 26.1.2 `ItemModels.java`/`SpecialModelRenderers.java` directly - both
are a private static `ExtraCodecs.LateBoundIdMapper` populated once via a `bootstrap()` method,
and (exactly like `ConditionalItemModelProperties.bootstrap()`) NeoForge patches
`ModLoader.postEvent(new Register...Event(ID_MAPPER))` directly into the END of that vanilla
method as a source patch - not something vanilla itself does, and not backed by a `BuiltInRegistries`
entry either (checked - no hits), so it's not moddable via `RegisterEvent` the way `TEST_FUNCTION`
turned out to be. Also checked `fabric-rendering-v1`'s own classes
(`PictureInPictureRendererRegistry`, `FabricModel`, `FabricRenderState`, `TransformCopyingModel`,
`ExtractItemDecorationsCallback`) for a Fabric-provided alternative - none fit
(`PictureInPictureRendererRegistry` is for GUI-only picture-in-picture renderers like
map-in-inventory, not held/world item rendering; the rest are entity/block model composition
helpers or item-decoration overlays). **This makes THREE separate vanilla bootstrap methods now
confirmed to have this exact same NeoForge-only-patched-event shape with no Fabric equivalent**
(`ConditionalItemModelProperties`, `ItemModels`, `SpecialModelRenderers`) - worth building ONE
shared Fabric-side Mixin utility/pattern that injects at the tail of each (an `@Inject` at
`RETURN`, then either an `@Accessor` for the private static `ID_MAPPER` field or reflection) rather
than solving each one-off. That single piece of infrastructure would unblock all three gaps at
once, including the icon-state gap already documented above.

**One more concrete finding, ruling out the "easy" path**: vanilla ships a ready-made `ItemModel`
implementation for exactly this situation, `SpecialModelWrapper` (JSON `"type": "minecraft:special"`,
fields `base` + optional `transformation` + `model`) - checked its real source specifically to see
if it could avoid writing a custom `ItemModel` entirely. It can't, for this renderer: its own
`update()` only ever calls `ModelRenderProperties.applyToLayer(layer, displayContext)` - a STATIC
per-displayContext transform table pulled from the `base` model's own vanilla `display` JSON block
- with no hook for computing a transform at runtime. This mod's actual transform logic is
fundamentally dynamic (live camera-relative rotation cancellation while presenting, `/presentdebug`
live-tunable values, a whole conditional branch tree far beyond what a static per-context transform
table can express) - `SpecialModelWrapper` genuinely can't express it. **Conclusion: write our own
`ItemModel.Unbaked`/`ItemModel` pair (registered via `RegisterItemModelsEvent`, JSON `"type":
"crazyphone:photo_card_model"` or similar, replacing today's `{"parent": "builtin/entity"}` in
`crazy_phone_photo.json`), not just a `SpecialModelRenderer`.** Its `update()` becomes the new home
for today's ENTIRE `render()` method's transform-branching logic (unchanged math, just building a
scratch `PoseStack` and finishing with `layer.setLocalTransform(scratchPoseStack.last().pose())`
instead of leaving the poseStack live) - `submit()` itself has no `ItemDisplayContext` parameter at
all, so which case applies must be resolved in `update()` and carried across via whatever small
argument type `setupSpecialModel(renderer, argument)` passes to `submit()`.

**Recommended approach for whoever picks this up**: get it working on NeoForge 26.1 first (only
one loader, and this file already documents the exact registration events), verify the OLD
`<1.21.10` NeoForge path still needs its own separate backport too (see `CrazyPhonePhotoItem.java`'s
own pre-existing TODO - this rework affects 1.21.10 through main, not just 26.x), THEN tackle
Fabric once the vanilla-side registration mechanism is confirmed. Live-test after every step -
this is exactly the kind of change (3D geometry, transform order, render-layer bucketing) that
looks right in a diff and renders as a black square or an invisible item in practice.

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

Current compile status, freshly verified in one pass: `:26.1:compileJava` (NeoForge) - **0
errors**, and its new item renderer is now wired end-to-end (registered, and
`crazy_phone_photo.json` actually references it - see above). `:26.1-fabric:compileJava` - **1
error** (`BuiltinItemRendererRegistry`, unaffected by the NeoForge-only rewrite above). `:26.2` /
`:26.2-fabric:compileJava` - isolated to the OLD `render()` method's own `MultiBufferSource`/
`RenderType` breakage (still needed there since Fabric hasn't been ported to the new architecture
yet) plus 1.21.10's own not-yet-reconciled API shape difference. All 5 pre-existing targets
(`1.20.4`, `1.21.1`, `1.21.1-fabric`, `1.21.10`, `1.20.1-fabric`) regression-checked clean
throughout this whole rendering-pipeline pass.

## Update: `:26.1:runClient` live-tested - the `ModelImpl`/`SpecialRendererImpl` approach works, real bugs found and fixed along the way

Item 1 below is now done. `:26.1:runClient` was launched and put through extensive live testing
(sneak-presenting a card in one hand, then two at once) alongside a real dedicated server on the
same build. The `ModelImpl`/`SpecialRendererImpl` approach itself holds up - no rewrite needed -
but getting first-person presenting fully correct took several real bug fixes, in
[CrazyPhonePresentHandGripMixin.java](src/main/java/fr/lordfinn/crazyphone/mixin/CrazyPhonePresentHandGripMixin.java)
(the `>=26` branch):

- The arm's own grip pose and the presented card's own transform used to be computed independently,
  at two different points in the frame - injecting `renderArmWithItem`'s own `HEAD` (fired once per
  hand by vanilla itself, right before that hand's own item render) instead of the old
  `renderPlayerArm`+`renderHandsWithItems` dual-injection is what actually keeps them from drifting
  apart, since both now start from the exact same poseStack snapshot. No camera-relative rotation
  math needed once that's true - confirmed live at every camera angle, not just when facing due
  north the way the old approach only ever gave a correct result.
- Off-hand handling: the mixin now cancels vanilla's own per-hand item render whenever that
  particular hand isn't actually holding the phone, so a leftover sword/tool/empty-hand render
  doesn't fight the shared grip pose for the same space.
- Dual-photo presenting (a card in each hand at once) needed its own live-tuned, per-hand X offset
  in `CrazyPhonePresentDebug.java` (`dualX`/`dualLeftExtra`) rather than one shared, sign-mirrored
  value - the two hands' own frames turned out to be genuinely asymmetric, not just mirror images of
  each other.

This same architectural fix (`renderArmWithItem`-HEAD injection, no camera-relative rotation, arm
and card sharing one transform) was then ported to the `<1.21.10` branch of the same mixin - shared
by 1.20.4, 1.21.1, and both Fabric nodes - which live testing on 1.21.1-fabric found to have the
exact same underlying bug (no bob, arms not drawn, up/down movement inverted, the card visibly
drifting from the arm while turning the camera). That branch needed its own extra fix beyond the
`>=26` one: the arm and card there are drawn as two SEPARATE calls (no shared `AvatarRenderer`-style
API), so the mixin now draws the card itself, directly, on the arm's own raw poseStack, instead of
letting vanilla's normal per-item dispatch draw it later on a different, further-transformed one.

Separately (not specific to 26.x, but found and fixed the same night while testing across every
version): the phone number/name/password registration wizard
([CrazyPhonePasswordScreenScreen.java](src/main/java/fr/lordfinn/crazyphone/client/gui/CrazyPhonePasswordScreenScreen.java),
loader-neutral, shared by every node including 26.x) had two compounding bugs that together meant a
newly registered phone almost never actually persisted server-side (bounced back to the
registration screen every time, or intermittently, depending on which of two racing writes won) -
see that file's own doc comments on `getEditBoxAndCheckBoxValues()` and the "Valider" button's
`onPress` handler for the details. Both are fixed now, on every version.

1. ~~**Live-test `:26.1:runClient`**~~ - done, see above.
2. **Port the same `ModelImpl`/`SpecialRendererImpl` approach to NeoForge 1.21.10** - blocked on
   step 1 actually working first. Needs reconciling the different `ItemModel.Unbaked.bake()`/
   `SpecialModelRenderer.getExtents()` API shape there (see above - not investigated beyond
   confirming it differs). Also needs its own `crazy_phone_photo.json` handling in `build.gradle.kts`
   (currently the resource-patching step only fires for `minecraftVersion.startsWith("26.")` -
   1.21.10 would need its own condition, probably `>=1.21.10 <26` once its JSON format is confirmed
   to be the same shape as 26.x's).
3. **Port the whole approach to Fabric** (all versions `>=1.21.10`, once proven on NeoForge) - the
   registration mechanism is the real open question: `ItemModels.bootstrap()`/
   `SpecialModelRenderers.bootstrap()` have the same "NeoForge patches an event-post call into
   vanilla's own bootstrap method, no Fabric equivalent" shape as `ConditionalItemModelProperties`
   (see the shared-Mixin-utility idea above) - solving that one Mixin pattern unblocks this AND item
   #4 below at once.
4. **The Fabric `ConditionalItemModelProperty` gap** (phone icon states not updating on Fabric
   `>=1.21.10`) - needs the same Mixin pattern as #3. Lower priority on its own (cosmetic - a static
   "dark" phone icon, no functional breakage) but likely worth doing alongside #3 given the shared
   infrastructure.
5. Once 26.2's Fabric-still-needed `render()` method is also ported (or the whole old method is
   finally retired once Fabric no longer needs it), re-verify `:26.2`/`:26.2-fabric:compileJava`.
6. This file should be deleted once the port is actually complete and merged.
