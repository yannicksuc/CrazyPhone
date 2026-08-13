package fr.lordfinn.crazyphone.init;

import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

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
