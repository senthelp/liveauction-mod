package com.pathetictry.auctionvision.client.mixin;

import com.pathetictry.auctionvision.client.AuctionHudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void auctionvision$card(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        AuctionHudRenderer.render(g);
    }
}
