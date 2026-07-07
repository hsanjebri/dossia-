package com.example.dossia.chat;

import com.example.dossia.common.GeminiException;
import com.example.dossia.config.GeminiProperties;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureStatus;
import com.example.dossia.procedure.repository.ProcedureRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcedureEmbeddingService {

    private final ProcedureRepository procedureRepository;
    private final GeminiProperties geminiProperties;
    private final ProcedureEmbeddingExecutor embeddingExecutor;

    public ProcedureEmbeddingService(
            ProcedureRepository procedureRepository,
            GeminiProperties geminiProperties,
            ProcedureEmbeddingExecutor embeddingExecutor) {
        this.procedureRepository = procedureRepository;
        this.geminiProperties = geminiProperties;
        this.embeddingExecutor = embeddingExecutor;
    }

    @Transactional
    public void embedProcedure(UUID id) {
        requireConfigured();
        embeddingExecutor.embedPublished(id);
    }

    @Transactional
    public void embedIfPublished(Procedure procedure) {
        if (!geminiProperties.isConfigured()) {
            return;
        }
        if (procedure.getStatus() != ProcedureStatus.PUBLISHED) {
            return;
        }
        embeddingExecutor.embedPublished(procedure.getId());
    }

    public EmbedAllStats embedAllPublishedMissingDetailed() {
        requireConfigured();

        List<UUID> ids = procedureRepository.findPublishedWithoutEmbedding();
        int embedded = 0;
        int errors = 0;

        for (UUID id : ids) {
            try {
                embeddingExecutor.embedPublished(id);
                embedded++;
            } catch (RuntimeException ex) {
                errors++;
            }
        }

        return new EmbedAllStats(embedded, 0, errors);
    }

    private void requireConfigured() {
        if (!geminiProperties.isConfigured()) {
            throw new GeminiException("Gemini is not configured. Set GEMINI_API_KEY in .env");
        }
    }

    public record EmbedAllStats(int embedded, int skipped, int errors) {}
}
