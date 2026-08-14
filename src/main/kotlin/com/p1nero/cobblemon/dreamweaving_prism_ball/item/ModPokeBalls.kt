package com.p1nero.cobblemon.dreamweaving_prism_ball.item

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.GuaranteedModifier
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import com.p1nero.cobblemon.dreamweaving_prism_ball.DreamweavingPrismBallMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Consumer
import java.util.function.Supplier

object ModPokeBalls {
    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(DreamweavingPrismBallMod.ID)
    val EPIC_ITEM_CONSTRUCTION = ThreadLocal.withInitial { false }

    private val PRISM_ID = id("prism_ball")
    private val DREAMWEAVING_PRISM_ID = id("dreamweaving_prism_ball")

    val PRISM = createBall(PRISM_ID)
    val DREAMWEAVING_PRISM = createBall(DREAMWEAVING_PRISM_ID)

    val PRISM_BALL: DeferredHolder<Item, PokeBallItem> = registerItem("prism_ball", PRISM)
    val DREAMWEAVING_PRISM_BALL: DeferredHolder<Item, PokeBallItem> =
        registerItem("dreamweaving_prism_ball", DREAMWEAVING_PRISM)

    init {
        registerWithCobblemon(PRISM)
        registerWithCobblemon(DREAMWEAVING_PRISM)
    }

    fun registerCaptureEffects() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Consumer { event ->
            when (event.pokeBallEntity.pokeBall.name) {
                PRISM_ID -> event.pokemon.shiny = true
                DREAMWEAVING_PRISM_ID -> {
                    event.pokemon.shiny = true
                    PERMANENT_STATS.forEach { stat -> event.pokemon.setIV(stat, 31) }
                }
            }
        })
    }

    private fun createBall(identifier: ResourceLocation) = PokeBall(
        identifier,
        GuaranteedModifier(),
        emptyList(),
        0.8F,
        identifier,
        ResourceLocation.fromNamespaceAndPath(identifier.namespace, "item/${identifier.path}_model"),
        1.25F,
        false
    )

    private fun registerItem(name: String, ball: PokeBall): DeferredHolder<Item, PokeBallItem> =
        ITEMS.register(name, Supplier {
            EPIC_ITEM_CONSTRUCTION.set(true)
            try {
                PokeBallItem(ball).also { item ->
                    setBallItem(ball, item)
                    CobblemonItems.pokeBalls.add(item)
                }
            } finally {
                EPIC_ITEM_CONSTRUCTION.set(false)
            }
        })

    @Suppress("UNCHECKED_CAST")
    private fun registerWithCobblemon(ball: PokeBall) {
        val defaultsField = PokeBalls::class.java.getDeclaredField("defaults")
        defaultsField.isAccessible = true
        val defaults = defaultsField.get(null) as MutableMap<ResourceLocation, PokeBall>
        check(defaults.putIfAbsent(ball.name, ball) == null) {
            "A Poke Ball named ${ball.name} is already registered"
        }
    }

    private fun setBallItem(ball: PokeBall, item: PokeBallItem) {
        val setter = PokeBall::class.java.getMethod("setItem\$common", PokeBallItem::class.java)
        setter.invoke(ball, item)
    }

    private fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(DreamweavingPrismBallMod.ID, path)

    private val PERMANENT_STATS = listOf(
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED
    )
}
