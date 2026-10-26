# Lemac DataLab — MVP Android

Aplicación académica de autorregistro en Neurodesarrollo, Habilidades DBT, Estado de ánimo y Adicciones. Android con Kotlin, Jetpack Compose, ViewModel, SQLite y WorkManager; API de prueba en Python con SQLite.

## Funciones disponibles

- Crear cuentas con alias de prueba, iniciar y cerrar sesión; separación de registros entre cuentas.
- Registrar los cuatro ámbitos, consultar historial por fechas/ámbito y editar o eliminar registros propios.
- Consultar evolución de la intensidad del ánimo y resúmenes de actividad.
- Crear objetivos de prueba y consultar su avance a partir de registros asociados.
- Consultar recursos, habilidades DBT, estrategias y acciones protectoras.
- Administrar contenidos y el parámetro de categorías de ánimo con el rol de responsable.
- Configurar recordatorios discretos con frecuencia y horario.
- Guardar sin conexión, vincular la API y sincronizar cambios en segundo plano, incluidos los borrados.

## Abrir y compilar

Abre esta carpeta como proyecto en Android Studio. Conserva el wrapper Gradle y las versiones ya configuradas en el proyecto: SDK 37, Android Gradle Plugin 9.3.3 y Gradle 9.5.0. Android mínimo: API 33.

Android Studio utiliza su JDK incluido. En un terminal PowerShell puedes usar:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Cuentas sintéticas

| Alias | Contraseña de demostración | Rol |
| --- | --- | --- |
| demo | Demo12345! | Usuario |
| contenidos_demo | Demo12345! | Responsable de contenidos |

Las cuentas existen en la app y en la API. Al crear una cuenta nueva en la app se asigna rol de usuario. No se solicita correo ni información personal real.

## API de prueba

Python 3.10 o posterior, sin dependencias externas:

```powershell
python .\backend\api.py
```

Escucha en `http://127.0.0.1:8080`. La base de prueba se crea en `backend/lemac-demo.sqlite3`. Puedes elegir otro archivo mediante `--db ruta.sqlite3`. Comprueba el servicio abriendo `http://127.0.0.1:8080/health`.

En la app abre **Perfil → API de prueba**, indica la URL y la contraseña de la misma cuenta de prueba, y pulsa **Vincular API**:

- Emulador de Android Studio: `http://10.0.2.2:8080`.
- Teléfono Android por USB: ejecuta `adb reverse tcp:8080 tcp:8080` y usa `http://127.0.0.1:8080`.
- Servidor de prueba remoto: utiliza HTTPS. HTTP está restringido a los hosts locales anteriores.

La app funciona localmente antes de vincular la API. Al vincular, inicia sesión en el servidor o crea allí la cuenta si no existe. Un alias ya registrado en el servidor requiere su contraseña correspondiente.

Después de vincular, los cambios se sincronizan al recuperar conexión y con WorkManager. También puedes pulsar **Sincronizar ahora**. Los cambios permanecen pendientes si la API está apagada. El token del servidor vence a las 24 horas; vuelve a vincular si se solicita. Cerrar sesión conserva tus datos locales y elimina el token del dispositivo.

## Recursos y parámetros

Entra como **contenidos_demo** y abre **Recursos** para crear o editar textos, ejercicios y entradas de catálogo. El recurso **Categorías de ánimo** permite modificar las opciones del check-in: escribe una categoría por línea. Los cambios locales quedan pendientes hasta sincronizar con la API.

El catálogo incluido contiene ejemplos sintéticos de demostración. El material institucional autorizado puede incorporarse con el editor de contenidos.

## Recordatorios

En **Perfil** configura horario, frecuencia y ámbito; pulsa **Activar / guardar** y permite las notificaciones. La app muestra un aviso genérico y lo oculta en la pantalla bloqueada. Al tocarlo se abre el ámbito elegido. La programación es aproximada y Android puede retrasarla por ahorro de batería.

## Pruebas

API HTTP:

```powershell
Push-Location .\backend
python -m unittest -v test_api
Pop-Location
```

Con la API activa en el puerto 8080 y un emulador conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

La suite Android verifica SQLite, aislamiento de cuentas, confirmación de revisiones, objetivos eliminados, el recorrido de interfaz, sincronización real y notificaciones discretas. El test de sincronización necesita acceder al host del emulador por `10.0.2.2`.

## Diseño y alcance

Consulta [la matriz de requisitos, arquitectura y decisiones](docs/IMPLEMENTACION.md). El código no contiene credenciales productivas; las credenciales indicadas arriba son exclusivas del entorno sintético de demostración. Este MVP utiliza únicamente datos ficticios y no ofrece diagnóstico, tratamiento ni atención de urgencia.

Referencias técnicas: [trabajo persistente en Android](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started), [permiso de notificaciones](https://developer.android.com/develop/ui/compose/notifications/notification-permission) y [almacenamiento SQLite](https://developer.android.com/training/data-storage/sqlite).

Equipo académico: Aníbal Romero, Luis Ahumada, Sebastián Cortés y Cristian Villavicencio.
