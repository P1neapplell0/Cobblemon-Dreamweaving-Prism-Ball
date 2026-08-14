package com.p1nero.cobblemon.dreamweaving_prism_ball.client.model

import com.cobblemon.mod.common.client.render.models.blockbench.pokeball.PokeBallModel
import net.minecraft.client.model.geom.ModelPart

/** Uses the stock Poke Ball poses while resolving animations from a ball-specific group. */
class PrismBallModel(root: ModelPart, val animationGroup: String) : PokeBallModel(root) {
    override fun registerPoses() {
        super.registerPoses()

        midair.animations[0] = bedrock(animationGroup, "throw")
        shut.animations[0] = bedrock(animationGroup, "shut_idle")
        open.animations[0] = bedrock(animationGroup, "open_idle")

        shut.transitions[open.poseName] = { _, _ ->
            bedrockStateful(animationGroup, "open")
        }
        open.transitions[shut.poseName] = { _, _ ->
            bedrockStateful(animationGroup, "shut")
        }
        midair.transitions[open.poseName] = { _, _ ->
            bedrockStateful(animationGroup, "open")
        }
    }

    companion object {
        const val PRISM_ANIMATION_GROUP = "prism_ball"
        const val DREAMWEAVING_ANIMATION_GROUP = "dreamweaving_prism_ball"
    }
}
