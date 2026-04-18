package interview.guide.infrastructure.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentParseService integration tests.
 */
@Tag("integration")
@DisplayName("文档解析服务集成测试")
class DocumentParseIntegrationTest {

    private DocumentParseService documentParseService;

    @BeforeEach
    void setUp() {
        documentParseService = new DocumentParseService(new TextCleaningService());
    }

    @Test
    @DisplayName("解析 TXT 格式简历")
    void testParseTxtResume() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/test-files/sample-resume.txt");
        assertNotNull(inputStream, "测试文件不存在");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample-resume.txt",
            "text/plain",
            inputStream.readAllBytes()
        );

        String result = documentParseService.parseContent(file);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("张三"));
        assertTrue(result.contains("zhangsan@example.com"));
        assertTrue(result.contains("清华大学"));
        assertTrue(result.contains("字节跳动"));
        assertTrue(result.contains("Spring Boot"));
        assertTrue(result.contains("Redis"));
    }

    @Test
    @DisplayName("解析 Markdown 格式简历")
    void testParseMarkdownResume() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/test-files/sample-resume.md");
        assertNotNull(inputStream, "测试文件不存在");

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample-resume.md",
            "text/markdown",
            inputStream.readAllBytes()
        );

        String result = documentParseService.parseContent(file);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("李四"));
        assertTrue(result.contains("全栈工程师"));
        assertTrue(result.contains("lisi@example.com"));
        assertTrue(result.contains("北京大学"));
        assertTrue(result.contains("阿里巴巴"));
        assertTrue(result.contains("Vue"));
        assertTrue(result.contains("TypeScript"));
        assertTrue(result.contains("Kubernetes"));
        assertTrue(result.contains("AWS Certified"));
    }

    @Test
    @DisplayName("清理特殊字符和噪音文本")
    void testParseTextWithSpecialCharacters() {
        String content = """
            姓名：王五

            联系方式：
            wangwu@example.com
            139-0000-0000

            技能清单：
            Java / Spring Boot
            Python / Django
            JavaScript / Vue

            工作经历：
            2020-2023 某科技公司
            - 负责后端开发
            - 性能优化 (50ms -> 20ms)
            - 代码覆盖率 30% -> 85%

            GitHub: https://github.com/wangwu
            个人网站: https://wangwu.dev
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "special-chars.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        String result = documentParseService.parseContent(file);

        assertNotNull(result);
        assertTrue(result.contains("王五"));
        assertTrue(result.contains("Spring Boot"));
        assertTrue(result.contains("github.com"));
        assertTrue(result.contains("Vue"));
    }

    @Test
    @DisplayName("解析中英文混合文本")
    void testParseMultilingualText() {
        String content = """
            Resume of John Zhang (张强)

            Personal Information
            Name: John Zhang / 张强
            Email: john.zhang@example.com
            Location: Beijing, China / 中国北京

            Education
            Master of Computer Science, Peking University
            北京大学 计算机科学 硕士

            Work Experience
            2020-2023 Google Beijing Software Engineer
            2020-2023 谷歌北京 软件工程师

            Skills
            - Programming Languages: Java, Python, Go
            - Frameworks: Spring Boot, Django, Gin
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "multilingual.txt",
            "text/plain",
            content.getBytes(StandardCharsets.UTF_8)
        );

        String result = documentParseService.parseContent(file);

        assertNotNull(result);
        assertTrue(result.contains("John Zhang"));
        assertTrue(result.contains("张强"));
        assertTrue(result.contains("Peking University"));
        assertTrue(result.contains("北京大学"));
        assertTrue(result.contains("Google Beijing"));
        assertTrue(result.contains("Spring Boot"));
    }
}
