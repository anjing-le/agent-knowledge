package com.anjing.demo.service;

import com.anjing.chat.service.RagChatOrchestrationService;
import com.anjing.demo.model.response.RagDemoSeedResponse;
import com.anjing.demo.model.response.RagEvidenceReportResponse;
import com.anjing.demo.model.response.RagRetrievalEvaluationResponse;
import com.anjing.knowledge.model.response.RagContextTrace;
import com.anjing.knowledge.model.response.RetrievalAdapterStatusResponse;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.service.RetrievalAdapterStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a copyable teaching report from runtime RAG demo evidence.
 */
@Service
@RequiredArgsConstructor
public class RagEvidenceReportService {

    private static final String READY = "Ready";
    private static final String PARTIAL = "Partial";
    private static final String EVIDENCE_REPORT_COMMAND =
            "curl -fsS -X POST http://localhost:10001/api/test/rag-demo/evidence-report";
    private static final String INGESTION_UPLOAD_API = "POST /api/knowledge/bases/{kbId}/documents";
    private static final String INGESTION_JAVA_BOUNDARY =
            "DocumentProcessingTask / DocumentProcessingProgressService / DocParserClient";
    private static final String INGESTION_PYTHON_BOUNDARY = "agent-doc-parser Python service /parse";
    private static final String INGESTION_PROBE_COMMAND = "./scripts/probe-rag-ingestion-runtime.sh";

    private final RagDemoSeedService ragDemoSeedService;
    private final RagRetrievalEvaluationService ragRetrievalEvaluationService;
    private final RetrievalAdapterStatusService adapterStatusService;
    private final RagChatOrchestrationService ragChatOrchestrationService;

    public RagEvidenceReportResponse buildTeachingEvidenceReport() {
        RagDemoSeedResponse demo = ragDemoSeedService.seedTeachingDemo();
        RagRetrievalEvaluationResponse evaluation = ragRetrievalEvaluationService.evaluateDemoRetrieval(demo);
        RetrievalAdapterStatusResponse adapterStatus = adapterStatusService.getStatus();
        RagEvidenceReportResponse.CitationEvidence citationEvidence = citationEvidence(demo);
        List<String> commands = evidenceCommands(demo, evaluation);
        RagEvidenceReportResponse.IngestionBoundary ingestionBoundary = ingestionBoundary();

        RagEvidenceReportResponse response = new RagEvidenceReportResponse();
        response.setStatus(Boolean.TRUE.equals(evaluation.getPassed()) ? READY : PARTIAL);
        response.setSummary(summary(demo, evaluation, commands));
        response.setDemo(demo);
        response.setEvaluation(evaluation);
        response.setAdapterStatus(adapterStatus);
        response.setStats(stats(demo, evaluation, commands));
        response.setScaffoldStack(scaffoldStack());
        response.setIngestionBoundary(ingestionBoundary);
        response.setCitationEvidence(citationEvidence);
        response.setEvidenceCommands(commands);
        response.setMarkdown(markdown(demo, evaluation, adapterStatus, ingestionBoundary, citationEvidence, commands));
        return response;
    }

    private String summary(
            RagDemoSeedResponse demo,
            RagRetrievalEvaluationResponse evaluation,
            List<String> commands
    ) {
        return String.format(
                "已由 Spring Boot 后端串联 %s、检索评估和 %d 条脚本证据，doc-parser 保持独立 Python 服务边界。",
                demo.getKbName(),
                commands.size()
        );
    }

    private List<RagEvidenceReportResponse.ReportStat> stats(
            RagDemoSeedResponse demo,
            RagRetrievalEvaluationResponse evaluation,
            List<String> commands
    ) {
        return List.of(
                stat("Scaffold Stack", "Vue + Spring + Python HTTP", "infra-dev-scaffolding 技术栈"),
                stat("Demo", "Seeded", demo.getKbName()),
                stat("Evaluation", recallAtK(evaluation), evaluation.getPassedCases() + "/" + evaluation.getTotalCases()
                        + " cases / " + evaluation.getSuiteName()),
                stat("Citation", demo.getChatQuestion(), "context trace and answer references"),
                stat("Evidence", String.valueOf(commands.size()), "commands ready to copy")
        );
    }

