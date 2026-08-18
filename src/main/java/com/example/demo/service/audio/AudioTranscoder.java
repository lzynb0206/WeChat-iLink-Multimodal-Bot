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

}
