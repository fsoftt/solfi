# Formato de contenido de Melodía (`levelN.json`)

Este documento es suficiente para escribir `level2.json` … `level5.json` sin leer el código.
El esquema tipado vive en `composeApp/src/commonMain/kotlin/com/melodia/app/content/Models.kt`
y las reglas de validación en `.../content/ContentValidator.kt`.

## Dónde van los archivos

```
composeApp/src/commonMain/composeResources/files/content/level1.json
composeApp/src/commonMain/composeResources/files/content/level2.json   <- nuevo
...
composeApp/src/commonMain/composeResources/files/content/level5.json
```

* El nombre debe ser `level<N>.json` y `"level": N` dentro del archivo debe coincidir.
* Si un archivo no existe (o no se puede leer) la app muestra «Próximamente» para ese nivel.
* No hay que tocar código: la app carga automáticamente los niveles 1 a 5 que existan.
* Los niveles se desbloquean en orden: aprobar el examen (última lección) del nivel N desbloquea el N+1.

## Cómo validar tu archivo

```
./gradlew :composeApp:desktopTest
```

`ContentFilesTest` valida **todos** los `levelN.json` presentes: estructura, idiomas, índices de
respuesta, y que **cada melodía se parsee y llene sus compases exactamente**. El JSON es estricto:
una clave desconocida o un tipo erróneo falla la decodificación.

## Texto localizado (`L10n`)

Todo texto visible es un objeto con los tres idiomas, ninguno vacío:

```json
{ "es": "Hola", "en": "Hello", "pt": "Olá" }
```

Los nombres de nota dentro de los textos se escriben en solfeo con la letra entre paréntesis
(`"Do (C)"`) porque el sistema de nombres (Do Re Mi / C D E) lo elige el usuario y los textos son fijos.
Los nombres que **genera la app** (opciones `note`, etiquetas del pentagrama, teclado) sí respetan esa opción.

## Estructura del archivo

```jsonc
{
  "level": 2,                       // 1..5
  "title": { "es": "...", "en": "...", "pt": "..." },
  "description": { "es": "...", "en": "...", "pt": "..." },
  "color": "#00B8A9",               // #RRGGBB
  "lessons": [ /* 6 a 8 lecciones; la ÚLTIMA es el examen (isExam: true) */ ],
  "solfeos": [ /* mínimo 4 */ ],
  "badge": {
    "id": "badge-level2",           // único
    "name": { "es": "...", "en": "...", "pt": "..." },
    "description": { "es": "...", "en": "...", "pt": "..." },
    "emoji": "🏅"
  }
}
```

### Lección

```jsonc
{
  "id": "l1-pulse",                 // único dentro del nivel (se usa para guardar el progreso)
  "title": { "es": "...", "en": "...", "pt": "..." },
  "emoji": "🥁",
  "isExam": false,                  // true solo en la última lección
  "steps": [ /* 3 a 6 pasos */ ]
}
```

Reglas: 6–8 lecciones; 3–6 pasos por lección; solo la última es examen; el examen debe tener al menos
3 pasos puntuables (todos menos `explain` y `listen`). Un examen se aprueba con ≥ 60 % de precisión.

### Solfeo

```jsonc
{
  "id": "s1-doremi",
  "title": { "es": "...", "en": "...", "pt": "..." },
  "description": { "es": "...", "en": "...", "pt": "..." },
  "melody": { "clef": "treble", "time": "4/4", "key": "C", "tempo": 80, "notes": "C4:q D4:q E4:h | ..." }
}
```

## Notación de melodías (`MelodySpec`)

```json
{ "clef": "treble", "time": "4/4", "key": "C", "tempo": 80,
  "notes": "C4:q D4:q E4:h | r:q G4:8 G4:8 A4:h" }
```

