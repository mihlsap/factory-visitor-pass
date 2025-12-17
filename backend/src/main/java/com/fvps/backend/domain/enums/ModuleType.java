package com.fvps.backend.domain.enums;

/**
 * Categorises the type of content served in a {@link com.fvps.backend.domain.entities.TrainingModule}.
 * <p>
 * This enum is used by the frontend client to determine which UI component should be rendered
 * to display the module content (e.g. a video player, a PDF/slide viewer, or a quiz form).
 * </p>
 */
public enum ModuleType {

    /**
     * Represents a video lecture or tutorial.
     * <p>
     * The module will contain a URL to a video file or stream (e.g. YouTube, Vimeo, or hosted mp4).
     * </p>
     */
    VIDEO,

    /**
     * Represents a static document, presentation slides, or a PDF file.
     * <p>
     * Used for reading materials that the user must browse through.
     * </p>
     */
    PDF_SLIDE,

    /**
     * Represents an interactive assessment.
     * <p>
     * The module will contain a list of questions that the user must answer correctly
     * to complete the module or the training.
     * </p>
     */
    QUIZ
}