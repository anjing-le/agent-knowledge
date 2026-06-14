package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderServiceTest {

    private final RagPromptBuilderService promptBuilderService = new RagPromptBuilderService();

    @Test
    void buildRagSystemPromptShouldTellModelWhenNoKnowledgeWasFound() {
        RagPromptBuilderService.RagPromptContext context = promptBuilderService.buildRagContext(List.of());
        String prompt = context.systemPrompt();

        assertThat(prompt)
                .contains("知识库中未找到相关内容")
                .contains("AI补充");
        assertThat(context.trace().getReferenceCount()).isZero();
        assertThat(context.trace().getPromptSections()).containsExactly("no_knowledge_fallback");
    }

    @Test
    void buildRagSystemPromptShouldRenderCitationContext() {
        SearchResult result = new SearchResult();
        result.setDocName("脚手架到 RAG.pdf");
        result.setContent("agent-knowledge 从脚手架生长出 RAG agent。");
        result.setFinalScore(0.92f);
        result.setRank(1);
        result.setChunkId("chunk_001");
        result.setRetrievalSource("vector");
        result.setScoreExplanation("rank=1 final=0.9200 similarity=0.9200 keyword=disabled hybrid=disabled rerank=disabled threshold=0.3000");

        RagPromptBuilderService.RagPromptContext context = promptBuilderService.buildRagContext(List.of(result));
        String prompt = context.systemPrompt();

        assertThat(prompt)
                .contains("绝对禁止幻觉")
                .contains("## 知识库参考内容")
                .contains("【参考 1】来源：脚手架到 RAG.pdf | 相似度：0.92")
                .contains("agent-knowledge 从脚手架生长出 RAG agent。")
                .contains("来源必须写参考内容中的真实文档名");
        assertThat(context.trace().getAssemblyStrategy()).isEqualTo("retrieval-context-to-system-prompt");
        assertThat(context.trace().getReferenceCount()).isEqualTo(1);
        assertThat(context.trace().getIncludedChunkCount()).isEqualTo(1);
        assertThat(context.trace().getContextCharCount()).isEqualTo(result.getContent().length());
        assertThat(context.trace().getPromptSections())
                .containsExactly("core_principles", "answer_format", "knowledge_context", "citation_reminder");
        assertThat(context.trace().getIncludedChunks()).hasSize(1);
        assertThat(context.trace().getIncludedChunks().get(0).getChunkId()).isEqualTo("chunk_001");
        assertThat(context.trace().getIncludedChunks().get(0).getRetrievalSource()).isEqualTo("vector");
    }
}