    private RagEvidenceReportResponse.ReportStat stat(String label, String value, String hint) {
        RagEvidenceReportResponse.ReportStat stat = new RagEvidenceReportResponse.ReportStat();
        stat.setLabel(label);
        stat.setValue(value);
        stat.setHint(hint);
        return stat;
    }

    private List<String> scaffoldStack() {
        return List.of(
                "Frontend: Vue 3 + TypeScript + Vite + Element Plus",
                "Backend: Spring Boot + Java 17 + OpenAPI contract",
                "Doc Parser: Python service over HTTP",
                "Contract: APIResponse / PageResult / ApiConstants / ApiPaths"
        );
    }

    private RagEvidenceReportResponse.IngestionBoundary ingestionBoundary() {
        RagEvidenceReportResponse.IngestionBoundary boundary = new RagEvidenceReportResponse.IngestionBoundary();
        boundary.setUploadApi(INGESTION_UPLOAD_API);
        boundary.setJavaBoundary(INGESTION_JAVA_BOUNDARY);
        boundary.setPythonBoundary(INGESTION_PYTHON_BOUNDARY);
        boundary.setParserContract("contracts/doc-parser-contract.json");
        boundary.setProbeCommand(INGESTION_PROBE_COMMAND);
        return boundary;
    }

    private List<String> evidenceCommands(
            RagDemoSeedResponse demo,
            RagRetrievalEvaluationResponse evaluation
    ) {
        Set<String> commands = new LinkedHashSet<>();
        commands.add(EVIDENCE_REPORT_COMMAND);
        if (demo.getEvidenceCommands() != null) {
            commands.addAll(demo.getEvidenceCommands());
        }
        if (evaluation.getEvidenceCommands() != null) {
            commands.addAll(evaluation.getEvidenceCommands());
        }
        return List.copyOf(commands);
    }

    private RagEvidenceReportResponse.CitationEvidence citationEvidence(RagDemoSeedResponse demo) {
        RagChatOrchestrationService.RagChatAnswer answer = ragChatOrchestrationService.generateAnswer(
                "rag_demo_evidence_report",
                demo.getChatQuestion(),
                List.of(demo.getKbId())
        );
        RagContextTrace trace = answer.contextTrace();

        RagEvidenceReportResponse.CitationEvidence evidence = new RagEvidenceReportResponse.CitationEvidence();
        evidence.setChatQuestion(demo.getChatQuestion());
        evidence.setAnswerPreview(answerPreview(answer.content()));
        evidence.setChatRoute(demo.getChatRoute());
        if (trace != null) {
            evidence.setAssemblyStrategy(trace.getAssemblyStrategy());
            evidence.setContextWindowPolicy(trace.getContextWindowPolicy());
            evidence.setReferenceCount(trace.getReferenceCount());
            evidence.setIncludedChunkCount(trace.getIncludedChunkCount());
            evidence.setPromptCharCount(trace.getPromptCharCount());
            evidence.setContextCharCount(trace.getContextCharCount());
            evidence.setPromptSections(trace.getPromptSections());
            evidence.setIncludedChunks(citationChunks(trace.getIncludedChunks()));
        }
        evidence.setReferences(citationReferences(answer.references()));
        return evidence;
    }

