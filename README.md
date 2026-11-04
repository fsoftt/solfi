# Solfi

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
* **Piano libre**: 3 octavas con desplazamiento, multitoque y etiquetas.
* **Progreso**: XP, 1–3 estrellas por precisión, desbloqueo secuencial, examen que desbloquea el siguiente nivel y
  da una insignia, racha diaria. La lógica es pura y está probada (`ProgressRepository`).
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

## Estado conocido

* El target Android se ha escrito completo pero **no se ha podido compilar en el entorno de desarrollo inicial**
  (sin plataformas del Android SDK instaladas). La verificación se hizo con el target de escritorio.
* El código de iOS no se ha compilado (solo es posible en macOS).
* La latencia de entrada táctil no se compensa en el puntaje de ritmo (se usa el reloj de audio sin calibración).
* Solo existe el nivel 1; los demás muestran «Próximamente».
