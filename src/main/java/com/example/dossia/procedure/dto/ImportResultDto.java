package com.example.dossia.procedure.dto;

import java.util.List;

public record ImportResultDto(int created, int skipped, List<String> errors) {}
