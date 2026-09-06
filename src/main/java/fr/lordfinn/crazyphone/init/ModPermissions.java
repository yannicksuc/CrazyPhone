package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
//?}
// Real, original Forge 1.20.1 - same PermissionGatherEvent.Nodes/addNodes shape as NeoForge's own, javap-
// verified (see FeatureFlag.java's own permission-node port for the matching PermissionAPI/PermissionNode
// story).
//? if legacyforge {
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
//?}

import fr.lordfinn.crazyphone.FeatureFlag;

/** Registers every FeatureFlag's permission node - required once per node before PermissionAPI#getPermission
 * can query it, regardless of whether a permission plugin is actually installed. */
@EventBusSubscriber
public class ModPermissions {
    @SubscribeEvent
    public static void register(PermissionGatherEvent.Nodes event) {
        for (FeatureFlag flag : FeatureFlag.values())
            event.addNodes(flag.permission);
    }
}
