package me.odinmain.utils.skyblock.dungeon

import me.odinmain.OdinMain.mc
import me.odinmain.config.DungeonWaypointConfig
import me.odinmain.events.impl.EnteredDungeonRoomEvent
import me.odinmain.features.impl.dungeon.DungeonWaypoints.DungeonWaypoint
import me.odinmain.features.impl.dungeon.DungeonWaypoints.WaypointCategory
import me.odinmain.features.impl.dungeon.DungeonWaypoints.toVec3
import me.odinmain.features.impl.dungeon.LeapMenu
import me.odinmain.features.impl.dungeon.LeapMenu.odinSorting
import me.odinmain.utils.*
import me.odinmain.utils.clock.Executor
import me.odinmain.utils.clock.Executor.Companion.register
import me.odinmain.utils.render.Color
import me.odinmain.utils.skyblock.*
import me.odinmain.utils.skyblock.LocationUtils.currentDungeon
import me.odinmain.utils.skyblock.PlayerUtils.posY
import me.odinmain.utils.skyblock.dungeon.tiles.Room
import me.odinmain.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.block.BlockSkull
import net.minecraft.block.state.IBlockState
import net.minecraft.client.network.NetworkPlayerInfo
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.tileentity.TileEntitySkull
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.world.WorldSettings
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent

object DungeonUtils {

    inline val inDungeons
        get() =
            LocationUtils.inSkyblock && currentDungeon != null

    inline val inBoss
        get() =
            currentDungeon?.inBoss ?: false

    inline val secretCount get() = currentDungeon?.secretCount ?: 0
    inline val cryptsCount get() = currentDungeon?.cryptsCount ?: 0
    inline val deathCount get() = currentDungeon?.deathCount ?: 0

    data class FullRoom(
        val room: Room,
        var clayPos: BlockPos,
        val positions: List<ExtraRoom>,
        var categories: List<WaypointCategory>
    )

    data class FullRegion(
        val region: Island,
        var categories: List<WaypointCategory>
    )

    data class ExtraRoom(val x: Int, val z: Int, val core: Int)

    private var lastRoomPos: Pair<Int, Int> = Pair(0, 0)
    var currentRoom: FullRoom? = null
    val currentRoomName get() = currentRoom?.room?.data?.name ?: "Unknown"

    var currentRegion: FullRegion? = null
    val currentRegionName get() = currentRegion?.region?.name ?: "Unknown"

    private const val WITHER_ESSENCE_ID = "26bb1a8d-7c66-31c6-82d5-a9c04c94fb02"
    private const val REDSTONE_KEY = "edb0155f-379c-395a-9c7d-1b6005987ac8"

    private const val ROOM_SIZE = 32
    private const val START_X = -185
    private const val START_Z = -185

    /**
     * Checks if the current dungeon floor number matches any of the specified options.
     *
     * This function iterates through the provided floor number options and returns true if the current dungeon floor
     * matches any of them. Otherwise, it returns false.
     *
     * @param options The floor number options to compare with the current dungeon floor.
     * @return `true` if the current dungeon floor matches any of the specified options, otherwise `false`.
     */
    fun isFloor(vararg options: Int): Boolean {
        return options.any { it == currentDungeon?.floor?.floorNumber }
    }

    /**
     * Determines the phase based on the current dungeon floor and vertical position (y-coordinate).
     *
     * This function calculates the phase of the dungeon based on specific vertical position thresholds and the current floor.
     * The phase indicates the relative vertical position within the dungeon and is used in certain boss-related scenarios.
     *
     * @return The phase as an integer value. Returns `null` if the conditions for determining the phase are not met.
     * - Phase 1: posY > 210
     * - Phase 2: posY > 155
     * - Phase 3: posY > 100
     * - Phase 4: posY > 45
     * - Phase 5: posY <= 45
     */
    fun getPhase(): Island? {
        if (!isFloor(7) || !inBoss) return null

        return when {
            posY > 210 -> Island.M7P1
            posY > 155 -> Island.M7P2
            posY > 100 -> Island.M7P3
            posY > 45 -> Island.M7P4
            else -> Island.M7P5
        }
    }

