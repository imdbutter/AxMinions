package com.artillexstudios.axminions.integrations.island

import com.artillexstudios.axminions.api.integrations.types.IslandIntegration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.UUID

class UltimateSkyBlockIntegration : IslandIntegration {

    override fun getIslandAt(location: Location): String {
        val island = islandAt(location) ?: return ""
        return runCatching { island.javaClass.getMethod("getId").invoke(island).toString() }.getOrDefault("")
    }

    override fun getExtra(location: Location): Int {
        return 0
    }

    override fun handleBlockBreak(block: Block) {
        if (!block.hasMetadata("generator_block")) return
        val island = islandAt(block.location) ?: return
        val owner = onlineOwner(island)
        val target = block.type.name
        runCatching {
            val skyBlock = skyBlockPlugin() ?: return
            val questManager = skyBlock.javaClass.getMethod("getQuestManager").invoke(skyBlock)
            val actionPointsManager = skyBlock.javaClass.getMethod("getActionPointsManager").invoke(skyBlock)

            questManager.javaClass.getMethod(
                "advance",
                Player::class.java,
                island.javaClass,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ).invoke(questManager, owner, island, "generator-block", target, 1)

            questManager.javaClass.getMethod(
                "advance",
                Player::class.java,
                island.javaClass,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ).invoke(questManager, owner, island, "break-block", target, 1)

            actionPointsManager.javaClass.getMethod(
                "reward",
                Player::class.java,
                island.javaClass,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ).invoke(actionPointsManager, owner, island, "generator-block", target, 1)
        }
    }

    override fun register() {
    }

    private fun islandAt(location: Location): Any? {
        return runCatching {
            val skyBlock = skyBlockPlugin() ?: return null
            val manager = skyBlock.javaClass.getMethod("getIslandManager").invoke(skyBlock) ?: return null
            manager.javaClass.getMethod("getIslandByLocation", Location::class.java).invoke(manager, location)
        }.getOrNull()
    }

    private fun onlineOwner(island: Any): Player? {
        return runCatching {
            val uuid = island.javaClass.getMethod("getOwnerUUID").invoke(island) as? UUID ?: return null
            Bukkit.getPlayer(uuid)
        }.getOrNull()
    }

    private fun skyBlockPlugin() = Bukkit.getPluginManager().getPlugin("UltimateSkyBlock")?.takeIf { it.isEnabled }
}
