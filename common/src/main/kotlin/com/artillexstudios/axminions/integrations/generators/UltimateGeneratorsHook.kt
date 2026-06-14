package com.artillexstudios.axminions.integrations.generators

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method

object UltimateGeneratorsHook {
    private const val PLUGIN_NAME = "UltimateGenerators"
    private const val GENERATED_BLOCK_METADATA = "generator_block"

    private var cachedPlugin: Plugin? = null
    private var cachedManager: Any? = null
    private var cachedDatabaseManager: Any? = null
    private var isGeneratorMethod: Method? = null
    private var getGeneratorMethod: Method? = null
    private var addBlocksBrokenMethod: Method? = null
    private var saveGeneratorMethod: Method? = null

    fun isAvailable(): Boolean {
        return runCatching { manager() != null }.getOrDefault(false)
    }

    fun isGenerator(location: Location): Boolean {
        return runCatching {
            val manager = manager() ?: return false
            val method = isGeneratorMethod ?: return false
            (method.invoke(manager, location) as? Boolean) == true
        }.getOrDefault(false)
    }

    fun isGeneratedBlock(location: Location): Boolean {
        return runCatching {
            val block = location.block
            if (!block.hasMetadata(GENERATED_BLOCK_METADATA)) return false
            getGenerator(location.clone().subtract(0.0, 1.0, 0.0)) != null
        }.getOrDefault(false)
    }

    fun recordGeneratedBlockBreak(block: Block, amount: Int = 1) {
        runCatching {
            val plugin = plugin() ?: return
            val generator = getGenerator(block.location.clone().subtract(0.0, 1.0, 0.0)) ?: return

            block.removeMetadata(GENERATED_BLOCK_METADATA, plugin)
            addBlocksBrokenMethod?.invoke(generator, amount)
            saveGeneratorMethod?.invoke(cachedDatabaseManager, generator)
        }
    }

    private fun getGenerator(location: Location): Any? {
        val manager = manager() ?: return null
        val method = getGeneratorMethod ?: return null
        return method.invoke(manager, location)
    }

    private fun manager(): Any? {
        val plugin = plugin() ?: return null
        if (cachedManager != null && cachedPlugin === plugin) return cachedManager

        val manager = plugin.javaClass.getMethod("getGeneratorManager").invoke(plugin) ?: return null
        val databaseManager = plugin.javaClass.getMethod("getDatabaseManager").invoke(plugin) ?: return null

        cachedPlugin = plugin
        cachedManager = manager
        cachedDatabaseManager = databaseManager
        isGeneratorMethod = manager.javaClass.getMethod("isGenerator", Location::class.java)
        getGeneratorMethod = manager.javaClass.getMethod("getGenerator", Location::class.java)
        addBlocksBrokenMethod = Class.forName("me.dbutter.ultimateGenerators.generator.GeneratorBlock")
            .getMethod("addBlocksBroken", Int::class.javaPrimitiveType!!)
        saveGeneratorMethod = databaseManager.javaClass.interfaces
            .firstOrNull { it.name == "me.dbutter.ultimateGenerators.database.DatabaseManager" }
            ?.getMethod("saveGenerator", Class.forName("me.dbutter.ultimateGenerators.generator.GeneratorBlock"))
            ?: databaseManager.javaClass.getMethod(
                "saveGenerator",
                Class.forName("me.dbutter.ultimateGenerators.generator.GeneratorBlock")
            )

        return cachedManager
    }

    private fun plugin(): Plugin? {
        val plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) ?: return null
        if (!plugin.isEnabled) return null
        return plugin
    }
}
