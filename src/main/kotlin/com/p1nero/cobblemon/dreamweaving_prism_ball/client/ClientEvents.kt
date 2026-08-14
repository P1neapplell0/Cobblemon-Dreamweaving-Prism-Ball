package com.p1nero.cobblemon.dreamweaving_prism_ball.client

import com.p1nero.cobblemon.dreamweaving_prism_ball.DreamweavingPrismBallMod
import com.p1nero.cobblemon.dreamweaving_prism_ball.item.ModPokeBalls
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent

@EventBusSubscriber(modid = DreamweavingPrismBallMod.ID, value = [Dist.CLIENT])
object ClientEvents {
    @SubscribeEvent
    fun onItemTooltip(event: ItemTooltipEvent) {
        val path = when (event.itemStack.item) {
            ModPokeBalls.PRISM_BALL.get() -> "prism_ball"
            ModPokeBalls.DREAMWEAVING_PRISM_BALL.get() -> "dreamweaving_prism_ball"
            else -> return
        }

        event.toolTip.add(Component.translatable("item.${DreamweavingPrismBallMod.ID}.$path.tooltip").withStyle(ChatFormatting.GRAY))
    }
}
