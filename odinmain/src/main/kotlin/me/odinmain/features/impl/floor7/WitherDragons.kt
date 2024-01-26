package me.odinmain.features.impl.floor7

import me.odinmain.events.impl.ChatPacketEvent
import me.odinmain.events.impl.ReceivePacketEvent
import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.impl.floor7.DragonBoxes.renderBoxes
import me.odinmain.features.impl.floor7.DragonDeathCheck.dragonJoinWorld
import me.odinmain.features.impl.floor7.DragonDeathCheck.dragonLeaveWorld
import me.odinmain.features.impl.floor7.DragonDeathCheck.lastDragonDeath
import me.odinmain.features.impl.floor7.DragonDeathCheck.onChatPacket
import me.odinmain.features.impl.floor7.DragonHealth.renderHP
import me.odinmain.features.impl.floor7.DragonTimer.renderTime
import me.odinmain.features.impl.floor7.DragonTimer.updateTime
import me.odinmain.features.settings.Setting.Companion.withDependency
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.features.settings.impl.DualSetting
import me.odinmain.features.settings.impl.HudSetting
import me.odinmain.features.settings.impl.NumberSetting
import me.odinmain.ui.clickgui.util.ColorUtil.withAlpha
import me.odinmain.ui.hud.HudElement
import me.odinmain.utils.noControlCodes
import me.odinmain.utils.render.Color
import me.odinmain.utils.render.gui.Fonts
import me.odinmain.utils.render.gui.getTextWidth
import me.odinmain.utils.render.gui.roundedRectangle
import me.odinmain.utils.render.gui.text
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.max


