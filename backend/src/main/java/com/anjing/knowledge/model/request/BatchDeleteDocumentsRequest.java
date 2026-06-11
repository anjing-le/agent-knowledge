package com.anjing.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Batch delete documents request.
 */
@Data
public class BatchDeleteDocumentsRequest {

    @NotEmpty(message = "文档ID列表不能为空")
    private List<@NotBlank(message = "文档ID不能为空") String> docIds;
}
