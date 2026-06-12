package com.anjing.chat.model.response;

import com.anjing.chat.model.entity.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageResponseTest {

    @Test
    void shouldParseCitationEvidenceFieldsForFrontendReferenceCards() {
        Message message = new Message();
        message.setMessageId("msg_001");
        message.setConversationId("conv_001");
        message.setRole("assistant");
        message.setContent("回答内容");
        message.setReferences("""
                [
                  {
                    "chunkId": "chunk_001",
                    "docId": "doc_001",
                    "docName": "脚手架到 RAG.pdf",
                    "kbId": "kb_001",
                    "kbName": "RAG 教学库",
                    "content": "引用片段",
                    "finalScore": 0.92,
                    "metadata": {
                      "page_idx": [3, 4],
                      "content_type": "markdown",
                      "source_parser_result_ids": ["p1", "p2"]
                    }
                  }
                ]
                """);

        MessageResponse response = MessageResponse.fromEntity(message);

        assertThat(response.getReferences()).hasSize(1);
        MessageResponse.ReferenceInfo reference = response.getReferences().get(0);
        assertThat(reference.getChunkId()).isEqualTo("chunk_001");
        assertThat(reference.getDocId()).isEqualTo("doc_001");
        assertThat(reference.getDocName()).isEqualTo("脚手架到 RAG.pdf");
        assertThat(reference.getKbId()).isEqualTo("kb_001");
        assertThat(reference.getKbName()).isEqualTo("RAG 教学库");
        assertThat(reference.getContent()).isEqualTo("引用片段");
        assertThat(reference.getScore()).isEqualTo(0.92f);
        assertThat(reference.getMetadata())
                .containsEntry("content_type", "markdown")
                .containsEntry("source_parser_result_ids", List.of("p1", "p2"));
        assertThat(reference.getMetadata().get("page_idx")).isEqualTo(List.of(3, 4));
    }

    @Test
    void shouldUseSimilarityScoreWhenFinalScoreIsMissing() {
        Message message = new Message();
        message.setMessageId("msg_002");
        message.setReferences("""
                [
                  {
                    "chunkId": "chunk_002",
                    "docId": "doc_002",
                    "similarityScore": 0.81
                  }
                ]
                """);

        MessageResponse response = MessageResponse.fromEntity(message);

        assertThat(response.getReferences()).hasSize(1);
        assertThat(response.getReferences().get(0).getScore()).isEqualTo(0.81f);
    }

    @Test
    void shouldIgnoreInvalidReferenceJsonWithoutBreakingMessageResponse() {
        Message message = new Message();
        message.setMessageId("msg_003");
        message.setConversationId("conv_001");
        message.setRole("assistant");
        message.setContent("回答内容");
        message.setReferences("not-json");

        MessageResponse response = MessageResponse.fromEntity(message);

        assertThat(response.getMessageId()).isEqualTo("msg_003");
        assertThat(response.getConversationId()).isEqualTo("conv_001");
        assertThat(response.getRole()).isEqualTo("assistant");
        assertThat(response.getContent()).isEqualTo("回答内容");
        assertThat(response.getReferences()).isNull();
    }
}
