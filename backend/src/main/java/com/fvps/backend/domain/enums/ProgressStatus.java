package com.fvps.backend.domain.enums;

/**
 * Tracks the lifecycle and current state of a user's progress through a specific training.
 * <p>
 * This status determines whether the training is considered valid for generating a Visitor Pass.
 * </p>
 */
public enum ProgressStatus {

    /**
     * The training has been assigned to the user, but they have not engaged with any content yet.
     */
    NOT_STARTED,

    /**
     * The user has started viewing the training modules (videos/slides) but has not yet finished them
     * or has not attempted the final quiz.
     */
    IN_PROGRESS,

    /**
     * The user has successfully finished all modules.
     * <p>
     * <b>Note:</b> Only trainings with this status are included in the user's security clearance calculation
     * and appear on the generated pass.
     * </p>
     */
    COMPLETED,

    /**
     * The user has attempted the final quiz but failed to achieve the required passing threshold.
     * <p>
     * Depending on system configuration, the user may need to retake the quiz or restart the training
     * to move to {@code COMPLETED}.
     * </p>
     */
    FAILED
}