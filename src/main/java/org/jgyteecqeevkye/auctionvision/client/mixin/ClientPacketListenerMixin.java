package org.jgyteecqeevkye.auctionvision.client.mixin;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgyteecqeevkye.auctionvision.client.AuctionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvents;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    private static final Logger AV_LOG = LoggerFactory.getLogger("auctionvision");

    // Matches "<name> paid you $1,500" / "<name> sent you $150k" (space after $ allowed)
    private static final Pattern PAT = Pattern.compile(
        "(\\w{1,16}) (?:paid|sent) you \\$\\s*([\\d,]+(?:\\.\\d+)?)\\s*([kKmMbB])?",
        Pattern.CASE_INSENSITIVE);

    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void auctionvision$onMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!AuctionState.active) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return; // handler is entered on the netty thread first

        String text = packet.content().getString();
        if (text.indexOf('$') >= 0) AV_LOG.info("[auction] saw: {}", text); // debug aid

        Matcher m = PAT.matcher(text);
        if (!m.find()) return;

        BigDecimal amt = new BigDecimal(m.group(2).replace(",", ""));
        String suf = m.group(3);
        if (suf != null) {
            amt = amt.multiply(switch (Character.toLowerCase(suf.charAt(0))) {
                case 'k' -> BigDecimal.valueOf(1_000L);
                case 'm' -> BigDecimal.valueOf(1_000_000L);
                default -> BigDecimal.valueOf(1_000_000_000L);
            });
        }

        if (AuctionState.offer(m.group(1), amt.longValue())) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
        }
    }
}
