# Features

Cada sección: qué hace, default, archivos involucrados, y notas de implementación
que no son obvias leyendo el nombre del archivo.

## Mobcap

Escala el límite de mobs que pueden existir a la vez, por categoría
(`MobCategory`, ej. `monster`, `creature`), con un multiplicador configurable.

- **Default**: x1 (sin cambio) para toda categoría sin multiplicador guardado.
- **Archivos**: [`MobcapControl.kt`](../src/main/kotlin/com/hardmod/feature/MobcapControl.kt),
  [`MobCategoryMixin.java`](../src/main/java/com/hardmod/mixin/MobCategoryMixin.java).
- **Cómo**: un solo mixin sobre `MobCategory.getMaxInstancesPerChunk()` — ese
  número alimenta tanto el cap global por chunk (`NaturalSpawner.SpawnState`)
  como el cap por-jugador (`LocalMobCapCalculator`), así que un solo hook escala
  los dos a la vez.
- **Comando**: `/hardmod mobcap <categoria> <multiplicador>`.
- **Diagnóstico**: `/hardmod mobcap list` resume todas las categorías y
  `/hardmod mobcap list <categoria>` muestra el conteo por tipo. Excluye
  `MISC` y mobs persistentes, igual que el conteo vanilla de `NaturalSpawner`.

## Mesa de encantamientos

Bloqueada por defecto. Se puede bloquear/desbloquear a mano o por una duración
en segundos, con reversión automática.

- **Default**: bloqueada (`enchantTableLocked = true`).
- **Archivos**: [`EnchantTableLock.kt`](../src/main/kotlin/com/hardmod/feature/EnchantTableLock.kt).
- **Cómo**: `UseBlockCallback` cancela la interacción sobre `EnchantingTableBlock`
  mientras esté bloqueada, mostrando una excusa temática al azar en la action
  bar ("una fuerza arácnida no te deja usar la mesa..."). Si se bloqueó/
  desbloqueó con duración, un chequeo cada 20 ticks revierte el estado solo al
  cumplirse el plazo (mismo patrón que `MandatoryMissionService` de mision-mod).
- **Comandos**: `/hardmod enchanttable lock [segundos]`, `unlock [segundos]`.

## Aldeanos

Elimina el spawn de aldeanos — tanto en aldeas generadas por el mundo como
(opcionalmente) en runtime — con dos excepciones cuidadosamente separadas.

- **Default**: bloqueados (`blockVillagers = true`).
- **Archivos**: [`VillagerSpawnControl.kt`](../src/main/kotlin/com/hardmod/feature/VillagerSpawnControl.kt),
  [`VillagerSpawnMixin.java`](../src/main/java/com/hardmod/mixin/VillagerSpawnMixin.java),
  [`VillagerWorldGenSpawnMixin.java`](../src/main/java/com/hardmod/mixin/VillagerWorldGenSpawnMixin.java),
  [`MobConversionMixin.java`](../src/main/java/com/hardmod/mixin/MobConversionMixin.java).
- **Cómo — dos caminos de inserción distintos, dos predicados distintos**:
  - `shouldBlock` (vía `VillagerSpawnMixin`, engancha el `ServerLevel.addEntity`
    privado — el punto común de `addFreshEntity`/`addWithUUID`/
    `addDuringTeleport`): bloquea aldeanos ADULTOS, pero deja pasar (1) bebés
    (`Entity.isBaby`, cría real de reproducción) y (2) un zombie aldeano recién
    curado (bandera `curingInProgress`, activada por `MobConversionMixin`
    alrededor de `Mob.convertTo` solo cuando es específicamente
    `ZombieVillager -> Villager`).
  - `shouldBlockWorldGen` (vía `VillagerWorldGenSpawnMixin`, engancha
    `ServerLevel.addWorldGenChunkEntities` — el camino separado que usa la
    generación de estructuras, que **no** pasa por `addEntity`): bloquea
    CUALQUIER Villager, bebé o adulto, sin excepción — las aldeas generadas no
    tienen crías reales ni curas, así que ninguna excepción aplica ahí.
