package me.odinmain.features.impl.dungeon

import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.settings.impl.StringSetting
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.skyblock.partyMessage
import net.minecraft.entity.monster.EntityZombie
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object MimicMessage : Module(
    "Mimic Message",
    description = "Send message in party chat when mimic is killed.",
    category = Category.DUNGEON,
    tag = TagType.NEW
) {
    private val mimicMessage: String by StringSetting("Mimic Message", "Mimic Killed", 40, description = "Message sent when mimic is detected as killed")

    private var mimicKilled = false

    @SubscribeEvent
    fun onEntityDeath(event: LivingDeathEvent) {
        if (!DungeonUtils.inDungeons || event.entity !is EntityZombie || mimicKilled) return
        val entity = event.entity as EntityZombie
        if (entity.isChild && entity.inventory.drop(1).all { it == null }) {
            mimicKilled = true
            partyMessage(mimicMessage)
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load?) {
        mimicKilled = false
    }
}