# VíaAlerta — Plan Maestro de Avance (Lista de Trabajo por Etapas)

> Documento vivo de planificación. Se irá ejecutando etapa por etapa a pedido del creador.

---

## ETAPA 1 — Base de Viralización y Primera Impresión (Sin fricción)
*Objetivo: Que cualquier conductor nuevo que recibe un link entienda el valor de VíaAlerta en 10 segundos y pueda descargar/probar sin trabas.*

- [x] **1.0. Limpieza inicial: Sacar "Controles Policiales" (conservar solo "Radar Vial / Velocidad"):**
  - En Reporte Express, filtros, mapa, voz y avisos del radar, reemplazar `"Control Policial / Radar Vial"` por `"Radar Vial / Velocidad"` (sin alertas de operativos policiales).
- [x] **1.1. Link de descarga permanente y confiable (chau Gofile):**
  - Actualizar `public/index.html` con la versión actual, beneficios reales (copiloto por voz, alertas en segundo plano, rutas alternativas, Aventón/SOS) y guía simple de instalación en Android.
  - Conectar el workflow de GitHub Actions para publicar automáticamente el APK en un **GitHub Release fijo** (`releases/latest/download/ViaAlerta.apk`) o enlace estable en Firebase Hosting (`index.html`).
  - Actualizar todos los textos de compartir por WhatsApp (`ProfileScreen` y `HazardDetailBottomSheet`) para que apunten siempre a esa URL fija oficial.
- [x] **1.2. Modo Invitado con lectura pública de alertas:**
  - Permitir que quien abre la app en Modo Invitado **vea los pozos, radares viales, obras, desvíos y peligros en el mapa en tiempo real** (solo lectura; sin reportar, votar ni usar módulos sociales/voz PRO) para que perciba al instante que la comunidad está viva.
- [x] **1.3. Sistema de Referidos + Recompensa PRO:**
  - Generar código / link personal de invitación por conductor (ej. `VIA-XXXX`).
  - Otorgar **+150 XP y días PRO gratis** tanto al que invita como al invitado cuando el nuevo usuario completa su primer reporte real o primer viaje.
- [x] **1.4. Conversión Inteligente a PRO (Resumen Post-Viaje e Hitos — a evaluar):**
  - Disparadores únicamente en usuario **Freemium** (y nunca mientras el vehículo está en movimiento):
    1. **Fin de viaje real** (tras completar ruta/tramo con al menos 3 alertas recibidas y ya detenido).
    2. **Hito de valor acumulado** (ej. cada 15 o 25 baches/peligros avisados).
    3. **Aviso suave 24 hs antes** de que venza la prueba gratuita de 7 días.
  - Formato no invasivo de **"Resumen de Seguridad del Viaje"** (*"En este viaje te avisamos de X pozos críticos"*) con **3 salidas claras**: Pasar a PRO / Canjear Puntos o Invitar Amigos / Ahora no.
  - **Enfriamiento obligatorio (cooldown):** mínimo 7 días si el usuario toca *"Ahora no"* (y espaciar a 20 días tras 3 rechazos).
  - Conectar el botón *"Pasar a PRO"* a un flujo de pago real/centralizado (alias/link de Mercado Pago desde Firestore o Play Billing a futuro).
- [x] **1.5. Checklist Rápido de Seguridad Pre-Viaje (Auto vs Moto):**
  - Al trazar una ruta y empezar a circular (o antes de arrancar), emitir un recordatorio breve de 4–5 segundos adaptado al vehículo elegido en Perfil (`Auto / Camioneta` vs `Moto` vs `Camión`):
    - **Auto / Camioneta:** cinturón de seguridad puesto, luces bajas encendidas, documentación al día (licencia, cédula, seguro, VTV).
    - **Moto:** casco bien abrochado, luces encendidas y documentación encima.
  - **Cero molestia / Anti-fatiga:** frecuencia inteligente (ej. solo al iniciar ruta nueva o 1 vez por día, no en cada cuadra).
  - **Desactivación inmediata:** interruptor manual en Perfil/Configuración **y por comando de voz directo** (*"desactivar recordatorio"*, *"no me avises más esto"* o *"silenciar chequeo"*).

---

