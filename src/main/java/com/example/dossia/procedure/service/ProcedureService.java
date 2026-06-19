package com.example.dossia.procedure.service;

import com.example.dossia.common.ConflictException;
import com.example.dossia.common.Language;
import com.example.dossia.common.ResourceNotFoundException;
import com.example.dossia.procedure.domain.OfficeLocation;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureCategory;
import com.example.dossia.procedure.domain.ProcedureDocument;
import com.example.dossia.procedure.domain.ProcedureStatus;
import com.example.dossia.procedure.domain.ProcedureStep;
import com.example.dossia.procedure.dto.BulkImportRequest;
import com.example.dossia.procedure.dto.CategoryDto;
import com.example.dossia.procedure.dto.CreateProcedureRequest;
import com.example.dossia.procedure.dto.ImportResultDto;
import com.example.dossia.procedure.dto.OfficeLocationRequest;
import com.example.dossia.procedure.dto.PagedResponse;
import com.example.dossia.procedure.dto.ProcedureDetailDto;
import com.example.dossia.procedure.dto.ProcedureDocumentRequest;
import com.example.dossia.procedure.dto.ProcedureStepRequest;
import com.example.dossia.procedure.dto.ProcedureSummaryDto;
import com.example.dossia.procedure.mapper.ProcedureMapper;
import com.example.dossia.procedure.repository.ProcedureRepository;
import com.example.dossia.procedure.util.SlugUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProcedureService {

    private static final int MAX_SHORT_TEXT = 500;
    private static final int MAX_STEP_TITLE = 500;

    private final ProcedureRepository procedureRepository;
    private final ProcedureMapper procedureMapper;
    private final ProcedureImportService procedureImportService;

    public ProcedureService(
            ProcedureRepository procedureRepository,
            ProcedureMapper procedureMapper,
            ProcedureImportService procedureImportService) {
        this.procedureRepository = procedureRepository;
        this.procedureMapper = procedureMapper;
        this.procedureImportService = procedureImportService;
    }

    public PagedResponse<ProcedureSummaryDto> listPublished(
            String query, ProcedureCategory category, Language lang, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("titleFr").ascending());
        Page<Procedure> results =
                procedureRepository.searchPublished(ProcedureStatus.PUBLISHED, category, query, pageable);
        List<ProcedureSummaryDto> content =
                results.getContent().stream().map(p -> procedureMapper.toSummary(p, lang)).toList();
        return new PagedResponse<>(
                content, results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }

    public ProcedureDetailDto getPublishedBySlug(String slug, Language lang) {
        Procedure procedure = procedureRepository
                .findPublishedDetailBySlug(slug, ProcedureStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure not found: " + slug));
        return procedureMapper.toDetail(procedure, lang);
    }

    public List<CategoryDto> listCategories() {
        return CategoryDto.all();
    }

    public PagedResponse<ProcedureSummaryDto> listAdmin(
            ProcedureStatus status,
            String query,
            ProcedureCategory category,
            Language lang,
            int page,
            int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<Procedure> results = procedureRepository.searchAll(status, category, query, pageable);
        List<ProcedureSummaryDto> content =
                results.getContent().stream().map(p -> procedureMapper.toSummary(p, lang)).toList();
        return new PagedResponse<>(
                content, results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
    }

    @Transactional
    public ImportResultDto bulkImport(BulkImportRequest request, Language lang) {
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        if (request.procedures() == null) {
            return new ImportResultDto(0, 0, List.of());
        }

        for (CreateProcedureRequest procedureRequest : request.procedures()) {
            String slug = resolveSlug(procedureRequest.slug(), procedureRequest.titleFr());
            if (procedureRepository.existsBySlug(slug)) {
                skipped++;
                continue;
            }
            try {
                procedureImportService.importOne(withDraftStatus(procedureRequest), lang);
                created++;
            } catch (ConflictException ex) {
                skipped++;
            } catch (RuntimeException ex) {
                errors.add(slug + ": " + ex.getMessage());
            }
        }

        return new ImportResultDto(created, skipped, errors);
    }

    private CreateProcedureRequest withDraftStatus(CreateProcedureRequest request) {
        if (request.status() != null) {
            return request;
        }
        return new CreateProcedureRequest(
                request.slug(),
                request.titleFr(),
                request.titleAr(),
                request.titleTn(),
                request.descriptionFr(),
                request.descriptionAr(),
                request.ministry(),
                request.category(),
                request.difficulty(),
                request.deliveryMode(),
                request.processingTime(),
                request.fees(),
                request.sourceUrl(),
                request.sourceReference(),
                ProcedureStatus.DRAFT,
                request.documents(),
                request.steps(),
                request.offices(),
                request.relatedProcedureSlugs());
    }

    @Transactional
    public ProcedureDetailDto create(CreateProcedureRequest request, Language lang) {
        String slug = resolveSlug(request.slug(), request.titleFr());
        if (procedureRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug already exists: " + slug);
        }

        Procedure procedure = new Procedure();
        procedure.setSlug(slug);
        applyFields(procedure, request);
        procedure.setStatus(request.status() != null ? request.status() : ProcedureStatus.DRAFT);
        applyChildren(procedure, request);

        Procedure saved = procedureRepository.save(procedure);
        return procedureMapper.toDetail(saved, lang);
    }

    @Transactional
    public ProcedureDetailDto update(UUID id, CreateProcedureRequest request, Language lang) {
        Procedure procedure = procedureRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure not found: " + id));

        if (request.slug() != null && !request.slug().equals(procedure.getSlug())) {
            String newSlug = resolveSlug(request.slug(), request.titleFr());
            if (!newSlug.equals(procedure.getSlug()) && procedureRepository.existsBySlug(newSlug)) {
                throw new ConflictException("Slug already exists: " + newSlug);
            }
            procedure.setSlug(newSlug);
        }

        applyFields(procedure, request);
        if (request.status() != null) {
            procedure.setStatus(request.status());
        }

        procedure.getDocuments().clear();
        procedure.getSteps().clear();
        procedure.getOffices().clear();
        procedure.getRelatedProcedures().clear();
        applyChildren(procedure, request);

        return procedureMapper.toDetail(procedureRepository.save(procedure), lang);
    }

    @Transactional
    public ProcedureDetailDto verify(UUID id, Language lang) {
        Procedure procedure = procedureRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Procedure not found: " + id));
        procedure.setLastVerifiedAt(Instant.now());
        if (procedure.getStatus() == ProcedureStatus.DRAFT) {
            procedure.setStatus(ProcedureStatus.PUBLISHED);
        }
        return procedureMapper.toDetail(procedureRepository.save(procedure), lang);
    }

    private void applyFields(Procedure procedure, CreateProcedureRequest request) {
        procedure.setTitleFr(request.titleFr());
        procedure.setTitleAr(request.titleAr());
        procedure.setTitleTn(request.titleTn());
        procedure.setDescriptionFr(request.descriptionFr());
        procedure.setDescriptionAr(request.descriptionAr());
        procedure.setMinistry(request.ministry());
        procedure.setCategory(request.category());
        procedure.setDifficulty(request.difficulty());
        procedure.setDeliveryMode(truncate(request.deliveryMode(), 100));
        procedure.setProcessingTime(truncate(request.processingTime(), MAX_SHORT_TEXT));
        procedure.setFees(truncate(request.fees(), MAX_SHORT_TEXT));
        procedure.setSourceUrl(request.sourceUrl());
        procedure.setSourceReference(request.sourceReference());
    }

    private void applyChildren(Procedure procedure, CreateProcedureRequest request) {
        if (request.documents() != null) {
            for (ProcedureDocumentRequest docReq : request.documents()) {
                ProcedureDocument doc = new ProcedureDocument();
                doc.setProcedure(procedure);
                doc.setSortOrder(docReq.sortOrder());
                doc.setTitleFr(docReq.titleFr());
                doc.setTitleAr(docReq.titleAr());
                doc.setDescriptionFr(docReq.descriptionFr());
                doc.setDescriptionAr(docReq.descriptionAr());
                procedure.getDocuments().add(doc);
            }
        }

        if (request.steps() != null) {
            for (ProcedureStepRequest stepReq : request.steps()) {
                ProcedureStep step = new ProcedureStep();
                step.setProcedure(procedure);
                step.setStepNumber(stepReq.stepNumber());
                step.setTitleFr(truncate(stepReq.titleFr(), MAX_STEP_TITLE));
                step.setTitleAr(truncate(stepReq.titleAr(), MAX_STEP_TITLE));
                step.setDescriptionFr(stepReq.descriptionFr());
                step.setDescriptionAr(stepReq.descriptionAr());
                procedure.getSteps().add(step);
            }
        }

        if (request.offices() != null) {
            for (OfficeLocationRequest officeReq : request.offices()) {
                OfficeLocation office = new OfficeLocation();
                office.setProcedure(procedure);
                office.setName(officeReq.name());
                office.setAddress(officeReq.address());
                office.setCity(officeReq.city());
                office.setGovernorate(officeReq.governorate());
                office.setHoursFr(officeReq.hoursFr());
                office.setHoursAr(officeReq.hoursAr());
                office.setLatitude(officeReq.latitude());
                office.setLongitude(officeReq.longitude());
                procedure.getOffices().add(office);
            }
        }

        if (request.relatedProcedureSlugs() != null && !request.relatedProcedureSlugs().isEmpty()) {
            procedure.setRelatedProcedures(new HashSet<>());
            for (String relatedSlug : request.relatedProcedureSlugs()) {
                Procedure related = procedureRepository
                        .findBySlug(relatedSlug)
                        .or(() -> procedureRepository.findById(parseUuidOrThrow(relatedSlug)))
                        .orElseThrow(() -> new ResourceNotFoundException("Related procedure not found: " + relatedSlug));
                procedure.getRelatedProcedures().add(related);
            }
        }
    }

    private UUID parseUuidOrThrow(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Related procedure not found: " + value);
        }
    }

    private String resolveSlug(String requestedSlug, String titleFr) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            return SlugUtils.slugify(requestedSlug);
        }
        return SlugUtils.slugify(titleFr);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
