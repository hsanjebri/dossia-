package com.example.dossia.procedure.controller;

import com.example.dossia.common.Language;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.dto.CategoryDto;
import com.example.dossia.procedure.dto.PagedResponse;
import com.example.dossia.procedure.dto.ProcedureDetailDto;
import com.example.dossia.procedure.dto.ProcedureSummaryDto;
import com.example.dossia.procedure.service.ProcedureService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procedures")
public class ProcedureController {

    private final ProcedureService procedureService;

    public ProcedureController(ProcedureService procedureService) {
        this.procedureService = procedureService;
    }

    @GetMapping
    public PagedResponse<ProcedureSummaryDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProcedureCategory category,
            @RequestParam(defaultValue = "fr") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return procedureService.listPublished(q, category, parseLang(lang), page, size);
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return procedureService.listCategories();
    }

    @GetMapping("/{slug}")
    public ProcedureDetailDto getBySlug(@PathVariable String slug, @RequestParam(defaultValue = "fr") String lang) {
        return procedureService.getPublishedBySlug(slug, parseLang(lang));
    }

    private Language parseLang(String lang) {
        try {
            return Language.valueOf(lang.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Language.FR;
        }
    }
}
