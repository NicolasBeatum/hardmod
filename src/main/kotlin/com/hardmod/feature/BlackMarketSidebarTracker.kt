package com.hardmod.feature

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Consume el estado opcional que npc-mod publica mediante Fabric Object Share.
 * No hay dependencia de compilacion ni se revelan datos de la ubicacion.
 */
object BlackMarketSidebarTracker {

    private const val STATUS_SHARE_KEY = "harddeathnpc:black_market_status"
    private const val SUBSCRIBE_SHARE_KEY = "harddeathnpc:black_market_subscribe"
    private const val TARGET_KEY = "black_market"
    private const val CHECK_INTERVAL_TICKS = 20

    @Volatile
    private var statusSupplier: Supplier<*>? = null

    @Volatile
    private var latestStatus: Map<*, *>? = null

    private var ticksSinceCheck = 0

    @Suppress("UNCHECKED_CAST")
    fun register() {
        FabricLoader.getInstance().objectShare.whenAvailable(STATUS_SHARE_KEY) { _, value ->
            statusSupplier = value as? Supplier<*>
        }
        FabricLoader.getInstance().objectShare.whenAvailable(SUBSCRIBE_SHARE_KEY) { _, value ->
            val subscribe = value as? Consumer<*> ?: return@whenAvailable
            runCatching {
                (subscribe as Consumer<Consumer<Map<String, Any>>>).accept(
                    Consumer { status -> latestStatus = status }
                )
            }
        }
        ServerTickEvents.END_SERVER_TICK.register { _ ->
            ticksSinceCheck++
            if (ticksSinceCheck >= CHECK_INTERVAL_TICKS) {
                ticksSinceCheck = 0
                tick()
            }
        }
    }

    private fun tick() {
        val status = latestStatus
            ?: (runCatching { statusSupplier?.get() }.getOrNull() as? Map<*, *>)
        val active = status?.get("active") as? Boolean ?: false
        val endMillis = (status?.get("endMillis") as? Number)?.toLong() ?: 0L
        if (!active || endMillis <= System.currentTimeMillis()) {
            BossCompassSidebar.hide(TARGET_KEY)
            return
        }

        BossCompassSidebar.showNotice(
            TARGET_KEY,
            "Mercado negro abierto",
            endMillis
        )
    }
}
