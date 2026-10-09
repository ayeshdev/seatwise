package com.seatwise.common.error;

/** One entry of the {@code errors} array on a VALIDATION_FAILED problem. */
public record FieldErrorItem(String field, String message) {}
