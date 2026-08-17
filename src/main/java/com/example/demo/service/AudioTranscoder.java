// service 包保存音频转换业务。
package com.example.demo.service;

// 导入包含 SILK 编解码器路径的配置对象。
import com.example.demo.config.MediaAiConfig;
// @Component 让 Spring 自动创建这个音频转换对象。
import org.springframework.stereotype.Component;

// ByteArrayOutputStream 用来在内存中组合 WAV 文件头和 PCM 数据。
import java.io.ByteArrayOutputStream;
// IOException 表示文件读写或外部程序执行失败。
import java.io.IOException;
// ByteBuffer 用来按二进制格式读写 WAV 文件头。
import java.nio.ByteBuffer;
// ByteOrder 用来指定 WAV 使用的小端字节顺序。
import java.nio.ByteOrder;
// Files 提供创建、读取和删除临时文件的方法。
import java.nio.file.Files;
// Path 表示一个文件系统路径。
import java.nio.file.Path;

// 把 AudioTranscoder 注册到 Spring 容器。
@Component
public class AudioTranscoder {
    // 微信语音和 CosyVoice 在本项目中统一使用 24,000Hz 采样率。
    private static final int SAMPLE_RATE = 24_000;
    // 保存 SILK 编解码程序所在路径。
    private final MediaAiConfig config;

    // Spring 自动传入媒体模型配置。
    public AudioTranscoder(MediaAiConfig config) {
        // 保存配置供后续方法使用。
        this.config = config;
    }

    // 把微信下载到的 SILK 字节转换为百炼能识别的 WAV 字节。
    public byte[] silkToWav(byte[] silk) throws IOException, InterruptedException {
        // 在系统临时目录创建 SILK 文件；它不是项目 downloads 目录。
        Path silkFile = Files.createTempFile("wechat-voice-", ".silk");
        // 在系统临时目录创建用于接收解码结果的 PCM 文件。
        Path pcmFile = Files.createTempFile("wechat-voice-", ".pcm");
        // try/finally 确保成功或失败时都会删除临时文件。
        try {
            // 把内存中的微信语音临时写入 SILK 文件。
            Files.write(silkFile, silk);
            // 调用本地编解码器执行 SILK → PCM。
            runCodec("decode", silkFile, pcmFile);
            // 读取 PCM、添加 WAV 文件头，并把结果返回到内存。
            return pcmToWav(Files.readAllBytes(pcmFile), SAMPLE_RATE);
        } finally {
            // 删除临时 SILK 文件，避免占用磁盘。
            Files.deleteIfExists(silkFile);
            // 删除临时 PCM 文件，避免占用磁盘。
            Files.deleteIfExists(pcmFile);
        }
    }

    // 把 CosyVoice 返回的 WAV 字节转换为微信能发送的 SILK 字节。
    public byte[] wavToSilk(byte[] wav) throws IOException, InterruptedException {
        // 从 WAV 文件中取出真正的 PCM 音频数据。
        byte[] pcm = extractWavPcm(wav);
        // 创建一个临时 PCM 输入文件。
        Path pcmFile = Files.createTempFile("tts-answer-", ".pcm");
        // 创建一个临时 SILK 输出文件。
        Path silkFile = Files.createTempFile("tts-answer-", ".silk");
        // try/finally 保证临时文件最终被删除。
        try {
            // 把 PCM 字节写入临时文件。
            Files.write(pcmFile, pcm);
            // 调用本地编解码器执行 PCM → SILK。
            runCodec("encode", pcmFile, silkFile);
            // 读取 SILK 结果到内存并返回。
            return Files.readAllBytes(silkFile);
        } finally {
            // 删除临时 PCM 文件。
            Files.deleteIfExists(pcmFile);
            // 删除临时 SILK 文件。
            Files.deleteIfExists(silkFile);
        }
    }

    // 根据 WAV 中 PCM 的字节数计算语音时长，单位是毫秒。
    public int wavDurationMillis(byte[] wav) throws IOException {
        // 24kHz、单声道、16bit 表示每秒有 24,000×2 字节。
        return extractWavPcm(wav).length * 1000 / (SAMPLE_RATE * 2);
    }

