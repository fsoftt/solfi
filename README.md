# Solfi

**Sitio web:** https://fsoftt.github.io/solfi/ · **App en el navegador:** https://fsoftt.github.io/solfi/app/

Aplicación nativa para **aprender música desde cero**, pensada para niños y adultos. Escrita en Kotlin con
Compose Multiplatform (Android, iOS y escritorio JVM), sin backend: todo funciona sin conexión y el progreso
se guarda en el dispositivo.

Esta es la **fase 1**: arquitectura, motor de audio, toda la interfaz y el contenido del **nivel 1**
(Iniciación). Los niveles 2 a 5 se añaden como archivos JSON independientes (ver [CONTENT.md](CONTENT.md)).

## Características

* **Lecciones interactivas** con 7 tipos de paso: explicación, escucha con resaltado, preguntas de opción múltiple,
  identificar notas en el pentagrama, entrenamiento auditivo, teclado de piano y ritmo (tocar al compás).
* **Pentagrama propio** dibujado en Canvas: claves de sol y fa, compás, armadura, figuras, plicas, corchetes y barras,
  puntillos, alteraciones, líneas adicionales, silencios, ligaduras, varias líneas por compases y nombre bajo cada nota.
  Resalta la nota que suena y permite tocar una nota para oírla.
* **Sintetizador común** (44,1 kHz, mono): voz tipo piano con armónicos y ADSR, polifonía, metrónomo con acento y
  secuenciador con precisión de muestra. La posición de reproducción (en pulsos) alimenta el resaltado y el reloj de
  audio puntúa los toques de ritmo.
* **Solfeos**: reproducir/pausar/detener, tempo 40–160, metrónomo, cuenta previa, repetición y modo «solo pulso».
* **Solfeos aleatorios**: pestaña «Aleatorios» con 4 dificultades (Básico, Intermedio, Avanzado, Experto), todas disponibles desde el inicio. Un generador puro y determinista (PRNG propio SplitMix64, mismo número de ejercicio = misma melodía en todas las plataformas) crea frases antecedente/consecuente con cadencia suspensiva y final en la tónica, a partir de bibliotecas de células rítmicas por compás. El ejercicio no suena solo: se practica con pulso y cuenta previa, luego «Escuchar solución». Autoevaluación «¿Cómo te fue?» (Bien/Regular/Repetir = 5/3/0 XP, máx. 20 premios al día, sin afectar estrellas) y opción «Ir a ejercicio #» para repetir una semilla.
* **Preferencia de clave** por perfil: Recomendada (según el instrumento), Sol o Fa. Las melodías se reescriben en la clave elegida; el contenido que habla de posiciones del pentagrama puede fijar su clave con `clefLocked` (ver [CONTENT.md](CONTENT.md)).
* **Tocar con tu instrumento** (micrófono): en los pasos de escucha/explicación, en los pasos de teclado («🎤 Mi instrumento») y en los solfeos se puede tocar la melodía con el instrumento real. Un detector de tono YIN propio (`PitchDetector`, Kotlin puro) y un seguidor nota a nota (`NoteFollower`) esperan a que suene la nota correcta e indican si está **afinada, un poco alta/baja o es otra nota más alta/baja**. Se tienen en cuenta los **instrumentos transpositores** (`InstrumentTuning`): trompeta y clarinete en Si♭, saxo alto en Mi♭, saxo tenor en Si♭, trompa en Fa, guitarra y contrabajo una octava grave. Voz y «Otro» aceptan cualquier octava (y hay una opción para todos).
* **Pestaña «Practicar» — Practica con tu instrumento**: afinador (nota escrita para tu instrumento y nota de concierto), «Lee y toca» (notas sueltas dentro del registro del instrumento, con o sin alteraciones), melodías de las lecciones y melodías aleatorias.
  Micrófono: `AudioRecord` + permiso `RECORD_AUDIO` (Android), `AVAudioEngine` (iOS: añadir `NSMicrophoneUsageDescription` al Info.plist), `TargetDataLine` (escritorio) y `getUserMedia` (web, requiere https).
* **Piano libre**: 3 octavas con desplazamiento, multitoque y etiquetas.
* **Progreso**: XP, 1–3 estrellas por precisión, desbloqueo secuencial, examen que desbloquea el siguiente nivel y
  da una insignia, racha diaria.
* **Examen para saltar un nivel**: desde el mapa, cualquier nivel no aprobado (también los bloqueados) se puede saltar
  haciendo directamente su examen. Con al menos un 70 % se completan sus lecciones y las de los niveles anteriores,
  se desbloquea el siguiente y se gana la insignia del nivel; las lecciones saltadas se pueden repetir para ganar estrellas. La lógica es pura y está probada (`ProgressRepository`).
* **Modo Niño / Adulto**: botones grandes, color, mascota dibujada (nota musical con cara), confeti y textos
  juguetones frente a un estilo más sobrio.
