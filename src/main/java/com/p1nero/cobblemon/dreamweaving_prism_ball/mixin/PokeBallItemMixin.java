package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.p1nero.cobblemon.dreamweaving_prism_ball.item.ModPokeBalls;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PokeBallItem.class)
public abstract class PokeBallItemMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/item/CobblemonItem;<init>(Lnet/minecraft/world/item/Item$Properties;)V",
                    remap = false
            ),
            index = 0,
            remap = false
    )
    private static Item.Properties makeEpic(Item.Properties properties) {
        if (ModPokeBalls.INSTANCE.getEPIC_ITEM_CONSTRUCTION().get()) {
            return properties.fireResistant().rarity(Rarity.EPIC);
        }
        return properties;
    }
}
