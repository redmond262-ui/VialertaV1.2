import React from "react";
import { Composition } from "remotion";
import { TOTAL_WALKTHROUGH_FRAMES } from "./brand";
import { ViaAlertaWalkthrough } from "./ViaAlertaWalkthrough";

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="ViaAlertaWalkthrough-Vertical"
        component={ViaAlertaWalkthrough}
        durationInFrames={TOTAL_WALKTHROUGH_FRAMES}
        fps={30}
        width={1080}
        height={1920}
        defaultProps={{ orientation: "vertical" as const }}
      />
      <Composition
        id="ViaAlertaWalkthrough-Horizontal"
        component={ViaAlertaWalkthrough}
        durationInFrames={TOTAL_WALKTHROUGH_FRAMES}
        fps={30}
        width={1920}
        height={1080}
        defaultProps={{ orientation: "horizontal" as const }}
      />
    </>
  );
};
