package com.argus.rag.ingestion.service.pipeline.parser;

import com.argus.rag.common.exception.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Arrays;
import java.util.List;

/**
 * 文本解码工具类，用于自动检测并解码输入流的字符编码。
 *
 * <h3>编码检测策略</h3>
 * <p>解码过程按以下优先级进行：</p>
 * <ol>
 *     <li><b>BOM 检测</b>：通过文件头部的字节序标记判断编码
 *         <ul>
 *             <li>UTF-8 BOM（{@code EF BB BF}）→ 使用 UTF-8 解码（跳过 BOM）</li>
 *             <li>UTF-16 LE BOM（{@code FF FE}）→ 使用 UTF-16LE 解码（跳过 BOM）</li>
 *             <li>UTF-16 BE BOM（{@code FE FF}）→ 使用 UTF-16BE 解码（跳过 BOM）</li>
 *         </ul>
 *     </li>
 *     <li><b>无 BOM 回退</b>：按顺序尝试以下编码，使用严格模式解码（遇到非法字节或
 *         不可映射字符立即报错），选取第一个成功解码的编码：
 *         <ol>
 *             <li>UTF-8</li>
 *             <li>GB18030（中文编码，兼容 GBK/GB2312）</li>
 *         </ol>
 *     </li>
 * </ol>
 *
 * <h3>严格解码模式</h3>
 * <p>所有解码均使用严格模式（{@link CodingErrorAction#REPORT}），即遇到非法输入
 * 或不可映射字符时立即抛出 {@link CharacterCodingException}，而非静默替换，
 * 以避免数据丢失或乱码。</p>
 *
 * <h3>不可实例化</h3>
 * <p>所有方法均为静态方法，构造器私有化以防止实例化。</p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public final class TextDecodingSupport {

    /** UTF-8 BOM 字节序列：EF BB BF */
    private static final byte[] UTF_8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** UTF-16 小端序 BOM 字节序列：FF FE */
    private static final byte[] UTF_16_LE_BOM = new byte[]{(byte) 0xFF, (byte) 0xFE};

    /** UTF-16 大端序 BOM 字节序列：FE FF */
    private static final byte[] UTF_16_BE_BOM = new byte[]{(byte) 0xFE, (byte) 0xFF};

    /** GB18030 编码实例，用于中文文本的回退解码 */
    private static final Charset GB18030 = Charset.forName("GB18030");

    /** 无 BOM 时的回退编码列表，按优先级排列 */
    private static final List<Charset> FALLBACK_CHARSETS = List.of(StandardCharsets.UTF_8, GB18030);

    /** 私有构造器，防止实例化 */
    private TextDecodingSupport() {
    }

    /**
     * 自动检测编码并解码输入流为字符串。
     *
     * <p>优先通过 BOM 判断编码，若无 BOM 则按 UTF-8 → GB18030 顺序回退尝试。
     * 所有解码均使用严格模式，确保不会静默替换非法字节。</p>
     *
     * @param inputStream   包含文本内容的输入流，解码时将读取全部字节
     * @param failureMessage 解码失败时的业务异常消息
     * @return 解码后的字符串
     * @throws com.argus.rag.common.exception.BusinessException 当所有编码尝试均失败或读取异常时抛出，
     *         异常消息使用 failureMessage 参数值
     */
    public static String decode(InputStream inputStream, String failureMessage) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            if (hasBom(bytes, UTF_8_BOM)) {
                return decodeStrict(bytes, UTF_8_BOM.length, StandardCharsets.UTF_8);
            }
            if (hasBom(bytes, UTF_16_LE_BOM)) {
                return decodeStrict(bytes, UTF_16_LE_BOM.length, StandardCharsets.UTF_16LE);
            }
            if (hasBom(bytes, UTF_16_BE_BOM)) {
                return decodeStrict(bytes, UTF_16_BE_BOM.length, StandardCharsets.UTF_16BE);
            }
            return decodeWithoutBom(bytes, failureMessage);
        } catch (IOException exception) {
            throw new BusinessException(failureMessage, exception);
        }
    }

    /**
     * 检测字节数组是否以指定的 BOM 字节序列开头。
     *
     * @param bytes 待检测的字节数组
     * @param bom   BOM 字节序列
     * @return {@code true} 表示字节数组以指定的 BOM 开头
     */
    private static boolean hasBom(byte[] bytes, byte[] bom) {
        return bytes.length >= bom.length && Arrays.equals(Arrays.copyOf(bytes, bom.length), bom);
    }

    /**
     * 使用指定编码的严格模式解码字节数组。
     *
     * <p>遇到非法输入或不可映射字符时立即抛出 {@link CharacterCodingException}。</p>
     *
     * @param bytes   待解码的字节数组
     * @param offset  解码起始偏移量（用于跳过 BOM）
     * @param charset 目标字符编码
     * @return 解码后的字符串
     * @throws CharacterCodingException 当字节数据不符合指定编码时抛出
     */
    private static String decodeStrict(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        return charBuffer.toString();
    }

    /**
     * 对无 BOM 的字节数据按回退列表依次尝试解码。
     *
     * <p>按 {@link #FALLBACK_CHARSETS} 列表顺序依次尝试严格解码，
     * 返回第一个解码成功的字符串。若全部失败则抛出最后一个
     * {@link CharacterCodingException}。</p>
     *
     * @param bytes          待解码的字节数组
     * @param failureMessage 解码失败时的业务异常消息（当前未使用，保留用于将来扩展）
     * @return 解码后的字符串
     * @throws CharacterCodingException 当所有编码均无法解码时抛出最后一个异常
     */
    private static String decodeWithoutBom(byte[] bytes, String failureMessage) throws CharacterCodingException {
        CharacterCodingException lastException = null;
        for (Charset charset : FALLBACK_CHARSETS) {
            try {
                return decodeStrict(bytes, 0, charset);
            } catch (CharacterCodingException exception) {
                lastException = exception;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new CharacterCodingException();
    }
}
