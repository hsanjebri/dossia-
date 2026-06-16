package com.example.dossia.procedure.mapper;

import com.example.dossia.common.Language;
import com.example.dossia.procedure.domain.OfficeLocation;
import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureDocument;
import com.example.dossia.procedure.domain.ProcedureStep;
import com.example.dossia.procedure.dto.OfficeLocationDto;
import com.example.dossia.procedure.dto.ProcedureDetailDto;
import com.example.dossia.procedure.dto.ProcedureDocumentDto;
import com.example.dossia.procedure.dto.ProcedureStepDto;
import com.example.dossia.procedure.dto.ProcedureSummaryDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProcedureMapper {

    public ProcedureSummaryDto toSummary(Procedure procedure, Language lang) {
        return new ProcedureSummaryDto(
                procedure.getId(),
                procedure.getSlug(),
                localized(procedure.getTitleFr(), procedure.getTitleAr(), lang),
                procedure.getTitleAr(),
                procedure.getMinistry(),
                procedure.getCategory(),
                procedure.getDifficulty(),
                procedure.getDeliveryMode(),
                procedure.getProcessingTime(),
                procedure.getFees(),
                procedure.getLastVerifiedAt());
    }

    public ProcedureDetailDto toDetail(Procedure procedure, Language lang) {
        return new ProcedureDetailDto(
                procedure.getId(),
                procedure.getSlug(),
                localized(procedure.getTitleFr(), procedure.getTitleAr(), lang),
                procedure.getTitleAr(),
                procedure.getTitleTn(),
                localized(procedure.getDescriptionFr(), procedure.getDescriptionAr(), lang),
                procedure.getDescriptionAr(),
                procedure.getMinistry(),
                procedure.getCategory(),
                procedure.getDifficulty(),
                procedure.getDeliveryMode(),
                procedure.getProcessingTime(),
                procedure.getFees(),
                procedure.getSourceUrl(),
                procedure.getSourceReference(),
                procedure.getLastVerifiedAt(),
                procedure.getStatus(),
                mapDocuments(procedure.getDocuments(), lang),
                mapSteps(procedure.getSteps(), lang),
                mapOffices(procedure.getOffices(), lang),
                procedure.getRelatedProcedures().stream()
                        .map(related -> toSummary(related, lang))
                        .toList());
    }

    private List<ProcedureDocumentDto> mapDocuments(List<ProcedureDocument> documents, Language lang) {
        return documents.stream()
                .map(doc -> new ProcedureDocumentDto(
                        localized(doc.getTitleFr(), doc.getTitleAr(), lang),
                        doc.getTitleAr(),
                        localized(doc.getDescriptionFr(), doc.getDescriptionAr(), lang),
                        doc.getDescriptionAr(),
                        doc.getSortOrder()))
                .toList();
    }

    private List<ProcedureStepDto> mapSteps(List<ProcedureStep> steps, Language lang) {
        return steps.stream()
                .map(step -> new ProcedureStepDto(
                        step.getStepNumber(),
                        localized(step.getTitleFr(), step.getTitleAr(), lang),
                        step.getTitleAr(),
                        localized(step.getDescriptionFr(), step.getDescriptionAr(), lang),
                        step.getDescriptionAr()))
                .toList();
    }

    private List<OfficeLocationDto> mapOffices(List<OfficeLocation> offices, Language lang) {
        return offices.stream()
                .map(office -> new OfficeLocationDto(
                        office.getName(),
                        office.getAddress(),
                        office.getCity(),
                        office.getGovernorate(),
                        localized(office.getHoursFr(), office.getHoursAr(), lang),
                        office.getHoursAr(),
                        office.getLatitude(),
                        office.getLongitude()))
                .toList();
    }

    private String localized(String fr, String ar, Language lang) {
        if (lang == Language.AR || lang == Language.TN) {
            return ar != null && !ar.isBlank() ? ar : fr;
        }
        return fr != null && !fr.isBlank() ? fr : ar;
    }
}