## ETAPA 2 — Niveles, Trayectoria (XP) y Comunidad Inteligente
*Objetivo: Motivar reportes de calidad, evitar alertas fantasma en el mapa y premiar sin castigar el nivel del usuario.*

- [x] **2.1. Separar XP Histórica (Nivel) vs Puntos Canjeables:**
  - Incorporar campo `xpTotal` en perfil de usuario que **nunca baja** al canjear días PRO o beneficios.
  - Mantener `pointsRuta` como saldo canjeable.
- [x] **2.2. Escala de 6 Niveles Viales y Beneficios:**
  1. `0 – 299 XP` · **Copiloto Inicial** (7 días PRO bienvenida + canje estándar)
  2. `300 – 999 XP` · **Conductor Atento** (contador de impacto comunitario + insignia)
  3. `1.000 – 2.499 XP` · **Baqueano de Ruta** (reportes con mayor peso de confianza + 10% dto. en canjes PRO)
  4. `2.500 – 5.999 XP` · **Patrulla Comunitaria** (insignia verificada en Aventón/SOS + voto doble al marcar *Solucionado* + 1 finde PRO bonus/mes)
  5. `6.000 – 14.999 XP` · **Centinela de Corredor** (25% dto. en canjes PRO + acceso beta + moderación de duplicados)
  6. `15.000+ XP` · **Leyenda Vial** (PRO sin costo manteniendo actividad mensual + emblema destacado / Pionero de Ruta)
- [x] **2.3. Nuevas fuentes de puntos y bonus de precisión:**
  - `+20 XP/pts` por marcar alertas como *Solucionado / Tapado* (limpieza activa del mapa).
  - `+25 XP/pts` bonus al autor original cuando 2 o más conductores confirman su alerta.
  - Placa compartible **"Pionero de Ruta / A cuántos conductores ayudaste"** para Estados de WhatsApp/Instagram.
- [x] **2.4. Caducidad inteligente según tipo de peligro (Radares Viales sí, Controles Policiales no):**
  - Alertas temporales (*neblina, agua en calzada, animales sueltos, auto en banquina*) expiran automáticamente a las pocas horas si nadie las reconfirma.
  - Alertas estructurales (*pozo, asfalto roto, alcantarilla, corte de obra, radar vial fijo*) permanecen activas o hasta recibir votos de solución.
  - *(Nota obligatoria de producto: VíaAlerta **NO incluye reportes de controles policiales**; solo radares de velocidad y peligros viales/clima/auxilio).*

---

## ETAPA 3 — Conducción Real Avanzada y Zonas sin Señal
*Objetivo: Volver a VíaAlerta superior a cualquier GPS genérico en rutas argentinas.*

- [ ] **3.1. Índice de Asfalto en las 2–3 rutas sugeridas:**
  - Calcular sobre cada trazado cuántos pozos críticos, cortes u obras tiene cargados (ej. `Opción 1: 145 km • ⚠️ 9 pozos` vs `Opción 2: 154 km • ✅ Camino limpio`).
- [x] **3.2. Modo "Sin Señal" (Offline Inteligente):**
  - Cachear alertas activas en Room para tramos sin señal.
  - Encolar localmente en cola persistente (`OfflineAlertQueue`) los reportes por voz o toque hechos sin 4G/5G y sincronizarlos automáticamente al recuperar conectividad.
- [ ] **3.3. Navegación y Pantalla de Auto:**
  - Vista apaisada nocturna de alto contraste para tablero (velocidad grande, distancia al próximo peligro, sin encandilar).
  - Recálculo automático ante desvío prolongado + indicaciones giro a giro principales por voz.
  - Burbuja flotante opcional sobre otras apps (`Overlay`) para marcar un pozo en 1 toque mientras usás Maps/WhatsApp.
- [ ] **3.4. Seguridad extra en SOS, Aventón y Reclamos:**
  - Contacto de emergencia en Perfil para enviar ubicación GPS exacta + patente en 1 toque al tocar SOS.
  - Aventón conectado al corredor y sentido del viaje actual + insignia de confianza del nivel.
  - Reclamos Oficiales adjuntando confirmaciones comunitarias y coordenadas exactas.
