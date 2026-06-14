package com.artillexstudios.axminions.api.minions.miniontype

import com.artillexstudios.axapi.config.Config
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings
import com.artillexstudios.axapi.utils.ItemBuilder
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.utils.Keys
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

abstract class MinionType(private val name: String, defaults: InputStream, @Suppress("UNUSED_PARAMETER") private val autoUpdateConfig: Boolean) {
    val faces = arrayOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)
    private val defaultBytes = defaults.use { it.readBytes() }
    private lateinit var config: Config

    constructor(name: String, defaults: InputStream) : this(name, defaults, false)

    fun load() {
        config = loadConfig()

        if (!isValidConfig(config)) {
            overwriteWithDefaults(configFile())
            config = loadConfig()
        }

        if (!isValidConfig(config)) {
            AxMinionsAPI.INSTANCE.getAxMinionsInstance().logger.severe("Failed to load minion config for $name from jar defaults!")
        }

        AxMinionsAPI.INSTANCE.getDataHandler().insertType(this)
    }

    fun getName(): String {
        return this.name
    }

    open fun onToolDirty(minion: Minion) {

    }

    open fun shouldRun(minion: Minion): Boolean {
        return true
    }

    fun tick(minion: Minion) {
        if (!com.artillexstudios.axminions.api.config.Config.WORK_WHEN_OWNER_OFFLINE() && !minion.isOwnerOnline()) return
        if (!shouldRun(minion)) return

        minion.resetAnimation()
        run(minion)
    }

    fun getItem(level: Int = 1, actions: Long = 0, charge: Long = 0, bonusLootLevel: Int = 1): ItemStack {
        val builder = ItemBuilder.create(
            getItemSection(),
            Placeholder.unparsed("level", level.toString()),
            Placeholder.unparsed("actions", actions.toString()),
            Placeholder.unparsed("bonus_loot_level", bonusLootLevel.toString())
        )
        val item = builder.clonedGet()
        val meta = item.itemMeta!!
        meta.persistentDataContainer.set(Keys.MINION_TYPE, PersistentDataType.STRING, name)
        meta.persistentDataContainer.set(Keys.LEVEL, PersistentDataType.INTEGER, level)
        meta.persistentDataContainer.set(Keys.STATISTICS, PersistentDataType.LONG, actions)
        meta.persistentDataContainer.set(Keys.CHARGE, PersistentDataType.LONG, charge)
        meta.persistentDataContainer.set(Keys.BONUS_LOOT_LEVEL, PersistentDataType.INTEGER, bonusLootLevel)
        item.itemMeta = meta
        return item
    }

    private fun getItemSection(): Section {
        config.backingDocument?.getSection("item")?.let {
            return it
        }

        overwriteWithDefaults(configFile())
        config = loadConfig()

        return config.backingDocument?.getSection("item")
            ?: throw IllegalStateException("Minion config $name.yml does not contain an item section")
    }

    fun getConfig(): Config {
        return this.config
    }

    fun reloadConfig(): Boolean {
        runCatching { config.reload() }

        if (isValidConfig(config)) {
            return true
        }

        val file = configFile()
        overwriteWithDefaults(file)
        config = loadConfig()
        return isValidConfig(config)
    }

    fun getString(key: String, level: Int): String {
        return get(key, level, "---", String::class.java)!!
    }

    fun getDouble(key: String, level: Int): Double {
        return get(key, level, 0.0, Double::class.java)!!
    }

    fun getBonusLootDouble(key: String, level: Int): Double {
        return get("bonus-loot-upgrades", key, level, 0.0, Double::class.java)!!
    }

    fun getLong(key: String, level: Int): Long {
        return get(key, level, 0, Long::class.java)!!
    }

    fun getBonusLootLong(key: String, level: Int): Long {
        return get("bonus-loot-upgrades", key, level, 0, Long::class.java)!!
    }

    fun getSection(key: String, level: Int): Section? {
        return get(key, level, null, Section::class.java)
    }

    private fun <T> get(key: String, level: Int, defaultValue: T?, clazz: Class<T>): T? {
        return get("upgrades", key, level, defaultValue, clazz)
    }

    private fun <T> get(section: String, key: String, level: Int, defaultValue: T?, clazz: Class<T>): T? {
        var n = defaultValue

        if (!config.backingDocument.isSection(section)) {
            return n
        }

        config.getSection(section).getRoutesAsStrings(false).forEach {
            if (it.toInt() > level) {
                return n
            }

            if (config.backingDocument.getAsOptional("$section.$it.$key", clazz).isEmpty) return@forEach

            n = config.get("$section.$it.$key")
        }

        return n
    }

    fun hasReachedMaxLevel(minion: Minion): Boolean {
        return !config.backingDocument.isSection("upgrades.${minion.getLevel() + 1}")
    }

    fun hasBonusLootUpgrades(): Boolean {
        return config.backingDocument.isSection("bonus-loot-upgrades")
    }

    fun hasReachedMaxBonusLootLevel(minion: Minion): Boolean {
        return !config.backingDocument.isSection("bonus-loot-upgrades.${minion.getBonusLootLevel() + 1}")
    }

    fun hasChestOnSide(block: Block): Boolean {
        for (face in faces) {
            if (block.getRelative(face).type == Material.CHEST) {
                return true
            }
        }

        return false
    }

    abstract fun run(minion: Minion)

    private fun loadConfig(): Config {
        val file = configFile()
        file.parentFile?.mkdirs()
        writeDefaultsIfMissing(file)

        return Config(
            file,
            ByteArrayInputStream(defaultBytes),
            GeneralSettings.builder().setUseDefaults(false).build(),
            LoaderSettings.DEFAULT,
            DumperSettings.DEFAULT,
            UpdaterSettings.DEFAULT
        )
    }

    private fun writeDefaultsIfMissing(file: File) {
        if (file.exists()) return

        file.writeBytes(defaultBytes)
    }

    private fun configFile(): File {
        return File(File(AxMinionsAPI.INSTANCE.getAxMinionsDataFolder(), "minions"), "$name.yml")
    }

    private fun isValidConfig(config: Config): Boolean {
        return runCatching {
            val document = config.backingDocument ?: return false
            document.isSection("item")
        }.getOrDefault(false)
    }

    private fun overwriteWithDefaults(file: File) {
        file.parentFile?.mkdirs()
        file.writeBytes(defaultBytes)
    }
}
