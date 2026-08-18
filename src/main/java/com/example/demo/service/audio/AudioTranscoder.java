package com.example.demo.service.audio;

import com.example.demo.config.AiConfig;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@Component
public class AudioTranscoder {
    public static final int SAMPLE_RATE = 24_000;
    private final AiConfig config;

    public AudioTranscoder(AiConfig config) {
        this.config = config;
    }

    public byte[] silkToWav(byte[] silk) throws IOException, InterruptedException {
        Path silkFile = Files.createTempFile("wechat-voice-", ".silk");
        Path pcmFile = Files.createTempFile("wechat-voice-", ".pcm");
        try {
            Files.write(silkFile, silk);
            runCodec("decode", silkFile, pcmFile);
            return pcmToWav(Files.readAllBytes(pcmFile));
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
        return Math.max(1, extractWavPcm(wav).length * 1000 / (SAMPLE_RATE * 2));
    }

    private void runCodec(String operation, Path input, Path output)
            throws IOException, InterruptedException {
        Path codec = Path.of(config.getSilkCodecPath()).toAbsolutePath().normalize();
        if (!Files.isExecutable(codec)) {
            throw new IOException("SILK 编解码器不存在或不可执行：" + codec);
        }
        Process process = new ProcessBuilder(
                codec.toString(), operation, input.toString(), output.toString())
                .redirectErrorStream(true)
                .start();
        String processLog = new String(
                process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException("SILK 转码失败：" + processLog);
        }
    }

    private byte[] pcmToWav(byte[] pcm) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(44 + pcm.length);
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        output.write(header.array());
        output.write(pcm);
        return output.toByteArray();
    }

    private byte[] extractWavPcm(byte[] wav) throws IOException {
        if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I'
                || wav[2] != 'F' || wav[3] != 'F') {
            throw new IOException("语音合成结果不是有效的 WAV 音频");
        }
        int offset = 12;
        while (offset + 8 <= wav.length) {
            String chunkId = new String(wav, offset, 4, StandardCharsets.US_ASCII);
            int chunkLength = ByteBuffer.wrap(wav, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
            if (chunkLength < 0) {
                throw new IOException("WAV 子块长度无效");
            }
            long chunkEnd = (long) offset + 8 + chunkLength;
            if ("data".equals(chunkId) && chunkEnd <= wav.length) {
                return Arrays.copyOfRange(wav, offset + 8, (int) chunkEnd);
            }
            long nextOffset = chunkEnd + (chunkLength & 1);
            if (nextOffset > wav.length || nextOffset > Integer.MAX_VALUE) {
                break;
            }
            offset = (int) nextOffset;
        }
        throw new IOException("WAV 中找不到 PCM data 块");
    }
}