| Campo | Valores | Por defecto |
|---|---|---|
| `clef` | `treble`, `bass` | `treble` |
| `time` | `2/4`, `3/4`, `4/4`, `6/8`, `3/8`, `12/8`… (`beats/unit`, unit ∈ 1,2,4,8,16) | `4/4` |
| `key` | `C G D A E B F# C#` / `F Bb Eb Ab Db Gb Cb` y menores `Am Em Bm F#m C#m G#m D#m A#m Dm Gm Cm Fm Bbm Ebm Abm` | `C` |
| `tempo` | 30–220 (negras por minuto) | `80` |
| `notes` | cadena de notación (abajo) | obligatorio |

### Sintaxis de `notes`

Los símbolos se separan por espacios. `|` marca una barra de compás.

* **Nota**: `<altura>:<duración>` → `C4:q`, `F#4:8`, `Bb3:h`.
  La altura es la letra `C D E F G A B`, alteraciones `#` (sostenido) o `b` (bemol) y el número de
  octava científico (`C4` = Do central). **Escribe siempre la altura real**: en la tonalidad de Sol,
  el fa sonoro es `F#4` (la armadura ya lo dibuja sin alteración).
* **Duración**: `w` redonda, `h` blanca, `q` negra, `8` corchea, `16` semicorchea.
  Añade `.` para puntillo (`q.`, `h.`) o `..` para doble puntillo.
* **Silencio**: `r:q`, `r:h`, `r:8`… (no se puede ligar).
* **Acorde**: `[C4 E4 G4]:h` (las alturas entre corchetes, la duración fuera).
* **Ligadura**: `~` entre dos notas de la **misma altura**: `C4:h ~ C4:q` o con sufijo `C4:h~ C4:q`.
  Suenan como una sola nota de duración sumada.
* **Ritmo sin altura**: `x:q` es una nota «de percusión» (se dibuja en la línea central). Úsalo en pasos
  `rhythmTap` y explicaciones de pulso.

Duraciones en pulsos de negra: `w`=4, `h`=2, `q`=1, `8`=0.5, `16`=0.25; el puntillo suma la mitad.

**Cada compás debe sumar exactamente el compás indicado** (p. ej. en `4/4` = 4 negras; en `3/4` = 3;
en `6/8` = 6 corcheas). Si no, el archivo no pasa los tests. Los compases se separan con `|`; no hace falta
`|` al final.

Corcheas y semicorcheas dentro de un mismo pulso se unen con barras automáticamente; las notas por
encima de la línea central llevan la plica hacia abajo.

## Tipos de paso (`"type"`)

Todos los pasos son objetos con `"type"` obligatorio.

### 1. `explain`: tarjeta de teoría

```json
{
  "type": "explain",
  "title": { "es": "La redonda", "en": "The whole note", "pt": "A semibreve" },
  "text": { "es": "...", "en": "...", "pt": "..." },
  "emoji": "⚪",
  "melody": { "clef": "treble", "time": "4/4", "key": "C", "tempo": 80, "notes": "C4:w" },
  "play": true
}
```
`title`, `emoji`, `melody` y `play` son opcionales. Con `melody` se dibuja el pentagrama con botón de reproducir.
No puntúa.

### 2. `listen`: escuchar una melodía con resaltado

```json
{
  "type": "listen",
  "prompt": { "es": "Escucha y sigue las notas.", "en": "...", "pt": "..." },
  "melody": { "notes": "C4:q D4:q E4:q F4:q | G4:w" }
}
```
No puntúa. (`clef`, `time`, `key`, `tempo` tienen valores por defecto.)

### 3. `quiz`: opción múltiple

```json
{
  "type": "quiz",
  "question": { "es": "¿Cuántos pulsos dura una blanca?", "en": "...", "pt": "..." },
  "options": [
    { "text": { "es": "1 pulso", "en": "1 beat", "pt": "1 tempo" } },
    { "text": { "es": "2 pulsos", "en": "2 beats", "pt": "2 tempos" } },
    { "note": "G4" },
    { "melody": { "notes": "C4:h C4:h" } }
  ],
  "answer": 1,
  "explanation": { "es": "...", "en": "...", "pt": "..." },
  "melody": { "notes": "C4:q C4:q G4:q G4:q" }
}
```
* 2 a 6 opciones. Cada opción tiene al menos uno de: `text` (localizado), `note` (altura; se muestra con el
  sistema de nombres del usuario) o `melody` (se dibuja y suena al tocarla).