    private List<RagEvidenceReportResponse.CitationChunk> citationChunks(
            List<RagContextTrace.IncludedChunk> includedChunks
    ) {
        if (includedChunks == null || includedChunks.isEmpty()) {
            return List.of();
        }
        return includedChunks.stream().map(item -> {
            RagEvidenceReportResponse.CitationChunk chunk = new RagEvidenceReportResponse.CitationChunk();
            chunk.setRank(item.getRank());
            chunk.setChunkId(item.getChunkId());
            chunk.setDocId(item.getDocId());
            chunk.setDocName(item.getDocName());
            chunk.setKbId(item.getKbId());
            chunk.setKbName(item.getKbName());
            chunk.setRetrievalSource(item.getRetrievalSource());
            chunk.setFinalScore(item.getFinalScore());
            chunk.setContentChars(item.getContentChars());
            chunk.setScoreExplanation(item.getScoreExplanation());
            return chunk;
        }).toList();
    }

    private List<RagEvidenceReportResponse.CitationReference> citationReferences(List<SearchResult> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return references.stream().map(item -> {
            RagEvidenceReportResponse.CitationReference reference =
                    new RagEvidenceReportResponse.CitationReference();
            reference.setRank(item.getRank());
            reference.setChunkId(item.getChunkId());
            reference.setDocId(item.getDocId());
            reference.setDocName(item.getDocName());
            reference.setKbId(item.getKbId());
            reference.setKbName(item.getKbName());
            reference.setRetrievalSource(item.getRetrievalSource());
            reference.setSimilarityScore(item.getSimilarityScore());
            reference.setFinalScore(item.getFinalScore());
            reference.setKeywordScore(item.getKeywordScore());
            reference.setHybridScore(item.getHybridScore());
            reference.setRerankScore(item.getRerankScore());
            reference.setRerankProvider(item.getRerankProvider());
            reference.setScoreExplanation(item.getScoreExplanation());
            return reference;
        }).toList();
    }

    private String markdown(
            RagDemoSeedResponse demo,
            RagRetrievalEvaluationResponse evaluation,
            RetrievalAdapterStatusResponse adapterStatus,
            RagEvidenceReportResponse.IngestionBoundary ingestionBoundary,
            RagEvidenceReportResponse.CitationEvidence citationEvidence,
            List<String> commands
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# agent-knowledge RAG Demo Evidence");
        lines.add("");
        lines.add("## Scaffold Stack");
        scaffoldStack().forEach(item -> lines.add("- " + item));
        lines.add("");
        lines.add("## Demo Run");
        lines.add("- KB: " + valueOrDash(demo.getKbName()));
        lines.add("- Document: " + valueOrDash(demo.getDocName()));
        lines.add("- Vectors: " + demo.getVectorCount());
        lines.add("- Hits: " + demo.getSampleResultCount());
        lines.add("- Retrieval Query: " + valueOrDash(demo.getRetrievalQuery()));
        lines.add("");
        lines.add("## Retrieval Evaluation");
        lines.add("- Suite: " + valueOrDash(evaluation.getSuiteName()));
        lines.add("- Recall@K: " + recallAtK(evaluation));
        lines.add("- Cases: " + evaluation.getPassedCases() + "/" + evaluation.getTotalCases());
        lines.add("- Passed: " + (Boolean.TRUE.equals(evaluation.getPassed()) ? "yes" : "not-yet"));
        lines.add("");
        lines.add("## Runtime Adapter Status");
        lines.add(valueOrDash(adapterStatus.getSummary()));
        lines.add(adapterLines(adapterStatus));
        lines.add("");
        lines.add("## Ingestion Boundary");
        lines.add("- Upload API: " + ingestionBoundary.getUploadApi());
        lines.add("- Java: " + ingestionBoundary.getJavaBoundary());
        lines.add("- Python: " + ingestionBoundary.getPythonBoundary());
        lines.add("- Parser Contract: " + ingestionBoundary.getParserContract());
        lines.add("- Probe: " + ingestionBoundary.getProbeCommand());
        lines.add("");
        lines.add("## Citation Inspector");
        lines.add("- Chat Question: " + valueOrDash(citationEvidence.getChatQuestion()));
        lines.add("- Answer Preview: " + valueOrDash(citationEvidence.getAnswerPreview()));
        lines.add("- Strategy: " + valueOrDash(citationEvidence.getAssemblyStrategy()));
        lines.add("- Context Policy: " + valueOrDash(citationEvidence.getContextWindowPolicy()));
        lines.add("- References: " + intOrZero(citationEvidence.getReferenceCount()));
        lines.add("- Included Chunks: " + intOrZero(citationEvidence.getIncludedChunkCount()));
        lines.add("- Prompt Chars: " + intOrDash(citationEvidence.getPromptCharCount()));
        lines.add("- Chat Route: " + valueOrDash(citationEvidence.getChatRoute()));
        lines.add("");
        lines.add("### Prompt Sections");
        promptSectionLines(citationEvidence).forEach(lines::add);
        lines.add("");
        lines.add("### Context Chunks");
        citationChunkLines(citationEvidence).forEach(lines::add);
        lines.add("");
        lines.add("### Citation Cards");
        citationReferenceLines(citationEvidence).forEach(lines::add);
        lines.add("");
        lines.add("## Evidence Commands");
        commands.forEach(command -> lines.add("- `" + command + "`"));
        return String.join("\n", lines);
    }

