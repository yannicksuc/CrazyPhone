package fr.lordfinn.crazyphone.item.model;

import software.bernie.geckolib.model.GeoModel;

import net.minecraft.resources.ResourceLocation;

import fr.lordfinn.crazyphone.item.CrazyPhoneItem;

public class CrazyPhoneItemModel extends GeoModel<CrazyPhoneItem> {
    @Override
    public ResourceLocation getAnimationResource(CrazyPhoneItem animatable) {
        return ResourceLocation.parse("crazyphone:animations/crazyphone.animation.json");
    }

    @Override
    public ResourceLocation getModelResource(CrazyPhoneItem animatable) {
        return ResourceLocation.parse("crazyphone:geo/crazyphone.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CrazyPhoneItem animatable) {
        return ResourceLocation.parse("crazyphone:textures/item/crazyphone.png");
    }
}
