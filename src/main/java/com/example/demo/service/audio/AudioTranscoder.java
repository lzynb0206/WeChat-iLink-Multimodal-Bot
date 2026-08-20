package com.example.demo.service.audio;

import com.example.demo.config.AudioConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Component
public class AudioTranscoder {
    private final AudioConfig config;

    public AudioTranscoder(AudioConfig config) {
        this.config = config;
    }

    public byte[] silkToWav(byte[] silk) throws IOException, InterruptedException {
        if (silk == null || silk.length == 0) {
            throw new IllegalArgumentException("SILK 语音内容为空");
        }
        Path script = Path.of(config.getSilkDecoderScript()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            throw new IOException("npm SILK 解码脚本不存在：" + script);
        }

        Process process = new ProcessBuilder(
                config.getNodeExecutable(),
                script.toString(),
                String.valueOf(config.getSampleRate()))
                .start();
        CompletableFuture<byte[]> wavFuture = readBytes(process.getInputStream());
        CompletableFuture<byte[]> errorFuture = readBytes(process.getErrorStream());
        try (var input = process.getOutputStream()) {
            input.write(silk);
        }

        if (!process.waitFor(config.getDecodeTimeoutSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("npm SILK 解码超时");
        }
        byte[] wav = await(wavFuture);
        String error = new String(await(errorFuture), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException("npm SILK 解码失败：" + error);
        }
        if (wav.length < 44 || !"RIFF".equals(
                new String(wav, 0, 4, StandardCharsets.US_ASCII))) {
            throw new IOException("npm SILK 解码器未返回有效 WAV 数据");
        }
        return wav;
    }

    private CompletableFuture<byte[]> readBytes(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return stream.readAllBytes();
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    private byte[] await(CompletableFuture<byte[]> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("读取 npm SILK 解码结果失败", cause);
        }
    }
}