* **Perfiles locales** múltiples con avatar, idioma, nombres de nota, tema y volumen propios.
* **Idiomas**: español (por defecto), inglés y portugués; se detecta el del sistema la primera vez y se puede cambiar
  por perfil. Los nombres de nota (solfeo *Do Re Mi* o letras *C D E*) son un ajuste independiente del idioma.
* **Háptica** en las respuestas (Android e iOS), modo oscuro, accesibilidad (descripciones de contenido) y
  diseño de borde a borde.

## Stack

| Pieza | Versión |
|---|---|
| Kotlin Multiplatform | 2.1.21 |
| Compose Multiplatform + Material 3 | 1.8.2 |
| Android Gradle Plugin | 8.7.3 (Gradle 8.11.1, JDK 17) |
| kotlinx.coroutines / serialization / datetime | 1.10.2 / 1.8.1 / 0.6.2 |

Las versiones están en `gradle/libs.versions.toml`. Persistencia propia con `expect/actual`
(SharedPreferences / NSUserDefaults / `java.util.prefs`), audio con `AudioTrack` / `AVAudioEngine` /
`SourceDataLine`.

## Cómo ejecutar

Requisitos: JDK 17 o superior. El wrapper de Gradle ya está incluido.

### Escritorio (Windows, macOS, Linux)

```bash
./gradlew :composeApp:run              # abre la app
./gradlew :composeApp:desktopTest      # ejecuta los tests
./gradlew :composeApp:compileKotlinDesktop
```

### Web (navegador)

La misma app compilada a WebAssembly (Kotlin/Wasm) con la misma interfaz Compose, contenido, sintetizador y
progreso (guardado en `localStorage`). Necesita un navegador con WebAssembly GC: Chrome/Edge 119+, Firefox 120+,
Safari 18.2+.

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # servidor local con recarga
./gradlew :composeApp:publishWebApp                 # build de producción copiado a docs/app/
```

El workflow `.github/workflows/web.yml` recompila y hace commit de `docs/app/` en cada push que toque el código,
y `.github/workflows/pages.yml` publica `docs/` en GitHub Pages (Settings → Pages → Source: «GitHub Actions»), que la sirve en `/solfi/app/`. El código específico está en `composeApp/src/wasmJsMain/`: audio con
Web Audio (`ScriptProcessorNode`, con remuestreo si el navegador no acepta 44,1 kHz), botón «atrás» del navegador,
vibración y fuentes incluidas (Roboto y subconjuntos de Noto Color Emoji / Noto Music / Noto Sans Symbols).

### Android

Necesitas el Android SDK (plataforma 35 y build-tools). Define `ANDROID_HOME` / `ANDROID_SDK_ROOT` o crea
`local.properties` con `sdk.dir=...`. El target Android **solo se activa si encuentra un SDK con alguna plataforma
instalada** (así el resto se puede compilar sin él).

```bash
./gradlew :composeApp:assembleDebug    # APK en composeApp/build/outputs/apk/debug/
./gradlew :composeApp:installDebug     # instala en un dispositivo/emulador conectado
```

### iOS (requiere macOS con Xcode)

El framework `ComposeApp` está configurado (`iosArm64`, `iosSimulatorArm64`) y no se puede compilar en Windows.
En un Mac:

1. Crea en Xcode un proyecto iOS «App» (SwiftUI) en `iosApp/` y sustituye `iOSApp.swift` y `ContentView.swift` por
   los archivos de `iosApp/iosApp/` de este repositorio.
2. Añade una *Run Script phase* antes de compilar: `cd "$SRCROOT/.." && ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
3. En *Build Settings* → *Framework Search Paths* añade `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` y enlaza `ComposeApp`.
4. Ejecuta en un simulador o dispositivo.

El código iOS específico (`AVAudioEngine` + `AVAudioSourceNode`, háptica, `NSUserDefaults`) está en
`composeApp/src/iosMain/` y no se ha podido verificar en Windows.

## Estructura

```
composeApp/src/
  commonMain/kotlin/com/solfi/app/
    music/      teoría: Pitch, nombres de nota, intervalos, escalas, armaduras, acordes, Melody + parser
    audio/      Synth (voces), Mixer (precisión de muestra), AudioEngine (reproducción y posición)
    content/    modelos @Serializable, ContentValidator, ContentRepository
    data/       perfiles, progreso, ProfileStore
    logic/      ProgressRepository (reglas de XP/estrellas/desbloqueo/racha), RhythmScorer
    i18n/       interfaz Strings + EsStrings / EnStrings / PtStrings
    platform/   expect: AudioSink, Haptics, KeyValueStore, PlatformLock, BackHandler...
    ui/         theme, notation (StaffLayout + StaffView), widgets (Mascot, Confetti, PianoKeyboard), screens
    App.kt  AppState.kt
  commonMain/composeResources/files/content/level1.json
  commonTest/   teoría, melodías, audio/secuenciador, progreso, textos, layout del pentagrama
  desktopTest/  validación de todos los levelN.json + capturas de pantallas (build/render/*.png)
  androidMain/  MainActivity, manifiesto, icono adaptativo vectorial, actuales Android
  iosMain/      actuales iOS y MainViewController
  desktopMain/  main() y actuales de escritorio
iosApp/         archivos Swift para el proyecto de Xcode
CONTENT.md      formato de contenido para escribir los niveles 2–5
```

