package com.example.dossia.procedure.service;

import com.example.dossia.common.ConflictException;
import com.example.dossia.common.Language;
import com.example.dossia.procedure.dto.CreateProcedureRequest;
import com.example.dossia.procedure.repository.ProcedureRepository;
import com.example.dossia.procedure.util.SlugUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcedureImportService {

    private final ProcedureRepository procedureRepository;
    private final ProcedureService procedureService;

    public ProcedureImportService(
            ProcedureRepository procedureRepository, @Lazy ProcedureService procedureService) {
        this.procedureRepository = procedureRepository;
        this.procedureService = procedureService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importOne(CreateProcedureRequest request, Language lang) {
        String slug = resolveSlug(request.slug(), request.titleFr());
        if (procedureRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug already exists: " + slug);
        }
        procedureService.create(request, lang);
    }

    private String resolveSlug(String requestedSlug, String titleFr) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            return SlugUtils.slugify(requestedSlug);
        }
        return SlugUtils.slugify(titleFr);
    }
}
