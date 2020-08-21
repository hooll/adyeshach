package ink.ptms.adyeshach.common.path

import ink.ptms.adyeshach.api.nms.NMS
import ink.ptms.adyeshach.internal.mirror.Mirror
import io.izzel.taboolib.Version
import io.izzel.taboolib.module.ai.SimpleAiSelector
import io.izzel.taboolib.module.inject.TFunction
import io.izzel.taboolib.module.inject.TListener
import io.izzel.taboolib.module.inject.TSchedule
import io.izzel.taboolib.module.packet.Packet
import io.izzel.taboolib.module.packet.TPacket
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

/**
 * @Author sky
 * @Since 2020-08-13 16:31
 */
object PathFinderProxy {

    private val version = Version.getCurrentVersionInt()
    private val proxyEntity = ConcurrentHashMap<String, PathEntity>()

    fun request(start: Location, target: Location, pathType: PathType = PathType.WALK_2, call: (PathResult) -> (Unit)) {
        if (pathType.supportVersion > version) {
            throw RuntimeException("PathType \"$pathType\" not supported this minecraft version.")
        }
        if (start.world!!.name != target.world!!.name) {
            throw PathException("cannot request navigation in different worlds.")
        }
        val proxyEntity = proxyEntity[start.world!!.name]!!
        if (proxyEntity.spawnFailed.contains(pathType)) {
            throw PathException("navigation proxy did not complete initialization.")
        }
        proxyEntity.schedule.add(PathSchedule(start, target, pathType, call))
    }

    fun isProxyEntity(id: Int): Boolean {
        return proxyEntity.values.any { it.entity.values.any { entity -> entity.entityId == id } }
    }

    @TPacket(type = TPacket.Type.SEND)
    private fun packet(player: Player, packet: Packet): Boolean {
        return !packet.`is`("PacketPlayOutSpawnEntityLiving") || !isProxyEntity(packet.read("a", Int::class.java))
    }

    @TFunction.Cancel
    private fun cancel() {
        proxyEntity.values.forEach { it.entity.values.forEach { entity -> entity.remove() } }
    }

    @TSchedule(period = 20)
    private fun check() {
        Bukkit.getWorlds().forEach {
            Mirror.get("PathFinderProxy:onCheck:${it.name}", false).eval {
                val pathEntity = proxyEntity.computeIfAbsent(it.name) { PathEntity() }
                pathEntity.entity.values.removeIf { entity -> !entity.isValid }
                PathType.values().filterNot { pathType -> pathEntity.entity.containsKey(pathType) && pathType.supportVersion <= version }.forEach e@{ pathType ->
                    val proxyEntity = it.spawn(Location(it, 0.0, 100.0, 0.0), pathType.entity) { entity -> pathEntity.entity[pathType] = entity }.silent()
                    if (proxyEntity.isValid) {
                        pathEntity.spawnFailed.remove(pathType)
                    } else {
                        pathEntity.spawnFailed.add(pathType)
                    }
                }
            }
        }
    }

    @TSchedule(period = 5)
    private fun schedule() {
        proxyEntity.forEach { (world, pathEntity) ->
            if (pathEntity.schedule.isEmpty()) {
                return@forEach
            }
            Mirror.get("PathFinderProxy:onSchedule:$world").eval {
                pathEntity.schedule.forEach { schedule ->
                    val entity = pathEntity.entity[schedule.pathType]
                    if (entity != null) {
                        val time = System.currentTimeMillis()
                        entity.teleport(schedule.start)
                        val pathList = NMS.INSTANCE.getNavigationPathList(entity, schedule.target)
                        if (pathList.isNotEmpty() || schedule.retry++ > 4) {
                            schedule.call.invoke(PathResult(NMS.INSTANCE.getNavigationPathList(entity, schedule.target), schedule.beginTime, time))
                            pathEntity.schedule.remove(schedule)
                        }
                    }
                }
            }
        }
    }

    private fun Mob.silent(): Mob {
        customName = "Adyeshach Pathfinder Proxy"
        isSilent = true
        isCollidable = false
        isInvulnerable = true
        SimpleAiSelector.getExecutor().clearGoalAi(this)
        SimpleAiSelector.getExecutor().clearTargetAi(this)
        return this
    }

    @TListener
    class PathListener : Listener {

        @EventHandler
        fun e(e: EntityDeathEvent) {
            if (isProxyEntity(e.entity.entityId)) {
                e.drops.clear()
                e.droppedExp = 0
            }
        }
    }
}