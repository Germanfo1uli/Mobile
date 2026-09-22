import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const sampleRate = 44_100;
const destination = join(dirname(fileURLToPath(import.meta.url)), "..", "app", "src", "main", "res", "raw");
mkdirSync(destination, { recursive: true });

function randomSource(seed) {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 0x100000000 * 2 - 1;
  };
}

function renderWave(name, duration, makeSample) {
  const count = Math.round(duration * sampleRate);
  const source = makeSample(randomSource(name === "theurgy_fracture.wav" ? 0x5448454f : 0x52455455));
  const mono = new Float32Array(count);
  for (let index = 0; index < count; index++) mono[index] = source(index / sampleRate);
  const buffer = Buffer.alloc(44 + count * 4);
  buffer.write("RIFF", 0);
  buffer.writeUInt32LE(buffer.length - 8, 4);
  buffer.write("WAVEfmt ", 8);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(2, 22);
  buffer.writeUInt32LE(sampleRate, 24);
  buffer.writeUInt32LE(sampleRate * 4, 28);
  buffer.writeUInt16LE(4, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write("data", 36);
  buffer.writeUInt32LE(count * 4, 40);
  for (let index = 0; index < count; index++) {
    const leftEcho = index > 3_500 ? mono[index - 3_500] * 0.16 : 0;
    const rightEcho = index > 5_800 ? mono[index - 5_800] * 0.21 : 0;
    const left = Math.tanh((mono[index] + leftEcho) * 1.35) * 0.88;
    const right = Math.tanh((mono[index] + rightEcho) * 1.35) * 0.88;
    buffer.writeInt16LE(Math.round(left * 32767), 44 + index * 4);
    buffer.writeInt16LE(Math.round(right * 32767), 46 + index * 4);
  }
  writeFileSync(join(destination, name), buffer);
}

renderWave("theurgy_fracture.wav", 1.7, (random) => {
  let lowNoise = 0;
  let previousNoise = 0;
  return (time) => {
    const noise = random();
    lowNoise = lowNoise * 0.965 + noise * 0.035;
    const highNoise = noise - previousNoise * 0.75;
    previousNoise = noise;
    const hit = (at, decay) => time >= at ? Math.exp(-(time - at) * decay) : 0;
    const snap = highNoise * (hit(0.015, 36) * 0.37 + hit(0.095, 31) * 0.23 + hit(0.18, 37) * 0.14);
    const bassPhase = 2 * Math.PI * (125 * time - 55 * time * time);
    const bass = Math.sin(bassPhase) * Math.exp(-time * 6) * 0.45;
    const fracture = (
      Math.sin(2 * Math.PI * 970 * time) * 0.14 +
      Math.sin(2 * Math.PI * 1_661 * time) * 0.10 +
      Math.sin(2 * Math.PI * 2_731 * time) * 0.07
    ) * Math.exp(-time * 5.8);
    const rubble = lowNoise * Math.exp(-time * 2.8) * 0.50;
    return snap + bass + fracture + rubble;
  };
});

renderWave("theurgy_reentry.wav", 0.8, (random) => {
  let lowNoise = 0;
  return (time) => {
    const noise = random();
    lowNoise = lowNoise * 0.93 + noise * 0.07;
    const rise = Math.min(1, time / 0.48);
    const sweep = (noise - lowNoise) * rise * rise * 0.20;
    const slamTime = Math.max(0, time - 0.48);
    const slam = time >= 0.48
      ? (Math.sin(2 * Math.PI * (92 * slamTime - 28 * slamTime * slamTime)) * 0.38 + noise * 0.23)
        * Math.exp(-slamTime * 15)
      : 0;
    return sweep + slam;
  };
});
