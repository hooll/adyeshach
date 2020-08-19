package ink.ptms.adyeshach.common.entity.type

import ink.ptms.adyeshach.common.entity.EntityTypes
import org.bukkit.DyeColor

/**
 * @author sky
 * @date 2020/8/4 23:15
 */
class AdySheep() : AdyEntityAgeable(EntityTypes.SHEEP) {

    init {
        val index = at(11500 to 16, 11400 to 15, 11000 to 13, 10900 to 12)
        registerMeta(index, "dyeColor", 15.toByte())
        registerMetaByteMask(index, "isSheared", 0x10)
    }

    @Suppress("DEPRECATION")
    fun getDyeColor(): DyeColor {
        return DyeColor.getByWoolData(getMetadata("dyeColor")) ?: DyeColor.WHITE
    }

    @Suppress("DEPRECATION")
    fun setDyeColor(dyeColor: DyeColor) {
        setMetadata("dyeColor", dyeColor.woolData)
    }

    fun isSheared(): Boolean {
        return getMetadata("isSheared")
    }

    fun setSheared(value: Boolean) {
        setMetadata("isSheared", value)
    }

}