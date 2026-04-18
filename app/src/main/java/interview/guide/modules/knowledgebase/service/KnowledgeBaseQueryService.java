package interview.guide.modules.knowledgebase.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.TextCleaningService;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseChunkEntity;
import interview.guide.modules.knowledgebase.model.QueryRequest;
import interview.guide.modules.knowledgebase.model.QueryResponse;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseQueryService {

  private static final String NO_RESULT_RESPONSE =
      "抱歉，当前选中的知识库中没有检索到足够相关的内容。请换一个更具体的问题，或补充关键词后再试。";
  private static final String GREETING_RESPONSE =
      "已连接当前选中的知识库，你可以直接提问，我会先检索再回答。";
  private static final String THANKS_RESPONSE = "不客气，你可以继续追问当前知识库里的内容。";
  private static final String STREAM_ERROR_RESPONSE = "知识库检索失败，请稍后重试。";
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9_+#.-]{2,}");
  private static final Pattern ZH_QUESTION_PREFIX = Pattern.compile(
      "^(?:什么是|如何|怎么|怎样|为什么|什么叫|讲一下|解释一下|介绍一下)(.+)$");
  private static final Pattern ZH_QUESTION_SUFFIX = Pattern.compile(
      "^(.+?)(?:是什么|怎么样|如何|有哪些|有什么|是啥).*$");
  private static final Pattern PURE_GREETING_PATTERN = Pattern.compile(
      "^(?:你好|您好|hi|hello|hey|在吗|在么|早上好|晚上好)[!！。？?\\s]*$",
      Pattern.CASE_INSENSITIVE
  );
  private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");
  private static final Pattern SELF_INTRO_PATTERN = Pattern.compile(
      "^(?:你好[，,、\\s]*)?(?:我是|我叫|叫我|你可以叫我|我的名字是)(.+)$"
  );
  private static final Pattern THANKS_PATTERN = Pattern.compile(
      "^(?:谢谢|谢谢你|thanks|thank\\s*you|辛苦了)[!！。？?\\s]*$",
      Pattern.CASE_INSENSITIVE
  );
  private static final int STREAM_PROBE_WINDOW = 120;
  private static final int MAX_HISTORY_TURNS = 6;
  private static final int KEYWORD_SNIPPET_WINDOW = 220;
  private static final int MAX_SOURCES = 5;
  private static final int MAX_SEARCH_TERMS = 24;
  private static final int FALLBACK_SUMMARY_LENGTH = 180;
  private static final List<String> SEARCH_STOP_PHRASES = List.of(
      "请问", "一下", "介绍", "讲讲", "说说", "解释", "如何", "怎么", "怎样", "为什么",
      "有哪些", "有什么", "是什么", "的", "常见的", "常见", "实现", "原理", "机制", "方法"
  );
  private static final List<String> GENERIC_TERMS = List.of(
      "什么", "如何", "怎么", "哪些", "一下", "实现", "原理", "机制", "方法", "介绍", "说说", "常见"
  );
  private static final List<String> DOMAIN_PHRASES = List.of(
      "面试题", "高频题", "原题", "消息队列", "异步消息队列", "延时消息队列", "分布式锁",
      "持久化", "哨兵", "集群", "主从", "缓存穿透", "缓存击穿", "缓存雪崩", "过期策略", "淘汰策略"
  );

  private final LlmProviderRegistry llmProviderRegistry;
  private final KnowledgeBaseVectorService vectorService;
  private final KnowledgeBaseListService listService;
  private final KnowledgeBaseCountService countService;
  private final KnowledgeBaseChunkRepository chunkRepository;
  private final TextCleaningService textCleaningService;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final PromptTemplate rewritePromptTemplate;
  private final boolean rewriteEnabled;
  private final int shortQueryLength;
  private final int topkShort;
  private final int topkMedium;
  private final int topkLong;
  private final double minScoreShort;
  private final double minScoreDefault;

  public KnowledgeBaseQueryService(
      LlmProviderRegistry llmProviderRegistry,
      KnowledgeBaseVectorService vectorService,
      KnowledgeBaseListService listService,
      KnowledgeBaseCountService countService,
      KnowledgeBaseChunkRepository chunkRepository,
      TextCleaningService textCleaningService,
      KnowledgeBaseQueryProperties queryProperties,
      ResourceLoader resourceLoader
  ) throws IOException {
    this.llmProviderRegistry = llmProviderRegistry;
    this.vectorService = vectorService;
    this.listService = listService;
    this.countService = countService;
    this.chunkRepository = chunkRepository;
    this.textCleaningService = textCleaningService;
    this.systemPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(queryProperties.getSystemPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.userPromptTemplate = new PromptTemplate(
        resourceLoader.getResource(queryProperties.getUserPromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.rewritePromptTemplate = new PromptTemplate(
        resourceLoader.getResource(queryProperties.getRewritePromptPath())
            .getContentAsString(StandardCharsets.UTF_8)
    );
    this.rewriteEnabled = queryProperties.getRewrite().isEnabled();
    this.shortQueryLength = queryProperties.getSearch().getShortQueryLength();
    this.topkShort = queryProperties.getSearch().getTopkShort();
    this.topkMedium = queryProperties.getSearch().getTopkMedium();
    this.topkLong = queryProperties.getSearch().getTopkLong();
    this.minScoreShort = queryProperties.getSearch().getMinScoreShort();
    this.minScoreDefault = queryProperties.getSearch().getMinScoreDefault();
  }

  public String answerQuestion(Long knowledgeBaseId, String question) {
    return answerQuestion(List.of(knowledgeBaseId), question);
  }

  public String answerQuestion(List<Long> knowledgeBaseIds, String question) {
    return answerQuestion(knowledgeBaseIds, question, List.of());
  }

  public String answerQuestion(
      List<Long> knowledgeBaseIds,
      String question,
      List<ConversationTurn> conversationHistory
  ) {
    return generateAnswer(knowledgeBaseIds, question, conversationHistory);
  }

  public QueryResponse queryKnowledgeBase(QueryRequest request) {
    String answer = answerQuestion(request.knowledgeBaseIds(), request.question());
    List<String> knowledgeBaseNames = listService.getKnowledgeBaseNames(request.knowledgeBaseIds());
    Long primaryKnowledgeBaseId = request.knowledgeBaseIds().isEmpty() ? null : request.knowledgeBaseIds().get(0);
    return new QueryResponse(answer, primaryKnowledgeBaseId, String.join("、", knowledgeBaseNames));
  }

  public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question) {
    return answerQuestionStream(knowledgeBaseIds, question, List.of());
  }

  public Flux<String> answerQuestionStream(
      List<Long> knowledgeBaseIds,
      String question,
      List<ConversationTurn> conversationHistory
  ) {
    String normalizedQuestion = normalizeQuestion(question);
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizedQuestion.isBlank()) {
      return Flux.just(NO_RESULT_RESPONSE);
    }
    if (isPureGreeting(normalizedQuestion)) {
      return Flux.just(GREETING_RESPONSE);
    }
    if (isThanks(normalizedQuestion)) {
      return Flux.just(THANKS_RESPONSE);
    }

    List<ConversationTurn> safeHistory = trimConversationHistory(conversationHistory);

    try {
      countService.updateQuestionCounts(knowledgeBaseIds);
      QueryContext queryContext = buildQueryContext(normalizedQuestion, safeHistory);
      List<RetrievedChunk> retrievedChunks = retrieveRelevantChunks(queryContext, knowledgeBaseIds);
      if (retrievedChunks.isEmpty()) {
        return Flux.just(NO_RESULT_RESPONSE);
      }

      Flux<String> rawFlux = chatClient().prompt()
          .system(systemPromptTemplate.render())
          .user(buildUserPrompt(retrievedChunks, normalizedQuestion, safeHistory))
          .stream()
          .content();

      Flux<String> normalizedFlux = normalizeStreamOutput(rawFlux);
      return appendSourcesToStream(normalizedFlux, retrievedChunks)
          .onErrorResume(e -> {
            log.error("知识库流式问答失败: question={}", normalizedQuestion, e);
            return Flux.just(STREAM_ERROR_RESPONSE);
          });
    } catch (BusinessException e) {
      log.error("知识库流式问答失败: question={}", normalizedQuestion, e);
      return Flux.just(STREAM_ERROR_RESPONSE);
    } catch (Exception e) {
      log.error("知识库流式问答失败: question={}", normalizedQuestion, e);
      return Flux.just(STREAM_ERROR_RESPONSE);
    }
  }

  private String generateAnswer(
      List<Long> knowledgeBaseIds,
      String question,
      List<ConversationTurn> conversationHistory
  ) {
    String normalizedQuestion = normalizeQuestion(question);
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizedQuestion.isBlank()) {
      return NO_RESULT_RESPONSE;
    }
    if (isPureGreeting(normalizedQuestion)) {
      return GREETING_RESPONSE;
    }
    if (isThanks(normalizedQuestion)) {
      return THANKS_RESPONSE;
    }

    List<ConversationTurn> safeHistory = trimConversationHistory(conversationHistory);

    try {
      countService.updateQuestionCounts(knowledgeBaseIds);
      QueryContext queryContext = buildQueryContext(normalizedQuestion, safeHistory);
      List<RetrievedChunk> retrievedChunks = retrieveRelevantChunks(queryContext, knowledgeBaseIds);
      if (retrievedChunks.isEmpty()) {
        return NO_RESULT_RESPONSE;
      }

      String answer = chatClient().prompt()
          .system(systemPromptTemplate.render())
          .user(buildUserPrompt(retrievedChunks, normalizedQuestion, safeHistory))
          .call()
          .content();
      return appendSources(normalizeAnswer(answer), retrievedChunks);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("知识库问答失败: question={}", normalizedQuestion, e);
      throw new BusinessException(
          ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED,
          "知识库查询失败: " + e.getMessage(),
          e
      );
    }
  }

  private QueryContext buildQueryContext(String originalQuestion, List<ConversationTurn> conversationHistory) {
    String contextualQuestion = enrichQuestionWithHistory(originalQuestion, conversationHistory);
    String rewrittenQuestion = rewriteQuestion(contextualQuestion);
    SearchParams searchParams = resolveSearchParams(contextualQuestion);

    Set<String> candidateQueries = new LinkedHashSet<>();
    candidateQueries.add(rewrittenQuestion);
    candidateQueries.add(contextualQuestion);
    candidateQueries.add(originalQuestion);
    return new QueryContext(originalQuestion, contextualQuestion, new ArrayList<>(candidateQueries), searchParams);
  }

  private List<RetrievedChunk> retrieveRelevantChunks(QueryContext queryContext, List<Long> knowledgeBaseIds) {
    for (String candidateQuery : queryContext.candidateQueries()) {
      if (candidateQuery == null || candidateQuery.isBlank()) {
        continue;
      }
      List<Document> documents = vectorService.similaritySearch(
          candidateQuery,
          knowledgeBaseIds,
          queryContext.searchParams().topK(),
          queryContext.searchParams().minScore()
      );
      List<RetrievedChunk> retrievedChunks = hydrateRetrievedChunks(documents);
      log.info("Redis 向量检索: query='{}', hits={}", candidateQuery, retrievedChunks.size());
      if (hasEffectiveHit(queryContext.originalQuestion(), retrievedChunks)) {
        return retrievedChunks;
      }
    }
    return retrieveChunksByKeyword(queryContext.candidateQueries(), knowledgeBaseIds);
  }

  private List<RetrievedChunk> hydrateRetrievedChunks(List<Document> documents) {
    if (documents == null || documents.isEmpty()) {
      return List.of();
    }

    Map<String, KnowledgeBaseChunkEntity> chunkMap = chunkRepository.findByDocumentIds(
            documents.stream().map(Document::getId).toList()
        ).stream()
        .collect(Collectors.toMap(KnowledgeBaseChunkEntity::getDocumentId, chunk -> chunk));

    List<RetrievedChunk> hydratedChunks = new ArrayList<>();
    for (Document document : documents) {
      KnowledgeBaseChunkEntity chunkEntity = chunkMap.get(document.getId());
      Map<String, Object> metadata = document.getMetadata();

      Long kbId = chunkEntity != null
          ? chunkEntity.getKnowledgeBaseId()
          : parseLong(metadata.get("kb_id"));
      String sourceName = normalizeDisplayText(chunkEntity != null
          ? chunkEntity.getSourceName()
          : safeString(metadata.get("source_name"), "知识库片段"));
      String sourceFileName = normalizeDisplayText(chunkEntity != null
          ? chunkEntity.getSourceFilename()
          : safeString(metadata.get("source_file_name"), sourceName));
      int chunkIndex = chunkEntity != null
          ? defaultInt(chunkEntity.getChunkIndex())
          : parseInt(metadata.get("chunk_index"));
      String content = chunkEntity != null && chunkEntity.getContent() != null && !chunkEntity.getContent().isBlank()
          ? chunkEntity.getContent()
          : document.getText();
      String normalizedContent = normalizeRetrievedContent(content);

      hydratedChunks.add(new RetrievedChunk(
          document.getId(),
          kbId,
          sourceName,
          sourceFileName,
          chunkIndex,
          normalizedContent,
          document.getScore()
      ));
    }

    return hydratedChunks.stream()
        .collect(Collectors.toMap(
            RetrievedChunk::documentId,
            chunk -> chunk,
            (left, right) -> left,
            java.util.LinkedHashMap::new
        ))
        .values()
        .stream()
        .limit(MAX_SOURCES)
        .toList();
  }

  private String rewriteQuestion(String question) {
    if (!rewriteEnabled || question == null || question.isBlank()) {
      return question;
    }
    try {
      String prompt = rewritePromptTemplate.render(Map.of("question", question));
      String rewritten = chatClient().prompt().user(prompt).call().content();
      if (rewritten == null || rewritten.isBlank()) {
        return question;
      }
      return rewritten.trim();
    } catch (Exception e) {
      log.warn("查询改写失败，回退原始问题: {}", e.getMessage());
      return question;
    }
  }

  private SearchParams resolveSearchParams(String question) {
    int compactLength = question.replaceAll("\\s+", "").length();
    if (compactLength <= shortQueryLength) {
      return new SearchParams(topkShort, minScoreShort);
    }
    if (compactLength <= 12) {
      return new SearchParams(topkMedium, minScoreDefault);
    }
    return new SearchParams(topkLong, minScoreDefault);
  }

  private String buildUserPrompt(
      List<RetrievedChunk> retrievedChunks,
      String question,
      List<ConversationTurn> conversationHistory
  ) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("context", buildContext(retrievedChunks));
    variables.put("question", question);
    String prompt = userPromptTemplate.render(variables);

    String historyText = formatConversationHistory(conversationHistory);
    if (historyText.isBlank()) {
      return prompt;
    }
    return prompt + "\n\n## 对话上下文\n" + historyText;
  }

  private boolean hasEffectiveHit(String originalQuestion, List<RetrievedChunk> retrievedChunks) {
    if (retrievedChunks == null || retrievedChunks.isEmpty()) {
      return false;
    }
    if (!isShortTokenQuery(originalQuestion)) {
      return hasMeaningfulTermOverlap(originalQuestion, retrievedChunks);
    }
    String coreTerm = extractCoreTerm(originalQuestion).toLowerCase(Locale.ROOT);
    if (coreTerm.isBlank()) {
      return true;
    }
    return retrievedChunks.stream()
        .map(RetrievedChunk::content)
        .filter(content -> content != null && !content.isBlank())
        .map(content -> content.toLowerCase(Locale.ROOT))
        .anyMatch(content -> content.contains(coreTerm));
  }

  private boolean isShortTokenQuery(String question) {
    String coreTerm = extractCoreTerm(question).replaceAll("\\s+", "");
    return !coreTerm.isBlank() && coreTerm.length() <= shortQueryLength;
  }

  private String extractCoreTerm(String question) {
    String normalized = normalizeQuestion(question).replaceAll("[?？!！。；;，,]+$", "");
    if (normalized.isBlank()) {
      return "";
    }
    Matcher matcher = ZH_QUESTION_PREFIX.matcher(normalized);
    if (matcher.matches()) {
      return matcher.group(1).trim();
    }
    matcher = ZH_QUESTION_SUFFIX.matcher(normalized);
    if (matcher.matches()) {
      return matcher.group(1).trim();
    }
    return normalized;
  }

  private List<RetrievedChunk> retrieveChunksByKeyword(List<String> candidateQueries, List<Long> knowledgeBaseIds) {
    List<String> searchTerms = extractSearchTerms(candidateQueries);
    if (searchTerms.isEmpty() || knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
      return List.of();
    }

    boolean catalogQuery = isCatalogQuery(candidateQueries);

    List<KeywordHit> hits = new ArrayList<>();
    for (KnowledgeBaseChunkEntity chunk : chunkRepository.findLightweightByKnowledgeBaseIds(knowledgeBaseIds)) {
      if (chunk.getContent() == null || chunk.getContent().isBlank()) {
        continue;
      }
      KeywordHit bestHit = findBestKeywordHit(chunk, searchTerms, catalogQuery);
      if (bestHit != null) {
        hits.add(bestHit);
      }
    }

    if (hits.isEmpty()) {
      return List.of();
    }

    return hits.stream()
        .sorted(Comparator.comparingInt(KeywordHit::score).reversed())
        .collect(Collectors.toMap(
            hit -> hit.chunk().getDocumentId(),
            hit -> hit,
            (left, right) -> left.score() >= right.score() ? left : right,
            java.util.LinkedHashMap::new
        ))
        .values()
        .stream()
        .limit(MAX_SOURCES)
        .map(hit -> new RetrievedChunk(
            hit.chunk().getDocumentId(),
            hit.chunk().getKnowledgeBaseId(),
            normalizeDisplayText(hit.chunk().getSourceName()),
            normalizeDisplayText(hit.chunk().getSourceFilename()),
            defaultInt(hit.chunk().getChunkIndex()),
            hit.excerpt(),
            null
        ))
        .toList();
  }

  private List<String> extractSearchTerms(List<String> candidateQueries) {
    Set<String> terms = new LinkedHashSet<>();
    for (String query : candidateQueries) {
      String normalized = normalizeQuestion(query).toLowerCase(Locale.ROOT);
      if (normalized.isBlank()) {
        continue;
      }
      addEnglishTerms(normalized, terms);
      addDomainPhraseTerms(normalized, terms);
      addChineseTerms(normalized, terms);
    }
    return terms.stream().limit(MAX_SEARCH_TERMS).toList();
  }

  private KeywordHit findBestKeywordHit(
      KnowledgeBaseChunkEntity chunk,
      List<String> searchTerms,
      boolean catalogQuery
  ) {
    String content = normalizeRetrievedContent(chunk.getContent());
    String normalizedContent = normalizeSearchableText(content);
    KeywordHit bestHit = null;
    Set<String> matchedTerms = new LinkedHashSet<>();
    int totalOccurrences = 0;
    int longestMatchLength = 0;
    for (String searchTerm : searchTerms) {
      int firstIndex = normalizedContent.indexOf(searchTerm);
      if (firstIndex < 0) {
        continue;
      }
      matchedTerms.add(searchTerm);
      totalOccurrences += Math.min(countOccurrences(normalizedContent, searchTerm), 5);
      longestMatchLength = Math.max(longestMatchLength, searchTerm.length());
      String excerpt = buildExcerpt(content, firstIndex, searchTerm.length());
      bestHit = new KeywordHit(chunk, excerpt, 0, matchedTerms.size(), totalOccurrences, longestMatchLength);
    }

    if (bestHit == null || !isMeaningfulKeywordHit(bestHit, catalogQuery, searchTerms.size())) {
      return null;
    }

    int score = bestHit.distinctMatchCount() * 22
        + bestHit.totalOccurrences() * 8
        + Math.min(bestHit.longestMatchLength(), 30);
    if (catalogQuery && containsAny(normalizedContent, List.of("面试", "原题", "高频题", "题目", "目录"))) {
      score += 28;
    }
    if (catalogQuery && defaultInt(chunk.getChunkIndex()) <= 20) {
      score += 12;
    }
    return new KeywordHit(
        bestHit.chunk(),
        bestHit.excerpt(),
        score,
        bestHit.distinctMatchCount(),
        bestHit.totalOccurrences(),
        bestHit.longestMatchLength()
    );
  }

  private int countOccurrences(String content, String term) {
    int count = 0;
    int fromIndex = 0;
    while (true) {
      int index = content.indexOf(term, fromIndex);
      if (index < 0) {
        return count;
      }
      count++;
      fromIndex = index + term.length();
    }
  }

  private String buildExcerpt(String content, int matchIndex, int termLength) {
    String normalized = content.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= KEYWORD_SNIPPET_WINDOW) {
      return normalized;
    }

    int start = Math.max(0, matchIndex - KEYWORD_SNIPPET_WINDOW / 2);
    int end = Math.min(content.length(), matchIndex + termLength + KEYWORD_SNIPPET_WINDOW / 2);
    String snippet = content.substring(start, end).replaceAll("\\s+", " ").trim();
    if (start > 0) {
      snippet = "..." + snippet;
    }
    if (end < content.length()) {
      snippet = snippet + "...";
    }
    return snippet;
  }

  private String buildContext(List<RetrievedChunk> retrievedChunks) {
    List<String> sections = new ArrayList<>();
    for (int i = 0; i < retrievedChunks.size(); i++) {
      RetrievedChunk chunk = retrievedChunks.get(i);
      sections.add("[" + (i + 1) + "] 来源：" + referenceName(chunk)
          + "（文件：" + chunk.sourceFileName() + "，片段 #" + (chunk.chunkIndex() + 1) + "）\n"
          + chunk.content());
    }
    return String.join("\n\n---\n\n", sections);
  }

  private String formatConversationHistory(List<ConversationTurn> conversationHistory) {
    if (conversationHistory == null || conversationHistory.isEmpty()) {
      return "";
    }
    return conversationHistory.stream()
        .map(turn -> ("assistant".equalsIgnoreCase(turn.role()) ? "助手：" : "用户：") + turn.content())
        .collect(Collectors.joining("\n"));
  }

  private String appendSources(String answer, List<RetrievedChunk> retrievedChunks) {
    if (answer == null || answer.isBlank() || answer.equals(NO_RESULT_RESPONSE)
        || isNoResultLike(answer) || answer.contains("## 参考来源")) {
      return answer;
    }
    String sourceMarkdown = buildSourcesMarkdown(retrievedChunks);
    if (sourceMarkdown.isBlank()) {
      return answer;
    }
    return answer + sourceMarkdown;
  }

  private String buildSourcesMarkdown(List<RetrievedChunk> retrievedChunks) {
    if (retrievedChunks == null || retrievedChunks.isEmpty()) {
      return "";
    }

    Set<String> lines = new LinkedHashSet<>();
    for (int i = 0; i < retrievedChunks.size() && i < MAX_SOURCES; i++) {
      RetrievedChunk chunk = retrievedChunks.get(i);
      lines.add("- [" + (i + 1) + "] **" + chunk.sourceFileName() + "**（片段 #"
          + (chunk.chunkIndex() + 1) + "）："
          + summarizeForReference(chunk.content()));
    }
    String body = String.join("\n", lines);
    return "\n\n## 参考来源\n" + body;
  }

  private Flux<String> appendSourcesToStream(Flux<String> answerFlux, List<RetrievedChunk> retrievedChunks) {
    String sourceMarkdown = buildSourcesMarkdown(retrievedChunks);
    if (sourceMarkdown.isBlank()) {
      return answerFlux;
    }

    return Flux.create(sink -> {
      StringBuilder emitted = new StringBuilder();
      answerFlux.subscribe(
          chunk -> {
            emitted.append(chunk);
            sink.next(chunk);
          },
          sink::error,
          () -> {
            String answer = emitted.toString();
            if (!answer.isBlank() && !answer.equals(NO_RESULT_RESPONSE)
                && !isNoResultLike(answer) && !answer.contains("## 参考来源")) {
              sink.next(sourceMarkdown);
            }
            sink.complete();
          }
      );
    });
  }

  private String normalizeAnswer(String answer) {
    if (answer == null || answer.isBlank()) {
      return NO_RESULT_RESPONSE;
    }
    String normalized = normalizeMarkdown(answer.trim());
    if (isNoResultLike(normalized)) {
      return NO_RESULT_RESPONSE;
    }
    return normalized;
  }

  private Flux<String> normalizeStreamOutput(Flux<String> rawFlux) {
    return Flux.create(sink -> {
      StringBuilder probeBuffer = new StringBuilder();
      AtomicBoolean passthrough = new AtomicBoolean(false);

      rawFlux.subscribe(
          chunk -> {
            if (chunk == null || chunk.isEmpty() || sink.isCancelled()) {
              return;
            }
            if (passthrough.get()) {
              sink.next(normalizeMarkdown(chunk));
              return;
            }

            probeBuffer.append(chunk);
            String probeText = probeBuffer.toString();
            if (isNoResultLike(probeText)) {
              sink.next(NO_RESULT_RESPONSE);
              sink.complete();
              return;
            }

            if (probeBuffer.length() >= STREAM_PROBE_WINDOW) {
              passthrough.set(true);
              sink.next(normalizeMarkdown(probeText));
              probeBuffer.setLength(0);
            }
          },
          error -> {
            if (!sink.isCancelled()) {
              sink.error(error);
            }
          },
          () -> {
            if (sink.isCancelled()) {
              return;
            }
            if (!passthrough.get()) {
              String normalized = normalizeAnswer(probeBuffer.toString());
              if (!normalized.isBlank()) {
                sink.next(normalized);
              }
            }
            sink.complete();
          }
      );
    });
  }

  private boolean isNoResultLike(String text) {
    if (text != null && CITATION_PATTERN.matcher(text).find()) {
      return false;
    }
    String normalized = normalizeQuestion(text).replaceAll("\\s+", "");
    if (normalized.isBlank()) {
      return true;
    }
    return normalized.startsWith(normalizeQuestion(NO_RESULT_RESPONSE).replaceAll("\\s+", ""))
        || normalized.startsWith("没有找到相关信息")
        || normalized.startsWith("没有检索到足够相关的内容")
        || normalized.startsWith("未检索到相关信息")
        || normalized.startsWith("超出知识库范围")
        || normalized.startsWith("当前知识库信息不足")
        || (normalized.length() <= 80
        && (normalized.contains("信息不足") || normalized.contains("无法回答")));
  }

  private String normalizeMarkdown(String content) {
    return content
        .replaceAll("(?m)^(#{1,6})([^\\s#])", "$1 $2")
        .replaceAll("(?m)^([-*])([^\\s])", "$1 $2")
        .replaceAll("(?m)^(\\d+)\\.([^\\s])", "$1. $2")
        .replaceAll("(?m)^>([^\\s])", "> $1");
  }

  private String normalizeQuestion(String question) {
    if (question == null) {
      return "";
    }
    return textCleaningService.normalizeUnicode(question)
        .replaceAll("(?<=[A-Za-z0-9])(?=\\p{IsHan})", " ")
        .replaceAll("(?<=\\p{IsHan})(?=[A-Za-z0-9])", " ")
        .trim();
  }

  private String normalizeDisplayText(String text) {
    return textCleaningService.normalizeUnicode(text)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim();
  }

  private String normalizeRetrievedContent(String content) {
    return normalizeDisplayText(content);
  }

  private String normalizeSearchableText(String text) {
    return normalizeDisplayText(text).toLowerCase(Locale.ROOT);
  }

  private ChatClient chatClient() {
    return llmProviderRegistry.getDefaultChatClient();
  }

  private List<ConversationTurn> trimConversationHistory(List<ConversationTurn> conversationHistory) {
    if (conversationHistory == null || conversationHistory.isEmpty()) {
      return List.of();
    }
    List<ConversationTurn> sanitized = conversationHistory.stream()
        .filter(turn -> turn != null && turn.role() != null && turn.content() != null)
        .map(turn -> new ConversationTurn(turn.role().trim(), turn.content().trim()))
        .filter(turn -> !turn.role().isBlank() && !turn.content().isBlank())
        .toList();
    if (sanitized.size() <= MAX_HISTORY_TURNS) {
      return sanitized;
    }
    return sanitized.subList(sanitized.size() - MAX_HISTORY_TURNS, sanitized.size());
  }

  private String enrichQuestionWithHistory(String question, List<ConversationTurn> conversationHistory) {
    if (!requiresConversationContext(question, conversationHistory)) {
      return question;
    }

    String historyContext = conversationHistory.stream()
        .skip(Math.max(0, conversationHistory.size() - 2L))
        .map(turn -> ("assistant".equalsIgnoreCase(turn.role()) ? "助手：" : "用户：") + turn.content())
        .collect(Collectors.joining("；"));
    if (historyContext.isBlank()) {
      return question;
    }
    return "对话上下文：" + historyContext + "；当前问题：" + question;
  }

  private boolean requiresConversationContext(String question, List<ConversationTurn> conversationHistory) {
    if (conversationHistory == null || conversationHistory.isEmpty()) {
      return false;
    }

    String normalized = normalizeQuestion(question);
    String compact = normalized.replaceAll("\\s+", "");
    return compact.length() <= 8
        || normalized.startsWith("那")
        || normalized.startsWith("它")
        || normalized.contains("这个")
        || normalized.contains("那个")
        || normalized.contains("上面")
        || normalized.contains("刚才")
        || normalized.contains("继续")
        || normalized.contains("区别");
  }

  private boolean isPureGreeting(String question) {
    return PURE_GREETING_PATTERN.matcher(normalizeQuestion(question)).matches();
  }

  private boolean isThanks(String question) {
    return THANKS_PATTERN.matcher(normalizeQuestion(question)).matches();
  }

  private boolean hasMeaningfulTermOverlap(String question, List<RetrievedChunk> retrievedChunks) {
    List<String> verificationTerms = extractVerificationTerms(question);
    if (verificationTerms.isEmpty()) {
      return true;
    }

    int strongestMatchCount = 0;
    int strongestNonEnglishMatchLength = 0;
    for (RetrievedChunk retrievedChunk : retrievedChunks) {
      String normalizedContent = normalizeSearchableText(retrievedChunk.content());
      Set<String> matchedTerms = verificationTerms.stream()
          .filter(normalizedContent::contains)
          .collect(Collectors.toCollection(LinkedHashSet::new));
      strongestMatchCount = Math.max(strongestMatchCount, matchedTerms.size());
      strongestNonEnglishMatchLength = Math.max(
          strongestNonEnglishMatchLength,
          matchedTerms.stream()
              .filter(term -> term.chars().anyMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN))
              .mapToInt(String::length)
              .max()
              .orElse(0)
      );
    }

    if (verificationTerms.size() == 1) {
      return strongestMatchCount >= 1;
    }
    return strongestMatchCount >= 2 || strongestNonEnglishMatchLength >= 4;
  }

  private Long parseLong(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private int parseInt(Object value) {
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private int defaultInt(Integer value) {
    return value == null ? 0 : value;
  }

  private String safeString(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String normalized = String.valueOf(value).trim();
    return normalized.isBlank() ? fallback : normalized;
  }

  private List<String> extractVerificationTerms(String question) {
    List<String> rawTerms = extractSearchTerms(List.of(question));
    List<String> prioritizedTerms = rawTerms.stream()
        .filter(term -> term != null && !term.isBlank())
        .sorted(Comparator.comparingInt(String::length).reversed())
        .distinct()
        .limit(6)
        .toList();

    if (prioritizedTerms.size() <= 1) {
      return prioritizedTerms;
    }

    List<String> nonGenericTerms = prioritizedTerms.stream()
        .filter(term -> !GENERIC_TERMS.contains(term))
        .toList();
    return nonGenericTerms.isEmpty() ? prioritizedTerms : nonGenericTerms;
  }

  private void addEnglishTerms(String normalized, Set<String> terms) {
    Matcher matcher = Pattern.compile("[A-Za-z0-9_+#.-]{2,}").matcher(normalized);
    while (matcher.find()) {
      String token = matcher.group().trim();
      if (token.length() >= 2) {
        terms.add(token);
      }
    }
  }

  private void addDomainPhraseTerms(String normalized, Set<String> terms) {
    for (String phrase : DOMAIN_PHRASES) {
      if (normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
        terms.add(phrase.toLowerCase(Locale.ROOT));
      }
    }
    if (normalized.contains("面试") && (normalized.contains("哪些") || normalized.contains("常见"))) {
      terms.add("面试");
      terms.add("原题");
      terms.add("高频题");
    }
  }

  private void addChineseTerms(String normalized, Set<String> terms) {
    String cleaned = normalized;
    for (String stopPhrase : SEARCH_STOP_PHRASES) {
      cleaned = cleaned.replace(stopPhrase, " ");
    }
    cleaned = cleaned.replaceAll("[^\\p{IsHan}\\s]", " ");
    for (String segment : cleaned.split("\\s+")) {
      if (segment == null || segment.isBlank()) {
        continue;
      }
      if (segment.length() <= 6) {
        addUsefulChineseTerm(segment, terms);
        addChineseNgrams(segment, terms);
        continue;
      }
      addChineseNgrams(segment, terms);
    }
  }

  private void addChineseNgrams(String segment, Set<String> terms) {
    int added = 0;
    int maxLength = Math.min(6, segment.length());
    for (int length = maxLength; length >= 2 && added < 12; length--) {
      for (int start = 0; start + length <= segment.length() && added < 12; start++) {
        String candidate = segment.substring(start, start + length);
        if (addUsefulChineseTerm(candidate, terms)) {
          added++;
        }
      }
    }
  }

  private boolean addUsefulChineseTerm(String term, Set<String> terms) {
    if (term == null || term.isBlank()) {
      return false;
    }
    String normalized = term.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() < 2 || GENERIC_TERMS.contains(normalized)) {
      return false;
    }
    return terms.add(normalized);
  }

  private boolean isMeaningfulKeywordHit(KeywordHit hit, boolean catalogQuery, int totalSearchTerms) {
    if (hit.distinctMatchCount() >= 2) {
      return true;
    }
    if (catalogQuery && hit.excerpt() != null && containsAny(hit.excerpt().toLowerCase(Locale.ROOT),
        List.of("面试", "原题", "高频题", "题目", "目录"))) {
      return true;
    }
    if (totalSearchTerms > 1) {
      return false;
    }
    return hit.longestMatchLength() >= 4;
  }

  private boolean isCatalogQuery(List<String> candidateQueries) {
    return candidateQueries.stream()
        .filter(query -> query != null && !query.isBlank())
        .map(query -> query.toLowerCase(Locale.ROOT))
        .anyMatch(query -> containsAny(query, List.of("有哪些", "常见", "高频", "面试题", "题目", "目录")));
  }

  private boolean containsAny(String text, List<String> terms) {
    for (String term : terms) {
      if (text.contains(term.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private String summarizeForReference(String content) {
    String normalized = normalizeDisplayText(content)
        .replaceAll("https?://\\S+", "")
        .replaceAll("\\s+", " ")
        .trim();
    if (normalized.isBlank()) {
      return "知识库片段";
    }
    if (normalized.length() <= 42) {
      return normalized;
    }
    return normalized.substring(0, 42) + "...";
  }

  private String referenceName(RetrievedChunk chunk) {
    String summary = summarizeForReference(chunk.content());
    if (summary.equals("知识库片段")) {
      return chunk.sourceName();
    }
    return summary;
  }

  public record ConversationTurn(String role, String content) {
  }

  private record SearchParams(int topK, double minScore) {
  }

  private record QueryContext(
      String originalQuestion,
      String contextualQuestion,
      List<String> candidateQueries,
      SearchParams searchParams
  ) {
  }

  private record RetrievedChunk(
      String documentId,
      Long knowledgeBaseId,
      String sourceName,
      String sourceFileName,
      int chunkIndex,
      String content,
      Double score
  ) {
  }

  private record KeywordHit(
      KnowledgeBaseChunkEntity chunk,
      String excerpt,
      int score,
      int distinctMatchCount,
      int totalOccurrences,
      int longestMatchLength
  ) {
  }
}
