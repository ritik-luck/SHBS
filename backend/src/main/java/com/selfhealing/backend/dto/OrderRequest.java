package com.selfhealing.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * OrderRequest — Validated DTO for order creation.
 *
 * Replaces the raw Map<String, Object> payload in OrderController,
 * ensuring type safety and input validation before processing.
 */
public record OrderRequest(
    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must be less than 255 characters")
    String product,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity
) {}
