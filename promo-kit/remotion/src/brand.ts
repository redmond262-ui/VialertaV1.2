export const BRAND = {
  name: "VíaAlerta",
  tagline: "Avisá. Cuidá. Conducí mejor.",
  officialUrl: "https://gen-lang-client-0699193083.web.app",
  shortUrl: "gen-lang-client-0699193083.web.app",
  colors: {
    slate950: "#020617",
    slate900: "#0F172A",
    slate800: "#1E293B",
    slate700: "#334155",
    slate400: "#94A3B8",
    slate200: "#E2E8F0",
    slate50: "#F8FAFC",
    amber500: "#F59E0B",
    amber400: "#FBBF24",
    amber600: "#D97706",
    red500: "#E11D48",
    green500: "#10B981",
    green400: "#34D399",
    blue500: "#38BDF8",
    cyan400: "#00D4FF",
  },
} as const;

export interface WalkthroughSceneSpec {
  id: string;
  durationFrames: number;
  badge: string;
  title: string;
  subtitle: string;
  bullets: string[];
  clipFile?: string;
  uiMode: "hook" | "map" | "voice" | "seguime" | "offline_sos" | "community" | "cta";
  accentColor: string;
}

export const WALKTHROUGH_SCENES: WalkthroughSceneSpec[] = [
  {
    id: "hook",
    durationFrames: 150, // 5s
    badge: "SEGURIDAD VIAL EN RUTA",
    title: "Anticipate a lo que viene adelante",
    subtitle: "Pozos críticos, obras, radares y tramos sin señal en un solo copiloto real.",
    bullets: [
      "Pensado para rutas argentinas de día y de noche",
      "Modo oscuro anti-encandilamiento en ruta",
      "Funciona incluso en segundo plano o con pantalla apagada",
    ],
    uiMode: "hook",
    accentColor: BRAND.colors.amber400,
  },
  {
    id: "map",
    durationFrames: 240, // 8s
    badge: "01 • RADAR + ACELERÓMETRO",
    title: "Detecta baches y avisa antes",
    subtitle: "El acelerómetro detecta impactos reales de calzada y alerta a toda la comunidad.",
    bullets: [
      "Mapa nativo nocturno con velocímetro digital en km/h",
      "Alertas por distancia real sobre tu ruta y sentido",
      "Confirmación por voz o en 1 toque sin distraerte",
    ],
    clipFile: "clips/clip-1-mapa.mp4",
    uiMode: "map",
    accentColor: BRAND.colors.cyan400,
  },
  {
    id: "voice",
    durationFrames: 240, // 8s
    badge: "02 • COPILOTO MANOS LIBRES",
    title: "Hablale sin soltar el volante",
    subtitle: "Micrófono continuo inteligente, consultas en ruta y alertas de descanso anti-fatiga.",
    bullets: [
      "Preguntá «¿Cuánto falta?» o «Hora de llegada» por voz",
      "Activá o apagá manos libres hablando al instante",
      "Recordatorio configurable de descanso cada 2h o 3h",
    ],
    clipFile: "clips/clip-2-voz.mp4",
    uiMode: "voice",
    accentColor: BRAND.colors.amber400,
  },
  {
    id: "seguime",
    durationFrames: 270, // 9s
    badge: "03 • SEGUIME EN TIEMPO REAL",
    title: "Compartí tu viaje con privacidad",
    subtitle: "Enviá un enlace privado por WhatsApp con mapa en vivo, velocidad y destino.",
    bullets: [
      "Tu familia ve tu ruta directo en mapa propio (sin APIs ni Google Maps)",
      "Caduca y borra coordenadas al llegar o cancelar",
      "En zonas sin 4G conserva el último punto y actualiza al volver",
    ],
    clipFile: "clips/clip-3-seguime.mp4",
    uiMode: "seguime",
    accentColor: BRAND.colors.blue500,
  },
  {
    id: "offline_sos",
    durationFrames: 240, // 8s
    badge: "04 • COLA OFFLINE + SOS VERIFICADO",
    title: "Confiable aunque se corte la señal",
    subtitle: "Guardá reportes sin 4G y pedí auxilio seguro con identidad y patente comprobadas.",
    bullets: [
      "Cola offline persistente: sincroniza al recuperar señal",
      "SOS Anti-Emboscadas con DNI verificado y dominio",
      "Aventón Seguro y Baúl Vial para objetos en ruta",
    ],
    clipFile: "clips/clip-4-offline-sos.mp4",
    uiMode: "offline_sos",
    accentColor: BRAND.colors.red500,
  },
  {
    id: "community",
    durationFrames: 180, // 6s
    badge: "05 • COMUNIDAD Y NIVEL VIAL",
    title: "Sumá XP, niveles y días PRO",
    subtitle: "Probá el mapa al instante en Modo Invitado o activá tu cuenta Google.",
    bullets: [
      "7 días PRO de bienvenida y +7 días por cada referido VIA-XXXX",
      "Subí de Nivel Vial confirmando alertas útiles reales",
      "Sin reportes de controles policiales: foco 100% seguridad",
    ],
    clipFile: "clips/clip-5-perfil.mp4",
    uiMode: "community",
    accentColor: BRAND.colors.green400,
  },
  {
    id: "cta",
    durationFrames: 120, // 4s
    badge: "DISPONIBLE PARA ANDROID 7.0+",
    title: "VíaAlerta",
    subtitle: "Avisá. Cuidá. Conducí mejor.",
    bullets: [
      "Descarga oficial gratuita sin vencimiento de enlaces",
      "Probá ahora en Modo Invitado o con Google",
    ],
    uiMode: "cta",
    accentColor: BRAND.colors.amber400,
  },
];

export const TOTAL_WALKTHROUGH_FRAMES = WALKTHROUGH_SCENES.reduce(
  (acc, s) => acc + s.durationFrames,
  0
);
