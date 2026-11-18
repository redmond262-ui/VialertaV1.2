import React from "react";
import {
  AbsoluteFill,
  Audio,
  getStaticFiles,
  interpolate,
  Sequence,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { BRAND, WALKTHROUGH_SCENES, WalkthroughSceneSpec } from "./brand";
import { PhoneMockup } from "./PhoneMockup";

interface WalkthroughProps {
  orientation: "vertical" | "horizontal";
}

const SceneSlide: React.FC<{
  scene: WalkthroughSceneSpec;
  orientation: "vertical" | "horizontal";
  index: number;
}> = ({ scene, orientation, index }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const enter = spring({
    frame,
    fps,
    config: { damping: 16, stiffness: 120 },
  });

  const fadeOut = interpolate(
    frame,
    [scene.durationFrames - 12, scene.durationFrames],
    [1, 0],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
  );

  const phoneTranslateY = interpolate(enter, [0, 1], [40, 0]);
  const isVertical = orientation === "vertical";

  return (
    <AbsoluteFill
      style={{
        opacity: fadeOut,
        padding: isVertical ? "72px 58px" : "56px 86px",
        display: "flex",
        flexDirection: isVertical ? "column" : "row",
        alignItems: "center",
        justifyContent: isVertical ? "space-between" : "space-between",
        gap: isVertical ? 32 : 64,
      }}
    >
      {/* Left / Top Editorial Copy */}
      <div
        style={{
          flex: isVertical ? "0 0 auto" : "1 1 54%",
          width: isVertical ? "100%" : undefined,
          transform: `translateY(${interpolate(enter, [0, 1], [20, 0])}px)`,
          opacity: enter,
        }}
      >
        <div
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 10,
            padding: "8px 16px",
            borderRadius: 999,
            background: "rgba(15,23,42,0.9)",
            border: `1.5px solid ${scene.accentColor}`,
            color: scene.accentColor,
            fontSize: isVertical ? 22 : 18,
            fontWeight: 900,
            letterSpacing: 1.1,
            marginBottom: 18,
          }}
        >
          {scene.badge}
        </div>

        <h1
          style={{
            margin: 0,
            color: BRAND.colors.slate50,
            fontSize: isVertical ? 54 : 52,
            lineHeight: 1.08,
            fontWeight: 900,
            letterSpacing: -0.8,
          }}
        >
          {scene.title}
        </h1>

        <p
          style={{
            margin: "16px 0 22px",
            color: BRAND.colors.slate200,
            fontSize: isVertical ? 26 : 24,
            lineHeight: 1.38,
            fontWeight: 500,
          }}
        >
          {scene.subtitle}
        </p>

        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {scene.bullets.map((line, i) => {
            const itemDelay = i * 8;
            const itemOpacity = interpolate(frame, [itemDelay, itemDelay + 12], [0, 1], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
            });
            return (
              <div
                key={line}
                style={{
                  opacity: itemOpacity,
                  display: "flex",
                  alignItems: "center",
                  gap: 12,
                  background: "rgba(15,23,42,0.72)",
                  border: `1px solid ${BRAND.colors.slate800}`,
                  borderRadius: 14,
                  padding: isVertical ? "12px 16px" : "11px 16px",
                }}
              >
                <span
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: 999,
                    background: scene.accentColor,
                    flexShrink: 0,
                  }}
                />
                <span
                  style={{
                    color: BRAND.colors.slate50,
                    fontSize: isVertical ? 22 : 20,
                    fontWeight: 600,
                  }}
                >
                  {line}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Right / Bottom Realistic Phone Mockup */}
      <div
        style={{
          flex: isVertical ? "1 1 auto" : "0 0 auto",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          transform: `translateY(${phoneTranslateY}px)`,
          opacity: enter,
        }}
      >
        <PhoneMockup
          scene={scene}
          width={isVertical ? 450 : 410}
          height={isVertical ? 880 : 810}
        />
      </div>

      {/* Subtle Progress Indicator */}
      <div
        style={{
          position: "absolute",
          bottom: 24,
          left: isVertical ? 58 : 86,
          right: isVertical ? 58 : 86,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          color: BRAND.colors.slate400,
          fontSize: 18,
          fontWeight: 700,
        }}
      >
        <span>VíaAlerta v2.1 • {BRAND.shortUrl}</span>
        <span>
          {index + 1} / {WALKTHROUGH_SCENES.length}
        </span>
      </div>
    </AbsoluteFill>
  );
};

export const ViaAlertaWalkthrough: React.FC<WalkthroughProps> = ({
  orientation,
}) => {
  const staticFiles = getStaticFiles();
  const hasVoiceover = staticFiles.some((f) => f.name === "audio/locucion.mp3");

  let cursor = 0;

  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(circle at 80% 18%, #13254A 0%, ${BRAND.colors.slate950} 65%)`,
        fontFamily:
          'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {hasVoiceover ? <Audio src={staticFile("audio/locucion.mp3")} /> : null}

      {WALKTHROUGH_SCENES.map((scene, idx) => {
        const from = cursor;
        cursor += scene.durationFrames;
        return (
          <Sequence key={scene.id} from={from} durationInFrames={scene.durationFrames}>
            <SceneSlide scene={scene} orientation={orientation} index={idx} />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
