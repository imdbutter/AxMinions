package com.artillexstudios.axminions.minions.utils

import com.artillexstudios.axminions.api.minions.Minion
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt

object MinionLoot {
    fun rollExtraDrops(minion: Minion, drops: Iterable<ItemStack>): List<ItemStack> {
        val chance = normalizedChance(
            if (minion.getType().hasBonusLootUpgrades()) {
                minion.getType().getBonusLootDouble("bonus-loot-chance", minion.getBonusLootLevel())
            } else {
                minion.getType().getDouble("bonus-loot-chance", minion.getLevel())
            }
        )
        val multiplier = if (minion.getType().hasBonusLootUpgrades()) {
            minion.getType().getBonusLootDouble("bonus-loot-multiplier", minion.getBonusLootLevel())
        } else {
            minion.getType().getDouble("bonus-loot-multiplier", minion.getLevel())
        }
        if (chance <= 0.0 || multiplier <= 1.0) return emptyList()
        if (ThreadLocalRandom.current().nextDouble() > chance) return emptyList()

        val extraMultiplier = multiplier - 1.0
        val extraDrops = ArrayList<ItemStack>()
        drops.forEach { item ->
            if (item.type.isAir || item.amount <= 0) return@forEach
            val amount = (item.amount * extraMultiplier).roundToInt().coerceAtLeast(1)
            extraDrops.addAll(splitItem(item, amount))
        }
        return extraDrops
    }

    fun countItems(drops: Iterable<ItemStack>): Int {
        var amount = 0
        drops.forEach {
            amount += it.amount
        }
        return amount
    }

    private fun normalizedChance(chance: Double): Double {
        if (chance <= 0.0) return 0.0
        return (if (chance > 1.0) chance / 100.0 else chance).coerceAtMost(1.0)
    }

    private fun splitItem(template: ItemStack, amount: Int): List<ItemStack> {
        val items = ArrayList<ItemStack>()
        var remaining = amount
        val maxStackSize = template.type.maxStackSize.coerceAtLeast(1)
        while (remaining > 0) {
            val item = template.clone()
            item.amount = remaining.coerceAtMost(maxStackSize)
            items.add(item)
            remaining -= item.amount
        }
        return items
    }
}
