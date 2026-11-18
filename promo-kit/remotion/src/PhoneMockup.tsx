import React from "react";
import {
  getStaticFiles,
  interpolate,
  OffthreadVideo,
  staticFile,
  useCurrentFrame,
} from "remotion";
import { BRAND, WalkthroughSceneSpec } from "./brand";

interface PhoneMockupProps {
  scene: WalkthroughSceneSpec;
  width: number;
  height: number;
}

const ScreenPreview: React.FC<{ scene: WalkthroughSceneSpec; frame: number }> = ({
  scene,
  frame,
}) => {
  const pulse = 0.8 + 0.2 * Math.sin(frame * 0.16);
  const carProgress = interpolate(frame, [0, scene.durationFrames], [0, 1], {
    extrapolateRight: "clamp",
  });

  if (scene.uiMode === "hook") {
    return (
      <div
        style={{
          flex: 1,
          background:
            "radial-gradient(circle at 50% 25%, #162447 0%, #020617 75%)",
          padding: 20,
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          color: BRAND.colors.slate50,
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            fontSize: 12,
            color: BRAND.colors.slate400,
          }}
        >
          <span style={{ fontWeight: 800, color: BRAND.colors.amber400 }}>
            VÍAALERTA v2.1
          </span>
          <span>GPS ACTIVO</span>
        </div>
        <div style={{ textAlign: "center" }}>
          <div
            style={{
              display: "inline-block",
              padding: "6px 14px",
              borderRadius: 999,
              background: "rgba(225,29,72,0.18)",
              border: `1px solid ${BRAND.colors.red500}`,
              color: "#FDA4AF",
              fontSize: 12,
              fontWeight: 800,
              marginBottom: 14,
            }}
          >
            ALERTA EN RUTA RN 9 • KM 148
          </div>
          <div style={{ fontSize: 28, fontWeight: 900, lineHeight: 1.15 }}>
            Bache profundo a 350 m
          </div>
          <div
            style={{
              marginTop: 10,
              fontSize: 14,
              color: BRAND.colors.slate400,
            }}
          >
            Calzada reducida • Sentido descendente
          </div>
        </div>
        <div
          style={{
            background: BRAND.colors.slate900,
            border: `1px solid ${BRAND.colors.slate700}`,
            borderRadius: 16,
            padding: 14,
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <div>
            <div style={{ fontSize: 11, color: BRAND.colors.slate400 }}>
              VELOCIDAD ACTUAL
            </div>
            <div style={{ fontSize: 26, fontWeight: 900, color: BRAND.colors.cyan400 }}>
              96 <span style={{ fontSize: 12 }}>km/h</span>
            </div>
          </div>
          <div
            style={{
              padding: "8px 14px",
              borderRadius: 12,
              background: "rgba(16,185,129,0.18)",
              border: `1px solid ${BRAND.colors.green500}`,
              color: BRAND.colors.green400,
              fontWeight: 800,
              fontSize: 12,
            }}
          >
            COPILOTO ACTIVO
          </div>
        </div>
      </div>
    );
  }

  if (scene.uiMode === "map") {
    return (
      <div
        style={{
          flex: 1,
          background: "#081020",
          position: "relative",
          overflow: "hidden",
          padding: 16,
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
        }}
      >
        {/* Dark stylized road grid */}
        <svg
          viewBox="0 0 320 580"
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        >
          <path
            d="M 40 580 Q 150 380 175 260 T 260 0"
            fill="none"
            stroke="#1E293B"
            strokeWidth="26"
          />
          <path
            d="M 40 580 Q 150 380 175 260 T 260 0"
            fill="none"
            stroke="#10B981"
            strokeWidth="6"
            strokeDasharray="10 8"
          />
          <circle cx="195" cy="185" r={20 * pulse} fill="rgba(225,29,72,0.28)" />
          <circle cx="195" cy="185" r="11" fill="#E11D48" stroke="#FFFFFF" strokeWidth="2.5" />
          <circle
            cx={110 + carProgress * 65}
            cy={420 - carProgress * 140}
            r="14"
            fill="#00D4FF"
            stroke="#FFFFFF"
            strokeWidth="3"
          />
        </svg>

        <div
          style={{
            position: "relative",
            background: "rgba(15,23,42,0.95)",
            border: `1px solid ${BRAND.colors.slate700}`,
            borderRadius: 14,
            padding: "10px 12px",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <span style={{ fontSize: 13, color: BRAND.colors.slate200, fontWeight: 700 }}>
            🎯 Ruta Nacional 8 • Hacia Pergamino
          </span>
          <span
            style={{
              fontSize: 11,
              fontWeight: 800,
              color: BRAND.colors.amber400,
            }}
          >
            42 km
          </span>
        </div>

        <div
          style={{
            position: "relative",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-end",
          }}
        >
          <div
            style={{
              background: "rgba(2,6,23,0.94)",
              border: `2px solid ${BRAND.colors.cyan400}`,
              borderRadius: 16,
              padding: "10px 16px",
              textAlign: "center",
            }}
          >
            <div style={{ fontSize: 28, fontWeight: 900, color: "#FFFFFF" }}>88</div>
            <div style={{ fontSize: 10, fontWeight: 700, color: BRAND.colors.slate400 }}>
              KM/H
            </div>
          </div>
          <div
            style={{
              background: BRAND.colors.amber500,
              color: "#020617",
              borderRadius: 14,
              padding: "12px 16px",
              fontWeight: 900,
              fontSize: 13,
            }}
          >
            + Reportar Peligro
          </div>
        </div>
      </div>
    );
  }

  if (scene.uiMode === "voice") {
    return (
      <div
        style={{
          flex: 1,
          background: BRAND.colors.slate950,
          padding: 18,
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
        }}
      >
        <div
          style={{
            background: "rgba(16,185,129,0.16)",
            border: `1px solid ${BRAND.colors.green500}`,
            borderRadius: 14,
            padding: 12,
            color: BRAND.colors.green400,
            fontSize: 12,
            fontWeight: 800,
          }}
        >
          🎙️ MANOS LIBRES ACTIVO • ESCUCHANDO EN RUTA
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div
            style={{
              alignSelf: "flex-end",
              background: BRAND.colors.slate800,
              borderRadius: 14,
              padding: "10px 14px",
              fontSize: 14,
              fontWeight: 700,
              color: "#FFFFFF",
            }}
          >
            «¿Cuánto falta para llegar?»
          </div>
          <div
            style={{
              alignSelf: "flex-start",
              background: "rgba(245,158,11,0.14)",
              border: `1px solid ${BRAND.colors.amber500}`,
              borderRadius: 14,
              padding: "10px 14px",
              fontSize: 13,
              color: "#FDE68A",
              lineHeight: 1.4,
            }}
          >
            🗣️ «Faltan 38 kilómetros, unos 29 minutos. Llegás a las 18:40.»
          </div>
        </div>

        <div
          style={{
            background: BRAND.colors.slate900,
            border: `1px solid ${BRAND.colors.slate700}`,
            borderRadius: 14,
            padding: 12,
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <div>
            <div style={{ fontSize: 11, color: BRAND.colors.slate400 }}>
              ANTI-FATIGA CONFIGURADO
            </div>
            <div style={{ fontSize: 14, fontWeight: 800, color: BRAND.colors.amber400 }}>
              Aviso cada 120 min (2 horas)
            </div>
          </div>
          <div
            style={{
              padding: "6px 10px",
              borderRadius: 10,
              background: BRAND.colors.amber500,
              color: "#020617",
              fontWeight: 900,
              fontSize: 11,
            }}
          >
            🔊 Probar Audio
          </div>
        </div>
      </div>
    );
  }

  if (scene.uiMode === "seguime") {
    return (
      <div
        style={{
          flex: 1,
          background: "#0B1220",
          padding: 16,
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            background: BRAND.colors.slate900,
            borderRadius: 12,
            padding: "8px 12px",
            border: `1px solid ${BRAND.colors.slate700}`,
          }}
        >
          <span style={{ fontSize: 12, fontWeight: 900, color: "#FFFFFF" }}>
            Seguime en Vivo • VíaAlerta
          </span>
          <span
            style={{
              fontSize: 10,
              fontWeight: 800,
              padding: "4px 8px",
              borderRadius: 999,
              background: "rgba(34,197,94,0.2)",
              color: "#4ADE80",
            }}
          >
            🟢 En ruta
          </span>
        </div>

        <div
          style={{
            flex: 1,
            margin: "10px 0",
            borderRadius: 14,
            background:
              "radial-gradient(circle at 55% 45%, #162B4D 0%, #09111E 85%)",
            border: `1px solid ${BRAND.colors.slate700}`,
            position: "relative",
            overflow: "hidden",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <svg viewBox="0 0 280 260" style={{ width: "100%", height: "100%" }}>
            <path
              d="M 35 220 C 90 170, 140 160, 195 95"
              fill="none"
              stroke="#38BDF8"
              strokeWidth="4"
              strokeDasharray="6 6"
            />
            <circle cx="195" cy="95" r={16 * pulse} fill="rgba(34,197,94,0.28)" />
            <circle cx="195" cy="95" r="9" fill="#22C55E" stroke="#FFFFFF" strokeWidth="3" />
          </svg>
          <div
            style={{
              position: "absolute",
              bottom: 10,
              left: 10,
              right: 10,
              background: "rgba(15,23,42,0.92)",
              borderRadius: 10,
              padding: "6px 10px",
              fontSize: 11,
              color: "#BAE6FD",
            }}
          >
            🔒 Privado: caduca y borra GPS al llegar o cancelar
          </div>
        </div>

        <div style={{ display: "flex", gap: 8 }}>
          <div
            style={{
              flex: 1,
              background: BRAND.colors.amber500,
              color: "#020617",
              borderRadius: 10,
              padding: "9px 10px",
              fontSize: 11,
              fontWeight: 900,
              textAlign: "center",
            }}
          >
            🎯 Ubicar en el mapa
          </div>
          <div
            style={{
              flex: 1,
              background: BRAND.colors.green500,
              color: "#020617",
              borderRadius: 10,
              padding: "9px 10px",
              fontSize: 11,
              fontWeight: 900,
              textAlign: "center",
            }}
          >
            ✅ ¡Llegué Bien!
          </div>
        </div>
      </div>
    );
  }

  if (scene.uiMode === "offline_sos") {
    return (
      <div
        style={{
          flex: 1,
          background: BRAND.colors.slate950,
          padding: 16,
          display: "flex",
          flexDirection: "column",
          gap: 12,
          justifyContent: "center",
        }}
      >
        <div
          style={{
            background: "rgba(245,158,11,0.14)",
            border: `1px solid ${BRAND.colors.amber500}`,
            borderRadius: 14,
            padding: 14,
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 900, color: BRAND.colors.amber400 }}>
            📴 MODO OFFLINE ACTIVO
          </div>
          <div style={{ fontSize: 13, color: "#FFFFFF", marginTop: 4 }}>
            2 alertas en cola local. Se publican automáticamente al recuperar 4G/5G.
          </div>
        </div>

        <div
          style={{
            background: "rgba(225,29,72,0.16)",
            border: `1px solid ${BRAND.colors.red500}`,
            borderRadius: 14,
            padding: 14,
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 900, color: "#FDA4AF" }}>
            🚨 SOS AUXILIO VERIFICADO
          </div>
          <div style={{ fontSize: 13, color: "#FFFFFF", marginTop: 6, fontWeight: 700 }}>
            DNI verificado • Patente comprobada
          </div>
          <div style={{ fontSize: 11, color: BRAND.colors.slate200, marginTop: 4 }}>
            Asistencia mecánica anti-emboscadas en ruta + Aventón Seguro + Baúl Vial.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        flex: 1,
        background: "linear-gradient(180deg, #0F172A 0%, #020617 100%)",
        padding: 18,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
      }}
    >
      <div
        style={{
          background: BRAND.colors.slate900,
          border: `1px solid ${BRAND.colors.green500}`,
          borderRadius: 16,
          padding: 14,
        }}
      >
        <div style={{ fontSize: 11, color: BRAND.colors.green400, fontWeight: 800 }}>
          NIVEL 4 • BAQUEANO DE RUTA
        </div>
        <div style={{ fontSize: 22, fontWeight: 900, color: "#FFFFFF", marginTop: 4 }}>
          1.450 XP • PRO Activo
        </div>
        <div style={{ fontSize: 12, color: BRAND.colors.slate400, marginTop: 4 }}>
          Código de invitación: VIA-84K2M (+7 días PRO)
        </div>
      </div>

      <div
        style={{
          background: BRAND.colors.slate900,
          border: `1px solid ${BRAND.colors.amber500}`,
          borderRadius: 16,
          padding: 16,
          textAlign: "center",
        }}
      >
        <div style={{ fontSize: 20, fontWeight: 900, color: BRAND.colors.amber400 }}>
          VíaAlerta v2.1
        </div>
        <div style={{ fontSize: 12, color: BRAND.colors.slate200, marginTop: 6 }}>
          {BRAND.shortUrl}
        </div>
      </div>
    </div>
  );
};

export const PhoneMockup: React.FC<PhoneMockupProps> = ({ scene, width, height }) => {
  const frame = useCurrentFrame();
  const staticFiles = getStaticFiles();
  const hasRealClip = Boolean(
    scene.clipFile && staticFiles.some((f) => f.name === scene.clipFile)
  );

  return (
    <div
      style={{
        width,
        height,
        borderRadius: Math.round(width * 0.12),
        background: "#0B1120",
        padding: Math.round(width * 0.032),
        border: "3px solid #334155",
        boxShadow:
          "0 30px 70px rgba(0,0,0,0.65), inset 0 0 0 1px rgba(255,255,255,0.08)",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          flex: 1,
          borderRadius: Math.round(width * 0.092),
          overflow: "hidden",
          background: "#020617",
          display: "flex",
          flexDirection: "column",
          position: "relative",
        }}
      >
        {/* Top dynamic island */}
        <div
          style={{
            position: "absolute",
            top: 10,
            left: "50%",
            transform: "translateX(-50%)",
            width: Math.round(width * 0.28),
            height: 18,
            borderRadius: 999,
            background: "#000000",
            zIndex: 10,
          }}
        />
        {hasRealClip && scene.clipFile ? (
          <OffthreadVideo
            src={staticFile(scene.clipFile)}
            muted
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
          />
        ) : (
          <div style={{ paddingTop: 28, flex: 1, display: "flex" }}>
            <ScreenPreview scene={scene} frame={frame} />
          </div>
        )}
      </div>
    </div>
  );
};