    @SubscribeEvent
    fun onMove(event: LivingEvent.LivingUpdateEvent) {
        if (mc.theWorld == null /*|| !inDungeons *//*|| inBoss*/ || !event.entity.equals(mc.thePlayer)) return
        if (inDungeons) {
            val xPos = START_X + ((mc.thePlayer.posX + 200) / 32).toInt() * ROOM_SIZE
            val zPos = START_Z + ((mc.thePlayer.posZ + 200) / 32).toInt() * ROOM_SIZE
            if (lastRoomPos.equal(xPos, zPos) && currentRoom != null) return
            lastRoomPos = Pair(xPos, zPos)

            val room = scanRoom(xPos, zPos) ?: return
            val positions = room.let { findRoomTilesRecursively(it.x, it.z, it, mutableSetOf()) }
            currentRoom = FullRoom(room, BlockPos(0, 0, 0), positions, emptyList())
            currentRoom?.let {
                val topLayer = getTopLayerOfRoom(it.positions.first().x, it.positions.first().z)
                it.room.rotation = Rotations.entries.dropLast(1).find { rotation ->
                    it.positions.any { pos ->
                        val blockPos = BlockPos(pos.x + rotation.x, topLayer, pos.z + rotation.z)
                        val isCorrectClay = getBlockIdAt(blockPos) == 159 &&
                                EnumFacing.HORIZONTALS.all { facing ->
                                    getBlockIdAt(
                                        blockPos.add(
                                            facing.frontOffsetX,
                                            0,
                                            facing.frontOffsetZ
                                        )
                                    ).equalsOneOf(159, 0)
                                }
                        if (isCorrectClay) it.clayPos = blockPos
                        return@any isCorrectClay
                    }
                } ?: Rotations.NONE
                devMessage("Found rotation ${it.room.rotation}, clay pos: ${it.clayPos}")
                setRoomWaypoints(it)
                EnteredDungeonRoomEvent(it).postAndCatch()
            }
        } else {
            var island = LocationUtils.currentArea ?: return
            if (island == Island.DungeonBoss || island == Island.M7P1 || island == Island.M7P2 || island == Island.M7P3 || island == Island.M7P4 || island == Island.M7P5) island =
                Island.Dungeon
            currentRegion = FullRegion(island, emptyList())
            currentRegion?.let {
                devMessage("Entered region $island")
                setRegionWaypoints(it)
            }
        }
    }

    /**
     * Sets the waypoints for the current room.
     */
    fun setRoomWaypoints(curRoom: FullRoom) {
        val room = curRoom.room
        curRoom.categories = mutableListOf<WaypointCategory>().apply {
            DungeonWaypointConfig.waypointsRooms[room.data.name]?.let { categories ->
                addAll(categories)

            }
        }
        curRoom.categories.forEach { category ->
            category.waypoints.map { waypoint ->
                val vec = waypoint.toVec3().rotateAroundNorth(room.rotation)
                    .addVec(x = curRoom.clayPos.x, z = curRoom.clayPos.z)
                DungeonWaypoint(
                    vec.xCoord,
                    vec.yCoord,
                    vec.zCoord,
                    waypoint.color,
                    waypoint.filled,
                    waypoint.depth,
                    waypoint.aabb,
                    waypoint.title
                )
            }
        }
    }

    /**
     * Sets the waypoints for the current room.
     */
    fun setRegionWaypoints(curRegion: FullRegion) {
        val region = curRegion.region
        curRegion.categories = mutableListOf<WaypointCategory>().apply {
            DungeonWaypointConfig.waypointsRooms[region.name]?.let { categories ->
                addAll(categories)

            }
        }
    }


    private fun findRoomTilesRecursively(x: Int, z: Int, room: Room, visited: MutableSet<Vec2>): List<ExtraRoom> {
        val tiles = mutableListOf<ExtraRoom>()
        val pos = Vec2(x, z)
        if (visited.contains(pos)) return tiles
        visited.add(pos)
        val core = ScanUtils.getCore(x, z)
        if (room.data.cores.any { core == it }) {
            tiles.add(ExtraRoom(x, z, core))
            EnumFacing.HORIZONTALS.forEach {
                tiles.addAll(
                    findRoomTilesRecursively(
                        x + it.frontOffsetX * ROOM_SIZE,
                        z + it.frontOffsetZ * ROOM_SIZE,
                        room,
                        visited
                    )
                )
            }
        }
        return tiles
    }

    private fun scanRoom(x: Int, z: Int): Room? {
        val roomCore = ScanUtils.getCore(x, z)
        return Room(x, z, ScanUtils.getRoomData(roomCore) ?: return null).apply {
            core = roomCore
        }
    }

    /**
     * Gets the top layer of blocks in a room (the roof) for finding the rotation of the room.
     * This could be made recursive, but it's only a slightly cleaner implementation so idk
     * @param x The x of the room to scan
     * @param z The z of the room to scan
     * @return The y-value of the roof, this is the y-value of the blocks.
     */
    private fun getTopLayerOfRoom(x: Int, z: Int): Int {
        var currentHeight = 170
        while (isAir(x, currentHeight, z) && currentHeight > 70) {
            currentHeight--
        }
        return currentHeight
    }

    /**
     * Enumeration representing player classes in a dungeon setting.
     *
     * Each class is associated with a specific code and color used for formatting in the game. The classes include Archer,
     * Mage, Berserk, Healer, and Tank.
     *
     * @property color The color associated with the class.
     * @property defaultQuadrant The default quadrant for the class.
     * @property prio The priority of the class.
     *
     */
    enum class Classes(
        val color: Color,
        val defaultQuadrant: Int,
        var prio: Int,
    ) {
        Archer(Color.ORANGE, 0, 2),
        Berserk(Color.DARK_RED, 1, 0),
        Healer(Color.PINK, 2, 2),
        Mage(Color.BLUE, 3, 2),
        Tank(Color.DARK_GREEN, 3, 1),
    }

