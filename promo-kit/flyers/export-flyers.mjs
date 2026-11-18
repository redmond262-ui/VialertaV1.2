import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { chromium } from "playwright";

const outDir = path.resolve("dist");
fs.mkdirSync(outDir, { recursive: true });

const htmlUrl = pathToFileURL(path.resolve("flyers.html")).href;
const targets = [
  { id: "flyer-1-copiloto", file: "01-ViaAlerta-Copiloto-1080x1920.png" },
  { id: "flyer-2-seguime", file: "02-ViaAlerta-Seguime-1080x1920.png" },
  { id: "flyer-3-ruta", file: "03-ViaAlerta-Ruta-1080x1350.png" },
  { id: "flyer-4-ecosistema", file: "04-ViaAlerta-Ecosistema-1080x1350.png" },
];

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
await page.setViewportSize({ width: 1400, height: 2200 });
await page.goto(htmlUrl, { waitUntil: "networkidle" });

for (const item of targets) {
  const loc = page.locator(`#${item.id}`);
  await loc.scrollIntoViewIfNeeded();
  const outPath = path.join(outDir, item.file);
  await loc.screenshot({ path: outPath });
  console.log("Exportado:", outPath);
}

await browser.close();