    // 启动编译好的本地 SILK 程序完成一次编码或解码。
    private void runCodec(String operation, Path input, Path output)
            throws IOException, InterruptedException {
        // 把配置中的相对路径转换为规范的绝对路径。
        Path codec = Path.of(config.getSilkCodecPath()).toAbsolutePath().normalize();
        // 文件不存在或没有执行权限时不能继续。
        if (!Files.isExecutable(codec)) {
            // 抛出明确错误，告诉开发者应该检查哪个路径。
            throw new IOException("SILK 编解码器不存在或不可执行：" + codec);
        }
        // 启动命令：silk_codec decode/encode 输入文件 输出文件。
        Process process = new ProcessBuilder(
                codec.toString(), operation, input.toString(), output.toString())
                // 把错误输出合并到普通输出，避免漏掉错误信息。
                .redirectErrorStream(true)
                // 真正启动子进程。
                .start();
        // 读取子进程输出，失败时用于日志提示。
        String processLog = new String(process.getInputStream().readAllBytes());
        // 等待转码完成；返回值 0 表示成功。
        if (process.waitFor() != 0) {
            // 非 0 时把转码日志包装成异常。
            throw new IOException("SILK 转码失败：" + processLog);
        }
    }

    // 给裸 PCM 数据添加标准的 44 字节 WAV 文件头。
    private byte[] pcmToWav(byte[] pcm, int sampleRate) throws IOException {
        // 创建容量足够保存“文件头 + PCM”的内存输出流。
        ByteArrayOutputStream output = new ByteArrayOutputStream(44 + pcm.length);
        // 创建 44 字节的小端序 WAV 文件头缓冲区。
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        // RIFF 是 WAV 文件的固定开头标识。
        header.put("RIFF".getBytes());
        // 写入 RIFF 块长度，它等于整个文件长度减去前 8 字节。
        header.putInt(36 + pcm.length);
        // 写入 WAVE 和 fmt 块标识。
        header.put("WAVEfmt ".getBytes());
        // PCM 的 fmt 块固定为 16 字节。
        header.putInt(16);
        // 音频格式 1 表示未压缩 PCM。
        header.putShort((short) 1);
        // 声道数 1 表示单声道。
        header.putShort((short) 1);
        // 写入每秒采样次数。
        header.putInt(sampleRate);
        // 每秒字节数 = 采样率 × 2 字节。
        header.putInt(sampleRate * 2);
        // 每个采样帧占 2 字节。
        header.putShort((short) 2);
        // 每个采样点使用 16bit。
        header.putShort((short) 16);
        // data 表示后面开始是真正的声音数据。
        header.put("data".getBytes());
        // 写入 PCM 数据长度。
        header.putInt(pcm.length);
        // 先把 WAV 文件头写入内存输出流。
        output.write(header.array());
        // 再写入真正的 PCM 声音数据。
        output.write(pcm);
        // 返回完整 WAV 字节数组。
        return output.toByteArray();
    }

    // 从 WAV 文件中定位并取出 PCM data 块。
    private byte[] extractWavPcm(byte[] wav) throws IOException {
        // WAV 至少应有 44 字节，并且开头必须是 RIFF。
        if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I'
                || wav[2] != 'F' || wav[3] != 'F') {
            // 格式不正确时停止处理。
            throw new IOException("CosyVoice 返回的不是 WAV 音频");
        }
        // 前 12 字节是 RIFF 基础头，第一个子块从位置 12 开始。
        int offset = 12;
        // 每个 WAV 子块至少包含 4 字节名称和 4 字节长度。
        while (offset + 8 <= wav.length) {
            // 读取当前子块的四字符名称，例如 fmt 或 data。
            String chunkId = new String(wav, offset, 4);
            // 按小端序读取当前子块的数据长度。
            int chunkLength = ByteBuffer.wrap(wav, offset + 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
            // 找到 data 块且长度没有超出文件范围时返回其中的数据。
            if ("data".equals(chunkId) && offset + 8 + chunkLength <= wav.length) {
                // 复制 data 块中的 PCM 字节。
                return java.util.Arrays.copyOfRange(
                        wav, offset + 8, offset + 8 + chunkLength);
            }
            // 跳到下一个子块；奇数长度的块需要补齐一个字节。
            offset += 8 + chunkLength + (chunkLength & 1);
        }
        // 遍历完整个文件仍找不到 data 块时说明 WAV 不完整。
        throw new IOException("WAV 中找不到 PCM data 块");
    }
}
