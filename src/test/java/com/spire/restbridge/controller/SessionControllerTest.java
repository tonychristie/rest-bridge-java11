package com.spire.restbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.restbridge.dto.ConnectRequest;
import com.spire.restbridge.dto.ConnectResponse;
import com.spire.restbridge.dto.DisconnectRequest;
import com.spire.restbridge.exception.ConnectionException;
import com.spire.restbridge.exception.SessionNotFoundException;
import com.spire.restbridge.model.RepositoryInfo;
import com.spire.restbridge.model.SessionInfo;
import com.spire.restbridge.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    private static final String SESSION_ID = "rest-session-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionService sessionService;

    private Map<String, String> connectBody() {
        Map<String, String> body = new HashMap<>();
        body.put("endpoint", "https://documentum.example.com/dctm-rest");
        body.put("repository", "TestRepo");
        body.put("username", "test01");
        body.put("password", "secret");
        return body;
    }

    @Test
    void connect_bindsBodyAndReturnsSession() throws Exception {
        ConnectResponse response = ConnectResponse.builder()
                .sessionId(SESSION_ID)
                .repositoryInfo(RepositoryInfo.builder()
                        .name("TestRepo")
                        .endpoint("https://documentum.example.com/dctm-rest")
                        .serverVersion("23.2")
                        .build())
                .build();
        when(sessionService.connect(any(ConnectRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connectBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.repositoryInfo.name").value("TestRepo"))
                .andExpect(jsonPath("$.repositoryInfo.serverVersion").value("23.2"));

        ArgumentCaptor<ConnectRequest> captor = ArgumentCaptor.forClass(ConnectRequest.class);
        verify(sessionService).connect(captor.capture());
        ConnectRequest bound = captor.getValue();
        assertEquals("https://documentum.example.com/dctm-rest", bound.getEndpoint());
        assertEquals("TestRepo", bound.getRepository());
        assertEquals("test01", bound.getUsername());
        assertEquals("secret", bound.getPassword());
    }

    @ParameterizedTest
    @ValueSource(strings = {"endpoint", "repository", "username", "password"})
    void connect_missingField_returns400(String field) throws Exception {
        Map<String, String> body = connectBody();
        body.remove(field);

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(sessionService);
    }

    @Test
    void connect_missingField_acceptXml_returns400Json() throws Exception {
        Map<String, String> body = connectBody();
        body.remove("password");

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void connect_badCredentials_returns401() throws Exception {
        when(sessionService.connect(any(ConnectRequest.class)))
                .thenThrow(new ConnectionException("Authentication failed", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connectBody())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CONNECTION_ERROR"));
    }

    @Test
    void disconnect_passesSessionIdAndReturns204() throws Exception {
        DisconnectRequest request = DisconnectRequest.builder().sessionId(SESSION_ID).build();

        mockMvc.perform(post("/api/v1/disconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(sessionService).disconnect(SESSION_ID);
    }

    @Test
    void disconnect_missingSessionId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/disconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(sessionService);
    }

    @Test
    void getSessionInfo_returnsSessionInfo() throws Exception {
        SessionInfo info = SessionInfo.builder()
                .sessionId(SESSION_ID)
                .repository("TestRepo")
                .user("test01")
                .endpoint("https://documentum.example.com/dctm-rest")
                .build();
        when(sessionService.getSessionInfo(SESSION_ID)).thenReturn(info);

        mockMvc.perform(get("/api/v1/session/" + SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID))
                .andExpect(jsonPath("$.repository").value("TestRepo"))
                .andExpect(jsonPath("$.user").value("test01"));
    }

    @Test
    void getSessionInfo_unknownSession_returns404() throws Exception {
        when(sessionService.getSessionInfo("unknown")).thenThrow(new SessionNotFoundException("unknown"));

        mockMvc.perform(get("/api/v1/session/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void isSessionValid_validSession_returnsTrue() throws Exception {
        when(sessionService.isSessionValid(SESSION_ID)).thenReturn(true);

        mockMvc.perform(get("/api/v1/session/" + SESSION_ID + "/valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void isSessionValid_invalidSession_returnsFalse() throws Exception {
        when(sessionService.isSessionValid("expired")).thenReturn(false);

        mockMvc.perform(get("/api/v1/session/expired/valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