## Cómo añadir un nivel

1. Copia la estructura de `level1.json` a `level2.json` (…`level5.json`) en
   `composeApp/src/commonMain/composeResources/files/content/`.
2. Sigue [CONTENT.md](CONTENT.md): 6–8 lecciones (la última es examen), 3–6 pasos por lección, ≥ 4 solfeos,
   insignia, textos en `es`/`en`/`pt` y compases que suman exactamente.
3. Ejecuta `./gradlew :composeApp:desktopTest`: `ContentFilesTest` valida automáticamente todos los niveles presentes.

No hace falta tocar código: el mapa de niveles, el progreso y las insignias se construyen a partir de los archivos.

## Cómo añadir un idioma

1. Añade el valor en `enum class Lang` (`music/Pitch.kt`) y sus nombres de nota/alteraciones en `NoteNames`.
2. Crea `XxStrings : Strings` en `i18n/` (el compilador fallará hasta implementar **todas** las cadenas) y añádelo a
   `stringsFor()`.
3. Añade el campo al `L10n` de `content/Models.kt` y a `L10n.get()`, y traduce `level*.json` (el validador exige
   todos los idiomas no vacíos).
4. Añade el idioma al selector de `OnboardingScreen`/`SettingsScreen` (se recorre `Lang.entries`).

## Publicación (release)

La publicación en Google Play la hace el workflow `.github/workflows/release.yml`. La clave de release **NO está en el repositorio** (el repo es público): solo vive en los secretos de GitHub Actions.

### Secretos necesarios (Settings > Secrets and variables > Actions)

| Secreto | Contenido |
|---|---|
| `SOLFI_RELEASE_KEYSTORE_BASE64` | Keystore de release codificado en base64 (`base64 -w0 release.keystore`) |
| `SOLFI_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `SOLFI_KEY_ALIAS` | Alias de la clave dentro del keystore |
| `SOLFI_KEY_PASSWORD` | Contraseña de la clave |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON completo de la cuenta de servicio de Google Play (texto plano) |

Si `PLAY_SERVICE_ACCOUNT_JSON` está vacío, el workflow compila y firma pero omite la subida a Play.

### Cómo publicar

```
git tag v1.0.0 && git push origin v1.0.0
```

O manualmente: Actions > Release > Run workflow, eligiendo `track` (internal, alpha, beta, production) y `status`. Los tags suben a `internal` como `draft` y además crean un GitHub Release con el APK y el AAB firmados. El `versionCode` es el número de ejecución + 100 y el `versionName` es el tag sin la `v`.

### Primera subida manual (una sola vez)

La API de Play no puede crear la app. Crea la app en Play Console, completa la ficha y sube a mano el primer AAB firmado (por ejemplo a pruebas internas), activando Play App Signing. Desde entonces el workflow puede subir versiones.

Primera vez: ejecuta el workflow manualmente con `upload_to_play=false`, descarga el artefacto `solfi-release-<versionName>` (AAB y APK firmados, se sube en todas las ejecuciones) y sube el AAB a mano en Play Console.

### Permisos de la cuenta de servicio

En Play Console > Usuarios y permisos, invita el correo de la cuenta de servicio con acceso a la app y permisos de "Publicar versiones en pruebas / producción" y "Gestionar versiones" (releases). La API de Android Publisher debe estar habilitada en el proyecto de Google Cloud.

### Draft vs completed

Mientras la app nunca se haya publicado, Play solo acepta versiones en estado `draft`: hay que revisarlas y lanzarlas a mano en Play Console. Con `completed` la versión se lanza directamente al track (solo funciona cuando la app ya está publicada).

Localmente, `./gradlew :composeApp:assembleRelease` sin las variables `SOLFI_KEYSTORE_FILE`, `SOLFI_KEYSTORE_PASSWORD`, `SOLFI_KEY_ALIAS` y `SOLFI_KEY_PASSWORD` genera un APK sin firmar y muestra un aviso. R8/minify está desactivado por ahora.

## Estado conocido

* El target Android se ha escrito completo pero **no se ha podido compilar en el entorno de desarrollo inicial**
  (sin plataformas del Android SDK instaladas). La verificación se hizo con el target de escritorio.
* El código de iOS no se ha compilado (solo es posible en macOS).
* La latencia de entrada táctil no se compensa en el puntaje de ritmo (se usa el reloj de audio sin calibración).
* Solo existe el nivel 1; los demás muestran «Próximamente».

## Licencia

MIT. Consulta el archivo [LICENSE](LICENSE).