- **No afecta** a `WanderingTrader` (no es un `Villager`).
- **Comando**: `/hardmod villagers on|off`.

## Tridente

Restringe qué encantamientos ofrece la mesa para un tridente, en 3 modos.

- **Default**: `MAX_UNBREAKING_III` (la mesa solo ofrece Unbreaking, tope nivel 3).
- **Modos de mesa**: `OFF` (sin restricción), `BLOCK_ALL` (la mesa no puede
  encantarlo), `MAX_UNBREAKING_III`.
- **Yunque libre**: los libros de Riptide, Loyalty, Channeling, Mending y demás
  funcionan normalmente; la restricción no filtra encantamientos aplicados por
  yunque.
- **Archivos**: [`TridentEnchantControl.kt`](../src/main/kotlin/com/hardmod/feature/TridentEnchantControl.kt),
  [`EnchantmentTablePoolMixin.java`](../src/main/java/com/hardmod/mixin/EnchantmentTablePoolMixin.java).
- **Cómo**: `filterPool` filtra el pool de candidatos de
  `EnchantmentHelper.selectEnchantment` antes del roll. Ese método corresponde
  a la mesa; el yunque no pasa por este filtro.
- **Comando**: `/hardmod trident off|block|unbreaking3`.

## Tótems

La activación del tótem de inmortalidad tiene una probabilidad configurable.

- **Default**: 100% (comportamiento vanilla).
- **Rango**: 0% impide toda activación; 100% conserva el comportamiento vanilla.
  Si el sorteo falla, el tótem no se consume.
- **Archivos**: [`TotemControl.kt`](../src/main/kotlin/com/hardmod/feature/TotemControl.kt),
  [`TotemActivationMixin.java`](../src/main/java/com/hardmod/mixin/TotemActivationMixin.java).
- **Comando**: `/hardmod totem chance <0-100>`.

## Raids

- **Default**: desactivadas.
- Usa la gamerule vanilla `raids`; al desactivarla se detienen las raids
  existentes y no comienzan nuevas.
- **Archivo**: [`RaidControl.kt`](../src/main/kotlin/com/hardmod/feature/RaidControl.kt).
- **Comando**: `/hardmod raids on|off`.

## Quemadura permanente

Con esto activado, un jugador en llamas no se apaga solo — solo tocando agua.

- **Default**: apagada (`permanentBurn = false`). Solo afecta jugadores, los
  mobs arden normal.
- **Archivos**: [`PermanentBurn.kt`](../src/main/kotlin/com/hardmod/feature/PermanentBurn.kt).
- **Cómo**: reponer `remainingFireTicks` a un valor fijo en CADA tick rompería
  el ritmo de daño de vanilla (`Entity.baseTick` solo pega daño cuando
  `remainingFireTicks % 20 == 0`) — por eso solo se repone (a 200, múltiplo de
  20) justo cuando el contador llega a 0, dejándolo bajar normal el resto del
  tiempo. Como `isOnFire()` ya da `false` el mismo tick que el contador toca 0,
  se lleva un set propio de "jugadores en modo quemadura" en vez de depender de
  `isOnFire()` para decidir cuándo reavivar.
- **Comando**: `/hardmod burn on|off`.

## Nether y End

Sella el acceso a ambas dimensiones hasta que un admin las active — en dos
niveles (creación del portal + teletransporte, por si acaso).

- **Default**: ambos sellados (`netherLocked = endLocked = true`).
- **Archivos**: [`DimensionLock.kt`](../src/main/kotlin/com/hardmod/feature/DimensionLock.kt),
  [`NetherPortalMixin.java`](../src/main/java/com/hardmod/mixin/NetherPortalMixin.java),
  [`EndPortalMixin.java`](../src/main/java/com/hardmod/mixin/EndPortalMixin.java).
