package com.spire.restbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.restbridge.dto.DqlRequest;
import com.spire.restbridge.exception.AggregateQueryNotSupportedException;
import com.spire.restbridge.exception.DqlNotAvailableException;
import com.spire.restbridge.model.QueryResult;
import com.spire.restbridge.service.DqlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DqlController.class)
class DqlControllerTest {

    private static final String SESSION_ID = "session-123";
    private static final String QUERY = "SELECT r_object_id, object_name FROM dm_document";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DqlService dqlService;

    private DqlRequest.DqlRequestBuilder request() {
        return DqlRequest.builder().sessionId(SESSION_ID).query(QUERY);
    }

    @Test
    void executeQuery_returnsResult() throws Exception {
        QueryResult result = QueryResult.builder()
                .columns(Collections.singletonList(QueryResult.ColumnInfo.builder()
                        .name("object_name").type("string").build()))
                .rows(Collections.singletonList(Collections.singletonMap("object_name", "test.txt")))
                .rowCount(1)
                .hasMore(false)
                .build();
        when(dqlService.executeQuery(any(DqlRequest.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request().maxRows(10).startRow(20).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.columns[0].name").value("object_name"))
                .andExpect(jsonPath("$.rows[0].object_name").value("test.txt"));

        ArgumentCaptor<DqlRequest> captor = ArgumentCaptor.forClass(DqlRequest.class);
        verify(dqlService).executeQuery(captor.capture());
        DqlRequest bound = captor.getValue();
        assertEquals(SESSION_ID, bound.getSessionId());
        assertEquals(QUERY, bound.getQuery());
        assertEquals(10, bound.getMaxRows());
        assertEquals(20, bound.getStartRow());
    }

    @Test
    void executeQuery_withoutMaxRowsOrStartRow_passesDefaults() throws Exception {
        when(dqlService.executeQuery(any(DqlRequest.class))).thenReturn(QueryResult.builder().build());

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + SESSION_ID + "\",\"query\":\"" + QUERY + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<DqlRequest> captor = ArgumentCaptor.forClass(DqlRequest.class);
        verify(dqlService).executeQuery(captor.capture());
        assertEquals(100, captor.getValue().getMaxRows());
        assertEquals(0, captor.getValue().getStartRow());
    }

    @Test
    void executeQuery_blankQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request().query(" ").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(dqlService);
    }

    @Test
    void executeQuery_zeroMaxRows_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request().maxRows(0).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(dqlService);
    }

    @Test
    void executeQuery_negativeStartRow_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request().startRow(-1).build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(dqlService);
    }

    @Test
    void executeQuery_dqlNotAvailable_returns503() throws Exception {
        when(dqlService.executeQuery(any(DqlRequest.class))).thenThrow(new DqlNotAvailableException());

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request().build())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DQL_NOT_AVAILABLE"));
    }

    @Test
    void executeQuery_aggregateQuery_returns400() throws Exception {
        when(dqlService.executeQuery(any(DqlRequest.class)))
                .thenThrow(new AggregateQueryNotSupportedException("SELECT COUNT(*) FROM dm_document"));

        mockMvc.perform(post("/api/v1/dql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request().query("SELECT COUNT(*) FROM dm_document").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AGGREGATE_QUERY_NOT_SUPPORTED"));
    }

    @Test
    void isDqlAvailable_available_returnsTrue() throws Exception {
        when(dqlService.isDqlAvailable(SESSION_ID)).thenReturn(true);

        mockMvc.perform(get("/api/v1/dql/available").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void isDqlAvailable_notAvailable_returnsFalse() throws Exception {
        when(dqlService.isDqlAvailable(SESSION_ID)).thenReturn(false);

        mockMvc.perform(get("/api/v1/dql/available").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