- [ ] **3.5. Sistema BLE Inteligente (Baliza SOS Varado y Malla Sin Señal):**
  - **Baliza SOS "Efecto Posta" (Vehículo detenido/varado en PRO):** emisión de paquete BLE liviano cuando no hay señal para que otros autos con VíaAlerta que pasen cerca capten el paquete cifrado en silencio y lo suban al recuperar 4G (avisando al Contacto de Emergencia y SOS).
  - **Enfoque realista de Seguridad Preventiva (en lugar de pánico en movimiento):**
    - No prometer rastreo ni botones complejos en pleno movimiento durante un asalto; el seguimiento continuo preventivo se resuelve activando **`Seguime (Tiempo Real)` antes de salir** (con foto de último cruce + rumbo + velocidad vía BLE si pasa otro auto en pozo negro).
    - **Marcadores preventivos en ruta:** *"Tramo oscuro / No detenerse de noche"* vs *"Paradas Seguras iluminadas 24 hs"* para prevenir paradas en zonas riesgosas sin exponer personas ni llamar a civiles a intervenir.
    - **Privacidad total por Bluetooth:** payload BLE cifrado y anónimo (cero nombres, teléfonos ni patentes en claro) + aviso explícito de que no reemplaza al 911.
  - **Estrategia realista por etapas (límites de Android/iOS y baja densidad inicial):**
    - *Por qué exclusivo VíaAlerta:* por seguridad del sistema operativo, un teléfono que no tiene la app instalada no puede retransmitir paquetes BLE a internet automáticamente. Por eso el túnel de malla funciona **exclusivo entre usuarios de VíaAlerta**.
    - *Respaldo desde el Día 1 (pocos usuarios):* cola offline de alertas/huella GPS + última ubicación antes del pozo negro + envío rápido por **SMS con coordenadas** (cuando hay señal GSM sin 4G).
    - *Baliza local de rescate (solo accidente físico extremo):* anuncio BLE identificable a 50–100 m para orientar a bomberos/auxilio que ya busquen en ese kilómetro.
    - *Nodos Fijos Aliados en ruta (fase crecimiento):* posibilidad de dejar receptores/tablets con la app en paradores, gomerías o estaciones amigas del corredor para sincronizar alertas y balizas de cada vehículo que pasa por la puerta.
  - **Consumo optimizado (~0,3%–1%/h):** escaneo en `SCAN_MODE_LOW_POWER` con `ScanFilter` por hardware exclusivo del UUID de VíaAlerta; solo transmite al perder señal, en ruta activa o en emergencia; modo ahorro extremo varado con pantalla apagada.
  - **Permisos transparentes:** pantalla explicativa previa clara antes de solicitar `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` y `BLUETOOTH_CONNECT`, con interruptor ON/OFF opcional para el usuario.
- [x] **3.6. Alerta Configurable de Descanso / Anti-Fatiga:**
  - Selector en Perfil/Configuración para elegir si activarlo y con qué intervalo (`Desactivado` / `Cada 2 hs` / `Cada 3 hs` de conducción continua).
- [x] **3.7. "Seguime (Tiempo Real)" — Dashboard Familiar por WhatsApp (Base En Vivo + Cola Offline; Puente BLE en 3.5):**
  - Al iniciar viaje o desde el mapa, botón rápido para enviar por WhatsApp/compartir un **link temporal privado** (`seguime.html?s=...`) a un mini-dashboard web con mapa en vivo, huella reciente, velocidad/rumbo y cierre *"¡Llegué bien!"* (caducidad y borrado inmediato de coordenadas al llegar, detener viaje, cerrar app, cancelar o máximo a las 8h).
  - **Aclaración transparente sobre señal:** tanto en el mensaje de WhatsApp como en un cartel fijo del dashboard web se explica claramente que en zonas rurales o de montaña sin cobertura celular el mapa conserva el último punto GPS y actualiza al recuperar señal.
  - **Respaldo offline en pozos negros de señal:** cola local de coordenadas GPS (`recentPoints`) que sincroniza automáticamente al recuperar 4G/5G (quedan pendientes para próxima etapa: respaldo por SMS sin datos móviles, puente BLE 3.5 e integración directa con Aventón).
