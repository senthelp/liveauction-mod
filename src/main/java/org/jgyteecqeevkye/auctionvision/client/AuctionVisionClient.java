package org.jgyteecqeevkye.auctionvision.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class AuctionVisionClient implements ClientModInitializer {
    private static volatile boolean pendingOpen = false;

    @Override
    public void onInitializeClient() {
        // Client commands: handled locally, never sent to the server.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(ClientCommands.literal("auction").executes(ctx -> {
                pendingOpen = true; // open next tick so the closing chat screen can't override it
                return 1;
            }));
            // /cancel closes the card (and ends the auction if it is still running)
            dispatcher.register(ClientCommands.literal("cancel").executes(ctx -> {
                AuctionState.dismiss();
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pendingOpen) {
                pendingOpen = false;
                Minecraft.getInstance().setScreen(new AuctionMenuScreen());
            }
        });
    }
}
