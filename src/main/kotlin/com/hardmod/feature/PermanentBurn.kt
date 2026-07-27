package com.hardmod.feature

import com.hardmod.config.HardModConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import java.util.UUID

/**
 * Quemadura permanente de jugadores (apagada por defecto): mientras el
 * jugador este en fuego y no en contacto con agua, evitamos que el fuego
 * se apague del todo -- vanilla ya pone fireTicks en 0 apenas isInWater()
 * es true (antes de que corra este handler, en el mismo tick), asi que no
 * hace falta ningun mixin para el apagado. Solo afecta jugadores, los
 * mobs arden normal.
 *
 * OJO con el timing: el daño de fuego de vanilla (Entity.baseTick) solo
 * pega cuando remainingFireTicks % 20 == 0. Repetimos el contador a un
 * valor fijo EN CADA tick (como hacia una version anterior de este
 * archivo) hace que ese valor fijo siempre caiga multiplo de 20 y el daño
 * pase a aplicarse TODOS los ticks en vez de cada 20 (1 vez por segundo)
 * -- mucho mas agresivo de lo normal. Por eso aca solo reponemos el
 * contador justo cuando llega a 0 (a punto de apagarse solo), dejandolo
 * bajar de a 1 por tick el resto del tiempo, igual que vanilla, para
 * respetar el mismo ritmo de daño de siempre.
 *
 * Ojo tambien con Entity.isOnFire(): en el server devuelve
 * remainingFireTicks > 0, asi que en el mismo tick que el contador llega
 * a 0 ya da false -- no sirve para decidir CUANDO reavivar. Por eso
 * llevamos nuestro propio registro de "este jugador esta en modo
 * quemadura" (se agrega apenas isOnFire da true, se saca solo al tocar
 * agua) en vez de depender de isOnFire para el chequeo de reavivado.
 */
object PermanentBurn {

    /** Con cuantos fire ticks se rearma el contador justo cuando esta por llegar a 0 (multiplo de 20 para no romper el ritmo de daño). */
    private const val REFRESH_TICKS = 200

    private val burningPlayers = mutableSetOf<UUID>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (!HardModConfig.permanentBurn) {
                if (burningPlayers.isNotEmpty()) burningPlayers.clear()
                return@register
            }
            for (player in server.playerList.players) {
                val uuid = player.getUUID()
                if (player.isInWater) {
                    burningPlayers.remove(uuid)
                    continue
                }
                if (player.isOnFire) {
                    burningPlayers.add(uuid)
                } else if (burningPlayers.contains(uuid) && player.remainingFireTicks <= 0) {
                    player.remainingFireTicks = REFRESH_TICKS
                }
            }
        }
    }
}
