package com.anjing.config.async;

import com.anjing.context.GlobalRequestContextHolder;
import com.anjing.model.request.GlobalRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestContextTaskDecoratorTest {

    private final RequestContextTaskDecorator taskDecorator = new RequestContextTaskDecorator();

    @AfterEach
    void tearDown() {
        GlobalRequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void decorateShouldPropagateRequestContextAndMdcThenRestorePreviousValues() {
        GlobalRequestContextHolder.set(GlobalRequestContext.builder()
                .requestId("rid-worker")
                .traceId("tid-worker")
                .tenantId("tenant-worker")
                .userId("user-worker")
                .build());
        MDC.put("requestId", "rid-worker");
        MDC.put("traceId", "tid-worker");

        Runnable decorated = taskDecorator.decorate(() -> {
            assertEquals("rid-worker", GlobalRequestContextHolder.requestIdOrEmpty());
            assertEquals("tid-worker", GlobalRequestContextHolder.traceIdOrEmpty());
            assertEquals("rid-worker", MDC.get("requestId"));
            assertEquals("tid-worker", MDC.get("traceId"));
        });

        GlobalRequestContextHolder.set(GlobalRequestContext.builder()
                .requestId("rid-main")
                .traceId("tid-main")
                .build());
        MDC.put("requestId", "rid-main");
        MDC.put("traceId", "tid-main");

        decorated.run();

        assertEquals("rid-main", GlobalRequestContextHolder.requestIdOrEmpty());
        assertEquals("tid-main", GlobalRequestContextHolder.traceIdOrEmpty());
        assertEquals("rid-main", MDC.get("requestId"));
        assertEquals("tid-main", MDC.get("traceId"));
    }
}
