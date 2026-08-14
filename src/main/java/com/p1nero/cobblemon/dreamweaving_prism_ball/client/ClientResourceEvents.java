package com.p1nero.cobblemon.dreamweaving_prism_ball.client;

import com.p1nero.cobblemon.dreamweaving_prism_ball.client.render.PrismBallColorPalette;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class ClientResourceEvents {
    private ClientResourceEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientResourceEvents::registerReloadListeners);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PrismBallColorPalette.INSTANCE);
    }
}
