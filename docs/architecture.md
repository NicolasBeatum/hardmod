# Arquitectura

## Stack

- Minecraft 26.2, Fabric Loader + Loom, sin mappings intermedios (Mojang ya
  distribuye nombres oficiales sin ofuscar en 26.1+).
- Kotlin vía `fabric-language-kotlin` (no se agrega `kotlin-stdlib` aparte, FLK ya
  lo trae). Los mixins están en Java (SpongePowered Mixin no soporta bien
  anotaciones Kotlin en algunos casos, y es el estilo ya usado en el resto del
  pack).
- Java 25 toolchain.
- 100% servidor: no hay `src/client`, todo lo visible para el jugador (chat,
  sonidos, chest GUIs, bossbars, sidebar) se resuelve con APIs vanilla/Fabric que
  ya sincronizan solas al cliente.

## Mapa de archivos

```
src/main/kotlin/com/hardmod/
├── HardMod.kt                          entrypoint: registra todo, recarga configs al iniciar el server
├── config/
│   └── HardModConfig.kt                config/hardmod/config.json (toggles admin-editables)
├── announce/
│   ├── Announcer.kt                    banner de chat + sonido + parser de colores '&'
│   └── MessagePresets.kt                config/hardmod/presets.json (mensaje + comandos por preset)
├── command/
│   └── HardModCommands.kt               árbol completo de /hardmod
├── gui/
│   ├── ChestGuiMenu.kt                  ChestMenu "estilo plugin" (slots bloqueados salvo acción registrada)
│   ├── GuiItems.kt                      helper de items con nombre/lore coloreados + fondo de vidrio
│   └── AdminPanelGui.kt                 panel visual (/hardmod panel)
└── feature/
    ├── MobcapControl.kt                 escala el cap de spawn por categoría
    ├── EnchantTableLock.kt              bloqueo temporal/indefinido de la mesa
    ├── VillagerSpawnControl.kt           predicados de bloqueo de aldeanos (dos caminos distintos)
    ├── TridentEnchantControl.kt          modos de restricción de encantamiento del tridente
    ├── TotemControl.kt                   probabilidad configurable de activación de tótems
    ├── RaidControl.kt                    toggle persistente sobre la gamerule raids
    ├── PermanentBurn.kt                  quemadura que no se apaga sola (solo jugadores)
    ├── DimensionLock.kt                  bloquea encender portal Nether / poner ojo en marco End
    ├── PvpScheduler.kt                   ventana de PVP diaria aleatoria + bossbar de cuenta regresiva
    ├── ServerShutdownScheduler.kt         cierre diario a hora fija + bossbar + extensión
    ├── LivesEliminationAnnouncer.kt       anuncia cuando un jugador llega a 0 vidas (lee scoreboard ajeno)
    ├── BossArenaCompass.kt               anticipa arenas 30m y detecta bosses (lee JSON ajeno + escanea entidades)
    ├── BlackMarketSidebarTracker.kt       consume snapshot/eventos opcionales del mercado mediante Object Share
    ├── BossCompassSidebar.kt             sidebar de brújula personalizada por jugador (paquetes a mano)
    └── trialrewards/
        ├── TrialRewardConfig.kt          config/hardmod/trial_rewards.json (overrides por tabla)
        ├── TrialRewardDefaultsLoader.kt   aplana la loot table vanilla real a item+peso+cantidad
        ├── TrialRewardLootModifier.kt     reemplaza la loot table (LootTableEvents.REPLACE)
        ├── TrialRewardEditorGui.kt        chest GUI paginada del editor principal
        ├── BookLibraryGui.kt              lista los libros con pool de encantamiento de una tabla
        └── BookPoolEditorGui.kt           toggle de encantamientos individuales de un libro

src/main/java/com/hardmod/mixin/
├── MobCategoryMixin.java                MobCategory.getMaxInstancesPerChunk -> MobcapControl.scale
├── EnchantmentTablePoolMixin.java         EnchantmentHelper.selectEnchantment -> TridentEnchantControl.filterPool (antes del roll)
├── TotemActivationMixin.java              LivingEntity.checkTotemDeathProtection -> sorteo configurable
├── VillagerSpawnMixin.java                ServerLevel.addEntity -> VillagerSpawnControl.shouldBlock (runtime: cría/cura)
├── VillagerWorldGenSpawnMixin.java         ServerLevel.addWorldGenChunkEntities -> shouldBlockWorldGen (aldeas generadas)
├── MobConversionMixin.java                Mob.convertTo -> marca "cura de zombie aldeano en curso"
├── NetherPortalMixin.java                 NetherPortalBlock.entityInside -> cancela si netherLocked (red de seguridad)
└── EndPortalMixin.java                    EndPortalBlock.entityInside -> cancela si endLocked (red de seguridad)

src/main/resources/
├── fabric.mod.json
└── hardmod.mixins.json
```

## Persistencia (config/hardmod/)

Todo se lee/escribe con `JsonObject` de Gson a mano (no se deserializa un data
class directo) — Gson no respeta los valores default de Kotlin cuando falta un
campo en el JSON, así que leer campo por campo con `?:` deja los defaults
explícitos y hace que agregar un campo nuevo sea aditivo (un JSON viejo sin ese
campo simplemente usa el default, no rompe).

| Archivo | Contenido |
|---|---|
| `config.json` | Todos los toggles de `HardModConfig` (mobcap, mesa, aldeanos, tridente, tótems, raids, quemadura, nether/end, sonido de anuncio). |
| `presets.json` | Presets de `/hardmod announce preset <nombre>`: `{message, commands}` por preset (formato viejo — solo string — sigue soportado). |
| `trial_rewards.json` | Overrides de recompensas de trial chambers por loot table id, más el rango de tiradas (`rollRanges`) guardado aparte. |

Ninguno de estos tres se toca por polling — se leen una vez en
`ServerLifecycleEvents.SERVER_STARTING` y se reescriben cada vez que un setter
cambia algo (`save()` al final de cada `set*`).

## Principios de diseño repetidos

- **Ningún toggle anuncia solo al chat.** Ver el README del índice de docs.
- **Mixin en el punto exacto de la mecánica.** `TotemActivationMixin` engancha
  `checkTotemDeathProtection`, mientras `VillagerSpawnMixin` engancha el
  `addEntity` privado donde delegan los caminos de spawn runtime.
- **Filtrar el pool ANTES del roll, no el resultado después.** El tridente se
  filtra en `EnchantmentTablePoolMixin`, específicamente en el pool de la mesa,
  para que otras opciones ni aparezcan sin afectar el yunque. Para los libros
  de trial chambers, el override reemplaza la loot table entera.
- **Server-side puro, sin tocar mods vecinos.** Ver la sección "Cero
  acoplamiento" del índice.
- **GUIs "estilo plugin".** `ChestGuiMenu` bloquea cualquier slot propio que no
  tenga una acción registrada explícitamente (no se puede sacar paneles
  decorativos ni nada fuera de lo previsto) — solo se habilita interacción
  vanilla normal donde hace falta (la fila de staging del editor de
  recompensas).
