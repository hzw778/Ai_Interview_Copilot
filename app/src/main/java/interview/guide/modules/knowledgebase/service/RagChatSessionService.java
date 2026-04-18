package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.KnowledgeBaseMapper;
import interview.guide.infrastructure.mapper.RagChatMapper;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import interview.guide.modules.knowledgebase.model.RagChatDTO.CreateSessionRequest;
import interview.guide.modules.knowledgebase.model.RagChatDTO.SessionDTO;
import interview.guide.modules.knowledgebase.model.RagChatDTO.SessionDetailDTO;
import interview.guide.modules.knowledgebase.model.RagChatDTO.SessionListItemDTO;
import interview.guide.modules.knowledgebase.model.RagChatMessageEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.RagChatMessageRepository;
import interview.guide.modules.knowledgebase.repository.RagChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatSessionService {

  private static final int HISTORY_LIMIT = 6;

  private final RagChatSessionRepository sessionRepository;
  private final RagChatMessageRepository messageRepository;
  private final KnowledgeBaseRepository knowledgeBaseRepository;
  private final KnowledgeBaseQueryService queryService;
  private final RagChatMapper ragChatMapper;
  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final RagChatMemoryCacheService memoryCacheService;

  @Transactional
  public SessionDTO createSession(CreateSessionRequest request) {
    List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllById(request.knowledgeBaseIds());
    if (knowledgeBases.size() != request.knowledgeBaseIds().size()) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "部分知识库不存在");
    }

    RagChatSessionEntity session = new RagChatSessionEntity();
    session.setTitle(request.title() != null && !request.title().isBlank()
        ? request.title()
        : generateTitle(knowledgeBases));
    session.setKnowledgeBases(new HashSet<>(knowledgeBases));
    session = sessionRepository.save(session);
    memoryCacheService.initializeSession(session.getId());

    log.info("创建 RAG 聊天会话: id={}, title={}", session.getId(), session.getTitle());
    return ragChatMapper.toSessionDTO(session);
  }

  public List<SessionListItemDTO> listSessions() {
    return sessionRepository.findAllOrderByPinnedAndUpdatedAtDesc()
        .stream()
        .map(ragChatMapper::toSessionListItemDTO)
        .toList();
  }

  public SessionDetailDTO getSessionDetail(Long sessionId) {
    RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    List<RagChatMessageEntity> messages = messageRepository.findBySessionIdOrderByMessageOrderAsc(sessionId);
    List<KnowledgeBaseListItemDTO> kbDTOs = knowledgeBaseMapper.toListItemDTOList(
        new ArrayList<>(session.getKnowledgeBases()));
    return ragChatMapper.toSessionDetailDTO(session, messages, kbDTOs);
  }

  @Transactional
  public Long prepareStreamMessage(Long sessionId, String question) {
    RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

    int nextOrder = session.getMessageCount();

    RagChatMessageEntity userMessage = new RagChatMessageEntity();
    userMessage.setSession(session);
    userMessage.setType(RagChatMessageEntity.MessageType.USER);
    userMessage.setContent(question);
    userMessage.setMessageOrder(nextOrder);
    userMessage.setCompleted(true);
    messageRepository.save(userMessage);

    RagChatMessageEntity assistantMessage = new RagChatMessageEntity();
    assistantMessage.setSession(session);
    assistantMessage.setType(RagChatMessageEntity.MessageType.ASSISTANT);
    assistantMessage.setContent("");
    assistantMessage.setMessageOrder(nextOrder + 1);
    assistantMessage.setCompleted(false);
    assistantMessage = messageRepository.save(assistantMessage);

    session.setMessageCount(nextOrder + 2);
    sessionRepository.save(session);
    memoryCacheService.appendUserTurn(sessionId, question);

    log.info("准备流式消息: sessionId={}, messageId={}", sessionId, assistantMessage.getId());
    return assistantMessage.getId();
  }

  @Transactional
  public void completeStreamMessage(Long messageId, String content) {
    RagChatMessageEntity message = messageRepository.findById(messageId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
    Long sessionId = resolveSessionId(message);

    message.setContent(content);
    message.setCompleted(true);
    messageRepository.save(message);
    if (sessionId != null) {
      memoryCacheService.appendAssistantTurn(sessionId, content);
    } else {
      log.warn("流式消息缺少 sessionId，跳过 Redis 会话记忆写入: messageId={}", messageId);
    }

    log.info("完成流式消息: messageId={}, contentLength={}", messageId, content.length());
  }

  public void completeStreamMessageSafely(Long messageId, String content) {
    try {
      completeStreamMessage(messageId, content);
    } catch (Exception e) {
      log.error("保存流式消息失败: messageId={}", messageId, e);
    }
  }

  public Flux<String> getStreamAnswer(Long sessionId, String question) {
    RagChatSessionEntity session = sessionRepository.findByIdWithKnowledgeBases(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));

    List<Long> kbIds = session.getKnowledgeBaseIds();
    List<KnowledgeBaseQueryService.ConversationTurn> conversationHistory =
        loadConversationHistory(sessionId, question);
    return queryService.answerQuestionStream(kbIds, question, conversationHistory);
  }

  @Transactional
  public void updateSessionTitle(Long sessionId, String title) {
    RagChatSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    session.setTitle(title);
    sessionRepository.save(session);
    log.info("更新会话标题: sessionId={}, title={}", sessionId, title);
  }

  @Transactional
  public void togglePin(Long sessionId) {
    RagChatSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    boolean currentPinned = session.getIsPinned() != null && session.getIsPinned();
    session.setIsPinned(!currentPinned);
    sessionRepository.save(session);
    log.info("切换会话置顶状态: sessionId={}, isPinned={}", sessionId, session.getIsPinned());
  }

  @Transactional
  public void updateSessionKnowledgeBases(Long sessionId, List<Long> knowledgeBaseIds) {
    RagChatSessionEntity session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    List<KnowledgeBaseEntity> knowledgeBases = knowledgeBaseRepository.findAllById(knowledgeBaseIds);
    session.setKnowledgeBases(new HashSet<>(knowledgeBases));
    sessionRepository.save(session);
    log.info("更新会话知识库: sessionId={}, kbIds={}", sessionId, knowledgeBaseIds);
  }

  @Transactional
  public void deleteSession(Long sessionId) {
    if (!sessionRepository.existsById(sessionId)) {
      throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
    }
    sessionRepository.deleteById(sessionId);
    memoryCacheService.clearSession(sessionId);
    log.info("删除会话: sessionId={}", sessionId);
  }

  private String generateTitle(List<KnowledgeBaseEntity> knowledgeBases) {
    if (knowledgeBases.isEmpty()) {
      return "新对话";
    }
    if (knowledgeBases.size() == 1) {
      return knowledgeBases.get(0).getName();
    }
    return knowledgeBases.size() + " 个知识库对话";
  }

  private List<KnowledgeBaseQueryService.ConversationTurn> loadConversationHistory(
      Long sessionId,
      String question
  ) {
    String rememberedName = memoryCacheService.getRememberedName(sessionId).orElse(null);
    List<KnowledgeBaseQueryService.ConversationTurn> cachedHistory =
        memoryCacheService.getConversationHistory(sessionId);
    if (!cachedHistory.isEmpty()) {
      List<KnowledgeBaseQueryService.ConversationTurn> preparedCachedHistory =
          augmentWithRememberedIdentity(excludeCurrentQuestion(trimHistory(cachedHistory), question), rememberedName);
      if (!preparedCachedHistory.isEmpty()) {
        return preparedCachedHistory;
      }
    }

    List<KnowledgeBaseQueryService.ConversationTurn> dbHistory = loadConversationHistoryFromDatabase(sessionId);
    if (dbHistory.isEmpty()) {
      return augmentWithRememberedIdentity(List.of(), rememberedName);
    }
    memoryCacheService.rebuildConversationHistory(sessionId, dbHistory);
    String rebuiltRememberedName = memoryCacheService.getRememberedName(sessionId).orElse(rememberedName);
    return augmentWithRememberedIdentity(
        excludeCurrentQuestion(trimHistory(dbHistory), question),
        rebuiltRememberedName
    );
  }

  private List<KnowledgeBaseQueryService.ConversationTurn> loadConversationHistoryFromDatabase(Long sessionId) {
    return messageRepository.findBySessionIdOrderByMessageOrderAsc(sessionId).stream()
        .filter(message -> Boolean.TRUE.equals(message.getCompleted()))
        .map(message -> new KnowledgeBaseQueryService.ConversationTurn(
            message.getTypeString(),
            normalizeMessage(message.getContent())))
        .filter(turn -> !turn.content().isBlank())
        .toList();
  }

  private List<KnowledgeBaseQueryService.ConversationTurn> trimHistory(
      List<KnowledgeBaseQueryService.ConversationTurn> history
  ) {
    if (history.size() <= HISTORY_LIMIT) {
      return history;
    }
    return history.subList(history.size() - HISTORY_LIMIT, history.size());
  }

  private List<KnowledgeBaseQueryService.ConversationTurn> excludeCurrentQuestion(
      List<KnowledgeBaseQueryService.ConversationTurn> history,
      String question
  ) {
    if (history.isEmpty()) {
      return history;
    }
    KnowledgeBaseQueryService.ConversationTurn lastTurn = history.get(history.size() - 1);
    if ("user".equals(lastTurn.role()) && lastTurn.content().equals(normalizeMessage(question))) {
      return history.subList(0, history.size() - 1);
    }
    return history;
  }

  private String normalizeMessage(String content) {
    return content == null ? "" : content.trim();
  }

  private List<KnowledgeBaseQueryService.ConversationTurn> augmentWithRememberedIdentity(
      List<KnowledgeBaseQueryService.ConversationTurn> history,
      String rememberedName
  ) {
    if (rememberedName == null || rememberedName.isBlank()) {
      return history;
    }
    boolean hasIdentityTurn = history.stream()
        .filter(turn -> "user".equalsIgnoreCase(turn.role()))
        .map(KnowledgeBaseQueryService.ConversationTurn::content)
        .anyMatch(content -> content.contains(rememberedName));
    if (hasIdentityTurn) {
      return history;
    }

    List<KnowledgeBaseQueryService.ConversationTurn> augmentedHistory = new ArrayList<>();
    augmentedHistory.add(new KnowledgeBaseQueryService.ConversationTurn("user", "我叫" + rememberedName));
    augmentedHistory.addAll(history);
    return augmentedHistory;
  }

  private Long resolveSessionId(RagChatMessageEntity message) {
    if (message.getSession() != null && message.getSession().getId() != null) {
      return message.getSession().getId();
    }
    return messageRepository.findSessionIdByMessageId(message.getId());
  }
}