object WitherDragons : Module(
    "Wither Dragons",
    description = "Displays a timer for when M7 dragons spawn.",
    category = Category.FLOOR7
) {

    private val dragonTimer: Boolean by BooleanSetting("Dragon Timer", true, description = "Displays a timer for when M7 dragons spawn.")
    val textScale: Float by NumberSetting(name = "Text Scale", default = 0.8f, min = 0.1f, max = 5f, increment = 0.1f).withDependency { dragonTimer}
    private val timerBackground: Boolean by BooleanSetting("HUD Timer Background", true, description = "Displays a background for the timer.").withDependency { dragonTimer && hud.displayToggle }

    private val dragonBoxes: Boolean by BooleanSetting("Dragon Boxes", true, description = "Displays boxes for where M7 dragons spawn.")
    val lineThickness: Float by NumberSetting("Line Width", 2f, 1.0, 5.0, 0.5).withDependency { dragonBoxes }

    val sendNotification: Boolean by BooleanSetting("Send Dragon Confirmation", true, description = "Sends a confirmation message when a dragon dies.")
    val sendTime: Boolean by BooleanSetting("Send Dragon Time Alive", true, description = "Sends a message when a dragon dies with the time it was alive.")
    val sendSpawning: Boolean by BooleanSetting("Send Dragon Spawning", true, description = "Sends a message when a dragon is spawning.")
    val sendSpawned: Boolean by BooleanSetting("Send Dragon Spawned", true, description = "Sends a message when a dragon has spawned.")

    private val dragonHealth: Boolean by BooleanSetting("Dragon Health", true, description = "Displays the health of M7 dragons.")

    val dragonPriorityToggle: Boolean by BooleanSetting("Dragon Priority", false, description = "Displays the priority of dragons spawning.")
    val configPower: Double by NumberSetting("Normal Power", 21.0, 10.0, 29.0, description = "Power needed to split.").withDependency { dragonPriorityToggle }
    val configEasyPower: Double by NumberSetting("Easy Power", 19.0, 10.0, 29.0, description = "Power needed when its Purple and another dragon.").withDependency { dragonPriorityToggle }
    val configSoloDebuff: Boolean by DualSetting("Purple Solo Debuff", "Tank", "Healer", true, description = "Displays the debuff of the config.The class that solo debuffs purple, the other class helps b/m.").withDependency { dragonPriorityToggle }
    val soloDebuffOnAll: Boolean by BooleanSetting("Solo Debuff on All Splits", true, description = "Same as Purple Solo Debuff but for all dragons (A will only have 1 debuff).").withDependency { dragonPriorityToggle }
    val paulBuff: Boolean by BooleanSetting("Paul Buff", false, description = "Multiplies the power in your run by 1.25").withDependency { dragonPriorityToggle }
    //private val tracer: Boolean by BooleanSetting("Tracer", default = false, description = "Draws a line from your position to the dragon").withDependency {dragPrioSpawnToggle}
    //private val tracerWidth: Int by NumberSetting("Tracer Width", default = 5, min = 1, max = 20).withDependency { tracer }

    private val hud: HudElement by HudSetting("Display", 10f, 10f, 1f, true) {
        if (it) {
            if (timerBackground) roundedRectangle(1f, 1f, getTextWidth("Purple spawning in 4500ms", 19f, Fonts.REGULAR), 32f, Color.DARK_GRAY.withAlpha(.75f), 3f)

            text("§5Purple spawning in §a4500ms", 2f, 10f, Color.WHITE, 16f, Fonts.REGULAR)
            text("§cRed spawning in §e1200ms", 2f, 26f, Color.WHITE,16f, Fonts.REGULAR)

            max(
                getTextWidth("Purple spawning in 4500ms", 16f, Fonts.REGULAR),
                getTextWidth("Red spawning in 1200ms", 16f, Fonts.REGULAR)
            ) + 2f to 33f
        } else if (DragonTimer.toRender.size != 0) {
            var width = 0f
            DragonTimer.toRender.forEachIndexed { index, triple ->
                text(triple.first, 1f, 9f + index * 17f, Color.WHITE,16f, Fonts.REGULAR)
                width = max(width, getTextWidth(triple.first.noControlCodes, 19f, Fonts.REGULAR))
            }
            roundedRectangle(1f, 1f, width + 2f, DragonTimer.toRender.size * 16f, Color.DARK_GRAY.withAlpha(.75f), 4f)
            width to DragonTimer.toRender.size * 17f
        } else 0f to 0f
    }

    val redPB = +NumberSetting("Panes PB", 1000.0, increment = 0.01, hidden = true)
    val orangePB = +NumberSetting("Color PB", 1000.0, increment = 0.01, hidden = true)
    val greenPB = +NumberSetting("Numbers PB", 1000.0, increment = 0.01, hidden = true)
    val bluePB = +NumberSetting("Melody PB", 1000.0, increment = 0.01, hidden = true)
    val purplePB = +NumberSetting("Starts With PB", 1000.0, increment = 0.01, hidden = true)

    private val shouldWork = DungeonUtils.getPhase() != 5

    @SubscribeEvent
    fun onReceivePacket(event: ReceivePacketEvent) {
        if (shouldWork) return
        handleSpawnPacket(event)
    }
    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        WitherDragonsEnum.entries.forEach {
            it.particleSpawnTime = 0L
            it.timesSpawned = 0
        }
        DragonTimer.toRender = ArrayList()
        lastDragonDeath = ""
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (shouldWork) return

        if (dragonTimer) {
            updateTime()
            renderTime()
        }
        if (dragonBoxes) renderBoxes()
        if (dragonHealth) renderHP()
       // if (tracer) renderTracerPriority(event, tracerWidth)
    }

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinWorldEvent) {
        if (shouldWork) return
        dragonJoinWorld(event)
    }

    @SubscribeEvent
    fun onEntityLeave(event: LivingDeathEvent) {
        if (shouldWork) return
        dragonLeaveWorld(event)
    }

    @SubscribeEvent
    fun onChat(event: ChatPacketEvent) {
        if (shouldWork) return
        onChatPacket(event)
    }

}