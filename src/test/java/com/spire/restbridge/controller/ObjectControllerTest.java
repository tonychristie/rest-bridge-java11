package com.spire.restbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.restbridge.dto.CreateObjectRequest;
import com.spire.restbridge.dto.UpdateObjectRequest;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.AttributeValue;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.model.TypeInfo;
import com.spire.restbridge.service.ObjectService;
import com.spire.restbridge.service.TypeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ObjectController.class)
class ObjectControllerTest {

    private static final String SESSION_ID = "session-123";
    private static final String OBJECT_ID = "0901234567890001";
    private static final String FOLDER_ID = "0b01234567890000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ObjectService objectService;

    @MockBean
    private TypeService typeService;

    @Test
    void getObject_existingObject_returnsObject() throws Exception {
        Map<String, AttributeValue> attrs = new HashMap<>();
        attrs.put("object_name", AttributeValue.builder()
                .type("string").value("test.txt").repeating(false).build());
        attrs.put("keywords", AttributeValue.builder()
                .type("string").value(Arrays.asList("a", "b")).repeating(true).build());
        ObjectInfo object = ObjectInfo.builder()
                .objectId(OBJECT_ID)
                .name("test.txt")
                .type("dm_document")
                .attributes(attrs)
                .build();
        when(objectService.getObject(SESSION_ID, OBJECT_ID)).thenReturn(object);

        mockMvc.perform(get("/api/v1/objects/" + OBJECT_ID).param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(OBJECT_ID))
                .andExpect(jsonPath("$.name").value("test.txt"))
                .andExpect(jsonPath("$.type").value("dm_document"))
                .andExpect(jsonPath("$.attributes.object_name.type").value("string"))
                .andExpect(jsonPath("$.attributes.object_name.value").value("test.txt"))
                .andExpect(jsonPath("$.attributes.object_name.repeating").value(false))
                .andExpect(jsonPath("$.attributes.keywords.value[1]").value("b"))
                .andExpect(jsonPath("$.attributes.keywords.repeating").value(true));

        verify(objectService).getObject(SESSION_ID, OBJECT_ID);
    }

