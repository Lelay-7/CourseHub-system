package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialReviewRequest(
        @JsonProperty("expected_revision") @NotNull @Min(1) Integer expectedRevision,
        @Size(max = 500) String reason) {}
