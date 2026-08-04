package com.wong.collector.application.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.wong.collector.application.workflow.rag.DocumentContextRetriever;
import com.wong.collector.application.workflow.rag.DocumentContextRetriever.ChunkHit;
import com.wong.collector.infrastructure.config.properties.CustomRagProperties;

class CustomRagServiceTest {

    @Test
    void abstainsWithoutCallingTheChatModelWhenRetrievalIsEmpty() {
        DocumentContextRetriever retriever = mock(DocumentContextRetriever.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(builder.build()).thenReturn(chatClient);
        when(retriever.topK("doc-1", "question", 8)).thenReturn(List.of());

        CustomRagService service = new CustomRagService(
            retriever, new CustomRagProperties(), builder);
        CustomRagService.QueryResult result = service.query("doc-1", "question", null);

        assertTrue(result.abstained());
        assertEquals("NO_RELEVANT_EVIDENCE", result.abstainReason());
        assertTrue(result.evidence().isEmpty());
        verifyNoInteractions(chatClient);
    }

    @Test
    void boundsTheContextAndReturnsOnlyTheTextSentToTheModel() {
        String content = "x".repeat(500);

        CustomRagService.ContextWindow context = CustomRagService.buildContextWindow(
            List.of(new ChunkHit("42", content)), 256);

        assertTrue(context.promptContext().length() <= 256);
        assertEquals(1, context.evidence().size());
        assertEquals(context.evidence().get(0).content().length() + "[ChunkId-42]\n".length(),
            context.promptContext().length());
        assertTrue(context.evidence().get(0).content().length() < content.length());
    }
}
