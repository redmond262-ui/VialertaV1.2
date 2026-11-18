# Kit Promocional Profesional — VíaAlerta (Para Antigravity)

Este directorio está pensado para exportar o abrir en **Antigravity** y generar:
1. **4 Flyers estáticos profesionales** (sin caricaturas, estilo editorial nocturno/ruta real) listos para WhatsApp, Instagram Reels/Feed y presentaciones a comercios/flotas.
2. **2 Videos explicativos con recorrido real por la app** en **Remotion** (1080×1920 Vertical para Reels/Shorts/Estados y 1920×1080 Horizontal para YouTube/Web).

> Este directorio es independiente de la app Android (`app/`) y de Firebase Hosting (`public/`), así que **no afecta ni al APK ni al sitio web**.

---

## Prompt listo para darle a Antigravity

Copiá y pegá esto en Antigravity cuando abras el proyecto:

> "Abrí la carpeta `promo-kit/`. Quiero generar el material promocional profesional de VíaAlerta **sin caricaturas ni ilustraciones infantiles**:
> 1. Revisá `promo-kit/flyers/flyers.html` y exportá los 4 flyers en alta resolución (PNG) usando `promo-kit/flyers/export-flyers.mjs` (o Playwright/Chromium), ajustando cualquier detalle de espaciado si hace falta.
> 2. En `promo-kit/remotion/`, instalá dependencias (`npm install`), verificá el proyecto de Remotion (`src/ViaAlertaWalkthrough.tsx`) y renderizá los videos `ViaAlertaWalkthrough-Vertical` (1080x1920) y `ViaAlertaWalkthrough-Horizontal` (1920x1080).
> 3. Si te paso grabaciones reales de pantalla del celular (`clip-1-mapa.mp4`, `clip-2-voz.mp4`, `clip-3-seguime.mp4`, `clip-4-offline-sos.mp4`, `clip-5-perfil.mp4`), ponelas en `promo-kit/remotion/public/clips/` para reemplazar la vista previa UI dentro del mockup del teléfono.
> 4. Si te paso o generamos el audio de locución (`promo-kit/script-locucion.txt`), guardalo como `promo-kit/remotion/public/audio/locucion.mp3` para que se sincronice automáticamente en el video."

---

## Grabaciones de pantalla recomendadas (opcional, eleva el video a nivel 10/10)

Grabá 5 clips cortos en tu teléfono (1080p, pantalla vertical, sin barra de notificaciones llena):

| Archivo en `promo-kit/remotion/public/clips/` | Qué mostrar en la app (7–9 segundos c/u) |
|---|---|
| `clip-1-mapa.mp4` | Mapa oscuro de VíaAlerta en movimiento: velocímetro digital, marcador de bache/obra y barra de búsqueda de destino. |
| `clip-2-voz.mp4` | Copiloto por voz con manos libres encendido: hablando sin tocar la pantalla (*"¿Cuánto falta?"* o reportando un bache por voz). |
| `clip-3-seguime.mp4` | Tocar **Seguime**, compartir el enlace privado, abrir el mapa web en vivo y tocar **"¡Llegué bien!"** mostrando cómo caduca al instante. |
| `clip-4-offline-sos.mp4` | Reporte guardado en **cola offline** sin señal 4G + vista de **SOS Verificado** (DNI + patente). |
| `clip-5-perfil.mp4` | Perfil del conductor con **Nivel Vial / XP**, selector **Anti-Fatiga** (2h / 3h) y código de referido `VIA-XXXXXX`. |

---

## Comandos manuales rápidos

### Exportar Flyers a PNG
```bash
cd promo-kit/flyers
npm install
npx playwright install chromium
node export-flyers.mjs
```
Los PNG quedan en `promo-kit/flyers/dist/`.

### Previsualizar y Renderizar Videos con Remotion
```bash
cd promo-kit/remotion
npm install
npx remotion studio
```
Para renderizar los MP4 finales:
```bash
npm run render:vertical
npm run render:horizontal
```
Los videos finales quedan en `promo-kit/remotion/out/`.