- **Cómo**:
  - `DimensionLock` (`UseBlockCallback`) bloquea poner un ojo de ender en un
    `EndPortalFrameBlock`, y encender un portal de Nether sobre obsidiana con
    mechero (pedernal y eslabón) O carga de fuego — las dos formas vanilla de
    prenderlo a mano. Muestra una excusa temática al azar en la action bar.
  - Los dos mixins son una red de seguridad ADEMÁS de eso: cancelan
    `entityInside` de `NetherPortalBlock`/`EndPortalBlock` por completo mientras
    esté sellado, así que si por algún motivo llega a existir un portal ya
    prendido (ej. un ruined portal generado así de fábrica), nadie se
    teletransporta igual.
- **Comandos**: `/hardmod nether lock|unlock`, `/hardmod end lock|unlock`.

## PVP programado

PVP apagado por defecto. Después de las 19:00 intenta activarse periódicamente.

- **Default**: inactivo.
- **Sorteo automático**: desde las 19:00 hasta la hora de cierre diaria, cada
  15 minutos hace un sorteo con 25% de probabilidad de activación. Si sale
  positivo y no hay una sesión
  activa ni un enfriamiento pendiente, activa el PVP.
- **Duración**: aleatoria entre 15 y 60 minutos por sesión.
- **Enfriamiento**: mínimo 30 minutos sin PVP después de que termina una sesión
  antes de poder volver a dispararse.
- **Bossbar**: visible desde el momento en que se activa hasta que termina
  (roja, cuenta regresiva `MM:SS`, progreso relativo a la duración total de
  esa sesión).
- **Bloqueo real de golpes**: mientras no esté activo, `ServerLivingEntityEvents.ALLOW_DAMAGE`
  cancela cualquier golpe jugador-contra-jugador y le muestra un aviso en la
  action bar al atacante.
- **Archivos**: [`PvpScheduler.kt`](../src/main/kotlin/com/hardmod/feature/PvpScheduler.kt).
- **Comandos**: `/hardmod pvp on [minutos]` (activación manual, ignora el
  enfriamiento; sin minutos, sortea entre 15 y 60), `/hardmod pvp off`
  (cancela y arranca el enfriamiento normal).
- **Nota de diseño**: cada cuarto de hora se procesa una sola vez. Los sorteos
  que coincidan con una sesión activa o con el enfriamiento se omiten; no se
  acumulan ni se ejecutan con retraso.

## Cierre diario del servidor

El servidor se detiene solo, todos los días, a una hora fija.

- **Default**: 02:00, hora del sistema del servidor.
- **Configuración**: `/hardmod server time <hora> [minuto]` permite cambiar la hora de cierre diaria en caliente (persiste en `config/hardmod/config.json`).
- **Bossbar**: visible las últimas 2 horas antes del cierre (amarilla, cuenta
  regresiva `H:MM:SS` o `MM:SS`).
- **Extensión**: `/hardmod server extend <minutos>` corre el cierre hacia
  adelante (ej. si cerraba a las 02:00 y se extiende 30, pasa a cerrar a las
  02:30).
- **Archivos**: [`ServerShutdownScheduler.kt`](../src/main/kotlin/com/hardmod/feature/ServerShutdownScheduler.kt).
- **Cómo se detiene**: `server.halt(false)` — el mismo método que usa
  internamente el comando `/stop` de vanilla.

## Anuncio de eliminación permanente

Cuando un jugador se queda sin vidas (sistema de vidas de
`hard-death-core-mod`), se anuncia en el chat con el banner completo.

- **Archivos**: [`LivesEliminationAnnouncer.kt`](../src/main/kotlin/com/hardmod/feature/LivesEliminationAnnouncer.kt).
- **Cómo**: se engancha a `ServerLivingEntityEvents.AFTER_DEATH` (no sondea cada
  tick) — al morir un jugador, encola su nombre y recién al terminar ESE mismo
  tick revisa su score en el objective de scoreboard vanilla `hdc_lives` (le da
  margen a que el listener de `hard-death-core-mod`, que decrementa la vida en
  su propio `AFTER_DEATH`, ya haya corrido — el orden entre listeners de
  distintos mods en el mismo evento no está garantizado, pero para cuando
  termina el tick ya corrieron todos). Si el score quedó en 0, dispara
  `Announcer.broadcast`.
