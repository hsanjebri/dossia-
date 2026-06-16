package com.example.dossia.procedure.dto;

import jakarta.validation.Valid;
import java.util.List;

public record BulkImportRequest(@Valid List<@Valid CreateProcedureRequest> procedures) {}
