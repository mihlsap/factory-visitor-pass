package com.fvps.backend.domain.dto.training;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuizResultDto {
    @Schema(description = "Indicates if the user passed the quiz", example = "true")
    private boolean passed;

    @Schema(description = "Percentage score (0.0 - 1.0)", example = "0.7")
    private double score;

    @Schema(description = "Number of correct answers", example = "7")
    private int correctAnswersCount;

    @Schema(description = "Total number of questions", example = "10")
    private int totalQuestionsCount;

    @Schema(description = "Passing threshold for this training", example = "0.8")
    private double passingThreshold;

    @Schema(description = "List of questions the user answered incorrectly (with correct answers). Provided only if passed.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<QuestionDto> incorrectQuestions;
}