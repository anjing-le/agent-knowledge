package com.anjing.demo.model.response;

import lombok.Data;

import java.util.List;

/**
 * Local teaching demo seed result.
 */
@Data
public class RagDemoSeedResponse {

    private String kbId;
    private String kbName;
    private String docId;
    private String docName;
    private List<String> chunkIds;
    private Integer vectorCount;
    private String retrievalQuery;
    private Integer sampleResultCount;
    private String topChunkId;
    private String topScoreExplanation;
    private String chatQuestion;
    private String pipelineRoute;
    private String knowledgeRoute;
    private String retrievalRoute;
    private String chatRoute;
    private List<String> evidenceCommands;
}
