plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.20.4"

// Loader identity as a Stonecutter boolean constant, usable in the shared source tree as
// //? if fabric { ... } / //? if neoforge { ... } - exactly the same mechanism already used throughout
// this codebase for Minecraft-version gating (//? if >=1.21.10 etc.), just gating on loader instead. Only
// the two new "*-fabric" nodes carry that suffix (see settings.gradle.kts); every other node is NeoForge.
stonecutter parameters {
    val loader = if (current.project.endsWith("-fabric")) "fabric" else "neoforge"
    constants.match(loader, "fabric", "neoforge")
    // 26.x renamed net.minecraft.resources.ResourceLocation to net.minecraft.resources.Identifier (same
    // package, same shape - confirmed via decompiled vanilla source, javap-verified) - used across 50+ files
    // in the shared tree, so a per-occurrence //? if >=26 branch everywhere would be enormous. A scoped swap
    // (see its usages: /*$ res_loc {*/ResourceLocation/*$}*/) substitutes just the bare class name at each
    // site instead, keeping "ResourceLocation" as the one name actually written in this source tree.
    swaps.put("res_loc", if (semantics.eval(current.version, ">=26")) "Identifier" else "ResourceLocation")
    // 26.x renamed Camera#getYRot()/getXRot() to yRot()/xRot() (dropped the "get" prefix - confirmed via
    // decompiled vanilla source). Only 4 call sites, both already nested inside an existing >=1.20.5
    // stonecutter comment block, so swapping just the method name (not the whole line) keeps that nesting
    // simple - see its usages: camera./*$ cam_yaw {*/getYRot/*$}*/().
    swaps.put("cam_yaw", if (semantics.eval(current.version, ">=26")) "yRot" else "getYRot")
    swaps.put("cam_pitch", if (semantics.eval(current.version, ">=26")) "xRot" else "getXRot")
    // 26.x moved RenderType#entityCutout(...) (and its sibling static factories) off RenderType itself and
    // onto a new sibling class RenderTypes (plural), in a new subpackage - the RenderType class name itself
    // still exists (as the return type), just no longer carries these factory methods.
    val newRenderTypes = semantics.eval(current.version, ">=26")
    swaps.put("render_type_import", if (newRenderTypes) "net.minecraft.client.renderer.rendertype.RenderTypes" else "net.minecraft.client.renderer.RenderType")
    swaps.put("render_types", if (newRenderTypes) "RenderTypes" else "RenderType")
    // 26.x's SavedDataType (already NeoForge-patched, >=1.21.10 only) changed its id parameter from a plain
    // String to a real Identifier - DATA_NAME is just a bare string constant (not namespaced), so it needs
    // wrapping through Crazyphone.resource(...) (already version-branched to return the right type) instead
    // of a straight rename. Used only at each SavedDataType(...) construction call site, not DATA_NAME's own
    // declaration or its other (String-expecting) usages in the same file.
    swaps.put("saved_data_id", if (semantics.eval(current.version, ">=26")) "Crazyphone.resource(DATA_NAME)" else "DATA_NAME")
    // 26.x moved GameRules to its own gamerules package, renamed RULE_KEEPINVENTORY -> KEEP_INVENTORY, and
    // changed GameRules#getBoolean(GameRuleKey) -> the generic #get(GameRule<T>) - bundled into one swap
    // each since the package/name/call-shape all change together.
    val newGameRules = semantics.eval(current.version, ">=26")
    swaps.put("game_rules_pkg", if (newGameRules) "net.minecraft.world.level.gamerules.GameRules" else "net.minecraft.world.level.GameRules")
    swaps.put("keep_inventory_call", if (newGameRules) "get(GameRules.KEEP_INVENTORY)" else "getBoolean(GameRules.RULE_KEEPINVENTORY)")
    // 26.x made Inventory#selected private, with a setSelectedSlot(int) setter replacing direct field
    // writes (confirmed via decompiled vanilla source) - every use in this codebase always sets it to 0.
    swaps.put("set_selected_slot_0", if (semantics.eval(current.version, ">=26")) ".setSelectedSlot(0)" else ".selected = 0")
    // 26.x renamed GuiGraphics to GuiGraphicsExtractor (same package) - the actual draw-primitive method
    // shapes (fill/blit/text/matrix-stack ops) are otherwise unchanged from the 1.21.10 rework already
    // handled in GuiCompat.java, confirmed against the real 26.1.2 vanilla jar. Every GuiGraphics-typed
    // parameter/field/import across the codebase goes through this one swap.
    swaps.put("gui_graphics_type", if (semantics.eval(current.version, ">=26")) "GuiGraphicsExtractor" else "GuiGraphics")
    // 26.x renamed several GuiGraphics(Extractor) instance methods (same argument shapes, confirmed against
    // the real 26.1.2 jar) - each of these is a simple guiGraphics.<name>(...) call-site rename.
    val is26 = semantics.eval(current.version, ">=26")
    swaps.put("gui_render_item", if (is26) "item" else "renderItem")
    swaps.put("gui_draw_string", if (is26) "text" else "drawString")
    swaps.put("gui_draw_centered_string", if (is26) "centeredText" else "drawCenteredString")
    swaps.put("gui_draw_word_wrap", if (is26) "textWithWordWrap" else "drawWordWrap")
    swaps.put("gui_render_tooltip", if (is26) "setTooltipForNextFrame" else "renderTooltip")
    swaps.put("gui_render_component_tooltip", if (is26) "setComponentTooltipForNextFrame" else "renderComponentTooltip")
    swaps.put("gui_render_transparent_background", if (is26) "extractTransparentBackground" else "renderTransparentBackground")
    // 26.x moved PlayerModel into its own subpackage (net.minecraft.client.model.player.PlayerModel).
    swaps.put("player_model_pkg", if (is26) "net.minecraft.client.model.player.PlayerModel" else "net.minecraft.client.model.PlayerModel")
    // Renderable/AbstractWidget's own public render dispatcher (what any caller uses to render a widget or
    // Button from outside, as opposed to the renderWidget/extractContents hook a widget overrides to draw
    // its own content) - render(...) -> extractRenderState(...), same param order.
    swaps.put("widget_render", if (is26) "extractRenderState" else "render")
    // 26.x moved net.minecraft.Util to net.minecraft.util.Util (backgroundExecutor() etc. unchanged).
    swaps.put("util_pkg", if (semantics.eval(current.version, ">=26")) "net.minecraft.util.Util" else "net.minecraft.Util")
    // Fabric API's own 26.x release renamed the "screen handler" module to "menu" (fabric-screen-handler-api-v1
    // -> fabric-menu-api-v1, confirmed against the actual resolved fabric-api dependency tree and jar contents
    // for 26.1.2 - the old module isn't even a transitive dependency anymore). ExtendedScreenHandlerType(.
    // ExtendedFactory) -> ExtendedMenuType(.ExtendedFactory) and ExtendedScreenHandlerFactory ->
    // ExtendedMenuProvider are otherwise identical in shape (constructor/method signatures match exactly,
    // javap-verified against the real 26.1.2-resolved jar) - pure renames, not a design change.
    val fabricMenuRework = semantics.eval(current.version, ">=26")
    swaps.put("fabric_ext_menu_type_import", if (fabricMenuRework) "net.fabricmc.fabric.api.menu.v1.ExtendedMenuType" else "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType")
    swaps.put("fabric_ext_menu_type", if (fabricMenuRework) "ExtendedMenuType" else "ExtendedScreenHandlerType")
    swaps.put("fabric_ext_menu_provider", if (fabricMenuRework) "net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider" else "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory")
    // Fabric API's fabric-screen-api-v1 renamed ScreenEvents#afterRender(Screen) -> #afterExtract(Screen) for
    // 26.x, matching vanilla's own render->extractRenderState rework - same callback shape otherwise
    // (Screen, GuiGraphicsExtractor, mouseX, mouseY, partialTick), javap-verified against the real
    // 26.1.2-resolved jar.
    swaps.put("fabric_screen_events_after_render", if (fabricMenuRework) "afterExtract" else "afterRender")
    // Fabric API's fabric-networking-api-v1 renamed PayloadTypeRegistry#playS2C()/playC2S() ->
    // #clientboundPlay()/#serverboundPlay() for 26.x (same semantics, vanilla's own clientbound/serverbound
    // terminology instead of S2C/C2S - javap-verified against the real 26.1.2-resolved jar).
    swaps.put("fabric_payload_registry_s2c", if (fabricMenuRework) "clientboundPlay" else "playS2C")
    swaps.put("fabric_payload_registry_c2s", if (fabricMenuRework) "serverboundPlay" else "playC2S")
    // 26.2 (not 26.1 - confirmed absent only starting there) removed Minecraft's own public `screen` field
    // and `setScreen(Screen)` method entirely, moving them to the Gui instance (Minecraft#gui) as
    // `screen()`/`setScreen(Screen)` - confirmed via decompiled source diff between 26.1.2 (both still on
    // Minecraft directly) and 26.2.0.71 (both gone from Minecraft). `Minecraft#setScreenAndShow(Screen)`
    // (present on both versions) is the more faithful replacement for the old `setScreen` - it forces an
    // immediate render frame the same way the old field-setting setScreen's callers likely expected,
    // confirmed by reading its body (delegates to gui.setScreen then renderFrame).
    val is262 = semantics.eval(current.version, ">=26.2")
    swaps.put("mc_get_screen", if (is262) "gui.screen()" else "screen")
    swaps.put("mc_set_screen", if (is262) "setScreenAndShow" else "setScreen")
    // 26.2 (not 26.1) also renamed GameRenderer#getMainCamera() -> #mainCamera() (dropped the "get"
    // prefix, matching the modern accessor convention used elsewhere - confirmed via decompiled source:
    // 26.1 still has getMainCamera(), 26.2 only has mainCamera()).
    swaps.put("gr_main_camera", if (is262) "mainCamera" else "getMainCamera")
}