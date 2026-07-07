package com.example.dossia.chat;

import com.example.dossia.procedure.domain.Procedure;
import com.example.dossia.procedure.domain.ProcedureDocument;
import com.example.dossia.procedure.domain.ProcedureStep;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class ProcedureContextBuilder {

    public String toEmbeddingText(Procedure procedure) {
        StringBuilder text = new StringBuilder();
        appendLine(text, "Titre", procedure.getTitleFr());
        appendLine(text, "Ministère", procedure.getMinistry());
        appendLine(text, "Catégorie", procedure.getCategory().name());
        appendLine(text, "Description", procedure.getDescriptionFr());
        appendLine(text, "Frais", procedure.getFees());
        appendLine(text, "Délai", procedure.getProcessingTime());
        appendLine(text, "Mode", procedure.getDeliveryMode());

        if (!procedure.getDocuments().isEmpty()) {
            text.append("Documents requis:\n");
            procedure.getDocuments().stream()
                    .sorted(Comparator.comparingInt(ProcedureDocument::getSortOrder))
                    .forEach(doc -> text.append("- ").append(doc.getTitleFr()).append('\n'));
        }

        if (!procedure.getSteps().isEmpty()) {
            text.append("Étapes:\n");
            procedure.getSteps().stream()
                    .sorted(Comparator.comparingInt(ProcedureStep::getStepNumber))
                    .forEach(step -> text.append(step.getStepNumber())
                            .append(". ")
                            .append(step.getTitleFr())
                            .append('\n'));
        }

        return text.toString().trim();
    }

    public String toPromptContext(Procedure procedure) {
        StringBuilder text = new StringBuilder();
        text.append("=== ").append(procedure.getTitleFr()).append(" ===\n");
        appendLine(text, "Slug", procedure.getSlug());
        appendLine(text, "Ministère", procedure.getMinistry());
        appendLine(text, "Frais", procedure.getFees());
        appendLine(text, "Délai", procedure.getProcessingTime());
        appendLine(text, "Description", procedure.getDescriptionFr());
        appendLine(text, "Source", procedure.getSourceUrl());
        appendLine(text, "Référence", procedure.getSourceReference());
        appendLine(text, "Dernière vérification", String.valueOf(procedure.getLastVerifiedAt()));

        if (!procedure.getDocuments().isEmpty()) {
            text.append("Documents:\n");
            procedure.getDocuments().stream()
                    .sorted(Comparator.comparingInt(ProcedureDocument::getSortOrder))
                    .forEach(doc -> text.append("- ").append(doc.getTitleFr()).append('\n'));
        }

        if (!procedure.getSteps().isEmpty()) {
            text.append("Étapes:\n");
            procedure.getSteps().stream()
                    .sorted(Comparator.comparingInt(ProcedureStep::getStepNumber))
                    .forEach(step -> text.append(step.getStepNumber())
                            .append(". ")
                            .append(step.getTitleFr())
                            .append('\n'));
        }

        return text.toString().trim();
    }

    private void appendLine(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append(label).append(": ").append(value).append('\n');
        }
    }
}
