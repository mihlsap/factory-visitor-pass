package com.fvps.backend.domain.enums;

/**
 * Defines the category or domain of a {@link com.fvps.backend.domain.entities.Training}.
 * <p>
 * These categories are used for organising trainings, filtering lists in the frontend,
 * and generating specific reports.
 * </p>
 */
public enum TrainingType {

    /**
     * Occupational Health and Safety training (BHP).
     * <p>
     * Critical, mandatory trainings required by law or internal safety regulations
     * before granting access to the facility.
     * </p>
     */
    OHS,

    /**
     * General informational content.
     * <p>
     * Includes non-critical guides, facility maps, welcome packets, or company policy updates
     * that do not strictly impact safety clearance.
     * </p>
     */
    INFORMATIONAL,

    /**
     * Specialised or client-specific training.
     * <p>
     * Used for tailored courses that do not fit into standard categories, such as specific
     * machinery instructions or temporary project requirements.
     * </p>
     */
    CUSTOM
}