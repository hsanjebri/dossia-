package com.example.dossia.chat;

import com.example.dossia.common.ResourceNotFoundException;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.repository.ProcedureRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcedureLoader {

    private final ProcedureRepository procedureRepository;

    public ProcedureLoader(ProcedureRepository procedureRepository) {
        this.procedureRepository = procedureRepository;
    }

    public Procedure loadWithDetails(UUID id) {
        Procedure procedure = procedureRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure not found: " + id));
        initializeCollections(procedure);
        return procedure;
    }

    public List<Procedure> loadWithDetails(Collection<UUID> ids) {
        Map<UUID, Procedure> ordered = new LinkedHashMap<>();
        for (UUID id : ids) {
            ordered.put(id, loadWithDetails(id));
        }
        return ordered.values().stream().toList();
    }

    private void initializeCollections(Procedure procedure) {
        procedure.getDocuments().size();
        procedure.getSteps().size();
        procedure.getOffices().size();
    }
}
