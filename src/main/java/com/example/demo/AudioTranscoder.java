package com.example.demo;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class AudioTranscoder {
    private static final int SAMPLE_RATE = 24_000;
    private final MediaAiConfig config;

    public AudioTranscoder(MediaAiConfig config) {
        this.config = config;
    }

    public byte[] silkToWav(byte[] silk) throws IOException, InterruptedException {
        Path silkFile = Files.createTempFile("wechat-voice-", ".silk");
        Path pcmFile = Files.createTempFile("wechat-voice-", ".pcm");
        try {
            Files.write(silkFile, silk);
            runCodec("decode", silkFile, pcmFile);
            return pcmToWav(Files.readAllBytes(pcmFile), SAMPLE_RATE);
        } finally {
            Files.deleteIfExists(silkFile);
            Files.deleteIfExists(pcmFile);
        }
    }

    public byte[] wavToSilk(byte[] wav) throws IOException, InterruptedException {
        byte[] pcm = extractWavPcm(wav);
        Path pcmFile = Files.createTempFile("tts-answer-", ".pcm");
        Path silkFile = Files.createTempFile("tts-answer-", ".silk");
        try {
            Files.write(pcmFile, pcm);
            runCodec("encode", pcmFile, silkFile);
            return Files.readAllBytes(silkFile);
        } finally {
            Files.deleteIfExists(pcmFile);
            Files.deleteIfExists(silkFile);
        }
    }

    public int wavDurationMillis(byte[] wav) throws IOException {
        return extractWavPcm(wav).length * 1000 / (SAMPLE_RATE * 2);
    }

    private void runCodec(String operation, Path input, Path output) throws IOException, InterruptedException {
        Path codec = Path.of(config.getSilkCodecPath()).toAbsolutePath().normalize();
        if (!Files.isExecutable(codec)) {
            throw new IOException("SILK 编解码器不存在或不可执行：" + codec);
        }
        Process process = new ProcessBuilder(codec.toString(), operation,
                input.toString(), output.toString()).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IOException("SILK 转码失败：" + log);
        }
    }

    private byte[] pcmToWav(byte[] pcm, int sampleRate) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes());
        header.putInt(pcm.length);
        out.write(header.array());
        out.write(pcm);
        return out.toByteArray();
    }

    private byte[] extractWavPcm(byte[] wav) throws IOException {
        if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            throw new IOException("CosyVoice 返回的不是 WAV 音频");
        }
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String id = new String(wav, offset, 4);
            int length = ByteBuffer.wrap(wav, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if ("data".equals(id) && offset + 8 + length <= wav.length) {
                return java.util.Arrays.copyOfRange(wav, offset + 8, offset + 8 + length);
            }
            offset += 8 + length + (length & 1);
        }
        throw new IOException("WAV 中找不到 PCM data 块");
    }
}
