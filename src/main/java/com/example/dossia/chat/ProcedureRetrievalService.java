package com.example.dossia.chat;

import com.example.dossia.common.GeminiException;
import com.example.dossia.common.Language;
import com.example.dossia.config.GeminiProperties;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureStatus;
import com.example.dossia.procedure.repository.ProcedureRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProcedureRetrievalService {

    private static final double KEYWORD_MATCH_SCORE = 50.0;
    private static final double INTENT_MATCH_SCORE = 100.0;

    private final ProcedureRepository procedureRepository;
    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final ProcedureLoader procedureLoader;
    private final ChatQueryMatcher queryMatcher;

    public ProcedureRetrievalService(
            ProcedureRepository procedureRepository,
            GeminiClient geminiClient,
            GeminiProperties geminiProperties,
            ProcedureLoader procedureLoader,
            ChatQueryMatcher queryMatcher) {
        this.procedureRepository = procedureRepository;
        this.geminiClient = geminiClient;
        this.geminiProperties = geminiProperties;
        this.procedureLoader = procedureLoader;
        this.queryMatcher = queryMatcher;
    }

    public List<RetrievedProcedure> retrieve(String query, Language lang) {
        Map<UUID, Double> scores = new LinkedHashMap<>();

        if (geminiProperties.isConfigured()) {
            try {
                float[] queryEmbedding = geminiClient.embed(query);
                List<UUID> vectorIds = procedureRepository.findSimilarPublishedIds(
                        VectorUtils.toPgVector(queryEmbedding), geminiProperties.retrievalLimit());
                for (int i = 0; i < vectorIds.size(); i++) {
                    scores.merge(vectorIds.get(i), (double) vectorIds.size() - i, Math::max);
                }
            } catch (GeminiException ignored) {
                // Vector search unavailable — keyword + intent matching still works.
            }
        }

        for (String term : queryMatcher.extractSearchTerms(query)) {
            for (UUID id : procedureRepository.findPublishedIdsMatchingTerm(term, 3)) {
                scores.merge(id, KEYWORD_MATCH_SCORE, Math::max);
            }
        }

        for (String slug : queryMatcher.matchIntentSlugs(query)) {
            procedureRepository
                    .findBySlugAndStatus(slug, ProcedureStatus.PUBLISHED)
                    .ifPresent(procedure -> scores.merge(procedure.getId(), INTENT_MATCH_SCORE, Math::max));
        }

        if (scores.isEmpty()) {
            return List.of();
        }

        List<UUID> rankedIds = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(geminiProperties.retrievalLimit())
                .toList();

        Map<UUID, Procedure> byId = new LinkedHashMap<>();
        procedureLoader.loadWithDetails(rankedIds).forEach(procedure -> byId.put(procedure.getId(), procedure));

        List<RetrievedProcedure> results = new ArrayList<>();
        for (UUID id : rankedIds) {
            Procedure procedure = byId.get(id);
            if (procedure != null) {
                results.add(new RetrievedProcedure(procedure, scores.get(id), lang));
            }
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(RetrievedProcedure::score).reversed())
                .toList();
    }

    public record RetrievedProcedure(Procedure procedure, double score, Language lang) {}
}
