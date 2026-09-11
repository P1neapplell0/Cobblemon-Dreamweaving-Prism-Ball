package com.p1nero.cobblemon.dreamweaving_prism_ball

import com.p1nero.cobblemon.dreamweaving_prism_ball.client.ClientResourceEvents
import com.p1nero.cobblemon.dreamweaving_prism_ball.item.ModPokeBalls
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.common.Mod
import org.slf4j.LoggerFactory

@Mod(DreamweavingPrismBallMod.ID)
class DreamweavingPrismBallMod(modBus: IEventBus) {
    init {
        ModPokeBalls.ITEMS.register(modBus)
        ModPokeBalls.registerGuaranteeHandlers()
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientResourceEvents.register(modBus)
        }
        LOGGER.info("Dreamweaving Prism Ball initialized")
    }

    companion object {
        const val ID = "cobblemon_dreamweaving_prism_ball"
        val LOGGER = LoggerFactory.getLogger(ID)
    }
}
