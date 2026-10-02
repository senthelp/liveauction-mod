package com.pathetictry.dragonskull.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * Client-side look for the Wither Skeleton Skull item:
 *  - drawn with the game's own Dragon Head item model (exact size/angle, hotbar + hand + ground)
 *  - EPIC rarity (light-purple name) like a real Dragon Head
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private <T> void dragonskull$useDragonModel(DataComponentType<? extends T> type, CallbackInfoReturnable<T> cir) {
        if (type != DataComponents.ITEM_MODEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) return;
        ItemStack self = (ItemStack) (Object) this;
        if (self.is(Items.WITHER_SKELETON_SKULL)) {
            cir.setReturnValue((T) (Object) Identifier.withDefaultNamespace("dragon_head"));
        }
    }

    @Inject(method = "getRarity", at = @At("RETURN"), cancellable = true)
    private void dragonskull$epicName(CallbackInfoReturnable<Rarity> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) return;
        ItemStack self = (ItemStack) (Object) this;
        if (self.is(Items.WITHER_SKELETON_SKULL)) {
            cir.setReturnValue(Rarity.EPIC);
        }
    }
}
