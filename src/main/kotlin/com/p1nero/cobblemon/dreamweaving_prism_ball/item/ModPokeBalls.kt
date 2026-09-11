package com.p1nero.cobblemon.dreamweaving_prism_ball.item

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.GuaranteedModifier
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.pokemon.Pokemon
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

    fun registerGuaranteeHandlers() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Consumer { event ->
            applyGuarantees(event.pokeBallEntity.pokeBall, event.pokemon)
        })
        // `caughtBall` is also assigned outside of a capture, without POKEMON_CAPTURED ever firing:
        // fossil revival and shed evolution take the ball from the player's hands/inventory, and
        // `pokeball=` properties (commands, datapacks, NBT) and save conversions assign it directly.
        // Re-assert the guarantees as soon as such a Pokemon joins the party and again every time it
        // is sent out of its ball, so it is never left without them.
        CobblemonEvents.POKEMON_GAINED.subscribe(Consumer { event ->
            applyGuarantees(event.pokemon.caughtBall, event.pokemon)
        })
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Consumer { event ->
            applyGuarantees(event.pokemon.caughtBall, event.pokemon)
        })
    }

    /**
     * Brings [pokemon] up to the guarantees of the ball it was caught with. Only missing
     * properties are written, so this is safe to run on every send-out.
     */
    private fun applyGuarantees(ball: PokeBall?, pokemon: Pokemon) {
        when (ball?.name) {
            PRISM_ID -> ensureShiny(pokemon)
            DREAMWEAVING_PRISM_ID -> {
                ensureShiny(pokemon)
                ensurePerfectIVs(pokemon)
            }
        }
    }

    private fun ensureShiny(pokemon: Pokemon) {
        if (!pokemon.shiny) {
            pokemon.shiny = true
        }
    }

    private fun ensurePerfectIVs(pokemon: Pokemon) {
        PERMANENT_STATS.forEach { stat ->
            if (pokemon.ivs[stat] != PERFECT_IV) {
                pokemon.setIV(stat, PERFECT_IV)
            }
        }
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

    private const val PERFECT_IV = 31

    private val PERMANENT_STATS = listOf(
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED
    )
}
