# Documentación de HardMod

Índice de la documentación técnica del mod. Para instalación/compilación ver el
[README](../README.md) de la raíz — esto es sobre cómo está armado por dentro.

- **[architecture.md](architecture.md)** — mapa de archivos, paquetes, cómo persiste su
  configuración, y los principios de diseño que se repiten en todo el mod (toggles
  silenciosos, cero acoplamiento con los mods vecinos).
- **[features.md](features.md)** — qué hace cada feature, sus defaults, y en qué
  archivos vive.
- **[commands.md](commands.md)** — árbol completo de `/hardmod`, qué hace cada
  subcomando y qué feedback da.

## Qué es HardMod

Mod 100% server-side (sin `src/client`) para endurecer un mundo estilo hardcore:
controla mobcap, bloquea/desbloquea la mesa de encantamientos, elimina aldeanos de
aldeas generadas, restringe la mesa para tridentes sin bloquear el yunque,
controla tótems y raids, hace la quemadura
permanente opcional, sella Nether/End hasta que un admin los active, dispara una
sorteos periódicos de PVP después de las 19:00, cierra el servidor a una hora fija
(extendible), anuncia cuando un jugador se queda sin vidas, muestra una brújula
hacia los bosses de arena de `hard-death-mobs-mod`, y permite editar las
recompensas de las vaults de trial chambers desde una chest GUI.

Todo se administra con el comando `/hardmod` (requiere `COMMANDS_GAMEMASTER`) o
con el panel visual (`/hardmod panel`).

## Regla de oro: ningún toggle anuncia solo

Ni un solo cambio de config (mesa, aldeanos, tridente, tótems, raids, quemadura,
mobcap, nether, end, PVP manual) dispara un mensaje automático al chat — ni por comando ni por el
panel. Todo lo que cambia un ajuste devuelve feedback solo al admin que lo
ejecutó (`ctx.source.sendSuccess`). El único mecanismo que sí anuncia a **todo el
mundo** es `/hardmod announce` (custom / preset / status) porque es una acción
deliberada, no un efecto secundario. Ver [features.md](features.md#anuncios-y-presets).

## Cero acoplamiento con los mods vecinos

Dos features leen datos de otro mod (`hard-death-core-mod` para las vidas,
`hard-death-mobs-mod` para las arenas de boss) sin importar ni una sola clase de
esos mods:

- **LivesEliminationAnnouncer** lee el objective de scoreboard vanilla
  `hdc_lives` que ya sincroniza `hard-death-core-mod` — scoreboard es una API
  100% vanilla, cualquier mod puede leerla.
- **BossArenaCompass** lee los archivos `config/harddeathmobs/arenas/*.json`
  (solo lectura, en disco) para conocer horarios y posiciones. Muestra la
  brújula 30 minutos antes apuntando al futuro spawn, y detecta que una pelea
  arrancó observando el mundo: busca seres vivos con nombre personalizado
  visible cerca de esa posición.
- **BlackMarketSidebarTracker** consume el snapshot y la suscripción de eventos
  de `npc-mod`; muestra solo que el mercado está abierto y su tiempo restante.

Estas integraciones evitan dependencias de compilación entre mods y mantienen a
HardMod compilable e instalable por separado.
