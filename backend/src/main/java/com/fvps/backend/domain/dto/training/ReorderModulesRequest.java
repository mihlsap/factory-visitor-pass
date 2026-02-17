package com.fvps.backend.domain.dto.training;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ReorderModulesRequest {

    @Schema(description = "List of module UUIDs in new order")
    private List<UUID> moduleIds;
}