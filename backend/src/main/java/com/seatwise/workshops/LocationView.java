package com.seatwise.workshops;

import java.util.UUID;

/** One of the centre's locations; also the API's {@code Location} shape ({@code {id, name}}). */
public record LocationView(UUID id, String name) {}
