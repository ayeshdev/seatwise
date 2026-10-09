/**
 * Cross-cutting infrastructure (security, error model, clock, configuration).
 * OPEN so every module may use its sub-packages directly.
 */
@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN)
package com.seatwise.common;

import org.springframework.modulith.ApplicationModule;
