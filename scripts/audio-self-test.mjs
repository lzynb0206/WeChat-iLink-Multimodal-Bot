import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { encode } from "silk-wasm";

const sampleRate = 24_000;
const silentPcm = Buffer.alloc(sampleRate * 2 / 10);
const encoded = await encode(silentPcm, sampleRate);
const decoderScript = fileURLToPath(new URL("./decode-silk.mjs", import.meta.url));
const decoder = spawn(process.execPath, [decoderScript, String(sampleRate)], {
  stdio: ["pipe", "pipe", "pipe"]
});

const output = [];
const errors = [];
decoder.stdout.on("data", chunk => output.push(chunk));
decoder.stderr.on("data", chunk => errors.push(chunk));
decoder.stdin.end(encoded.data);

const exitCode = await new Promise((resolve, reject) => {
  decoder.once("error", reject);
  decoder.once("close", resolve);
});
if (exitCode !== 0) {
  throw new Error(Buffer.concat(errors).toString("utf8"));
}

const wav = Buffer.concat(output);
if (wav.toString("ascii", 0, 4) !== "RIFF"
    || wav.toString("ascii", 8, 12) !== "WAVE"
    || wav.readUInt32LE(24) !== sampleRate) {
  throw new Error("decoder did not return a valid 24 kHz WAV file");
}

process.stdout.write(`silk-wasm decoder is ready (${encoded.data.length} SILK bytes → ${wav.length} WAV bytes)\n`);
