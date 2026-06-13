package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderServiceTest {

    private final RagPromptBuilderService promptBuilderService = new RagPromptBuilderService();

    @Test
    void buildRagSystemPromptShouldTellModelWhenNoKnowledgeWasFound() {
        String prompt = promptBuilderService.buildRagSystemPrompt(List.of());

        assertThat(prompt)
                .contains("知识库中未找到相关内容")
                .contains("AI补充");
    }

    @Test
    void buildRagSystemPromptShouldRenderCitationContext() {
        SearchResult result = new SearchResult();
        result.setDocName("脚手架到 RAG.pdf");
        result.setContent("agent-knowledge 从脚手架生长出 RAG agent。");
        result.setFinalScore(0.92f);

        String prompt = promptBuilderService.buildRagSystemPrompt(List.of(result));

        assertThat(prompt)
                .contains("绝对禁止幻觉")
                .contains("## 知识库参考内容")
                .contains("【参考 1】来源：脚手架到 RAG.pdf | 相似度：0.92")
                .contains("agent-knowledge 从脚手架生长出 RAG agent。")
                .contains("来源必须写参考内容中的真实文档名");
    }
}
