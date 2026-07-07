package com.example.dossia.procedure.controller;

import com.example.dossia.chat.ProcedureEmbeddingService;
import com.example.dossia.chat.dto.EmbedAllResultDto;
import com.example.dossia.common.Language;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.domain.ProcedureStatus;
import com.example.dossia.procedure.dto.BulkImportRequest;
import com.example.dossia.procedure.dto.CreateProcedureRequest;
import com.example.dossia.procedure.dto.ImportResultDto;
import com.example.dossia.procedure.dto.ProcedureDetailDto;
import com.example.dossia.procedure.dto.ProcedureSummaryDto;
import com.example.dossia.procedure.dto.PagedResponse;
import com.example.dossia.procedure.service.ProcedureService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/procedures")
public class AdminProcedureController {

    private final ProcedureService procedureService;
    private final ProcedureEmbeddingService procedureEmbeddingService;

    public AdminProcedureController(
            ProcedureService procedureService, ProcedureEmbeddingService procedureEmbeddingService) {
        this.procedureService = procedureService;
        this.procedureEmbeddingService = procedureEmbeddingService;
    }

    @GetMapping
    public PagedResponse<ProcedureSummaryDto> list(
            @RequestParam(required = false) ProcedureStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProcedureCategory category,
            @RequestParam(defaultValue = "fr") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return procedureService.listAdmin(status, q, category, parseLang(lang), page, size);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.OK)
    public ImportResultDto bulkImport(
            @Valid @RequestBody BulkImportRequest request, @RequestParam(defaultValue = "fr") String lang) {
        return procedureService.bulkImport(request, parseLang(lang));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcedureDetailDto create(
            @Valid @RequestBody CreateProcedureRequest request, @RequestParam(defaultValue = "fr") String lang) {
        return procedureService.create(request, parseLang(lang));
    }

    @PutMapping("/{id}")
    public ProcedureDetailDto update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProcedureRequest request,
            @RequestParam(defaultValue = "fr") String lang) {
        return procedureService.update(id, request, parseLang(lang));
    }

    @PatchMapping("/{id}/verify")
    public ProcedureDetailDto verify(@PathVariable UUID id, @RequestParam(defaultValue = "fr") String lang) {
        return procedureService.verify(id, parseLang(lang));
    }

    @PostMapping("/{id}/embed")
    @ResponseStatus(HttpStatus.OK)
    public void embed(@PathVariable UUID id) {
        procedureEmbeddingService.embedProcedure(id);
    }

    @PostMapping("/embed-all")
    @ResponseStatus(HttpStatus.OK)
    public EmbedAllResultDto embedAll() {
        var result = procedureEmbeddingService.embedAllPublishedMissingDetailed();
        return new EmbedAllResultDto(result.embedded(), result.skipped(), result.errors());
    }

    private Language parseLang(String lang) {
        try {
            return Language.valueOf(lang.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Language.FR;
        }
    }
}
