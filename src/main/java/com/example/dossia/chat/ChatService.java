package com.example.dossia.chat;

import com.example.dossia.auth.security.UserPrincipal;
import com.example.dossia.chat.domain.ChatSession;
import com.example.dossia.chat.dto.ChatChecklistItemDto;
import com.example.dossia.chat.dto.ChatRequest;
import com.example.dossia.chat.dto.ChatResponse;
import com.example.dossia.chat.dto.ChatSourceDto;
import com.example.dossia.chat.dto.ChatSuggestionDto;
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
    private static final double KEYWORD_CONFIDENCE = 50.0;
    private static final double SOURCE_MIN_SCORE_WEAK = 50.0;
    private static final int HISTORY_MESSAGES = 8;
    private static final int CATALOG_LIMIT = 40;

    private static final String FORMAT_RULES =
            """
            FORMAT (important — ChatGPT-style):
            - Airy, scannable answers — not a wall of text.
            - Clean Markdown: **bold** for key points, bullet lists (- ) or numbered lists (1. ).
            - Short ## headings when you use sections.
            - Pattern: brief intro → clear sections → one tip or follow-up question if useful.
            - No filler or vague generic fluff.
            """;

    private static final String RAG_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا), un assistant conversationnel intelligent pour les démarches
            administratives tunisiennes — naturel et clair comme ChatGPT, mais ancré dans le contexte fourni.

            Règles:
            - Utilise l'historique de conversation : si l'utilisateur répond "1", "2", "en Tunisie",
              "à l'étranger", "oui", etc., c'est un suivi de ta question précédente — continue la procédure choisie.
            - Reformule de façon fluide et utile.
            - Si quelque chose n'est pas clair, pose 1 question de clarification précise.
            - Ne jamais inventer de frais, délais, documents ou adresses.
            - Cite les titres des procédures utilisées.
            - Mentionne la date de dernière vérification / source officielle.
            - Ne propose des bureaux / une carte QUE si l'utilisateur demande où aller.
            - If the user already said renew + Tunisia (or abroad), give the steps and required documents
              immediately — do NOT ask them to pick between similar procedure titles.
            - If they ask for papers/documents ("papers", "papiers", "give me the docs"), list the
              required documents from the matching procedure, clearly bulleted.
            - Prefer ONE primary procedure. Only mention a second if it is clearly different
              (e.g. abroad vs Tunisia), never mix unrelated topics (permis, other IDs).
            """
                    + FORMAT_RULES;

    private static final String WEAK_RAG_SYSTEM_PROMPT =
            """
            Tu es l'assistant Dosya (دوسيا), spécialisé dans les démarches administratives tunisiennes.

            Le contexte ci-dessous peut être partiellement pertinent — utilise-le SEULEMENT s'il répond clairement.
            Tiens compte de l'historique : une réponse courte ("1", "2", "Tunisie") choisit souvent une option
            que tu as proposée. Continue alors sans redemander la question initiale.
            Sinon:
            - Pose 1 ou 2 questions de clarification concrètes.
            - Oriente vers le ministère / type de démarche (sans inventer de détails officiels).
            - Ton chaleureux et pratique.
            """
                    + FORMAT_RULES;

    private static final String FOLLOWUP_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا). L'utilisateur continue une conversation en cours (réponse courte ou choix numéroté).
            Règles:
            - Lis l'historique : si tu as proposé des options (1, 2…), "1" = la première option, etc.
            - Continue la procédure choisie avec étapes / documents utiles basés sur le contexte fourni s'il existe.
            - Si tu ne peux vraiment pas relier le message à l'historique, pose UNE question claire pour trancher.
            - Ne jamais inventer de frais ou adresses inventés.
            - Style conversationnel, clair.
            """
                    + FORMAT_RULES;

    private static final String FALLBACK_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا), un assistant conversationnel intelligent spécialisé dans les démarches
            administratives tunisiennes — aussi naturel et utile que ChatGPT, mais UNIQUEMENT dans ce domaine.

            Aucune procédure vérifiée exacte n'a été trouvée dans la base. Tu peux quand même aider :
            - Réponds de façon claire, chaleureuse et structurée (listes si utile).
            - Donne une orientation générale (ministère, type de démarche, étapes typiques).
            - NE JAMAIS inventer de montants, délais exacts, listes de documents officielles ni adresses précises.
            - Dis clairement quand c'est une orientation générale et invite à vérifier sur Dosya / sites officiels.
            - Si des procédures Dosya sont listées, suggère au plus 3 titres proches.
            """
                    + FORMAT_RULES;

    private static final String GREETING_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا), assistant chaleureux pour les démarches administratives tunisiennes.
            L'utilisateur te salue ou discute brièvement (bonjour, ça va, qui es-tu…).
            Réponds naturellement, en 2–4 phrases, comme un bon chatbot.
            Présente-toi et invite à poser une question de démarche (passeport, CIN, équivalence…).
            Ne liste pas de procédures. Pas de carte / bureaux.
            """;

    private static final String CLARIFY_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا). Le message est flou ou incomplet.
            Comme un vrai assistant conversationnel :
            - Accueille brièvement le message.
            - Pose 1–2 questions concrètes pour comprendre (type de démarche, Tunisie vs étranger, première demande vs renouvellement…).
            - Donne 2–3 exemples de reformulations utiles.
            Pas de liste longue de procédures. Pas de carte.
            """;

    private static final String OFF_TOPIC_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا). La question est HORS sujet (météo, sport, cuisine, films, code, etc.).
            Réponds poliment en 2–3 phrases maximum :
            - Dis que tu ne peux pas aider sur ce sujet.
            - Explique que tu es uniquement fait pour les démarches administratives tunisiennes
              (CIN, passeport, équivalence de diplôme, résidence, etc.).
            - Invite l'utilisateur à poser une question dans ce domaine.
            Ne réponds PAS au contenu hors sujet (pas de météo, pas de blague, etc.).
            """;

    private static final String CRISIS_SYSTEM_PROMPT =
            """
            Tu es Dosya (دوسيا). L'utilisateur exprime une détresse / idées suicidaires.
            Réponds avec compassion, en 3–5 phrases :
            - Prends-le au sérieux, sans dramatiser ni moraliser.
            - Dis clairement que tu n'es pas un service d'urgence / de santé mentale.
            - Oriente vers de l'aide humaine immédiate : urgences 190 (Tunisie), proches, ou un professionnel.
            - Rappelle brièvement que Dosya ne traite que les démarches administratives.
            Ne donne AUCUN conseil sur se faire du mal. Ne continue pas sur le sujet du suicide.
            """;

    private final LlmGateway llmGateway;
    private final GeminiProperties geminiProperties;
    private final ProcedureRetrievalService retrievalService;
    private final ProcedureContextBuilder contextBuilder;
    private final ChatHistoryService chatHistoryService;
    private final OfficeLocatorService officeLocatorService;
    private final ChatQueryExpander queryExpander;
    private final ChatQueryMatcher queryMatcher;
    private final ProcedureRepository procedureRepository;

    public ChatService(
            LlmGateway llmGateway,
            GeminiProperties geminiProperties,
            ProcedureRetrievalService retrievalService,
            ProcedureContextBuilder contextBuilder,
            ChatHistoryService chatHistoryService,
            OfficeLocatorService officeLocatorService,
            ChatQueryExpander queryExpander,
            ChatQueryMatcher queryMatcher,
            ProcedureRepository procedureRepository) {
        this.llmGateway = llmGateway;
        this.geminiProperties = geminiProperties;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.chatHistoryService = chatHistoryService;
        this.officeLocatorService = officeLocatorService;
        this.queryExpander = queryExpander;
        this.queryMatcher = queryMatcher;
        this.procedureRepository = procedureRepository;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request, Language lang, UserPrincipal principal) {
        List<ConversationTurn> history = loadHistory(principal, request);
        String message = request.message();
        boolean hasHistory = !history.isEmpty();
        boolean followUp = hasHistory
                && (queryMatcher.isFollowUp(message) || queryMatcher.isThreadContinuation(message));
        Language replyLang = resolveReplyLanguage(lang, message, history, request.agentId());

        if (queryMatcher.isCrisisQuery(message)) {
            AnswerResult answerResult = generateSpecialSafely(
                    withLanguage(CRISIS_SYSTEM_PROMPT, replyLang),
                    message,
                    history,
                    offlineCrisis(replyLang));
            return finalizeResponse(
                    request, principal, answerResult.answer(), List.of(), List.of(), answerResult.model(), replyLang);
        }

        // Mid-conversation: never treat short / messy turns as a fresh greeting or gibberish.
        if (!followUp && (queryMatcher.isGreeting(message) || queryMatcher.isConversational(message))) {
            AnswerResult answerResult = generateGreetingSafely(message, history, replyLang);
            return finalizeResponse(
                    request, principal, answerResult.answer(), List.of(), List.of(), answerResult.model(), replyLang);
        }

        if (!followUp && queryMatcher.isOffTopicQuery(message)) {
            AnswerResult answerResult = generateSpecialSafely(
                    withLanguage(OFF_TOPIC_SYSTEM_PROMPT, replyLang),
                    message,
                    history,
                    offlineOffTopic(replyLang));
            return finalizeResponse(
                    request, principal, answerResult.answer(), List.of(), List.of(), answerResult.model(), replyLang);
        }

        if (!hasHistory
                && queryMatcher.isLowSignalQuery(message)
                && !queryMatcher.isLocationIntent(message)
                && !queryMatcher.isDocumentAsk(message)) {
            AnswerResult answerResult = generateClarifySafely(message, history, replyLang);
            return finalizeResponse(
                    request, principal, answerResult.answer(), List.of(), List.of(), answerResult.model(), replyLang);
        }

        // Always enrich retrieval with prior turns once a thread exists.
        String retrievalQuery = buildRetrievalQuery(message, history, hasHistory);
        Language retrievalLang = replyLang == Language.EN ? Language.FR : replyLang;
        List<ProcedureRetrievalService.RetrievedProcedure> retrieved =
                retrieveWithExpansion(retrievalQuery, retrievalLang);

        ResponseMode mode;
        if (followUp || (hasHistory && queryMatcher.isDocumentAsk(message))) {
            if (retrieved.isEmpty()) {
                mode = ResponseMode.FOLLOWUP;
            } else if (topScore(retrieved) >= STRONG_CONFIDENCE) {
                mode = ResponseMode.STRONG_RAG;
            } else {
                mode = ResponseMode.FOLLOWUP;
            }
        } else {
            mode = classify(retrieved);
        }

        AnswerResult answerResult = generateAnswerSafely(mode, retrieved, message, history, replyLang);
        List<ChatSourceDto> sources = buildSources(mode, retrieved, message, history);
        List<Procedure> procedures = sources.stream()
                .map(source -> findProcedure(retrieved, source.id()))
                .filter(java.util.Objects::nonNull)
                .toList();

        return finalizeResponse(
                request, principal, answerResult.answer(), sources, procedures, answerResult.model(), replyLang);
    }

    private Language resolveReplyLanguage(
            Language requested, String message, List<ConversationTurn> history, String agentId) {
        Language fromAgent = languageFromAgent(agentId);
        if (fromAgent != null) {
            return fromAgent;
        }
        List<String> priorUsers = history.stream()
                .filter(turn -> "user".equals(turn.role()))
                .map(ConversationTurn::content)
                .toList();
        Language detected = queryMatcher.detectReplyLanguage(message, priorUsers);
        // Honor explicit AR/TN request; otherwise prefer detection (so English messages answer in EN).
        if (requested == Language.AR || requested == Language.TN) {
            return requested;
        }
        if (requested == Language.EN || requested == Language.FR) {
            // Frontend may send detected lang — trust it when it matches detection, else detection wins
            // for short follow-ups we already used history in detection.
            return detected;
        }
        return detected;
    }

    /** Locked voice-agent language wins over auto-detect. */
    private Language languageFromAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        return switch (agentId.strip().toLowerCase()) {
            case "sofia" -> Language.FR;
            case "yasmine" -> Language.TN;
            case "alex" -> Language.EN;
            default -> null;
        };
    }

    private String withLanguage(String systemPrompt, Language replyLang) {
        String rule = switch (replyLang) {
            case EN -> """

                    LANGUAGE (mandatory): Reply entirely in clear English.
                    Keep procedure titles as they appear in the context (may be French).
                    Persona: You are Alex, Dosya's English guide — warm, precise, civic and practical.
                    """;
            case AR -> """

                    اللغة (إلزامي): أجب بالعربية الفصحى الواضحة.
                    يمكنك الإبقاء على عناوين الإجراءات كما هي في السياق.
                    """;
            case TN -> """

                    اللغة (إلزامي جداً): جاوب دائماً بالدارجة التونسية الطبيعية (تونسي / Derja)، مش بالفصحى.
                    أنتِ ياسمين، مرشدة دوسيا — بنت تونسية ودودة، واضحة، وعملية.

                    قواعد الدارجة:
                    - احكي كيف التونسي في الحياة اليومية: عسلامة، شنوة تحب، كيفاش، وين نمشي، الأوراڨ/الأوراق، بطاقة تعريف، پاسپور/جواز...
                    - اكتب بالعربي (حروف عربية)، مش Latin Arabizi إلا إذا المستخدم كتب Arabizi صراحة.
                    - ممنوع الفصحى الثقيلة (مثل: "ينبغي عليك أن تقوم بـ...") — قولها تونسي: "لازم تعمل..." / "نجم نعاونك..."
                    - ممنوع كلمات غريبة، مخترعة، أو ترجمة حرفية بشعة. إذا ما فهمتش سؤال المستخدم، اسألو بالدارجة باش يوضح.
                    - خلّي عناوين الإجراءات الرسمية كما في السياق (غالباً بالفرنسية) وفسّرها بالدارجة حواليه.
                    - خلّي الجواب قصير وواضح ومرتب (نقاط إذا لزم).
                    - راهي إجراءات إدارية تونسية فقط — بلا فلسفة وبلا كلام فاضي.
                    """;
            case FR -> """

                    LANGUE (obligatoire): Réponds entièrement en français clair.
                    Persona: Tu es Sofia, le guide Dosya en français — chaleureuse, précise et pratique.
                    """;
        };
        return systemPrompt + rule;
    }

    private String offlineCrisis(Language lang) {
        return switch (lang) {
            case EN -> "If you are in distress, contact emergency services immediately (190 in Tunisia) "
                    + "or someone you trust. Dosya only helps with administrative procedures.";
            case TN -> "إذا فيك ضيق، اتصال بالطوارئ فورا (190 في تونس) وإلا لشخص تثق فيه. "
                    + "دوسيا غير تعينك في الإجراءات الإدارية برك.";
            case AR -> "إذا كنت في ضيق، اتصل فوراً بالطوارئ (190 في تونس) أو بشخص تثق به. "
                    + "دوسيا مساعد للإجراءات الإدارية فقط.";
            case FR -> "Si vous êtes en détresse, contactez immédiatement les urgences (190 en Tunisie) "
                    + "ou une personne de confiance. Dosya n'est qu'un assistant pour les démarches administratives.";
        };
    }

    private String offlineOffTopic(Language lang) {
        return switch (lang) {
            case EN -> "Sorry — I can only help with Tunisian administrative procedures "
                    + "(national ID, passport, diploma equivalence, residence…). Ask me something in that area!";
            case TN -> "سامحني، نجمش نعاونك غير على الإجراءات الإدارية التونسية "
                    + "(بطاقة تعريف، پاسپور، معادلة، إقامة...). قولّي شنوة تحتاج بالضبط!";
            case AR -> "عذراً، أستطيع المساعدة فقط في الإجراءات الإدارية التونسية "
                    + "(بطاقة تعريف، جواز سفر، معادلة شهادة، إقامة…). اسألني في هذا المجال!";
            case FR -> "Désolé, je ne peux pas vous aider sur ce sujet. "
                    + "Je suis Dosya — je suis uniquement fait pour les démarches administratives tunisiennes "
                    + "(CIN, passeport, équivalence de diplôme, résidence…). Posez-moi une question dans ce domaine !";
        };
    }

    private String buildRetrievalQuery(String message, List<ConversationTurn> history, boolean useHistory) {
        if (!useHistory || history.isEmpty()) {
            return message;
        }
        String lastAssistant = lastTurnContent(history, "model");
        String topicAnchor = topicAnchorQuery(message, history);
        String chosen = resolveChosenOption(message, lastAssistant);
        String focus = queryMatcher.isDocumentAsk(message)
                ? "documents requis pièces papiers " + message
                : (chosen != null ? chosen : message);
        // Do NOT append the full assistant reply — it lists CIN/docs and pollutes retrieval.
        return String.join(" ", topicAnchor, focus).strip();
    }

    /** Prefer the most recent user turn that still names a procedure topic (passport, CIN…). */
    private String topicAnchorQuery(String message, List<ConversationTurn> history) {
        if (detectTopic(message == null ? "" : message) != Topic.GENERAL) {
            return message;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationTurn turn = history.get(i);
            if (!"user".equals(turn.role()) || turn.content() == null) {
                continue;
            }
            if (detectTopic(turn.content()) != Topic.GENERAL) {
                return turn.content();
            }
        }
        return lastTurnContent(history, "user");
    }

    private String lastTurnContent(List<ConversationTurn> history, String role) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationTurn turn = history.get(i);
            if (role.equals(turn.role())) {
                return turn.content() != null ? turn.content() : "";
            }
        }
        return "";
    }

    /** Maps "1" / "2" to the numbered line in the last assistant reply when possible. */
    private String resolveChosenOption(String message, String lastAssistant) {
        if (lastAssistant == null || lastAssistant.isBlank()) {
            return null;
        }
        String normalized = queryMatcher.normalize(message == null ? "" : message)
                .replaceAll("[!?.]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
        java.util.regex.Matcher pick = java.util.regex.Pattern.compile("^(\\d{1,2})").matcher(normalized);
        if (!pick.find()) {
            return null;
        }
        int n = Integer.parseInt(pick.group(1));
        java.util.regex.Matcher lines = java.util.regex.Pattern.compile(
                        "(?m)^\\s*" + n + "\\s*[).:\\-]\\s*(.+)$")
                .matcher(lastAssistant);
        if (lines.find()) {
            return lines.group(1).strip();
        }
        lines = java.util.regex.Pattern.compile(
                        "(?m)(?:\\*\\*)?\\s*" + n + "\\s*[).:\\-]\\s*\\*?\\*?(.+?)\\*?\\*?\\s*$")
                .matcher(lastAssistant);
        if (lines.find()) {
            return lines.group(1).replace("*", "").strip();
        }
        return null;
    }

    private List<ProcedureRetrievalService.RetrievedProcedure> retrieveWithExpansion(String message, Language lang) {
        List<ProcedureRetrievalService.RetrievedProcedure> retrieved = retrievalService.retrieve(message, lang);

        if (!needsExpansion(retrieved)) {
            return retrieved;
        }

        return queryExpander.expand(llmGateway, message)
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
        double top = topScore(retrieved);
        if (top >= STRONG_CONFIDENCE) {
            return ResponseMode.STRONG_RAG;
        }
        if (top >= KEYWORD_CONFIDENCE) {
            return ResponseMode.WEAK_RAG;
        }
        return ResponseMode.FALLBACK;
    }

    private AnswerResult generateAnswerSafely(
            ResponseMode mode,
            List<ProcedureRetrievalService.RetrievedProcedure> retrieved,
            String message,
            List<ConversationTurn> history,
            Language replyLang) {
        if (!llmGateway.isChatConfigured()) {
            return new AnswerResult(buildOfflineAnswer(message, retrieved, mode, replyLang), "offline-fallback");
        }

        try {
            LlmGateway.LlmText answer = generateAnswer(mode, retrieved, message, history, replyLang);
            return new AnswerResult(answer.text(), answer.model());
        } catch (GeminiException ex) {
            org.slf4j.LoggerFactory.getLogger(ChatService.class)
                    .warn("LLM unavailable ({}), using offline procedure details", ex.getMessage());
            return new AnswerResult(buildOfflineAnswer(message, retrieved, mode, replyLang), "offline-fallback");
        }
    }

    private AnswerResult generateSpecialSafely(
            String systemPrompt, String message, List<ConversationTurn> history, String offline) {
        if (!llmGateway.isChatConfigured()) {
            return new AnswerResult(offline, "offline-fallback");
        }
        try {
            LlmGateway.LlmText answer = llmGateway.generate(
                    systemPrompt, history, "Message utilisateur:\n" + message, 0.3);
            return new AnswerResult(answer.text(), answer.model());
        } catch (GeminiException ex) {
            return new AnswerResult(offline, "offline-fallback");
        }
    }

    private AnswerResult generateGreetingSafely(
            String message, List<ConversationTurn> history, Language replyLang) {
        String offline = buildOfflineGreeting(message, replyLang);
        if (!llmGateway.isChatConfigured()) {
            return new AnswerResult(offline, "offline-fallback");
        }
        try {
            LlmGateway.LlmText answer = llmGateway.generate(
                    withLanguage(GREETING_SYSTEM_PROMPT, replyLang),
                    history,
                    "Message utilisateur:\n" + message,
                    0.4);
            return new AnswerResult(answer.text(), answer.model());
        } catch (GeminiException ex) {
            return new AnswerResult(offline, "offline-fallback");
        }
    }

    private String buildOfflineGreeting(String message, Language replyLang) {
        String normalized = queryMatcher.normalize(message == null ? "" : message);
        return switch (replyLang) {
            case EN -> (normalized.contains("evening") ? "Good evening" : "Hi")
                    + "! I'm Dosya, your assistant for Tunisian administrative procedures. "
                    + "Ask me about passports, national ID (CIN), diploma equivalence, residence…";
            case TN -> "عسلامة! أنا ياسمين من دوسيا. نجم نعاونك في الإجراءات الإدارية في تونس: "
                    + "پاسپور، بطاقة تعريف، معادلة، إقامة… قولّي شنوة تحتاج.";
            case AR -> "أهلاً! أنا دوسيا، مساعدك للإجراءات الإدارية في تونس. "
                    + "اسألني عن جواز السفر، بطاقة التعريف، معادلة الشهادة، الإقامة…";
            case FR -> {
                String hello = normalized.contains("bonsoir") ? "Bonsoir" : "Bonjour";
                yield hello
                        + " ! Je suis Dosya, votre assistant pour les démarches administratives tunisiennes. "
                        + "Posez-moi une question (passeport, CIN, équivalence de diplôme, résidence…).";
            }
        };
    }

    private AnswerResult generateClarifySafely(
            String message, List<ConversationTurn> history, Language replyLang) {
        String offline = switch (replyLang) {
            case EN -> "Your message is a bit unclear. Tell me which procedure you need "
                    + "(e.g. passport renewal, national ID, diploma equivalence, residence certificate).";
            case TN -> "ما فهمتش برشا. وضّحلي شنوة الإجراء اللي تحب عليه "
                    + "(مثلا تجديد پاسپور، بطاقة تعريف، معادلة، شهادة إقامة).";
            case AR -> "رسالتك غير واضحة بما يكفي. وضّح الإجراء الذي تحتاجه "
                    + "(مثلاً تجديد جواز السفر، بطاقة تعريف، معادلة شهادة، شهادة إقامة).";
            case FR -> "Votre message est trop court ou peu clair. Reformulez votre démarche "
                    + "(ex. passeport, CIN, équivalence de diplôme, attestation de résidence).";
        };
        if (!llmGateway.isChatConfigured()) {
            return new AnswerResult(offline, "offline-fallback");
        }
        try {
            LlmGateway.LlmText answer = llmGateway.generate(
                    withLanguage(CLARIFY_SYSTEM_PROMPT, replyLang),
                    history,
                    "Message utilisateur:\n" + message,
                    0.2);
            return new AnswerResult(answer.text(), answer.model());
        } catch (GeminiException ex) {
            return new AnswerResult(offline, "offline-fallback");
        }
    }

    private LlmGateway.LlmText generateAnswer(
            ResponseMode mode,
            List<ProcedureRetrievalService.RetrievedProcedure> retrieved,
            String message,
            List<ConversationTurn> history,
            Language replyLang) {
        return switch (mode) {
            case STRONG_RAG -> llmGateway.generate(
                    withLanguage(RAG_SYSTEM_PROMPT, replyLang),
                    history,
                    buildRagUserPrompt(retrieved, message),
                    0.2);
            case WEAK_RAG -> llmGateway.generate(
                    withLanguage(WEAK_RAG_SYSTEM_PROMPT, replyLang),
                    history,
                    buildRagUserPrompt(retrieved, message),
                    0.35);
            case FOLLOWUP -> llmGateway.generate(
                    withLanguage(FOLLOWUP_SYSTEM_PROMPT, replyLang),
                    history,
                    retrieved.isEmpty()
                            ? "Message de suivi de l'utilisateur:\n" + message
                            : buildRagUserPrompt(retrieved, message),
                    0.3);
            case FALLBACK -> llmGateway.generate(
                    withLanguage(FALLBACK_SYSTEM_PROMPT, replyLang),
                    history,
                    buildFallbackUserPrompt(message),
                    0.4);
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

    private String buildOfflineAnswer(
            String message,
            List<ProcedureRetrievalService.RetrievedProcedure> retrieved,
            ResponseMode mode,
            Language replyLang) {
        StringBuilder answer = new StringBuilder();
        boolean wantDocs = queryMatcher.isDocumentAsk(message)
                || mode == ResponseMode.FOLLOWUP
                || mode == ResponseMode.STRONG_RAG
                || replyLang == Language.TN
                || replyLang == Language.AR;

        if ((mode == ResponseMode.STRONG_RAG
                        || mode == ResponseMode.WEAK_RAG
                        || mode == ResponseMode.FOLLOWUP)
                && !retrieved.isEmpty()) {
            // Derja / Arabic: conversational, not a French admin form dump.
            if (replyLang == Language.TN || replyLang == Language.AR) {
                return buildConversationalOfflineAnswer(retrieved.get(0).procedure(), replyLang, wantDocs);
            }

            Procedure top = retrieved.get(0).procedure();
            answer.append(switch (replyLang) {
                case EN -> "Here's what Dosya has on **" + top.getTitleFr() + "**:\n\n";
                case FR -> "Voici ce que Dosya a pour **" + top.getTitleFr() + "** :\n\n";
                default -> "Here's what Dosya has on **" + top.getTitleFr() + "**:\n\n";
            });

            if (wantDocs && top.getDocuments() != null && !top.getDocuments().isEmpty()) {
                answer.append(switch (replyLang) {
                    case EN -> "**Required documents**\n";
                    case FR -> "**Documents requis**\n";
                    default -> "**Required documents**\n";
                });
                top.getDocuments().stream()
                        .sorted(Comparator.comparingInt(com.example.dossia.procedure.domain.ProcedureDocument::getSortOrder))
                        .forEach(doc -> answer.append("- ").append(doc.getTitleFr()).append('\n'));
                answer.append('\n');
            }

            if (top.getSteps() != null && !top.getSteps().isEmpty()) {
                answer.append(switch (replyLang) {
                    case EN -> "**Main steps**\n";
                    case FR -> "**Étapes principales**\n";
                    default -> "**Main steps**\n";
                });
                top.getSteps().stream()
                        .sorted(Comparator.comparingInt(com.example.dossia.procedure.domain.ProcedureStep::getStepNumber))
                        .limit(8)
                        .forEach(step -> answer
                                .append(step.getStepNumber())
                                .append(". ")
                                .append(step.getTitleFr())
                                .append('\n'));
                answer.append('\n');
            }

            if (top.getFees() != null && !top.getFees().isBlank()) {
                answer.append(switch (replyLang) {
                    case EN -> "**Fees:** ";
                    case FR -> "**Frais :** ";
                    default -> "**Fees:** ";
                }).append(top.getFees()).append("\n\n");
            }
            if (top.getProcessingTime() != null && !top.getProcessingTime().isBlank()) {
                answer.append(switch (replyLang) {
                    case EN -> "**Processing time:** ";
                    case FR -> "**Délai :** ";
                    default -> "**Processing time:** ";
                }).append(top.getProcessingTime()).append("\n\n");
            }
            if (top.getSourceUrl() != null && !top.getSourceUrl().isBlank()) {
                answer.append(switch (replyLang) {
                    case EN -> "Source: ";
                    case FR -> "Source : ";
                    default -> "Source: ";
                }).append(top.getSourceUrl()).append("\n\n");
            }

            if (retrieved.size() > 1) {
                answer.append(switch (replyLang) {
                    case EN -> "Related procedures:\n";
                    case FR -> "Procédures liées :\n";
                    default -> "Related procedures:\n";
                });
                for (int i = 1; i < Math.min(retrieved.size(), 3); i++) {
                    answer.append("- ").append(retrieved.get(i).procedure().getTitleFr()).append('\n');
                }
                answer.append('\n');
            }

            answer.append(switch (replyLang) {
                case EN -> "_Full AI answers are temporarily limited — this is the verified Dosya procedure content._";
                case FR -> "_L'IA complète est limitée temporairement — voici le contenu vérifié Dosya._";
                default -> "_Full AI answers are temporarily limited — this is the verified Dosya procedure content._";
            });
            return answer.toString().strip();
        }

        if (mode == ResponseMode.STRONG_RAG
                || mode == ResponseMode.WEAK_RAG
                || mode == ResponseMode.FOLLOWUP) {
            answer.append(switch (replyLang) {
                case EN -> "I understand you're continuing the conversation, but I couldn't load procedure details right now. "
                        + "Try again with “passport documents in Tunisia”.\n\n";
                case TN -> "فهمت السياق، أما ما نجمتش نحمّل تفاصيل الإجراء توّا. عاود جرّب بكلمات أوضح "
                        + "كـ «أوراق الپاسپور في تونس».\n\n";
                case AR -> "فهمت السياق لكن تعذّر تحميل تفاصيل الإجراء. أعد المحاولة.\n\n";
                case FR -> "Je comprends la suite, mais je n'ai pas pu charger les détails. Réessayez.\n\n";
            });
        } else {
            answer.append(switch (replyLang) {
                case EN -> "I couldn't find a verified procedure matching your question";
                case TN -> "ما لقيتش إجراء موثّق يطابق سؤالك";
                case AR -> "لم أجد إجراءً موثقاً يطابق سؤالك";
                case FR -> "Je n'ai pas trouvé de procédure vérifiée correspondant à votre question";
            });
            if (message != null && message.length() > 3) {
                answer.append(" (« ").append(message.strip()).append(" »)");
            }
            answer.append(".\n\n");
        }

        answer.append(switch (replyLang) {
            case EN -> "Try again in a moment, or rephrase with keywords like passport, CIN, equivalence, residence.";
            case TN -> "عاود قولّي بوضوح أكثر، مثلا: تبديل الپاسپور، بطاقة تعريف، معادلة شهادة، إقامة.";
            case AR -> "أعد المحاولة بعد لحظات، أو أعد صياغة السؤال بكلمات مثل جواز سفر، بطاقة تعريف، معادلة، إقامة.";
            case FR -> "Réessayez dans quelques instants, ou reformulez avec des mots-clés "
                    + "comme passeport, CIN, équivalence, résidence.";
        });
        return answer.toString().strip();
    }

    /** Natural spoken Derja / Arabic answer from procedure data (no French title dump). */
    private String buildConversationalOfflineAnswer(Procedure top, Language replyLang, boolean wantDocs) {
        String topic = friendlyProcedureLabel(top, replyLang);
        StringBuilder answer = new StringBuilder();
        if (replyLang == Language.TN) {
            answer.append("باهي، فهمتك — تحكي على ").append(topic).append(".\n\n");
        } else {
            answer.append("حسناً، بخصوص ").append(topic).append(":\n\n");
        }

        if (wantDocs && top.getDocuments() != null && !top.getDocuments().isEmpty()) {
            answer.append(replyLang == Language.TN ? "الأوراق اللي تلزمك تقريبًا:\n" : "الوثائق المطلوبة تقريباً:\n");
            top.getDocuments().stream()
                    .sorted(Comparator.comparingInt(com.example.dossia.procedure.domain.ProcedureDocument::getSortOrder))
                    .limit(6)
                    .forEach(doc -> answer
                            .append("- ")
                            .append(pickLocalized(null, doc.getTitleAr(), doc.getTitleFr(), replyLang))
                            .append('\n'));
            answer.append('\n');
        }

        if (top.getSteps() != null && !top.getSteps().isEmpty()) {
            answer.append(replyLang == Language.TN ? "كيفاش تمشي الخطوة بخطوة:\n" : "الخطوات الرئيسية:\n");
            top.getSteps().stream()
                    .sorted(Comparator.comparingInt(com.example.dossia.procedure.domain.ProcedureStep::getStepNumber))
                    .limit(5)
                    .forEach(step -> answer
                            .append(step.getStepNumber())
                            .append(". ")
                            .append(pickLocalized(null, step.getTitleAr(), step.getTitleFr(), replyLang))
                            .append('\n'));
            answer.append('\n');
        }

        if (top.getFees() != null && !top.getFees().isBlank()) {
            answer.append(replyLang == Language.TN ? "الثمن تقريبًا: " : "الرسوم تقريباً: ")
                    .append(top.getFees())
                    .append('\n');
        }
        if (top.getProcessingTime() != null && !top.getProcessingTime().isBlank()) {
            answer.append(replyLang == Language.TN ? "المدّة تقريبًا: " : "المدة تقريباً: ")
                    .append(top.getProcessingTime())
                    .append('\n');
        }

        answer.append('\n');
        if (replyLang == Language.TN) {
            answer.append("تحب نزيدك تفاصيل أكثر، ولا نقولّك وين تمشي تقدّم الملف؟");
        } else {
            answer.append("هل تريد تفاصيل إضافية أو مكان تقديم الملف؟");
        }
        return answer.toString().strip();
    }

    private String friendlyProcedureLabel(Procedure procedure, Language lang) {
        String slug = procedure.getSlug() == null ? "" : procedure.getSlug().toLowerCase();
        if (slug.contains("passeport")) {
            return lang == Language.TN ? "تجديد الپاسپور" : "تجديد جواز السفر";
        }
        if (slug.contains("identite") || slug.contains("cin") || slug.contains("national-id")) {
            return lang == Language.TN ? "بطاقة التعريف (CIN)" : "بطاقة التعريف الوطنية";
        }
        if (slug.contains("equivalence") || slug.contains("diplome")) {
            return lang == Language.TN ? "معادلة الشهادة" : "معادلة الشهادة";
        }
        if (slug.contains("residence")) {
            return lang == Language.TN ? "شهادة الإقامة" : "شهادة الإقامة";
        }
        return pickLocalized(procedure.getTitleTn(), procedure.getTitleAr(), procedure.getTitleFr(), lang);
    }

    private String pickLocalized(String tn, String ar, String fr, Language lang) {
        if (lang == Language.TN) {
            if (tn != null && !tn.isBlank()) {
                return tn.strip();
            }
            if (ar != null && !ar.isBlank() && !looksMostlyLatin(ar)) {
                return ar.strip();
            }
        }
        if (lang == Language.AR || lang == Language.TN) {
            if (ar != null && !ar.isBlank() && !looksMostlyLatin(ar)) {
                return ar.strip();
            }
        }
        return fr == null ? "" : fr.strip();
    }

    private boolean looksMostlyLatin(String text) {
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters == 0) {
            return true;
        }
        long latin = text.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN)
                .count();
        return latin * 2 >= letters;
    }

    private List<ChatSourceDto> buildSources(
            ResponseMode mode,
            List<ProcedureRetrievalService.RetrievedProcedure> retrieved,
            String message,
            List<ConversationTurn> history) {
        if (mode == ResponseMode.FALLBACK || retrieved.isEmpty()) {
            return List.of();
        }

        // Only use USER turns for topic/intent — assistant replies mention CIN as a document and
        // would incorrectly pull national-id procedures into a passport thread.
        StringBuilder userBlob = new StringBuilder(message == null ? "" : message);
        for (ConversationTurn turn : history) {
            if ("user".equals(turn.role()) && turn.content() != null) {
                userBlob.append(' ').append(turn.content());
            }
        }
        List<String> intentSlugs = queryMatcher.matchIntentSlugs(userBlob.toString());
        Topic topic = detectTopic(userBlob.toString());

        List<ProcedureRetrievalService.RetrievedProcedure> filtered = retrieved.stream()
                .filter(item -> matchesTopic(item.procedure(), topic, intentSlugs))
                .sorted(Comparator.comparingDouble(ProcedureRetrievalService.RetrievedProcedure::score)
                        .reversed())
                .limit(2)
                .toList();

        if (filtered.isEmpty()) {
            filtered = retrieved.stream()
                    .filter(item -> matchesTopic(item.procedure(), topic, intentSlugs))
                    .limit(1)
                    .toList();
        }
        if (filtered.isEmpty() && topic != Topic.GENERAL) {
            // Still nothing on-topic — show no cards rather than a wrong procedure.
            return List.of();
        }
        if (filtered.isEmpty()) {
            filtered = retrieved.stream().limit(1).toList();
        }

        // Prefer Tunisia-local passport over abroad when the user said they are in Tunisia.
        String n = queryMatcher.normalize(userBlob.toString());
        boolean inTunisia = (n.contains("in tunisia") || n.contains("en tunisie") || n.contains("tunisie"))
                && !n.contains("abroad")
                && !n.contains("etranger");
        if (topic == Topic.PASSPORT && inTunisia && filtered.size() > 1) {
            filtered = filtered.stream()
                    .sorted(Comparator.comparingInt((ProcedureRetrievalService.RetrievedProcedure item) -> {
                                String slug = item.procedure().getSlug() == null
                                        ? ""
                                        : item.procedure().getSlug().toLowerCase();
                                if (slug.contains("etranger") || slug.contains("abroad")) {
                                    return 2;
                                }
                                if (slug.contains("passeport") || slug.contains("passport")) {
                                    return 0;
                                }
                                return 1;
                            })
                            .thenComparing(Comparator.comparingDouble(
                                            ProcedureRetrievalService.RetrievedProcedure::score)
                                    .reversed()))
                    .limit(2)
                    .toList();
        }
        return filtered.stream().map(this::toSource).toList();
    }

    private enum Topic {
        PASSPORT,
        CIN,
        DIPLOMA,
        DRIVING,
        RESIDENCE,
        GENERAL
    }

    private Topic detectTopic(String text) {
        String n = queryMatcher.normalize(text == null ? "" : text);
        if (n.contains("passeport") || n.contains("passport") || n.contains("biometric")) {
            return Topic.PASSPORT;
        }
        if (n.contains("equivalence") || n.contains("diplome") || n.contains("diploma")) {
            return Topic.DIPLOMA;
        }
        if (n.contains("permis") || n.contains("conduire") || n.contains("driving")) {
            return Topic.DRIVING;
        }
        if (n.contains("residence") || n.contains("attestation de residence")) {
            return Topic.RESIDENCE;
        }
        // CIN only if passport wasn't the main topic
        if (n.contains(" cin")
                || n.startsWith("cin")
                || n.contains("carte d identite")
                || n.contains("national id")
                || n.contains("identity card")) {
            return Topic.CIN;
        }
        return Topic.GENERAL;
    }

    private boolean matchesTopic(Procedure procedure, Topic topic, List<String> intentSlugs) {
        String slug = procedure.getSlug() == null ? "" : procedure.getSlug().toLowerCase();
        String title = queryMatcher.normalize(
                (procedure.getTitleFr() == null ? "" : procedure.getTitleFr())
                        + " "
                        + (procedure.getTitleAr() == null ? "" : procedure.getTitleAr()));

        if (!intentSlugs.isEmpty() && intentSlugs.contains(procedure.getSlug())) {
            return true;
        }

        return switch (topic) {
            case PASSPORT -> slug.contains("passeport")
                    || slug.contains("passport")
                    || title.contains("passeport")
                    || title.contains("passport");
            case CIN -> (slug.contains("identite") || slug.contains("cin") || title.contains("cin")
                            || title.contains("identite"))
                    && !slug.contains("passeport")
                    && !title.contains("passeport");
            case DIPLOMA -> slug.contains("equivalence")
                    || slug.contains("diplome")
                    || title.contains("equivalence")
                    || title.contains("diplome");
            case DRIVING -> slug.contains("permis") || title.contains("permis");
            case RESIDENCE -> slug.contains("residence") || title.contains("residence");
            case GENERAL -> true;
        };
    }

    private List<ChatSuggestionDto> buildSuggestions(
            Language replyLang, String message, List<ChatSourceDto> sources, boolean hasHistory) {
        if (sources.isEmpty() && !hasHistory) {
            return switch (replyLang) {
                case EN -> List.of(
                        new ChatSuggestionDto("passport", "Renew passport", "How do I renew my Tunisian passport?"),
                        new ChatSuggestionDto("cin", "Renew CIN", "How do I renew my national ID (CIN)?"),
                        new ChatSuggestionDto("diploma", "Diploma equivalence", "How does diploma equivalence work in Tunisia?"));
                case TN -> List.of(
                        new ChatSuggestionDto("passport", "تجديد الپاسپور", "كيفاش نجدّد الپاسپور التونسي؟"),
                        new ChatSuggestionDto("cin", "تجديد بطاقة التعريف", "كيفاش نجدّد بطاقة التعريف؟"),
                        new ChatSuggestionDto("diploma", "معادلة", "كيفاش نعمل معادلة شهادة؟"));
                case AR -> List.of(
                        new ChatSuggestionDto("passport", "تجديد جواز السفر", "كيف أجدد جواز سفري التونسي؟"),
                        new ChatSuggestionDto("cin", "تجديد بطاقة التعريف", "كيف أجدد بطاقة التعريف الوطنية؟"),
                        new ChatSuggestionDto("diploma", "معادلة شهادة", "كيف تتم معادلة الشهادة في تونس؟"));
                case FR -> List.of(
                        new ChatSuggestionDto("passport", "Renouveler un passeport", "Comment renouveler mon passeport tunisien ?"),
                        new ChatSuggestionDto("cin", "Renouveler la CIN", "Comment renouveler ma CIN ?"),
                        new ChatSuggestionDto("diploma", "Équivalence de diplôme", "Comment faire une équivalence de diplôme ?"));
            };
        }

        boolean wantsDocs = queryMatcher.isDocumentAsk(message);
        boolean wantsWhere = queryMatcher.isLocationIntent(message);
        java.util.ArrayList<ChatSuggestionDto> chips = new java.util.ArrayList<>();

        if (!wantsDocs) {
            chips.add(switch (replyLang) {
                case EN -> new ChatSuggestionDto("docs", "Documents needed", "What documents do I need?");
                case TN -> new ChatSuggestionDto("docs", "الأوراق اللازمة", "شنوة الأوراق اللي نحتاجهم؟");
                case AR -> new ChatSuggestionDto("docs", "الوثائق المطلوبة", "ما هي الوثائق التي أحتاجها؟");
                case FR -> new ChatSuggestionDto("docs", "Documents requis", "Quels documents dois-je préparer ?");
            });
        }
        if (!wantsWhere) {
            chips.add(switch (replyLang) {
                case EN -> new ChatSuggestionDto("where", "Where to go", "Where should I go to submit this?");
                case TN -> new ChatSuggestionDto("where", "وين نمشي؟", "وين لازم نمشي باش نقدّم الملف؟");
                case AR -> new ChatSuggestionDto("where", "أين أذهب؟", "أين يجب أن أذهب لإيداع الملف؟");
                case FR -> new ChatSuggestionDto("where", "Où aller", "Où dois-je déposer mon dossier ?");
            });
        }
        chips.add(switch (replyLang) {
            case EN -> new ChatSuggestionDto("fees", "Fees & delays", "What are the fees and processing time?");
            case TN -> new ChatSuggestionDto("fees", "الثمن والمدة", "قدّاش يلزم و قداش تقعد؟");
            case AR -> new ChatSuggestionDto("fees", "الرسوم والآجال", "كم التكلفة وما هي المدة؟");
            case FR -> new ChatSuggestionDto("fees", "Frais & délais", "Quels sont les frais et les délais ?");
        });
        chips.add(switch (replyLang) {
            case EN -> new ChatSuggestionDto("abroad", "I'm abroad", "How does this work if I am abroad?");
            case TN -> new ChatSuggestionDto("abroad", "نا برّة", "كيفاش نعمل إذا كنت في الخارج؟");
            case AR -> new ChatSuggestionDto("abroad", "أنا بالخارج", "كيف تتم الإجراءات إذا كنت بالخارج؟");
            case FR -> new ChatSuggestionDto("abroad", "Je suis à l'étranger", "Comment ça se passe si je suis à l'étranger ?");
        });
        return chips.stream().limit(4).toList();
    }

    private List<ConversationTurn> loadHistory(UserPrincipal principal, ChatRequest request) {
        if (principal != null && request.sessionId() != null) {
            try {
                List<ConversationTurn> stored =
                        chatHistoryService.getRecentTurns(principal.getId(), request.sessionId(), HISTORY_MESSAGES);
                if (!stored.isEmpty()) {
                    return stored;
                }
            } catch (RuntimeException ignored) {
                // Stale/foreign session id from the client — fall through to request.history().
            }
        }
        // Guests (and first turn before a session exists): client-sent history for multi-turn.
        if (request.history() == null || request.history().isEmpty()) {
            return List.of();
        }
        return request.history().stream()
                .filter(turn -> turn != null && turn.role() != null && turn.content() != null)
                .filter(turn -> !turn.content().isBlank())
                .map(turn -> {
                    String role = turn.role().equalsIgnoreCase("assistant")
                                    || turn.role().equalsIgnoreCase("model")
                            ? "model"
                            : "user";
                    return new ConversationTurn(role, turn.content().strip());
                })
                .limit(HISTORY_MESSAGES)
                .toList();
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
            List<Procedure> procedures,
            String model,
            Language replyLang) {
        List<NearbyOfficeDto> nearbyOffices = resolveNearbyOffices(request, procedures);
        UUID sessionId = persistExchange(request, principal, answer, sources);
        boolean hasHistory = (request.history() != null && !request.history().isEmpty())
                || request.sessionId() != null;
        List<ChatSuggestionDto> suggestions =
                buildSuggestions(replyLang, request.message(), sources, hasHistory);
        List<ChatChecklistItemDto> checklist = buildChecklist(request.message(), procedures, replyLang);
        return new ChatResponse(answer, sources, model, sessionId, nearbyOffices, suggestions, checklist);
    }

    private List<ChatChecklistItemDto> buildChecklist(
            String message, List<Procedure> procedures, Language replyLang) {
        if (!queryMatcher.isChecklistAsk(message) || procedures.isEmpty()) {
            return List.of();
        }
        Procedure top = procedures.get(0);
        if (top.getDocuments() == null || top.getDocuments().isEmpty()) {
            return List.of();
        }
        return top.getDocuments().stream()
                .sorted(Comparator.comparingInt(com.example.dossia.procedure.domain.ProcedureDocument::getSortOrder))
                .map(doc -> {
                    boolean arabic = replyLang == Language.AR || replyLang == Language.TN;
                    String label = arabic
                                    && doc.getTitleAr() != null
                                    && !doc.getTitleAr().isBlank()
                            ? doc.getTitleAr()
                            : doc.getTitleFr();
                    String hint = arabic
                                    && doc.getDescriptionAr() != null
                                    && !doc.getDescriptionAr().isBlank()
                            ? doc.getDescriptionAr()
                            : doc.getDescriptionFr();
                    return new ChatChecklistItemDto(
                            doc.getId().toString(), label, hint != null ? hint : "");
                })
                .toList();
    }

    private List<NearbyOfficeDto> resolveNearbyOffices(ChatRequest request, List<Procedure> procedures) {
        if (request.latitude() == null || request.longitude() == null) {
            return List.of();
        }
        // Only show the map when the user explicitly asks where to go / nearest office.
        if (!queryMatcher.isLocationIntent(request.message())) {
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
        // resolveSession soft-creates when sessionId is missing or unknown (no 404).
        ChatSession session =
                chatHistoryService.resolveSession(principal.getId(), request.sessionId(), request.message());
        chatHistoryService.appendUserMessage(session, request.message());
        chatHistoryService.appendAssistantMessage(session, answer, sources);
        chatHistoryService.maybeRefreshTitle(session, request.message());
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
                retrieved.score(),
                procedure.getMinistry(),
                procedure.getCategory() != null ? procedure.getCategory().name() : null);
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
        FOLLOWUP,
        FALLBACK
    }

    private record AnswerResult(String answer, String model) {}
}
