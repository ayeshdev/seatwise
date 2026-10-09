package com.seatwise.workshops;

/** One changed field of an edit: its value before and after. Values are JSON-friendly (strings, numbers, instants, UUIDs). */
public record FieldChange(Object from, Object to) {}