- [ ] **3.8. Validación de Identidad Progresiva en 3 Capas (Aventón y Baúl Vial):**
  - **Capa 1 — Costo $0 y sin riesgo legal (no guardar fotos de DNI en base propia):**
    - *Teléfono verificado por SMS (Firebase Phone Auth)* obligatorio para usar Aventón o reclamar ítems en Baúl Vial.
    - *Aventón sin frenar al chofer:* **única selfie en vivo** (cámara directa, sin galería) sacada **una sola vez en casa** al habilitar Aventón para ambos roles + modelo/patente del vehículo. En ruta, el chofer acepta con **1 toque o comando de voz** (*"Aceptar aventón"*) viendo la foto del pasajero, y **el pasajero** realiza el check-in al subir (dejando registro real de quién viajó con quién).
    - *Baúl Vial (documentos/objetos):* nunca exponer número de DNI ni foto pública de documentos encontrados; exigir últimos 3–4 dígitos / fecha de nacimiento + pregunta secreta del objeto + **código de cierre de entrega** presencial.
  - **Capa 2 — Reputación y antigüedad:** insignias claras (*Perfil Nuevo < 7 días* vs *Verificado Nivel 3+ • X viajes/devoluciones*) y restricciones nocturnas para cuentas recién creadas sin validar.
  - **Capa 3 — KYC Oficial con DNI / Biométrico (etapa escala):** conexión con proveedor KYC certificado (ej. Truora, Didit, Sumsub / Renaper) para validar documento + prueba de vida sin almacenar imágenes sensibles de DNI en servidores de VíaAlerta, otorgando insignia **`Identidad Verificada (DNI)`**.

---

## ETAPA 4 — Monetización Escalonada, Convenios B2B2C y Ecosistema VíaAlerta
*Objetivo: Fortalecer los ingresos desde temprano con múltiples vías (sin depender solo del abono mensual individual) y escalar mediante convenios.*

- [ ] **4.0. Estructura de Planes PRO y Flotas (a pulir):**
  - **Escalera de precios particular:**
    - **PRO Mensual base (`~$5.000 ARS/mes`):** radar y copiloto completo + acceso a descuentos en comercios adheridos ("se paga solo").
    - **Pase Escapada / Viaje (7 o 15 días, pago único sin débito automático):** pensado para viajeros ocasionales en rutas largas (> 200 km).
    - **Plan Anual PRO:** cobro anticipado con descuento (ej. 8 meses al precio de 12).
  - **Plan Flotas / Transportistas (B2B):** paquetes para comisionistas, fletes, remises y logística (5, 15, 50+ choferes) con monitoreo en Panel Web.
- [ ] **4.1. Red de Talleres, Gomerías Móviles y Grúas Aliadas ("Despacho SOS"):**
  - Perfil de prestador verificado con radio de cobertura en km y aparición destacada en el mapa.
  - Derivación directa desde el botón **SOS** al auxilio más cercano (abono mensual local o comisión 10%–15% por servicio cerrado + descuento exclusivo para PRO / Niveles 3+).
- [ ] **4.2. Paradores y Estaciones de Servicio Aliadas (Beneficios "Viceversa"):**
  - Beneficios físicos en ruta (10%–15% en alineación/lubricentro, café, parches, descanso) validando credencial digital PRO/Nivel dentro de la app para que la suscripción se amortice sola.
- [ ] **4.3. Línea VíaAlerta Seguros / Asistencia Vial (Marca Blanca / Alianza):**
  - Alianza con aseguradora o asistencia al viajero habilitada ante SSN para ofrecer micro-cobertura de ruta (ej. *Cubierta + Llanta + Grúa + Plan PRO*).
- [ ] **4.4. Android Auto (Car App Library):**
  - Proyección nativa de alertas y reporte rápido de 1 toque en la pantalla del vehículo.
- [ ] **4.5. Módulo de Convenios y Vouchers Corporativos (B2B2C):**
  - Canje de códigos/vouchers dentro de la app (ej. `GOMERIA-2026`, `SEGURO-XXXX`) para que **aseguradoras, casas de neumáticos, concesionarias, lubricentros, telepeajes o programas de puntos** incluyan meses de VíaAlerta PRO gratis con sus pólizas, cubiertas o services.
  - Atribución y métricas de activación por empresa socia en el **Panel Maestro Admin** para liquidación mayorista por volumen.
