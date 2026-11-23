package com.unciv.models.ruleset

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.fonts.Fonts
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

sealed class PerpetualConstruction(override var name: String, val description: String) : IConstruction {
    @Readonly
    abstract fun getProductionTooltip(city: City, withIcon: Boolean = false): String

    abstract class StatConversion(
        val stat: Stat
    ) : PerpetualConstruction(stat.name, "Convert production to [${stat.name}] at a rate of [rate] to 1") {
        override fun getProductionTooltip(city: City, withIcon: Boolean) : String
            = "\r\n${(city.cityStats.currentCityStats.production / getConversionRate(city)).roundToInt()}${if (withIcon) stat.character else ""}/${Fonts.turn}"

        @Readonly
        fun getConversionRate(city: City) : Int = (1 / city.cityStats.getStatConversionRate(stat)).roundToInt()

        override fun isBuildable(cityConstructions: CityConstructions): Boolean {
            val city = cityConstructions.city
            if (stat == Stat.Faith && !city.civ.gameInfo.isReligionEnabled())
                return false

            val context = city.state
            @Suppress("DEPRECATION") // forEachMatchingUnique doesn't allow a `return true` exit
            return city.civ.getMatchingUniques(UniqueType.EnablesStatProduction, context)
                .any { it.params[0] == stat.name }
        }
    }

    class ResourceConversion(
        val resource: TileResource
    ) : PerpetualConstruction(resource.name, "Convert production to [${resource.name}] at a rate of [rate] to 1") {
        override fun getProductionTooltip(city: City, withIcon: Boolean) : String
            = "\r\n${(city.cityStats.currentCityStats.production / getConversionRate(city)).roundToInt()}${if (withIcon) "ResourceIcons/$resource.name" else ""}/${Fonts.turn}"

        @Readonly
        fun getConversionRate(city: City) : Int = (1 / city.getResourceConversionRate(resource)).roundToInt()

        override fun isBuildable(cityConstructions: CityConstructions): Boolean {
            val city = cityConstructions.city

            val context = city.state
            @Suppress("DEPRECATION") // forEachMatchingUnique doesn't allow a `return true` exit
            return city.civ.getMatchingUniques(UniqueType.EnablesResourceProduction, context)
                .any { it.params[0] == resource.name }
        }
    }

    @Readonly
    override fun shouldBeDisplayed(cityConstructions: CityConstructions) = isBuildable(cityConstructions)
    @Readonly
    override fun getStockpiledResourceRequirements(state: GameContext) = Counter.ZERO
    @Readonly
    override fun getResourceRequirementsPerTurn(state: GameContext?) = Counter.ZERO
    @Readonly
    override fun requiredResources(state: GameContext): Set<String> = emptySet()

    object Science : StatConversion(Stat.Science)
    object Gold : StatConversion(Stat.Gold)
    object Culture : StatConversion(Stat.Culture)
    object Faith : StatConversion(Stat.Faith)
    object Food : StatConversion(Stat.Food)
    object Happiness : StatConversion(Stat.Happiness)
        
    object Idle : PerpetualConstruction("Nothing", "The city will not produce anything.") {
        override fun getProductionTooltip(city: City, withIcon: Boolean) = ""
        override fun isBuildable(cityConstructions: CityConstructions) = true
    }

    private object Mapper {
        /** Map of names to instances: Cannot be part of the companion due to initialization order injustice - a companion is nobility, members are peasants */
        val perpetualStatConstructionsMap: Map<String, PerpetualConstruction> =
            mapOf(Science.name to Science, Gold.name to Gold, Culture.name to Culture, Faith.name to Faith, Food.name to Food, Happiness.name to Happiness, Idle.name to Idle)
        val perpetualResourceConstructionsMap: MutableMap<String, PerpetualConstruction> = mutableMapOf<String, PerpetualConstruction>()
        
        fun populateResourceConstructions(ruleset: Ruleset){
            for ((name, resource) in ruleset.tileResources) {
                if (resource.isStockpiled) {
                    perpetualResourceConstructionsMap[name] = ResourceConversion(resource)
                }
            }
        }
    }

    companion object {
        val perpetualStatConstructionsMap get() = Mapper.perpetualStatConstructionsMap
        val perpetualResourceConstructionsMap get() = Mapper.perpetualResourceConstructionsMap
        
        fun populateResourceConstructions(ruleset: Ruleset) {Mapper.populateResourceConstructions(ruleset)}
        /** @return whether [name] represents a PerpetualConstruction - note "" is translated to Nothing in the queue so `isNamePerpetual("")==true` */
        fun isNamePerpetual(name: String) = name.isEmpty() || name in Mapper.perpetualStatConstructionsMap || name in Mapper.perpetualResourceConstructionsMap
    }
}
