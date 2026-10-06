# Daniel Master Studio — compilación automática del APK

Este proyecto incluye GitHub Actions para compilar el APK automáticamente en la nube.

## APK de prueba
1. En GitHub abre **Actions**.
2. Selecciona **Build Daniel Master Studio APK**.
3. Pulsa **Run workflow** (también se ejecuta al hacer push a main).
4. Cuando termine correctamente, abre la ejecución y busca **Artifacts**.
5. Descarga `Daniel-Master-Studio-debug-apk.zip`.
6. Dentro estará `app-debug.apk`. Pásalo al Android e instálalo.

## Qué instala
- Nombre: Daniel Master Studio
- Paquete: `com.daniel.masterstudio`
- Versión: 0.2.0
- APK de prueba: firmado con la clave de debug de Android y apto para instalación manual.

## Importante
El workflow de release genera un APK sin firmar. Para distribuir una versión definitiva conviene configurar una clave privada en GitHub Secrets.
