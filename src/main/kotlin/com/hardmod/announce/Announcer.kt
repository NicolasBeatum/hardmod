package com.hardmod.announce

import com.hardmod.config.HardModConfig
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import org.slf4j.LoggerFactory

/**
 * Anunciador central: todo cambio de toggle (mobcap, mesa, aldeanos,
 * tridente, quemadura) y todo mensaje custom/preset pasa por aca, para que
 * el formato, el banner y el ping sean siempre iguales. Cada anuncio sale
 * como un banner rojo de 3 lineas: la primera trae el titulo incrustado en
 * la propia barra ("---- HardDeath ----"), la segunda es el mensaje solo
 * (sin repetir que lo mando el mod), y la tercera cierra con otra barra.
 */
object Announcer {

    private val LOGGER = LoggerFactory.getLogger("hardmod")

    /** Titulo incrustado en la propia linea de separacion (--- HardDeath ---), no en una linea aparte. */
    private val HEADER = "&4&m" + "-".repeat(8) + "&r &c&l☠ HardDeath &r&4&m" + "-".repeat(8)

    /** Linea de cierre: solo la barra, sin repetir el titulo -- el mensaje ya quedo solo, abajo del header. */
    private val FOOTER = "&4&m" + "-".repeat(34)

    fun broadcast(server: MinecraftServer, message: String) {
        val banner = "$HEADER\n&f$message\n$FOOTER"
        val component = colorize(banner)
        server.playerList.broadcastSystemMessage(component, false)
        val sound = resolveSound()
        for (player in server.playerList.players) {
            player.playSound(sound, 1.0f, 1.0f)
        }
        LOGGER.info("[hardmod] Anuncio: {}", message)
    }

    private fun resolveSound(): SoundEvent {
        val id = Identifier.tryParse(HardModConfig.announceSoundId) ?: return SoundEvents.EXPERIENCE_ORB_PICKUP
        return BuiltInRegistries.SOUND_EVENT.get(id).map { it.value() }.orElse(SoundEvents.EXPERIENCE_ORB_PICKUP)
    }

    /**
     * Parser de codigos de color/formato estilo '&' (ej. "&c&lTexto &7resto").
     * A diferencia de un parser que solo recuerda el ultimo codigo, este
     * ACUMULA estilos (Style.applyFormat) para poder combinar color + negrita
     * + tachado a la vez (ej. "&4&m" para una linea roja tachada), y se
     * reinicia con "&r" -- ademas se reinicia SOLO al pasar un '\n', para que
     * un estilo (ej. tachado) de una linea nunca se filtre a la siguiente
     * aunque esa linea se olvide de poner "&r" al empezar.
     */
    fun colorize(text: String): MutableComponent {
        var result = Component.empty()
        var current = StringBuilder()
        var style = Style.EMPTY
        var i = 0
        fun flush() {
            if (current.isNotEmpty()) {
                result = result.append(Component.literal(current.toString()).withStyle(style))
                current = StringBuilder()
            }
        }
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                flush()
                result = result.append("\n")
                style = Style.EMPTY
                i++
                continue
            }
            if (c == '&' && i + 1 < text.length) {
                val code = ChatFormatting.getByCode(text[i + 1])
                if (code != null) {
                    flush()
                    style = if (code == ChatFormatting.RESET) Style.EMPTY else style.applyFormat(code)
                    i += 2
                    continue
                }
            }
            current.append(c)
            i++
        }
        flush()
        return result
    }
}
