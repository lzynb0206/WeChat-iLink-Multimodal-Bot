import { decode } from "silk-wasm";

const DEFAULT_SAMPLE_RATE = 24_000;

if (process.argv.includes("--check")) {
  process.stdout.write("silk-wasm decoder is ready\n");
  process.exit(0);
}

const sampleRate = Number.parseInt(process.argv[2] ?? String(DEFAULT_SAMPLE_RATE), 10);
if (!Number.isInteger(sampleRate) || sampleRate <= 0) {
  throw new Error("sampleRate must be a positive integer");
}

const chunks = [];
for await (const chunk of process.stdin) {
  chunks.push(chunk);
}

const silk = Buffer.concat(chunks);
if (silk.length === 0) {
  throw new Error("SILK input is empty");
}

const decoded = await decode(silk, sampleRate);
const pcm = Buffer.from(decoded.data.buffer, decoded.data.byteOffset, decoded.data.byteLength);
process.stdout.write(createWav(pcm, sampleRate));

function createWav(pcm, rate) {
  const header = Buffer.alloc(44);
  header.write("RIFF", 0, "ascii");
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8, "ascii");
  header.write("fmt ", 12, "ascii");
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(rate, 24);
  header.writeUInt32LE(rate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36, "ascii");
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}
