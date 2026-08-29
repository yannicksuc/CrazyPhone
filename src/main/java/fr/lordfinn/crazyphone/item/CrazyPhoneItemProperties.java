package fr.lordfinn.crazyphone.item;

//? if >=1.21.10 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import javax.annotation.Nullable;
*///? } else {
import net.minecraft.client.renderer.item.ItemProperties;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//?}

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Drives the phone's 5 item model states (see the overrides in models/item/crazy_phone.json): dark/unlit by
 * default, lit while its own screen is open, and 3 distinct call textures - "calling" (this phone is the one
 * waiting for an answer), "called_in" (this phone is the one being called, not yet answered/declined), and
 * "in_call" (actually connected) - each its own independent property rather than folded into screen_on, so
 * each predicate stays a simple boolean check; priority between them is expressed purely by the override
 * array's order (last matching entry wins), not by any priority logic here.
 *
 * Every predicate here reads directly off the specific rendered {@code stack}'s own persisted data (see
 * CrazyPhoneHelper#isPhoneScreenOpen/getPhoneCallState), not any client-local field - that's what makes this
 * correct for EVERY renderer of the item, not just the owning player's own client. Vanilla's own
 * equipment-sync already replicates a held item's data to nearby tracking players purely because the
 * ItemStack changed, so writing the state into the item itself (done server-side, in
 * CrazyPhoneDefaultScreenMenu and CallRegistry) makes it visible to bystanders for free, with zero new
 * packets.
 */
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber(value = Dist.CLIENT)
*///?}
//?}
public class CrazyPhoneItemProperties {
    //? if >=1.21.10 {
    /*@SubscribeEvent
    public static void onRegisterConditionalItemModelProperty(RegisterConditionalItemModelPropertyEvent event) {
        event.register(Crazyphone.resource("screen_on"), ScreenOn.MAP_CODEC);
        event.register(Crazyphone.resource("calling"), CallState.CALLING_CODEC);
        event.register(Crazyphone.resource("called_in"), CallState.CALLED_IN_CODEC);
        event.register(Crazyphone.resource("in_call"), CallState.IN_CALL_CODEC);
    }

    public record ScreenOn() implements ConditionalItemModelProperty {
        public static final MapCodec<ScreenOn> MAP_CODEC = MapCodec.unit(new ScreenOn());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
            return CrazyPhoneHelper.isPhoneScreenOpen(stack);
        }

        @Override
        public MapCodec<ScreenOn> type() {
            return MAP_CODEC;
        }
    }

    // One record for all 3 call-state predicates (calling/called_in/in_call) rather than 3 near-identical
    // records - the state each checks is baked into which MAP_CODEC constant it's registered under, exactly
    // mirroring how registerCallState(propertyName, targetState) worked pre-1.21.10.
    public record CallState(State targetState) implements ConditionalItemModelProperty {
        public static final MapCodec<CallState> CALLING_CODEC = MapCodec.unit(new CallState(State.CALLING));
        public static final MapCodec<CallState> CALLED_IN_CODEC = MapCodec.unit(new CallState(State.RINGING));
        public static final MapCodec<CallState> IN_CALL_CODEC = MapCodec.unit(new CallState(State.ACTIVE));

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
            return targetState.name().equals(CrazyPhoneHelper.getPhoneCallState(stack));
        }

        @Override
        public MapCodec<CallState> type() {
            return switch (targetState) {
                case CALLING -> CALLING_CODEC;
                case RINGING -> CALLED_IN_CODEC;
                case ACTIVE -> IN_CALL_CODEC;
                default -> throw new IllegalStateException("No registered item model property for call state " + targetState);
            };
        }
    }
    *///? } else {
    //? if neoforge {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CrazyPhoneItemProperties::registerAll);
    }
    //?}
    //? if fabric {
    /*// Called from CrazyphoneFabricClient#onInitializeClient - vanilla's ItemProperties.register needs no
    // event wrapper on Fabric, items are already registered by the time a ClientModInitializer runs.
    public static void register() {
        registerAll();
    }
    *///?}

    private static void registerAll() {
        ItemProperties.register(
                ModItems.CRAZY_PHONE.get(),
                Crazyphone.resource("screen_on"),
                (stack, level, entity, seed) -> CrazyPhoneHelper.isPhoneScreenOpen(stack) ? 1.0f : 0.0f
        );
        registerCallState("calling", State.CALLING);
        registerCallState("called_in", State.RINGING);
        registerCallState("in_call", State.ACTIVE);
    }

    private static void registerCallState(String propertyName, State targetState) {
        ItemProperties.register(
                ModItems.CRAZY_PHONE.get(),
                Crazyphone.resource(propertyName),
                (stack, level, entity, seed) -> targetState.name().equals(CrazyPhoneHelper.getPhoneCallState(stack)) ? 1.0f : 0.0f
        );
    }
    //?}
}