    @Test
    void getObject_nonExistingObject_returns404() throws Exception {
        when(objectService.getObject(SESSION_ID, "nonexistent"))
                .thenThrow(new ObjectNotFoundException("Object not found: nonexistent"));

        mockMvc.perform(get("/api/v1/objects/nonexistent").param("sessionId", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
    }

    @Test
    void getCabinets_returnsCabinetList() throws Exception {
        List<ObjectInfo> cabinets = Arrays.asList(
                ObjectInfo.builder().objectId("0c01234567890001").name("System").type("dm_cabinet").build(),
                ObjectInfo.builder().objectId("0c01234567890002").name("Temp").type("dm_cabinet").build(),
                ObjectInfo.builder().objectId("0c01234567890003").name("Templates").type("dm_cabinet").build()
        );
        when(objectService.getCabinets(SESSION_ID)).thenReturn(cabinets);

        mockMvc.perform(get("/api/v1/cabinets").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("System"))
                .andExpect(jsonPath("$[0].type").value("dm_cabinet"));
    }

    @Test
    void getCabinets_empty_returnsEmptyList() throws Exception {
        when(objectService.getCabinets(SESSION_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/cabinets").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listFolderContentsById_returnsContentsList() throws Exception {
        List<ObjectInfo> contents = Arrays.asList(
                ObjectInfo.builder().objectId("0b01234567890001").name("subfolder1").type("dm_folder").build(),
                ObjectInfo.builder().objectId("0901234567890001").name("document.txt").type("dm_document").build(),
                ObjectInfo.builder().objectId("0901234567890002").name("image.png").type("dm_document").build()
        );
        when(objectService.listFolderContentsById(SESSION_ID, FOLDER_ID)).thenReturn(contents);

        mockMvc.perform(get("/api/v1/objects/" + FOLDER_ID + "/contents").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("subfolder1"))
                .andExpect(jsonPath("$[0].type").value("dm_folder"));
    }

    @Test
    void listFolderContentsById_emptyFolder_returnsEmptyList() throws Exception {
        when(objectService.listFolderContentsById(SESSION_ID, FOLDER_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/objects/" + FOLDER_ID + "/contents").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listFolderContentsById_nonExistingFolder_returns404() throws Exception {
        when(objectService.listFolderContentsById(SESSION_ID, "nonexistent"))
                .thenThrow(new ObjectNotFoundException("Folder not found: nonexistent"));

        mockMvc.perform(get("/api/v1/objects/nonexistent/contents").param("sessionId", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
    }

    @Test
    void listTypes_withoutPattern_passesNullPattern() throws Exception {
        List<TypeInfo> types = Arrays.asList(
                TypeInfo.builder().name("dm_document").superType("dm_sysobject").build(),
                TypeInfo.builder().name("dm_folder").superType("dm_sysobject").build()
        );
        when(typeService.listTypes(SESSION_ID, null)).thenReturn(types);

        mockMvc.perform(get("/api/v1/types").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("dm_document"));

        verify(typeService).listTypes(SESSION_ID, null);
    }

    @Test
    void listTypes_withPattern_passesPattern() throws Exception {
        List<TypeInfo> types = Collections.singletonList(
                TypeInfo.builder().name("dm_document").superType("dm_sysobject").build()
        );
        when(typeService.listTypes(SESSION_ID, "dm_doc*")).thenReturn(types);

        mockMvc.perform(get("/api/v1/types").param("sessionId", SESSION_ID).param("pattern", "dm_doc*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("dm_document"));

        verify(typeService).listTypes(SESSION_ID, "dm_doc*");
    }

    @Test
    void getTypeInfo_existingType_returnsTypeInfo() throws Exception {
        TypeInfo type = TypeInfo.builder()
                .name("dm_document")
                .superType("dm_sysobject")
                .build();
        when(typeService.getTypeInfo(SESSION_ID, "dm_document")).thenReturn(type);

        mockMvc.perform(get("/api/v1/types/dm_document").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dm_document"))
                .andExpect(jsonPath("$.superType").value("dm_sysobject"));
    }

    @Test
    void updateObject_bindsBodyAndReturnsObject() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("object_name", "renamed.txt");
        attributes.put("title", "New title");
        UpdateObjectRequest request = UpdateObjectRequest.builder()
                .sessionId(SESSION_ID)
                .attributes(attributes)
                .build();
        when(objectService.updateObject(eq(SESSION_ID), eq(OBJECT_ID), any(UpdateObjectRequest.class)))
                .thenReturn(ObjectInfo.builder().objectId(OBJECT_ID).name("renamed.txt").build());

        mockMvc.perform(post("/api/v1/objects/" + OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(OBJECT_ID))
                .andExpect(jsonPath("$.name").value("renamed.txt"));

        ArgumentCaptor<UpdateObjectRequest> captor = ArgumentCaptor.forClass(UpdateObjectRequest.class);
        verify(objectService).updateObject(eq(SESSION_ID), eq(OBJECT_ID), captor.capture());
        assertEquals(attributes, captor.getValue().getAttributes());
    }

    @Test
    void updateObject_emptyAttributes_returns400() throws Exception {
        UpdateObjectRequest request = UpdateObjectRequest.builder()
                .sessionId(SESSION_ID)
                .attributes(Collections.emptyMap())
                .build();

        mockMvc.perform(post("/api/v1/objects/" + OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(objectService);
    }

    @Test
    void updateObject_missingSessionId_returns400() throws Exception {
        UpdateObjectRequest request = UpdateObjectRequest.builder()
                .attributes(Collections.singletonMap("title", "New title"))
                .build();

        mockMvc.perform(post("/api/v1/objects/" + OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(objectService);
    }

    @Test
    void updateObject_upstreamForbidden_returns403() throws Exception {
        UpdateObjectRequest request = UpdateObjectRequest.builder()
                .sessionId(SESSION_ID)
                .attributes(Collections.singletonMap("title", "New title"))
                .build();
        when(objectService.updateObject(eq(SESSION_ID), eq(OBJECT_ID), any(UpdateObjectRequest.class)))
                .thenThrow(new RestBridgeException("REST_ERROR", "Update failed", HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/v1/objects/" + OBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REST_ERROR"));
    }

    @Test
    void checkout_returnsObject() throws Exception {
        when(objectService.checkout(SESSION_ID, OBJECT_ID))
                .thenReturn(ObjectInfo.builder().objectId(OBJECT_ID).name("test.txt").build());

        mockMvc.perform(put("/api/v1/objects/" + OBJECT_ID + "/lock").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value(OBJECT_ID))
                .andExpect(jsonPath("$.name").value("test.txt"));

        verify(objectService).checkout(SESSION_ID, OBJECT_ID);
    }

    @Test
    void cancelCheckout_returns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/api/v1/objects/" + OBJECT_ID + "/lock").param("sessionId", SESSION_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(objectService).cancelCheckout(SESSION_ID, OBJECT_ID);
    }

    @Test
    void checkin_withoutVersionLabel_passesCurrent() throws Exception {
        when(objectService.checkin(SESSION_ID, OBJECT_ID, "CURRENT"))
                .thenReturn(ObjectInfo.builder().objectId("0901234567890002").build());

        mockMvc.perform(post("/api/v1/objects/" + OBJECT_ID + "/versions").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("0901234567890002"));

        verify(objectService).checkin(SESSION_ID, OBJECT_ID, "CURRENT");
    }

    @Test
    void checkin_withVersionLabel_passesLabel() throws Exception {
        when(objectService.checkin(SESSION_ID, OBJECT_ID, "RELEASED"))
                .thenReturn(ObjectInfo.builder().objectId("0901234567890002").build());

        mockMvc.perform(post("/api/v1/objects/" + OBJECT_ID + "/versions")
                        .param("sessionId", SESSION_ID)
                        .param("versionLabel", "RELEASED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("0901234567890002"));

        verify(objectService).checkin(SESSION_ID, OBJECT_ID, "RELEASED");
    }

    @Test
    void createObject_bindsBodyAndReturns201() throws Exception {
        CreateObjectRequest request = CreateObjectRequest.builder()
                .sessionId(SESSION_ID)
                .objectType("dm_document")
                .objectName("new.txt")
                .folderPath("/Temp/Reports")
                .attributes(Collections.singletonMap("title", "New document"))
                .build();
        when(objectService.createObject(eq(SESSION_ID), any(CreateObjectRequest.class)))
                .thenReturn(ObjectInfo.builder().objectId(OBJECT_ID).name("new.txt").type("dm_document").build());

        mockMvc.perform(post("/api/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectId").value(OBJECT_ID))
                .andExpect(jsonPath("$.name").value("new.txt"));

        ArgumentCaptor<CreateObjectRequest> captor = ArgumentCaptor.forClass(CreateObjectRequest.class);
        verify(objectService).createObject(eq(SESSION_ID), captor.capture());
        CreateObjectRequest bound = captor.getValue();
        assertEquals("dm_document", bound.getObjectType());
        assertEquals("new.txt", bound.getObjectName());
        assertEquals("/Temp/Reports", bound.getFolderPath());
        assertEquals(Collections.singletonMap("title", "New document"), bound.getAttributes());
    }

    @Test
    void createObject_missingObjectType_returns400() throws Exception {
        CreateObjectRequest request = CreateObjectRequest.builder()
                .sessionId(SESSION_ID)
                .objectName("new.txt")
                .build();

        mockMvc.perform(post("/api/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(objectService);
    }

    @Test
    void createObject_missingSessionId_returns400() throws Exception {
        CreateObjectRequest request = CreateObjectRequest.builder()
                .objectType("dm_document")
                .objectName("new.txt")
                .build();

        mockMvc.perform(post("/api/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(objectService);
    }

    @Test
    void deleteObject_withoutAllVersions_passesFalse() throws Exception {
        mockMvc.perform(delete("/api/v1/objects/" + OBJECT_ID).param("sessionId", SESSION_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(objectService).deleteObject(SESSION_ID, OBJECT_ID, false);
    }

    @Test
    void deleteObject_allVersionsTrue_passesTrue() throws Exception {
        mockMvc.perform(delete("/api/v1/objects/" + OBJECT_ID)
                        .param("sessionId", SESSION_ID)
                        .param("allVersions", "true"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(objectService).deleteObject(SESSION_ID, OBJECT_ID, true);
    }
}