- **Sin acoplamiento**: no importa ninguna clase de `hard-death-core-mod`, solo
  lee un objective de scoreboard vanilla por nombre.

## Brújula hacia bosses de arena

Sidebar personalizada por jugador que aparece 30 minutos antes del horario de
una arena de `hard-death-mobs-mod`, apunta al punto de spawn y después sigue al
boss durante la pelea.

- **Archivos**: [`BossArenaCompass.kt`](../src/main/kotlin/com/hardmod/feature/BossArenaCompass.kt)
  (detección), [`BossCompassSidebar.kt`](../src/main/kotlin/com/hardmod/feature/BossCompassSidebar.kt)
  (la sidebar en sí).
- **Aviso previo (sin tocar `hard-death-mobs-mod`)**: lee (solo lectura) los
  archivos `config/harddeathmobs/arenas/*.json` de ese mod para saber la
  posición (`setpos`), `triggerTimes`, `enabled` y horarios ya disparados.
  Durante los 30 minutos anteriores a un horario pendiente muestra la dirección,
  distancia, coordenadas X/Y/Z y cuenta regresiva.
- **Detección de pelea**: cada 3 segundos
  (`DETECTION_INTERVAL_TICKS`), si una arena todavía no tiene pelea trackeada,
  escanea un radio de 40 bloques alrededor de esa posición buscando seres
  vivos con nombre personalizado visible (así es exactamente como ese mod
  marca a sus bosses/minions, y a nadie más). Al encontrar alguno, arranca a
  seguir esas entidades puntuales por UUID.
- **Una vez trackeada, no importa la distancia**: el boss se puede alejar del
  spawn (teletransporte, persecución a caballo, etc.) sin que la brújula se
  apague — solo se oculta cuando esas entidades puntuales de verdad mueren o
  desaparecen. (Antes re-detectaba por distancia cada vez y se apagaba
  prematuramente si el boss se alejaba — corregido.)
- **Carga de configuración**: lee los JSON una sola vez al encender el
  servidor. `/hardmod arenas reload` permite forzar una recarga manual.
- **Contenido de la sidebar** (3 líneas por arena, actualizadas cada 10 ticks):
  1. `X:123 Y:64 Z:456` — punto exacto donde aparecerá el boss.
  2. `↗ Endermaster 234m` — flecha relativa a hacia dónde está mirando el
     jugador + distancia al spawn; durante la pelea sigue al boss en vivo.
  3. Antes del spawn: `⏳ El jefe spawneará en 29:59`. Durante la pelea:
     `❤ 78%`.
- **Por qué paquetes a mano en vez del scoreboard normal**: cada jugador
  necesita ver un contenido DISTINTO (su propia flecha, según su posición y
  hacia dónde mira) — el `Scoreboard` vanilla compartido manda el mismo texto a
  todos los que miran un slot. Se arma un `Objective` "suelto" (nunca
  registrado en `server.scoreboard`) y se mandan
  `ClientboundSetObjectivePacket`/`ClientboundSetDisplayObjectivePacket`/
  `ClientboundSetScorePacket` directo a cada conexión.
- **Costo**: el escaneo de entidades es sobre chunks ya cargados (si nadie está
  cerca de la arena, no hay nada que iterar — prácticamente gratis).
- **Comando**: `/hardmod arenas reload`.

## Leaderboard de vidas (`/lifestop`)

Chest GUI **pública** (cualquiera la puede abrir, sin permiso de admin) con una
cabeza por jugador — su skin real — y cuántas vidas le quedan, ordenado de más
a menos.

- **Archivos**: [`LivesLeaderboardGui.kt`](../src/main/kotlin/com/hardmod/gui/LivesLeaderboardGui.kt),
  helper de cabezas en [`GuiItems.kt`](../src/main/kotlin/com/hardmod/gui/GuiItems.kt) (`playerHead`).
