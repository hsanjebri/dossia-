package com.example.dossia.chat;

import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureStatus;
import com.example.dossia.procedure.repository.ProcedureRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcedureEmbeddingExecutor {

    private final ProcedureRepository procedureRepository;
    private final LlmGateway llmGateway;
    private final ProcedureContextBuilder contextBuilder;
    private final ProcedureLoader procedureLoader;

    public ProcedureEmbeddingExecutor(
            ProcedureRepository procedureRepository,
            LlmGateway llmGateway,
            ProcedureContextBuilder contextBuilder,
            ProcedureLoader procedureLoader) {
        this.procedureRepository = procedureRepository;
        this.llmGateway = llmGateway;
        this.contextBuilder = contextBuilder;
        this.procedureLoader = procedureLoader;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void embedPublished(UUID id) {
        Procedure procedure = procedureLoader.loadWithDetails(id);
        if (procedure.getStatus() != ProcedureStatus.PUBLISHED) {
            return;
        }
        String text = contextBuilder.toEmbeddingText(procedure);
        float[] embedding = llmGateway.embed(text);
        procedureRepository.updateEmbedding(procedure.getId(), VectorUtils.toPgVector(embedding));
    }
}