* `answer`: índice (desde 0) de la opción correcta. Debe ser válido.
* `melody` (opcional) se muestra y se puede reproducir junto a la pregunta.
* Puntuación: 1.0 al primer intento, 0.5 al segundo, 0 después.

### 4. `identifyNote`: nombrar una nota del pentagrama

```json
{
  "type": "identifyNote",
  "prompt": { "es": "¿Qué nota es esta?", "en": "...", "pt": "..." },
  "clef": "treble",
  "note": "G4",
  "choices": ["E4", "F4", "G4", "A4"],
  "explanation": { "es": "...", "en": "...", "pt": "..." }
}
```
`prompt`, `choices` y `explanation` son opcionales. Sin `choices` la app genera cuatro opciones
(la correcta más tres cercanas, de forma determinista). Si das `choices` deben incluir `note`.
Las opciones se muestran con el sistema de nombres del usuario.

### 5. `earTraining`: entrenamiento auditivo

```json
{
  "type": "earTraining",
  "prompt": { "es": "¿La melodía sube o baja?", "en": "...", "pt": "..." },
  "sound": { "notes": ["G4", "F4", "E4"], "together": false, "tempo": 90 },
  "options": [
    { "text": { "es": "Sube", "en": "Goes up", "pt": "Sobe" } },
    { "text": { "es": "Baja", "en": "Goes down", "pt": "Desce" } }
  ],
  "answer": 1,
  "explanation": { "es": "...", "en": "...", "pt": "..." }
}
```
* `sound.notes`: una nota, un intervalo (dos notas) o una secuencia; suenan una tras otra.
* `together: true`: suenan a la vez (acorde / intervalo armónico).
* Las opciones y la puntuación son iguales que en `quiz`. El sonido se reproduce solo al entrar y con el botón.

### 6. `piano`: tocar en el teclado

```json
{
  "type": "piano",
  "prompt": { "es": "Toca Do, Re, Mi en orden.", "en": "...", "pt": "..." },
  "targets": ["C4", "D4", "E4"],
  "showStaff": true,
  "showLabels": false,
  "clef": "treble"
}
```
`targets`: notas que hay que tocar en orden (una o varias; se comparan por altura sonora). `showStaff` muestra las
notas en el pentagrama; `showLabels` pone nombres a las teclas y a las notas. Cada error resta puntuación.

### 7. `rhythmTap`: tocar un ritmo

```json
{
  "type": "rhythmTap",
  "prompt": { "es": "Toca este ritmo.", "en": "...", "pt": "..." },
  "melody": { "time": "4/4", "tempo": 70, "notes": "x:q x:q x:h | x:h x:h" },
  "toleranceMs": 220,
  "countIn": true,
  "passScore": 0.6
}
```
* Hay un compás de cuenta previa (`countIn`) con metrónomo; después el alumno toca cada nota (las pausas y las
  ligaduras de continuación no cuentan).
* `toleranceMs` (40–400): margen de tiempo por golpe, medido con el reloj de audio. Niveles bajos: 200–250,
  avanzados: 100–150.
* `passScore` (0–1): puntuación mínima para continuar. Los golpes de más restan.

## Lista de comprobación para un nivel nuevo

1. 6–8 lecciones, la última `"isExam": true`, con 3–6 pasos cada una y mezcla de tipos.
2. Todos los textos en `es`, `en` y `pt` (sin cadenas vacías).
3. Todas las melodías con compases completos; `answer` válido; `color` `#RRGGBB`.
4. ≥ 4 solfeos con `id` únicos y una `badge` con `id` único.
5. `./gradlew :composeApp:desktopTest` en verde.
