package interview.guide.modules.resume.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.interview.model.ResumeAnalysisResponse.ScoreDetail;
import interview.guide.modules.interview.model.ResumeAnalysisResponse.Suggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resume grading service.
 */
@Service
public class ResumeGradingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeGradingService.class);
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\n。；;!?！？]");

    private final LlmProviderRegistry llmProviderRegistry;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<ResumeAnalysisResponseDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;

    private record ResumeAnalysisResponseDTO(
        int overallScore,
        ScoreDetailDTO scoreDetail,
        String summary,
        List<String> strengths,
        List<SuggestionDTO> suggestions
    ) {
    }

    private record ScoreDetailDTO(
        int contentScore,
        int structureScore,
        int skillMatchScore,
        int expressionScore,
        int projectScore
    ) {
    }

    private record SuggestionDTO(
        String category,
        String priority,
        String issue,
        String recommendation
    ) {
    }

    public ResumeGradingService(
        LlmProviderRegistry llmProviderRegistry,
        StructuredOutputInvoker structuredOutputInvoker,
        ResumeAnalysisProperties properties,
        ResourceLoader resourceLoader
    ) throws IOException {
        this.llmProviderRegistry = llmProviderRegistry;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(properties.getSystemPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.userPromptTemplate = new PromptTemplate(
            resourceLoader.getResource(properties.getUserPromptPath())
                .getContentAsString(StandardCharsets.UTF_8)
        );
        this.outputConverter = new BeanOutputConverter<>(ResumeAnalysisResponseDTO.class);
    }

    public ResumeAnalysisResponse analyzeResume(String resumeText) {
        log.info("开始分析简历，文本长度={} 字符", resumeText.length());

        try {
            ChatClient chatClient = llmProviderRegistry.getDefaultChatClient();
            String systemPrompt = systemPromptTemplate.render();
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", resumeText);
            String userPrompt = userPromptTemplate.render(variables);
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();

            ResumeAnalysisResponseDTO dto = structuredOutputInvoker.invoke(
                chatClient,
                systemPromptWithFormat,
                userPrompt,
                outputConverter,
                ErrorCode.RESUME_ANALYSIS_FAILED,
                "简历分析失败：",
                "resume_analysis",
                log
            );
            ResumeAnalysisResponse result = convertToResponse(dto, resumeText);
            log.info("简历分析完成，总分={}", result.overallScore());
            return result;
        } catch (Exception e) {
            log.error("简历分析 AI 调用失败，启用兜底分析: {}", e.getMessage(), e);
            return createFallbackResponse(resumeText, e.getMessage());
        }
    }

    private ResumeAnalysisResponse convertToResponse(ResumeAnalysisResponseDTO dto, String originalText) {
        ScoreDetail scoreDetail = new ScoreDetail(
            dto.scoreDetail().contentScore(),
            dto.scoreDetail().structureScore(),
            dto.scoreDetail().skillMatchScore(),
            dto.scoreDetail().expressionScore(),
            dto.scoreDetail().projectScore()
        );

        List<Suggestion> suggestions = dto.suggestions().stream()
            .map(item -> new Suggestion(
                item.category(),
                item.priority(),
                item.issue(),
                item.recommendation()
            ))
            .toList();

        return new ResumeAnalysisResponse(
            dto.overallScore(),
            scoreDetail,
            dto.summary(),
            dto.strengths(),
            suggestions,
            originalText
        );
    }

    private ResumeAnalysisResponse createFallbackResponse(String originalText, String errorMessage) {
        int contentScore = scoreByLength(originalText.length(), 900, 25);
        int structureScore = scoreByKeywords(originalText, List.of("教育", "项目", "技能", "工作", "经历"), 20);
        int skillMatchScore = scoreByKeywords(
            originalText,
            List.of("java", "spring", "mysql", "redis", "vue", "python", "docker", "ai"),
            25
        );
        int expressionScore = scoreByLength(originalText.length(), 700, 15);
        int projectScore = scoreByKeywords(originalText, List.of("项目", "优化", "并发", "缓存", "设计", "性能"), 15);
        int overallScore = Math.min(100, contentScore + structureScore + skillMatchScore + expressionScore + projectScore);

        ScoreDetail scoreDetail = new ScoreDetail(
            contentScore,
            structureScore,
            skillMatchScore,
            expressionScore,
            projectScore
        );

        return new ResumeAnalysisResponse(
            overallScore,
            scoreDetail,
            buildFallbackSummary(originalText),
            extractHighlights(originalText),
            List.of(
                new Suggestion("内容", "中", "建议补充更多量化结果", "在项目描述中增加耗时、QPS、覆盖率、收益等具体指标。"),
                new Suggestion("结构", "中", "建议提升段落结构化程度", "尽量按背景、职责、方案、结果的顺序描述项目经历。"),
                new Suggestion("技能", "低", "建议突出核心岗位技能", "把与目标岗位最相关的技术栈放在更靠前的位置。"),
                new Suggestion("提示", "低", "当前结果为自动兜底分析", buildFallbackTip(errorMessage))
            ),
            originalText
        );
    }

    private int scoreByLength(int textLength, int expectedLength, int maxScore) {
        double ratio = Math.min(1.0, Math.max(0.35, (double) textLength / expectedLength));
        return (int) Math.round(maxScore * ratio);
    }

    private int scoreByKeywords(String text, List<String> keywords, int maxScore) {
        String lower = text.toLowerCase(Locale.ROOT);
        long hits = keywords.stream().filter(lower::contains).count();
        double ratio = Math.min(1.0, Math.max(0.35, hits / (double) Math.max(1, keywords.size() - 1)));
        return (int) Math.round(maxScore * ratio);
    }

    private List<String> extractHighlights(String originalText) {
        List<String> highlights = new ArrayList<>();
        String[] fragments = SPLIT_PATTERN.split(originalText);
        for (String fragment : fragments) {
            String candidate = fragment.trim();
            if (candidate.length() < 12) {
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.contains("spring")
                || lower.contains("redis")
                || lower.contains("mysql")
                || lower.contains("项目")
                || lower.contains("优化")
                || lower.contains("并发")
                || lower.contains("缓存")) {
                highlights.add(candidate);
            }
            if (highlights.size() >= 5) {
                break;
            }
        }

        if (highlights.isEmpty()) {
            highlights.add("简历包含较完整的教育、技能和项目信息，可用于继续生成面试方案。");
        }
        return highlights;
    }

    private String buildFallbackSummary(String originalText) {
        String compact = originalText.replaceAll("\\s+", " ").trim();
        String preview = compact.length() > 90 ? compact.substring(0, 90) + "..." : compact;
        return "当前分析结果由兜底策略自动生成。整体来看，简历信息较完整，技术栈和项目经历能够支撑后续面试练习，但仍建议补充更多量化成果与结构化表达。摘要片段：" + preview;
    }

    private String buildFallbackTip(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "系统已自动生成可用的基础分析结果，你可以直接继续查看并根据建议完善简历。";
        }
        return "系统已自动生成可用的基础分析结果。若需要更细致的 AI 分析，可稍后重新触发。";
    }
}
