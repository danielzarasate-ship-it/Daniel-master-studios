# Daniel Master Studio — motor original v0.2

Aplicación Android privada para mastering de música. El motor trabaja internamente en `float` y la exportación es WAV PCM 24-bit.

## Cadena actual
- Limpieza sub-sónica/DC.
- EQ tonal conservadora: low-mid/mud, presencia y aire.
- De-esser enfocado en 5.2–11 kHz.
- Control dinámico multibanda de graves y agudos.
- Control de imagen estéreo.
- Compresión de bus program-dependent.
- Objetivo de loudness configurable por perfil.
- Limitación final con techo nominal de -1 dBTP.
- Lectura WAV PCM 16/24/32-bit y exportación 24-bit.

## Perfiles
Automático/Equilibrado, Corrido tumbado, Rap/Trap, Reguetón, Cumbia y Pop/Urbano.

## Importante
Esta versión es un motor DSP serio de base, pero “calidad profesional 2026” debe validarse con material real, medición LUFS/True Peak de referencia y pruebas A/B en diferentes sistemas. Los presets son puntos de partida y no sustituyen una mezcla bien balanceada.

## Compilación
El proyecto usa Android Gradle Plugin 8.7.3 y Kotlin 2.0.21. Para generar el APK hace falta un entorno con Android SDK + Gradle/Gradle Wrapper.

## Compilación automática en la nube
El proyecto incluye workflows en `.github/workflows/` para generar el APK automáticamente con GitHub Actions. Consulta `CLOUD-BUILD.md`.
