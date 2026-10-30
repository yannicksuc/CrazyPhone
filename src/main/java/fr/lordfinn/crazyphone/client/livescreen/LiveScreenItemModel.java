package fr.lordfinn.crazyphone.client.livescreen;

/**
 * &gt;=1.21.10 hook for the live phone screen: an item model type ("crazyphone:live_screen", referenced from
 * items/crazy_phone.json) that wraps the phone's normal model tree ("base") untouched and, when
 * {@link LiveScreenRenderer#prepare} says so, appends one extra special-model layer drawing the screen.
 * When it says no (GUI, ground, frames, other holders, options off...), the item renders exactly as before.
 *
 * Registered on NeoForge through RegisterItemModelsEvent (CrazyPhonePhotoItemClientBinding) and on Fabric
 * through CrazyPhoneItemModelRegistrationMixin, next to the photo card model. Older versions render the
 * screen from CrazyPhoneLiveScreenItemRenderMixin instead and only keep this empty class.
 */
//? if >=1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

public final class LiveScreenItemModel implements ItemModel {
    private static final Renderer RENDERER = new Renderer();
    private final ItemModel base;
    //? if >=26 {
    /^private final org.joml.Matrix4fc transformation;

    private LiveScreenItemModel(ItemModel base, org.joml.Matrix4fc transformation) {
        this.base = base;
        this.transformation = transformation;
    }
    ^///?} else {
    private LiveScreenItemModel(ItemModel base) {
        this.base = base;
    }
    //?}

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        base.update(output, item, resolver, displayContext, level, owner, seed);
        if (!LiveScreenViews.isHand(displayContext))
            return;
        try {
            LivingEntity holder = owner == null ? null : owner.asLivingEntity();
            LiveScreenMesh mesh = LiveScreenRenderer.prepare(item, holder, displayContext);
            if (mesh == null)
                return;
            Matrix4f local = LiveScreenRenderer.layerTransform(displayContext);
            ItemStackRenderState.LayerRenderState layer = output.newLayer();
            //? if >=26 {
            /^local.mul(transformation);
            layer.setLocalTransform(local);
            layer.setupSpecialModel(RENDERER, new Argument(mesh, null));
            ^///?} else {
            layer.setupSpecialModel(RENDERER, new Argument(mesh, local));
            //?}
        } catch (Throwable t) {
            LiveScreenRenderer.fail(t);
        }
    }

    record Argument(LiveScreenMesh mesh, @Nullable Matrix4f localTransform) {
    }

    public record Unbaked(ItemModel.Unbaked base) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemModels.CODEC.fieldOf("base").forGetter(Unbaked::base)
        ).apply(instance, Unbaked::new));

        @Override
        public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
            base.resolveDependencies(resolver);
        }

        //? if >=26 {
        /^@Override
        public ItemModel bake(ItemModel.BakingContext context, org.joml.Matrix4fc transformation) {
            return new LiveScreenItemModel(base.bake(context, transformation), transformation);
        }
        ^///?} else {
        @Override
        public ItemModel bake(ItemModel.BakingContext context) {
            return new LiveScreenItemModel(base.bake(context));
        }
        //?}

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private static final class Renderer implements SpecialModelRenderer<Argument> {
        //? if >=26 {
        /^@Override
        public void submit(Argument argument, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
                           int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
            if (argument == null)
                return;
            draw(argument.mesh(), poseStack, collector);
        }

        @Override
        public void getExtents(java.util.function.Consumer<org.joml.Vector3fc> output) {
        }
        ^///?} else {
        @Override
        public void submit(Argument argument, ItemDisplayContext displayContext, PoseStack poseStack,
                           net.minecraft.client.renderer.SubmitNodeCollector collector,
                           int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
            if (argument == null)
                return;
            poseStack.pushPose();
            if (argument.localTransform() != null)
                poseStack.mulPose(argument.localTransform());
            draw(argument.mesh(), poseStack, collector);
            poseStack.popPose();
        }

        @Override
        public void getExtents(java.util.Set<org.joml.Vector3f> output) {
        }
        //?}

        private static void draw(LiveScreenMesh mesh, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector) {
            try {
                collector.submitCustomGeometry(poseStack,
                        /^$ render_type_import {^/net.minecraft.client.renderer.RenderType/^$}^/.entityTranslucentEmissive(LiveScreenRenderer.WHITE),
                        (pose, buffer) -> LiveScreenRenderer.emit(buffer, pose, mesh));
            } catch (Throwable t) {
                LiveScreenRenderer.fail(t);
            }
        }

        @Override
        public @Nullable Argument extractArgument(ItemStack stack) {
            return null; // unused: LiveScreenItemModel#update hands the argument over directly
        }
    }
}
*///?} else {
public final class LiveScreenItemModel {
    private LiveScreenItemModel() {
    }
}
//?}
