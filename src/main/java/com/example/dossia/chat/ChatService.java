package com.example.dossia.chat;

import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.chat.domain.ChatSession;
import com.example.dossia.chat.dto.ChatRequest;
import com.example.dossia.chat.dto.ChatResponse;
import com.example.dossia.chat.dto.ChatSourceDto;
import com.example.dossia.common.GeminiException;
import com.example.dossia.common.Language;
import com.example.dossia.config.GeminiProperties;
import com.example.dossia.office.OfficeLocatorService;
import com.example.dossia.office.dto.NearbyOfficeDto;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureStatus;
import com.example.dossia.procedure.repository.ProcedureRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final double STRONG_CONFIDENCE = 20.0;
    private static final double SOURCE_MIN_SCORE_WEAK = 50.0;
    private static final int HISTORY_MESSAGES = 8;
    private static final int CATALOG_LIMIT = 40;

    private static final String RAG_SYSTEM_PROMPT =
            """
            Tu es l'assistant Dosya (دوسيا), spécialisé dans les démarches administratives tunisiennes.

            Règles strictes:
            - Réponds UNIQUEMENT à partir des procédures fournies dans le contexte.
            - Interprète les questions conversationnelles : si l'utilisateur dit qu'il vient d'obtenir un diplôme
              d'ingénieur et cherche une équivalence, guide-le vers la procédure correspondante si elle est dans le contexte.
            - Si le contexte ne contient pas l'information, dis clairement que tu ne sais pas et invite l'utilisateur
              à consulter une administration officielle.
            - Ne jamais inventer de frais, délais, documents ou adresses.
            - Cite les titres des procédures utilisées.
            - Réponds en français clair et pratique, avec des listes quand c'est utile.
            - Mentionne toujours que l'utilisateur doit vérifier la date de dernière vérification et la source officielle.
            """;

    private static final String WEAK_RAG_SYSTEM_PROMPT =
            """
            Tu es l'assistant Dosya (دوسيا), spécialisé dans les démarches administratives tunisiennes.

            Le contexte ci-dessous peut être partiellement pertinent — utilise-le SEULEMENT s'il répond clairement à la question.
            Sinon:
            - Explique ce que tu as compris de la demande.
            - Pose 1 ou 2 questions de clarification si nécessaire.
            - Oriente vers le ministère ou le type de démarche probable en Tunisie (sans détails inventés).
            - Propose des reformulations pour aider l'utilisateur.
            - Ne jamais inventer de frais, délais précis, documents officiels ni adresses.
            - Rappelle que seules les procédures vérifiées sur Dosya font foi.
            - Réponds en français, de manière chaleureuse et pratique.
            """;

    private static final String FALLBACK_SYSTEM_PROMPT =
            """
            Tu es l'assistant Dosya (دوسيا), spécialisé dans les démarches administratives tunisiennes.

            Aucune procédure vérifiée n'a été trouvée dans la base Dosya pour cette question.
            Aide l'utilisateur intelligemment:
            - Reformule ce que tu as compris de sa demande.
            - Pose 1 ou 2 questions de clarification si la demande est vague ou trop courte.
            - Indique quel ministère ou type de démarche pourrait être concerné en Tunisie (orientation générale).
            - Si des procédures Dosya sont listées ci-dessous, suggère celles qui semblent les plus proches.
            - Propose des mots-clés ou reformulations pour réessayer dans Dosya.
            - NE JAMAIS inventer de frais, délais précis, listes de documents officielles ni adresses.
            - Rappelle que les informations vérifiées sont sur Dosya et les sites officiels tunisiens.
            - Réponds en français, de manière chaleureuse et utile.
            """;

    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final ProcedureRetrievalService retrievalService;
    private final ProcedureContextBuilder contextBuilder;
    private final ChatHistoryService chatHistoryService;
    private final OfficeLocatorService officeLocatorService;
    private final ChatQueryExpander queryExpander;
    private final ProcedureRepository procedureRepository;

    public ChatService(
            GeminiClient geminiClient,
            GeminiProperties geminiProperties,
            ProcedureRetrievalService retrievalService,
            ProcedureContextBuilder contextBuilder,
            ChatHistoryService chatHistoryService,
            OfficeLocatorService officeLocatorService,
            ChatQueryExpander queryExpander,
            ProcedureRepository procedureRepository) {
        this.geminiClient = geminiClient;
        this.geminiProperties = geminiProperties;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.chatHistoryService = chatHistoryService;
        this.officeLocatorService = officeLocatorService;
        this.queryExpander = queryExpander;
        this.procedureRepository = procedureRepository;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, Language lang, UserPrincipal principal) {
        if (!geminiProperties.isConfigured()) {
            throw new GeminiException("Gemini is not configured. Set GEMINI_API_KEY in .env");
        }

        List<ConversationTurn> history = loadHistory(principal, request.sessionId());
        List<ProcedureRetrievalService.RetrievedProcedure> retrieved =
                retrieveWithExpansion(request.message(), lang);

        ResponseMode mode = classify(retrieved);
        String answer = generateAnswer(mode, retrieved, request.message(), history);
        List<ChatSourceDto> sources = buildSources(mode, retrieved);
        List<Procedure> procedures = sources.stream()
                .map(source -> findProcedure(retrieved, source.id()))
                .filter(java.util.Objects::nonNull)
                .toList();

        return finalizeResponse(request, principal, answer, sources, procedures);
    }

    private List<ProcedureRetrievalService.RetrievedProcedure> retrieveWithExpansion(String message, Language lang) {
        List<ProcedureRetrievalService.RetrievedProcedure> retrieved = retrievalService.retrieve(message, lang);

        if (!needsExpansion(retrieved)) {
            return retrieved;
        }

        return queryExpander.expand(geminiClient, message)
                .map(expanded -> mergeRetrieved(retrieved, retrievalService.retrieve(expanded, lang)))
                .orElse(retrieved);
    }

    private boolean needsExpansion(List<ProcedureRetrievalService.RetrievedProcedure> retrieved) {
        return retrieved.isEmpty() || topScore(retrieved) < STRONG_CONFIDENCE;
    }

    private List<ProcedureRetrievalService.RetrievedProcedure> mergeRetrieved(
            List<ProcedureRetrievalService.RetrievedProcedure> first,
            List<ProcedureRetrievalService.RetrievedProcedure> second) {
        Map<UUID, ProcedureRetrievalService.RetrievedProcedure> merged = new LinkedHashMap<>();
        for (ProcedureRetrievalService.RetrievedProcedure item : first) {
            merged.put(item.procedure().getId(), item);
        }
        for (ProcedureRetrievalService.RetrievedProcedure item : second) {
            merged.merge(item.procedure().getId(), item, (left, right) ->
                    left.score() >= right.score() ? left : right);
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(ProcedureRetrievalService.RetrievedProcedure::score)
                        .reversed())
                .limit(geminiProperties.retrievalLimit())
                .toList();
    }

    private ResponseMode classify(List<ProcedureRetrievalService.RetrievedProcedure> retrieved) {
        if (retrieved.isEmpty()) {
            return ResponseMode.FALLBACK;
        }
        if (topScore(retrieved) >= STRONG_CONFIDENCE) {
            return ResponseMode.STRONG_RAG;
        }
        return ResponseMode.WEAK_RAG;
    }

    private String generateAnswer(
            ResponseMode mode,
            List<ProcedureRetrievalService.RetrievedProcedure> retrieved,
            String message,
            List<ConversationTurn> history) {
        return switch (mode) {
            case STRONG_RAG -> geminiClient.generate(
                    RAG_SYSTEM_PROMPT, history, buildRagUserPrompt(retrieved, message), 0.2);
            case WEAK_RAG -> geminiClient.generate(
                    WEAK_RAG_SYSTEM_PROMPT, history, buildRagUserPrompt(retrieved, message), 0.35);
            case FALLBACK -> geminiClient.generate(
                    FALLBACK_SYSTEM_PROMPT, history, buildFallbackUserPrompt(message), 0.4);
        };
    }

    private String buildRagUserPrompt(List<ProcedureRetrievalService.RetrievedProcedure> retrieved, String message) {
        String context = retrieved.stream()
                .map(item -> contextBuilder.toPromptContext(item.procedure()))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        return """
                Contexte (procédures vérifiées):
                %s

                Question de l'utilisateur:
                %s
                """
                .formatted(context, message);
    }

    private String buildFallbackUserPrompt(String message) {
        List<String> catalog = procedureRepository.findPublishedTitles(ProcedureStatus.PUBLISHED);
        String catalogText = catalog.isEmpty()
                ? "Aucune procédure listée."
                : String.join("\n- ", catalog.subList(0, Math.min(catalog.size(), CATALOG_LIMIT)));

        return """
                Procédures actuellement disponibles sur Dosya (titres):
                - %s

                Question de l'utilisateur:
                %s
                """
                .formatted(catalogText, message);
    }

    private List<ChatSourceDto> buildSources(
            ResponseMode mode, List<ProcedureRetrievalService.RetrievedProcedure> retrieved) {
        return switch (mode) {
            case STRONG_RAG -> retrieved.stream().map(this::toSource).toList();
            case WEAK_RAG -> retrieved.stream()
                    .filter(item -> item.score() >= SOURCE_MIN_SCORE_WEAK)
                    .map(this::toSource)
                    .toList();
            case FALLBACK -> List.of();
        };
    }

    private List<ConversationTurn> loadHistory(UserPrincipal principal, UUID sessionId) {
        if (principal == null) {
            return List.of();
        }
        return chatHistoryService.getRecentTurns(principal.getId(), sessionId, HISTORY_MESSAGES);
    }

    private double topScore(List<ProcedureRetrievalService.RetrievedProcedure> retrieved) {
        return retrieved.isEmpty() ? 0.0 : retrieved.get(0).score();
    }

    private Procedure findProcedure(List<ProcedureRetrievalService.RetrievedProcedure> retrieved, UUID id) {
        return retrieved.stream()
                .map(ProcedureRetrievalService.RetrievedProcedure::procedure)
                .filter(procedure -> procedure.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    private ChatResponse finalizeResponse(
            ChatRequest request,
            UserPrincipal principal,
            String answer,
            List<ChatSourceDto> sources,
            List<Procedure> procedures) {
        List<NearbyOfficeDto> nearbyOffices = resolveNearbyOffices(request, procedures);
        UUID sessionId = persistExchange(request, principal, answer, sources);
        return new ChatResponse(answer, sources, geminiProperties.chatModel(), sessionId, nearbyOffices);
    }

    private List<NearbyOfficeDto> resolveNearbyOffices(ChatRequest request, List<Procedure> procedures) {
        if (request.latitude() == null || request.longitude() == null) {
            return List.of();
        }
        if (!procedures.isEmpty()) {
            return officeLocatorService.findNearestForProcedures(
                    request.latitude(), request.longitude(), procedures, 3);
        }
        return officeLocatorService.findNearest(
                request.latitude(), request.longitude(), null, request.message(), 3);
    }

    private UUID persistExchange(
            ChatRequest request, UserPrincipal principal, String answer, List<ChatSourceDto> sources) {
        if (principal == null) {
            return null;
        }
        ChatSession session =
                chatHistoryService.resolveSession(principal.getId(), request.sessionId(), request.message());
        chatHistoryService.appendUserMessage(session, request.message());
        chatHistoryService.appendAssistantMessage(session, answer, sources);
        return session.getId();
    }

    private ChatSourceDto toSource(ProcedureRetrievalService.RetrievedProcedure retrieved) {
        Procedure procedure = retrieved.procedure();
        String title = langTitle(procedure, retrieved.lang());
        return new ChatSourceDto(
                procedure.getId(),
                procedure.getSlug(),
                title,
                procedure.getSourceUrl(),
                procedure.getLastVerifiedAt(),
                retrieved.score());
    }

    private String langTitle(Procedure procedure, Language lang) {
        if (lang == Language.AR || lang == Language.TN) {
            return procedure.getTitleAr() != null && !procedure.getTitleAr().isBlank()
                    ? procedure.getTitleAr()
                    : procedure.getTitleFr();
        }
        return procedure.getTitleFr();
    }

    private enum ResponseMode {
        STRONG_RAG,
        WEAK_RAG,
        FALLBACK
    }
}
