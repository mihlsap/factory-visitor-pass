package com.fvps.backend.domain.dto.training;

import com.fvps.backend.domain.enums.ModuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateModuleRequest {

    @Schema(description = "Updated module title", example = "Advanced Safety Video", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Module title cannot be empty")
    private String title;

    @Schema(description = "Updated module type", example = "VIDEO", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Module type is required")
    private ModuleType type;

    @Schema(description = "Updated content URL", example = "https://youtube.com/watch?v=new_id")
    private String contentUrl;

    @Schema(description = "Optimistic locking version", example = "1")
    private Long version;

    @Schema(description = "New order index", example = "1")
    @Min(0)
    private Integer orderIndex;

    @Schema(description = "Force progress reset for users", example = "true")
    private boolean resetProgress;
}