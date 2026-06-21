# Assets de países y PlaceholderAPI

Este documento cubre puntos de integración complementarios del core `NetworkPlayerSettings`. El core no registra comandos, inventarios ni menús.

## `NetworkAssetService`

NetworkPlayerSettings registra `NetworkAssetService` durante startup después de cargar `assets/countries.yml`.

```java
import com.stephanofer.networkplayersettings.assets.api.CountryAsset;
import com.stephanofer.networkplayersettings.assets.api.NetworkAssetService;

NetworkAssetService assets = Bukkit.getServicesManager().load(NetworkAssetService.class);
CountryAsset argentina = assets.countryAsset("AR");
CountryAsset byAlias = assets.countryAsset("argentina");
CountryAsset fallback = assets.countryAsset("no-existe"); // XX
```

Comportamiento:

- `countryAsset(String)` acepta código canónico o alias.
- Códigos se buscan en mayúsculas; aliases en minúsculas.
- `null`, strings blancos o desconocidos devuelven `unknownCountryAsset()`.
- `countryAssets()` expone un mapa inmutable con códigos canónicos, no aliases.
- Las búsquedas de gameplay son en memoria; el catálogo se carga una vez al bootstrap.

## Flags por emoji

`CountryFlag` no depende del catálogo YAML. Sirve para normalizar y renderizar flags:

```java
import com.stephanofer.networkplayersettings.settings.country.CountryFlag;

String code = CountryFlag.normalizeCode(settings.countryCode(playerId));
String flag = CountryFlag.emoji(code);
```

Fallbacks:

- Código inválido o `null` -> `XX`.
- Emoji de `XX` -> `🏳`.

## PlaceholderAPI

La expansión se registra solo si:

1. `placeholderapi.enabled: true`;
2. el plugin PlaceholderAPI está instalado y habilitado.

Metadata real: `softdepend: [PlaceholderAPI]`.

Identificador de expansión: `playersettings`.

| Placeholder | Valor |
|---|---|
| `%playersettings_language%` | Idioma efectivo (`es`/`en`) para jugador online. Offline usa preferencia manual cacheada si existe; si está en `auto` o no hay UUID, usa `settings.default-language`. |
| `%playersettings_language_preference%` | Preferencia guardada/cacheada (`auto`/`es`/`en`). Si no hay jugador/UUID, fallback `auto`. |
| `%playersettings_language_name%` | Nombre del idioma efectivo visto desde ese idioma. Offline sigue la misma regla de fallback que `%playersettings_language%`. |
| `%playersettings_country%` | País efectivo (`AR`, `XX`, etc.). Si no hay jugador/UUID, fallback `XX`. |

Los placeholders se cachean por `playerId:param` durante `placeholderapi.cache-ttl-millis` si el TTL es positivo. Con TTL `0` o negativo, no se cachean. La expansión invalida el caché del jugador cuando recibe `PlayerSettingChangeEvent` y también al salir el jugador.

## Consumo desde interfaces externas

NetworkPlayerSettings no expone comandos ni menús propios. Un plugin consumidor puede construir cualquier interfaz usando únicamente:

- `PlayerSettingsService` para leer/mutar idioma y país;
- `NetworkAssetService` para renderizar assets de países;
- `PlayerSettingsReadyEvent` para esperar datos listos;
- `PlayerSettingChangeEvent` para reaccionar a cambios.

Regla importante: la UI externa no debe instanciar servicios internos ni escribir directo en la base de datos. Debe usar `ServicesManager` y los contratos públicos documentados en [`api-publica.md`](api-publica.md).