    /**
     * Data class representing a player in a dungeon, including their name, class, skin location, and associated player entity.
     *
     * @property name The name of the player.
     * @property clazz The player's class, defined by the [Classes] enum.
     * @property locationSkin The resource location of the player's skin.
     * @property entity The optional associated player entity. Defaults to `null`.
     */
    data class DungeonPlayer(
        val name: String,
        var clazz: Classes,
        val locationSkin: ResourceLocation = ResourceLocation("textures/entity/steve.png"),
        val entity: EntityPlayer? = null,
        var isDead: Boolean = false
    )

    val isGhost: Boolean get() = getItemSlot("Haunt", true) != null
    var dungeonTeammates: List<DungeonPlayer> = emptyList()
    var dungeonTeammatesNoSelf: List<DungeonPlayer> = emptyList()
    var leapTeammates = mutableListOf<DungeonPlayer>()

    init {
        Executor(500) {
            if (!inDungeons) return@Executor
            dungeonTeammates = getDungeonTeammates(dungeonTeammates)
            dungeonTeammatesNoSelf = dungeonTeammates.filter { it.entity != mc.thePlayer }

            leapTeammates =
                when (LeapMenu.type) {
                    0 -> odinSorting(dungeonTeammatesNoSelf.sortedBy { it.clazz.prio }).toMutableList()
                    1 -> dungeonTeammatesNoSelf.sortedWith(compareBy({ it.clazz.ordinal }, { it.name }))
                        .toMutableList()

                    2 -> dungeonTeammatesNoSelf.sortedBy { it.name }.toMutableList()
                    else -> dungeonTeammatesNoSelf.toMutableList()
                }

        }.register()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        dungeonTeammates = emptyList()
        dungeonTeammatesNoSelf = emptyList()
        leapTeammates.clear()
        currentRoom = null
        lastRoomPos = 0 to 0
    }

    private val tablistRegex = Regex("^\\[(\\d+)] (?:\\[\\w+] )*(\\w+) .*?\\((\\w+)(?: (\\w+))*\\)$")

    private fun getDungeonTeammates(previousTeammates: List<DungeonPlayer>): List<DungeonPlayer> {
        val teammates = mutableListOf<DungeonPlayer>()
        val tabList = getDungeonTabList() ?: return emptyList()

        for ((networkPlayerInfo, line) in tabList) {

            val (_, sbLevel, name, clazz, clazzLevel) = tablistRegex.find(line.noControlCodes)?.groupValues
                ?: noNameTablistRegex.find(line.noControlCodes)?.groupValues ?: continue

            addTeammate(name, clazz, teammates, networkPlayerInfo) // will fail to find the EMPTY or DEAD class and won't add them to the list
            if (clazz == "DEAD" || clazz == "EMPTY") {
                val previousClass = previousTeammates.find { it.name == name }?.clazz ?: continue
                addTeammate(name, previousClass.name, teammates, networkPlayerInfo) // will add the player with the previous class
            }
            teammates.find { it.name == name }?.isDead = clazz == "DEAD" // set the player as dead if they are
        }
        return teammates
    }

    private fun addTeammate(
        name: String,
        clazz: String,
        teammates: MutableList<DungeonPlayer>,
        networkPlayerInfo: NetworkPlayerInfo
    ) {
        Classes.entries.find { it.name == clazz }?.let { foundClass ->
            mc.theWorld.getPlayerEntityByName(name)?.let { player ->
                teammates.add(DungeonPlayer(name, foundClass, networkPlayerInfo.locationSkin, player))
            } ?: teammates.add(DungeonPlayer(name, foundClass, networkPlayerInfo.locationSkin, null))
        }
    }

    fun getDungeonTabList(): List<Pair<NetworkPlayerInfo, String>>? {
        val tabEntries = getTabList
        if (tabEntries.size < 18 || !tabEntries[0].second.contains("§r§b§lParty §r§f(")) return null
        return tabEntries
    }

    /**
     * Determines whether a given block state and position represent a secret location.
     *
     * This function checks if the specified block state and position correspond to a secret location based on certain criteria.
     * It considers blocks such as chests, trapped chests, and levers as well as player skulls with a specific player profile ID.
     *
     * @param state The block state to be evaluated for secrecy.
     * @param pos The position (BlockPos) of the block in the world.
     * @return `true` if the specified block state and position indicate a secret location, otherwise `false`.
     */
    fun isSecret(state: IBlockState, pos: BlockPos): Boolean {
        // Check if the block is a chest, trapped chest, or lever
        if (state.block == Blocks.chest || state.block == Blocks.trapped_chest || state.block == Blocks.lever) {
            return true
        } else if (state.block is BlockSkull) {
            // Check if the block is a player skull with a specific player profile ID
            val tile = mc.theWorld.getTileEntity(pos) ?: return false
            if (tile !is TileEntitySkull) return false
            return tile.playerProfile?.id.toString().equalsOneOf(WITHER_ESSENCE_ID, REDSTONE_KEY)
        }

        // If none of the above conditions are met, it is not a secret location
        return false
    }
}