package com.anjing.knowledge.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Toggle enabled status request.
 */
@Data
public class UpdateEnabledRequest {

    @NotNull(message = "启用状态不能为空")
    private Boolean isEnabled;

    public boolean enabledValue() {
        return Boolean.TRUE.equals(isEnabled);
    }
}
