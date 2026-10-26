# Implementación del MVP Lemac DataLab

Esta implementación cubre los flujos del alcance y los requerimientos v2 del material de referencia. Los documentos adjuntos se utilizaron como especificación funcional; las decisiones técnicas y las preguntas abiertas se detallan aquí.

| Requerimiento | Implementación |
| --- | --- |
| RF-01 / RF-02 | Registro e inicio de sesión local con alias de prueba, contraseña verificada con PBKDF2-SHA256, cuentas separadas y sesión persistida. Autenticación por token en la API al vincularla. |
| RF-03 | Inicio con acceso a Neurodesarrollo, DBT, Ánimo y Adicciones. Barra de navegación a Inicio, Historial, Recursos y Perfil. |
| RF-04 | Formularios con validación, UUID, fecha/hora, campos específicos y persistencia SQLite real. |
| RF-05 | Historial de la cuenta activa, filtros por ámbito y rango de fechas inclusivo. |
| RF-06 | Edición y eliminación de registros propios. Borrado lógico para transmitir eliminaciones sin conexión. |
| RF-07 | Recursos locales por ámbito, catálogo de habilidades, estrategias y acciones; descarga desde la API. |
| RF-08 | Recordatorio diario, de lunes a viernes o semanal, con horario configurable y ámbito opcional. Permiso de notificaciones, aviso discreto, restauración tras reinicio y cambios de hora. |
| RF-09 | Registro sin red; vinculación de API, sincronización manual, recuperación de conectividad, WorkManager con reintentos y ejecución periódica. |
| RF-10 | Rol responsable de contenidos; creación y edición de textos, ejercicios, habilidades, estrategias y acciones, más edición del parámetro de categorías de ánimo. Verificación de rol en el almacenamiento local y en el servidor. |
| RF-ND-01 / 02 / 03 | Rutina/tarea, situación o contexto, intensidad, estrategia y resultado percibido. Consulta y edición en historial. |
| RF-DBT-01 / 02 / 03 / 04 | Habilidad desde catálogo, emoción/situación, intensidad, utilidad percibida y contenido asociado. |
| RF-AN-01 / 02 / 03 | Categoría, intensidad, contexto, nota opcional y gráfico de evolución de la intensidad en el periodo filtrado. |
| RF-AD-01 / 02 / 03 | Impulso, intensidad, desencadenante, acción protectora, resultado y objetivo opcional. Gestión de metas y cálculo de progreso. |

## Arquitectura

`MainActivity → Jetpack Compose → AppViewModel → LemacRepository → LemacDatabase / ApiClient`

- La interfaz conserva los campos de formularios y la navegación al girar o recrear la actividad. El ViewModel coordina operaciones y errores.
- Las reglas del dominio viven en `model/Domain.kt`. Las operaciones de datos se ejecutan fuera del hilo principal.
- SQLiteOpenHelper proporciona la persistencia local sin depender de generación de código. Sustituye la propuesta de Room del borrador de arquitectura, manteniendo separadas las capas de interfaz, lógica y datos.
- Tablas locales: cuentas, registros, objetivos y recursos. Los campos específicos se conservan como JSON en tablas con propietario, revisión y estado pendiente. Es una implementación práctica del modelo conceptual, no una reproducción literal de todas sus tablas de extensión.
- La API Python tiene almacenamiento SQLite independiente y no requiere paquetes externos.

## Decisiones para las preguntas abiertas

