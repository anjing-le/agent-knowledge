package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.model.response.RagContextTrace;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the RAG system prompt from retrieval results and citation metadata.
 */
@Service
public class RagPromptBuilderService {

    private static final String ASSEMBLY_STRATEGY = "retrieval-context-to-system-prompt";
    private static final String CONTEXT_WINDOW_POLICY = "topK retrieval results, latest chat history window";
    private static final List<String> KNOWLEDGE_PROMPT_SECTIONS = List.of(
            "core_principles",
            "answer_format",
            "knowledge_context",
            "citation_reminder"
    );

    public String buildRagSystemPrompt(List<SearchResult> searchResults) {
        return buildRagContext(searchResults).systemPrompt();
    }

    public RagPromptContext buildRagContext(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            String prompt = "你是一个智能知识库助手。当前没有从知识库中检索到相关内容。请在回答开头加一行：⚠️ 知识库中未找到相关内容，以下为AI自身知识。然后用 **「💡 AI补充」** 标注你的回答内容。";
            return new RagPromptContext(prompt, noKnowledgeTrace(prompt));
        }

        StringBuilder context = new StringBuilder();
        int contextCharCount = 0;
        context.append("你是一个严谨的知识库问答助手。你必须严格遵守以下规则，违反任何一条都是不可接受的。\n\n");

        context.append("## 核心原则（最高优先级）\n");
        context.append("1. **绝对禁止幻觉**：你只能使用下方【知识库参考内容】中明确存在的信息来回答，不得编造、推测、脑补任何知识库中没有的内容\n");
        context.append("2. **忠于原文**：回答必须忠实于知识库原文的含义，可以重新组织语言，但不得改变原意、不得添加原文没有的细节\n");
        context.append("3. **宁缺毋滥**：如果知识库内容无法回答用户的问题，直接说【知识库中没有找到相关信息】，绝不要用自己的知识去补充\n");
        context.append("4. **相关性判断**：先判断下方参考内容是否与用户的问题真正相关。如果参考内容的主题和用户问题完全无关，即使有参考内容也要回答：❌ 知识库中的内容与您的问题不相关，未找到有效信息。不要强行用不相关的内容去回答\n\n");

        Set<String> docNames = new LinkedHashSet<>();
        for (SearchResult result : searchResults) {
            if (result.getDocName() != null) {
                docNames.add(result.getDocName());
            }
        }
        String docNameExample = docNames.isEmpty() ? "未知文档" : docNames.iterator().next();

        context.append("## 回答格式\n");
        context.append("1. 回答开头加一行：✅ 以下回答基于知识库检索结果\n");
        context.append("2. 每段回答末尾必须用括号标注实际的来源文档名，例如：（来源：").append(docNameExample).append("）\n");
        context.append("3. 注意：来源必须是下方参考内容中【来源】字段的真实文档名，禁止写占位符\n");
        context.append("4. 回答要清晰、有条理，适当使用列表和分段\n\n");

        context.append("## 知识库参考内容\n\n");

        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            String docName = result.getDocName() != null ? result.getDocName() : "未知文档";
            context.append(String.format("【参考 %d】来源：%s | 相似度：%.2f\n",
                    i + 1, docName, result.getFinalScore()));
            context.append("内容：");
            context.append(result.getContent());
            context.append("\n\n");
            contextCharCount += result.getContent() == null ? 0 : result.getContent().length();
        }

        context.append("---\n");
        context.append("再次提醒：只使用上面的参考内容回答。来源必须写参考内容中的真实文档名（如：")
                .append(docNameExample)
                .append("），不要写占位符。\n");

        String prompt = context.toString();
        return new RagPromptContext(prompt, buildTrace(searchResults, prompt, contextCharCount));
    }

    private RagContextTrace noKnowledgeTrace(String prompt) {
        RagContextTrace trace = new RagContextTrace();
        trace.setAssemblyStrategy(ASSEMBLY_STRATEGY);
        trace.setContextWindowPolicy(CONTEXT_WINDOW_POLICY);
        trace.setReferenceCount(0);
        trace.setIncludedChunkCount(0);
        trace.setHistoryMessageCount(0);
        trace.setPromptCharCount(prompt.length());
        trace.setContextCharCount(0);
        trace.setPromptSections(List.of("no_knowledge_fallback"));
        trace.setIncludedChunks(Collections.emptyList());
        return trace;
    }

    private RagContextTrace buildTrace(List<SearchResult> searchResults, String prompt, int contextCharCount) {
        RagContextTrace trace = new RagContextTrace();
        trace.setAssemblyStrategy(ASSEMBLY_STRATEGY);
        trace.setContextWindowPolicy(CONTEXT_WINDOW_POLICY);
        trace.setReferenceCount(searchResults.size());
        trace.setIncludedChunkCount(searchResults.size());
        trace.setHistoryMessageCount(0);
        trace.setPromptCharCount(prompt.length());
        trace.setContextCharCount(contextCharCount);
        trace.setPromptSections(KNOWLEDGE_PROMPT_SECTIONS);
        trace.setIncludedChunks(includedChunks(searchResults));
        return trace;
    }

    private List<RagContextTrace.IncludedChunk> includedChunks(List<SearchResult> searchResults) {
        List<RagContextTrace.IncludedChunk> chunks = new ArrayList<>();
        for (int index = 0; index < searchResults.size(); index++) {
            SearchResult result = searchResults.get(index);
            RagContextTrace.IncludedChunk chunk = new RagContextTrace.IncludedChunk();
            chunk.setRank(result.getRank() == null ? index + 1 : result.getRank());
            chunk.setChunkId(result.getChunkId());
            chunk.setDocId(result.getDocId());
            chunk.setDocName(result.getDocName());
            chunk.setKbId(result.getKbId());
            chunk.setKbName(result.getKbName());
            chunk.setRetrievalSource(result.getRetrievalSource());
            chunk.setFinalScore(result.getFinalScore());
            chunk.setContentChars(result.getContent() == null ? 0 : result.getContent().length());
            chunk.setScoreExplanation(result.getScoreExplanation());
            chunks.add(chunk);
        }
        return chunks;
    }

    public record RagPromptContext(String systemPrompt, RagContextTrace trace) {
    }
}
