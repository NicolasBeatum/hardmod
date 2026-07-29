# Comandos

Raíz: `/hardmod` — requiere el permiso `COMMANDS_GAMEMASTER` (igual que
`mision-admin` en mision-mod). Todo lo que cambia un ajuste da feedback solo al
admin que lo ejecutó (no anuncia al chat) salvo lo marcado como "público" abajo.
Ver [features.md](features.md) para el detalle de cada feature.

Además existe `/lifestop` como comando raíz propio (no bajo `/hardmod`), sin
requerir ningún permiso especial — cualquier jugador lo puede usar.

| Comando | Qué hace |
|---|---|
| `/hardmod panel` | Abre el panel visual (chest GUI) con todos los toggles. |
| `/hardmod mobcap <categoria> <multiplicador>` | Fija el multiplicador de cap de spawn para esa `MobCategory` (ej. `monster`, `creature`). `multiplicador` acepta decimales (mínimo 0.1). |
| `/hardmod enchanttable lock [segundos]` | Bloquea la mesa de encantamientos. Sin `segundos`, queda bloqueada indefinido; con `segundos`, se desbloquea sola al pasar ese tiempo. |
| `/hardmod enchanttable unlock [segundos]` | Igual que `lock` pero al revés (desbloquea, opcionalmente por tiempo limitado). |
| `/hardmod villagers on` | Permite el spawn de aldeanos de nuevo. |
| `/hardmod villagers off` | Bloquea el spawn de aldeanos (aldeas generadas y runtime, con las excepciones de cría/cura — ver features.md). |
| `/hardmod trident off` | La mesa no restringe los encantamientos del tridente. |
| `/hardmod trident block` | La mesa no puede encantar tridentes; el yunque sigue libre. |
| `/hardmod trident unbreaking3` | En la mesa, el tridente solo puede obtener Unbreaking (máximo III); el yunque sigue libre. |
| `/hardmod totem chance <0-100>` | Configura la probabilidad de activación del tótem; 0 lo desactiva y 100 conserva vanilla. |
| `/hardmod raids on` | Activa las raids. |
| `/hardmod raids off` | Detiene y desactiva las raids. |
| `/hardmod burn on` | Activa la quemadura permanente (no se apaga sola, solo con agua). |
| `/hardmod burn off` | Vuelve la quemadura a la normalidad de vanilla. |
| `/hardmod nether lock` | Sella el Nether (no se puede encender portal, y `entityInside` queda cancelado igual). |
| `/hardmod nether unlock` | Habilita el Nether. |
| `/hardmod end lock` | Sella el End (no se puede activar el portal con ojo de ender). |
| `/hardmod end unlock` | Habilita el End. |
| `/hardmod pvp on [minutos]` | Activa el PVP manualmente (ignora el enfriamiento). Sin `minutos`, sortea una duración entre 15 y 60. |
| `/hardmod pvp off` | Cancela el PVP activo y arranca el enfriamiento de 30 minutos normal. |
| `/hardmod server extend <minutos>` | Suma minutos al cierre diario programado del servidor. |
| `/hardmod server time <hora> [minuto]` | Cambia la hora de cierre diario del servidor (ej. `2` o `2 30`). |
| `/hardmod arenas reload` | Vuelve a leer `config/harddeathmobs/arenas/*.json` del disco (usar después de un `/hdm arena <id> setpos` en caliente). |
| `/hardmod rewards normal` | Abre el editor de recompensas de la vault de llave normal. |
| `/hardmod rewards ominous` | Abre el editor de recompensas de la vault de llave ominosa. |
| `/hardmod announce <mensaje>` | **Público.** Anuncia `<mensaje>` a todo el servidor con el banner + sonido. |
| `/hardmod announce preset <nombre>` | **Público.** Anuncia el mensaje del preset y ejecuta sus comandos (con permiso de consola) — ver `config/hardmod/presets.json`. |
| `/hardmod announce status` | **Público.** Anuncia el estado actual de TODOS los ajustes del mod (mesa, aldeanos, tridente, tótems, raids, quemadura, nether, end, mobcap, PVP, hora de cierre del servidor). |
| `/lifestop` | **Público, sin permiso.** Abre el leaderboard de vidas (chest GUI con cabezas de jugadores reales). No es un subcomando de `/hardmod`. |

## Notas

- Los subcomandos marcados **Público** son las únicas acciones de este mod que
  llegan a todos los jugadores — todo lo demás es feedback privado al admin.
- `/hardmod panel` da acceso a los mismos toggles que los comandos de arriba
  (salvo `pvp`, `server extend` y `arenas reload`, que por ahora solo existen
  como comando) más un botón de campana equivalente a `announce status`.