    private String adapterLines(RetrievalAdapterStatusResponse adapterStatus) {
        if (adapterStatus.getAdapters() == null || adapterStatus.getAdapters().isEmpty()) {
            return "- adapter status: design-only";
        }
        return adapterStatus.getAdapters().stream()
                .map(item -> "- " + item.getAxis() + ": " + item.getCurrentProvider()
                        + " / " + item.getRuntimeStatus())
                .collect(Collectors.joining("\n"));
    }

    private String recallAtK(RagRetrievalEvaluationResponse evaluation) {
        return Math.round(evaluation.getRecallAtK() * 100) + "%";
    }

    private List<String> promptSectionLines(RagEvidenceReportResponse.CitationEvidence citationEvidence) {
        if (citationEvidence.getPromptSections() == null || citationEvidence.getPromptSections().isEmpty()) {
            return List.of("- none");
        }
        return citationEvidence.getPromptSections().stream()
                .map(section -> "- " + section)
                .toList();
    }

    private List<String> citationChunkLines(RagEvidenceReportResponse.CitationEvidence citationEvidence) {
        if (citationEvidence.getIncludedChunks() == null || citationEvidence.getIncludedChunks().isEmpty()) {
            return List.of("- none");
        }
        return citationEvidence.getIncludedChunks().stream()
                .map(chunk -> "- #" + intOrDash(chunk.getRank()) + " " + valueOrDash(chunk.getDocName())
                        + " / chunk=" + valueOrDash(chunk.getChunkId())
                        + " / source=" + valueOrDash(chunk.getRetrievalSource())
                        + " / final=" + floatOrDash(chunk.getFinalScore())
                        + "\n  - score: " + valueOrDash(chunk.getScoreExplanation()))
                .toList();
    }

    private List<String> citationReferenceLines(RagEvidenceReportResponse.CitationEvidence citationEvidence) {
        if (citationEvidence.getReferences() == null || citationEvidence.getReferences().isEmpty()) {
            return List.of("- none");
        }
        return citationEvidence.getReferences().stream()
                .map(reference -> "- #" + intOrDash(reference.getRank()) + " " + valueOrDash(reference.getDocName())
                        + " / chunk=" + valueOrDash(reference.getChunkId())
                        + " / source=" + valueOrDash(reference.getRetrievalSource())
                        + " / final=" + floatOrDash(reference.getFinalScore())
                        + "\n  - score: " + valueOrDash(reference.getScoreExplanation()))
                .toList();
    }

    private String answerPreview(String content) {
        if (content == null || content.isBlank()) {
            return "-";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180) + "...";
    }

    private String intOrZero(Integer value) {
        return String.valueOf(value == null ? 0 : value);
    }

    private String intOrDash(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String floatOrDash(Float value) {
        return value == null ? "-" : String.format("%.4f", value);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
