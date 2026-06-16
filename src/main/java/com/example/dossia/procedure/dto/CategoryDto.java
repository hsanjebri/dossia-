package com.example.dossia.procedure.dto;

import com.example.dossia.procedure.domain.ProcedureCategory;
import java.util.List;

public record CategoryDto(ProcedureCategory code, String labelFr, String labelAr, String icon) {

    public static List<CategoryDto> all() {
        return List.of(
                new CategoryDto(ProcedureCategory.CIVIL_STATUS, "État civil", "الحالة المدنية", "person"),
                new CategoryDto(ProcedureCategory.BUSINESS, "Entreprise", "الأعمال", "business_center"),
                new CategoryDto(ProcedureCategory.VEHICLES, "Véhicules", "المركبات", "directions_car"),
                new CategoryDto(ProcedureCategory.SOCIAL, "Social", "الاجتماعي", "diversity_3"),
                new CategoryDto(ProcedureCategory.EDUCATION, "Éducation", "التعليم", "school"),
                new CategoryDto(ProcedureCategory.TAX, "Fiscalité", "الجباية", "account_balance"));
    }
}