- **Fuente de datos**: el mismo objective de scoreboard vanilla `hdc_lives` que
  usa [`LivesEliminationAnnouncer`](#anuncio-de-eliminación-permanente) — sin
  acoplarse a `hard-death-core-mod`.
- **Skins reales**: para un jugador actualmente online se usa su `GameProfile`
  completo (ya trae la textura, sin ida y vuelta a Mojang); para uno offline se
  usa `ResolvableProfile.createUnresolved(nombre)` — el mismo mecanismo que
  usa vanilla para `/give @s player_head[profile=Nombre]`, resuelve la textura
  en segundo plano solo con el nombre.
- **Solo lectura**: ningún slot es interactuable (mismo `ChestGuiMenu` estilo
  "plugin" bloqueado que el resto de las GUIs), paginado de a 45 cabezas.
- **Comando**: `/lifestop` — raíz propia, sin requerir `COMMANDS_GAMEMASTER`.

> **Nota sobre `/baltop`**: el balance de la economía (`/economia baltop`, ya
> existe como comando de texto) vive solo en el `SavedData` de
> `hard-death-core-mod` — a diferencia de las vidas, no hay ningún scoreboard
> ni dato vanilla que lo refleje, así que no se puede construir un
> `/baltop` con cabezas desde HardMod sin acoplarse a ese mod. Si se quiere
> una versión chest-GUI, va del lado de `hard-death-core-mod` (reutilizando
> `EconomyManager.top()`, que ya existe justo para esto).

## Recompensas de trial chambers

Editor visual (chest GUI) de las recompensas de las vaults de trial chambers —
llave normal y llave ominosa — con override persistido.

- **Archivos**: [`TrialRewardConfig.kt`](../src/main/kotlin/com/hardmod/feature/trialrewards/TrialRewardConfig.kt),
  [`TrialRewardDefaultsLoader.kt`](../src/main/kotlin/com/hardmod/feature/trialrewards/TrialRewardDefaultsLoader.kt),
  [`TrialRewardLootModifier.kt`](../src/main/kotlin/com/hardmod/feature/trialrewards/TrialRewardLootModifier.kt),
  [`TrialRewardEditorGui.kt`](../src/main/kotlin/com/hardmod/feature/trialrewards/TrialRewardEditorGui.kt),
  [`BookLibraryGui.kt`](../src/main/kotlin/com/hardmod/feature/trialrewards/BookLibraryGui.kt),
  [`BookPoolEditorGui.kt`](../src/main/kotlin/com/hardmod/feature/trialrewards/BookPoolEditorGui.kt).
- **Modelo simplificado**: cada entrada es item + peso decimal + rango de
  cantidad (+ pool de encantamiento opcional para libros). El peso decimal (no
  entero) representa la probabilidad esperada REAL de la tabla vanilla — se
  redondea a entero recién al construir el `LootPool` de verdad, con un factor
  de escala común (`TrialRewardLootModifier`) para minimizar el error de
  redondeo.
- **Cálculo del default de vanilla** (`TrialRewardDefaultsLoader`): lee la loot
  table JSON cruda vía `ResourceManager` (no la API de `LootTable` en runtime).
  Las tablas de vanilla no listan items directo — referencian otras tablas
  anidadas (`reward_common`/`rare`/`unique`, algunas detrás de un
  `random_chance`) — el resolver baja recursivamente y calcula el **valor
  esperado real** de cada item hoja (la esperanza es lineal, no es una
  aproximación). El rango de tiradas (`computeRollRange`) suma min/max de los
  pools de nivel superior, tratando los pools con `random_chance` como que
  contribuyen 0 al mínimo.
- **Aire (sin recompensa)**: representado con `minecraft:air` como id sentinela
  (ícono `structure_void` en el editor, porque aire normal no se ve bien como
  item en un slot) — al aplicar el override se convierte en una entrada
  `EmptyLootItem` real de vanilla (`minecraft:empty`), no un item "vacío".
- **Libros con pool de encantamiento**: la vault normal tiene 2 pools de libro
  distintos (combate/utilidad, y acuático/tridente); la ominosa tiene 3
  (Breach/Density, Knockback/Punch/Smite/Looting/Multishot, y Wind Burst). Se
  editan desde la "Biblioteca" (`BookLibraryGui`), que lista solo los libros de
  la tabla actual y abre `BookPoolEditorGui` para prender/apagar cada
  encantamiento del catálogo curado (42 opciones).
- **Reemplazo real**: `LootTableEvents.REPLACE` (no `MODIFY` — `MODIFY` solo
  permite AGREGAR pools encima de los de vanilla, no sacarlos) — mientras no
  haya override guardado para una tabla, deja pasar la tabla vanilla sin
  tocarla.
- **GUI**: paginada (27 entradas por página), fila de "staging" donde se puede
  soltar un item nuevo del inventario, "Guardar y Aplicar" (persiste + recarga
  recursos), "Restaurar Default de Minecraft" (descarta el override y
  recalcula todo desde la tabla real de vanilla, items Y rango de tiradas).
- **Comandos**: `/hardmod rewards normal`, `/hardmod rewards ominous` (abren el
  editor para esa tabla).

## Anuncios y presets

Banner de chat consistente + sonido de ping, y presets reutilizables que además
pueden ejecutar comandos.

- **Archivos**: [`Announcer.kt`](../src/main/kotlin/com/hardmod/announce/Announcer.kt),
  [`MessagePresets.kt`](../src/main/kotlin/com/hardmod/announce/MessagePresets.kt).
- **Formato**: banner de 3 líneas — separador con el título incrustado
  (`---- ☠ HardDeath ----`), el mensaje solo, y otro separador de cierre. Sonido
  configurable (`announceSoundId` en `config.json`, default
  `entity.experience_orb.pickup`).
- **Parser de colores `&`**: acumula estilos (`Style.applyFormat`, no solo el
  último código) para poder combinar color + negrita + tachado a la vez, y se
  resetea tanto en `&r` como en cada salto de línea (para que un tachado de una
  línea no se filtre a la siguiente).
- **Presets** (`config/hardmod/presets.json`): cada uno es `{message, commands}`
  — el mensaje se anuncia y después los comandos se ejecutan con permiso de
  CONSOLA (no el del jugador que dispara el preset), pensado para cambios que
  este mod no gestiona directo (mobs más fuertes, una mecánica de otro mod,
  subir la dificultad, etc.). Formato viejo (solo string, sin comandos) sigue
  soportado.
- **Comandos**: `/hardmod announce <mensaje>`, `/hardmod announce preset
  <nombre>`, `/hardmod announce status` (anuncia el estado de TODOS los
  ajustes del mod — la única vez que un "resumen de estado" se hace público, y
  es explícito, no automático).

## Panel de administración

Chest GUI con un item-toggle por ajuste, para admins que prefieran clickear en
vez de tipear comandos.

- **Archivos**: [`AdminPanelGui.kt`](../src/main/kotlin/com/hardmod/gui/AdminPanelGui.kt),
  [`ChestGuiMenu.kt`](../src/main/kotlin/com/hardmod/gui/ChestGuiMenu.kt),
  [`GuiItems.kt`](../src/main/kotlin/com/hardmod/gui/GuiItems.kt).
- **Cómo**: cada slot ocupado es un toggle/ciclo silencioso (cambia el config y
  refresca el item, sin anunciar nada) salvo el de la campana ("Recordar Estado
  Actual"), que sí llama a `Announcer.broadcast` — es la versión-panel de
  `/hardmod announce status`, una acción explícita.
- **Estilo "menu de plugin"**: `ChestGuiMenu` bloquea cualquier slot sin acción
  registrada — no se puede sacar los paneles de vidrio decorativos ni mover
  nada fuera de lo previsto.
- **Comando**: `/hardmod panel`.
