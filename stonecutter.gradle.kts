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
}