- Acceso mediante alias de 3 a 24 letras, números o guion bajo y contraseña de 8 a 128 caracteres. No se pide correo, RUT ni nombre real.
- Intensidades y utilidad/resultado percibido: escala 1–5. Resultados de Adicciones: Resistí, Cedí, En proceso.
- Nota opcional de hasta 1000 caracteres; campos breves de hasta 200. Sin límite diario de registros. El envío se divide en lotes de hasta 500 elementos por tipo.
- Objetivo opcional en Adicciones. Su meta es una cantidad de registros asociados con resultado Resistí. El avance se recalcula al editar o eliminar registros. También se puede indicar el estado manualmente. Una meta alcanzada se muestra como cumplida mientras el estado manual siga «En curso».
- Un recordatorio activo por cuenta, general o asociado a un ámbito. El semanal utiliza el día de la semana en que se activa por primera vez. El horario es aproximado y depende de las restricciones de batería de Android.
- La notificación no muestra el ámbito ni el contenido de registros. Al pulsarla abre el ámbito configurado, si lo hay.
- No se incluyó recuperación de contraseñas: no aparece como requisito cerrado y no existen correos reales. Una cuenta offline se vincula al servidor con su contraseña desde Perfil.
- Catálogos de texto y ejercicios locales, sin reproducción de videos. Los contenidos incluidos son ejemplos sintéticos de demostración; el responsable de contenidos puede sustituirlos por el catálogo autorizado cuando se entregue.
- Retención durante el uso del MVP; los borrados se conservan como marcadores para sincronización. No se definió una política institucional de purga en los documentos.

## Contrato de sincronización

1. Los cambios se guardan localmente y quedan pendientes, incluso cuando la API no está configurada.
2. La vinculación inicia sesión en la API o crea allí la cuenta de prueba si todavía no existe. Las cuentas públicas nuevas reciben únicamente rol de usuario.
3. La app envía objetivos, registros y, para el administrador, recursos pendientes con identificadores de mutación formados por dispositivo, entidad y revisión.
4. La API procesa el lote en una transacción, comprueba propietario y rol, y recuerda las mutaciones procesadas. Reintentar un envío confirmado no lo aplica otra vez.
5. La API devuelve registros y objetivos de la cuenta autenticada y el catálogo global. La app marca como confirmadas solamente las revisiones efectivamente enviadas. Una edición local realizada durante el envío sigue pendiente.
6. Conflictos entre dispositivos: prevalece la última mutación nueva que confirma el servidor. No hay mezcla de campos ni un editor manual de conflictos en este MVP. Los reintentos de mutaciones anteriores no revierten cambios ni reviven borrados. Una nueva edición de otro dispositivo sí puede reemplazar un estado previo, según esta política.
7. El servidor expira tokens a las 24 horas. La app indica que se debe volver a vincular si la API devuelve 401. Los datos locales permanecen disponibles.
8. Cerrar sesión conserva los datos locales y detiene recordatorios/sincronización; el token se elimina del dispositivo. La sesión local se restaura al reabrir la app mientras no se haya cerrado explícitamente.

## Privacidad y alcance

Solo datos sintéticos. Contraseñas con salt aleatorio y PBKDF2; tokens en preferencias privadas. Bases y preferencias excluidas de respaldos y transferencia. HTTP habilitado únicamente para localhost, 127.0.0.1 y el host del emulador 10.0.2.2; otras API necesitan HTTPS.

La app no realiza diagnóstico, recomendaciones clínicas, contacto con profesionales ni atención de urgencia. Los contenidos son material ilustrativo para verificar el flujo académico.

## Validación

- Tests de dominio: credenciales, cuatro módulos, rangos, filtro inclusivo por fecha, borrados, progreso y tipos de catálogo.
- Tests HTTP de la API: credenciales, roles, aislamiento, transacciones, los cuatro módulos, reintentos, borrados, objetivos y persistencia.
- Tests Android: reapertura de SQLite, edición y borrado, aislamiento de cuentas, ACK condicionado por revisión, objetivos eliminados, recorrido Compose, API real y aviso discreto persistente.
- `SyncIntegrationTest` requiere la API activa en el puerto 8080 del equipo y un emulador con conexión al host.

Consulta `README.md` para ejecutar los servicios y las pruebas. Los resultados efectivos de esta sesión se incluyen en el informe entregado junto con el APK.
