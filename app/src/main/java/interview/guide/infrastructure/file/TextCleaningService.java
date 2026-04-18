package interview.guide.infrastructure.file;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文本清理服务
 * 提供统一的文本内容清理和规范化功能
 */
@Service
public class TextCleaningService {

    // ========== 预编译正则表达式（性能优化）==========
    
    /**
     * 图片文件名行：image123.png
     * 整行匹配，防止误删正文中的文件名字符串
     */
    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?m)^image\\d+\\.(png|jpe?g|gif|bmp|webp)\\s*$");

    /**
     * HTTP/HTTPS 图片链接
     * 支持 URL 查询参数，大小写不敏感
     */
    private static final Pattern IMAGE_URL =
            Pattern.compile("https?://\\S+?\\.(png|jpe?g|gif|bmp|webp)(\\?\\S*)?", Pattern.CASE_INSENSITIVE);

    /**
     * 文件协议 URL（Tika PDF 临时文件路径等）
     */
    private static final Pattern FILE_URL =
            Pattern.compile("file:(//)?\\S+", Pattern.CASE_INSENSITIVE);

    /**
     * 分隔线：---, ___, ***, ===
     * 整行匹配，至少 3 个连续符号
     */
    private static final Pattern SEPARATOR_LINE =
            Pattern.compile("(?m)^\\s*[-_*=]{3,}\\s*$");

    /**
     * 控制字符（不可见字符）
     * 保留换行符 \n (0x0A) 和制表符 \t (0x09)
     */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");

    /**
     * 零宽字符和 BOM
     */
    private static final Pattern ZERO_WIDTH_CHARS =
            Pattern.compile("[\\u200B-\\u200F\\uFEFF]");

    /**
     * HTML 标签
     */
    private static final Pattern HTML_TAGS =
            Pattern.compile("<[^>]+>");

    /**
     * 常见的兼容区 / 异体区字符块，PDF 提取后容易出现“看起来一样、检索却不相等”的问题
     */
    private static final Set<Character.UnicodeBlock> COMPATIBILITY_BLOCKS = Set.of(
            Character.UnicodeBlock.CJK_COMPATIBILITY,
            Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
            Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT,
            Character.UnicodeBlock.KANGXI_RADICALS,
            Character.UnicodeBlock.VERTICAL_FORMS,
            Character.UnicodeBlock.SMALL_FORM_VARIANTS
    );

    /**
     * 清理和规范化文本内容
     * 
     * <p>语义级过滤（简历场景化）：</p>
     * <ul>
     *   <li>去除控制字符</li>
     *   <li>去除图片文件名（整行匹配）</li>
     *   <li>去除图片链接</li>
     *   <li>去除文件协议路径</li>
     *   <li>去除符号分隔线</li>
     * </ul>
     * 
     * <p>格式级清理：</p>
     * <ul>
     *   <li>规范化换行符</li>
     *   <li>去除行尾空格，保留空行（保持段落结构）</li>
     *   <li>压缩连续空行（最多保留 2 个换行符）</li>
     * </ul>
     * 
     * <p>作为 RAG/AI 分析前的"保险层"，确保文本质量</p>
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String t = normalizeUnicode(text);

        // ========== 第一层：语义去噪 ==========
        t = CONTROL_CHARS.matcher(t).replaceAll("");
        t = ZERO_WIDTH_CHARS.matcher(t).replaceAll("");
        t = IMAGE_FILENAME_LINE.matcher(t).replaceAll("");
        t = IMAGE_URL.matcher(t).replaceAll("");
        t = FILE_URL.matcher(t).replaceAll("");
        t = SEPARATOR_LINE.matcher(t).replaceAll("");

        // ========== 第二层：格式规范化 ==========
        // 统一换行符
        t = t.replace("\r\n", "\n").replace("\r", "\n");

        // 去掉行尾空格和制表符，保留空行（保持段落结构）
        t = t.replaceAll("(?m)[ \t]+$", "");
        
        // 压缩连续空行：最多保留 2 个换行符（即一个空行）
        t = t.replaceAll("\\n{3,}", "\n\n");

        return t.strip();
    }

    /**
     * 做 Unicode 兼容归一化，解决 PDF 提取后“⾯试 / 面试”“Ａ / A”无法正常检索的问题
     */
    public String normalizeUnicode(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (codePoint == '\u00A0' || codePoint == '\u3000') {
                normalized.append(' ');
            } else if (shouldNormalizeCodePoint(codePoint)) {
                normalized.append(Normalizer.normalize(
                    new String(Character.toChars(codePoint)),
                    Normalizer.Form.NFKC
                ));
            } else {
                normalized.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return normalized.toString();
    }

    /**
     * 判断文本中是否包含常见兼容区字符，便于旧知识库启动时自动修复
     */
    public boolean containsCompatibilityCharacters(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (shouldNormalizeCodePoint(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private boolean shouldNormalizeCodePoint(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        if (COMPATIBILITY_BLOCKS.contains(block)) {
            return true;
        }
        return block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                && ((codePoint >= 0xFF10 && codePoint <= 0xFF19)
                || (codePoint >= 0xFF21 && codePoint <= 0xFF3A)
                || (codePoint >= 0xFF41 && codePoint <= 0xFF5A));
    }

    /**
     * 清理文本并限制最大长度
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 清理后的文本（可能被截断）
     */
    public String cleanTextWithLimit(String text, int maxLength) {
        String cleaned = cleanText(text);
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    /**
     * 清理文本并移除所有换行符（转为空格）
     * 适用于需要单行显示的场景
     *
     * @param text 原始文本
     * @return 单行文本
     */
    public String cleanToSingleLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return normalizeUnicode(text)
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("\\s+", " ")
            .strip();
    }

    /**
     * 移除 HTML 标签和常见 HTML 实体
     *
     * @param text 可能包含 HTML 的文本
     * @return 纯文本
     */
    public String stripHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return HTML_TAGS.matcher(normalizeUnicode(text)).replaceAll(" ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replaceAll("\\s+", " ")
            .strip();
    }
}
