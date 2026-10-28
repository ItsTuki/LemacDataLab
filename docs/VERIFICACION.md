# Verificación de la entrega

Fecha: 11 de octubre de 2026. Versión Android: 1.1-mvp, código 2. Paquete: com.losk.lemacdatalab.

| Comprobación ejecutada | Resultado |
| --- | --- |
| Compilación de app Android, assembleDebug | Correcta |
| Compilación de APK de pruebas, assembleDebugAndroidTest | Correcta |
| Análisis estático, lintDebug | Correcto, sin errores |
| Pruebas JVM de dominio, JUnit 4 | 6 aprobadas, 0 fallos |
| Pruebas HTTP de API Python | 10 aprobadas, 0 fallos |
| Ejecución de pruebas en dispositivo/emulador | Pendiente: el emulador disponible no permaneció conectado en este entorno |

Las seis pruebas JVM incluyen la prueba base del proyecto y cinco pruebas de negocio. La API se probó mediante solicitudes HTTP reales contra servidores locales temporales y bases SQLite independientes.

Las pruebas Android de persistencia, interfaz, sincronización real y notificaciones están implementadas y compilan, pero su ejecución no se pudo confirmar. Deben ejecutarse con un emulador o dispositivo estable siguiendo el README. No se presenta la compilación de esas pruebas como una ejecución exitosa.

En el entorno restringido de esta sesión, Java informó que la carpeta original no era escribible aunque permitía guardar archivos. Se compiló una copia temporal del mismo código. El runner de tests de Gradle también encontró un error del entorno al renombrar su archivo de resultados; las clases de pruebas compiladas se ejecutaron directamente con JUnitCore, con resultado 6/6. Estos cambios de entorno no alteran la configuración normal de compilación del proyecto.

La firma del APK de depuración se verificó con apksigner. La entrega utiliza la clave de depuración existente del equipo; no incluye claves de firma en el ZIP del proyecto.

Las categorías y los contenidos iniciales son ejemplos sintéticos. Falta sustituirlos por el catálogo institucional cuando el equipo lo reciba. La sincronización exige ejecutar o alojar la API de prueba; no se ha contratado ni publicado un servidor externo.
