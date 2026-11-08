package com.spire.restbridge.controller;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.model.AttributeValue;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.model.TypeInfo;
import com.spire.restbridge.service.ObjectService;
import com.spire.restbridge.service.TypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ObjectController.
 */
class ObjectControllerTest {

    @Mock
    private ObjectService objectService;

    @Mock
    private TypeService typeService;

    private ObjectController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ObjectController(objectService, typeService);
    }

    @Test
    void getObject_existingObject_returnsObject() {
        Map<String, AttributeValue> attrs = new HashMap<>();
        attrs.put("object_name", AttributeValue.builder()
                .type("string").value("test.txt").repeating(false).build());
        attrs.put("r_object_type", AttributeValue.builder()
                .type("string").value("dm_document").repeating(false).build());

        ObjectInfo object = ObjectInfo.builder()
            .objectId("0901234567890001")
            .name("test.txt")
            .type("dm_document")
            .attributes(attrs)
            .build();
        when(objectService.getObject("session-123", "0901234567890001")).thenReturn(object);

        ResponseEntity<ObjectInfo> response = controller.getObject("0901234567890001", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("0901234567890001", response.getBody().getObjectId());
        assertEquals("test.txt", response.getBody().getName());
        assertEquals("dm_document", response.getBody().getType());
        // Verify attribute format includes type metadata
        AttributeValue nameAttr = response.getBody().getAttributes().get("object_name");
        assertNotNull(nameAttr);
        assertEquals("string", nameAttr.getType());
        assertEquals("test.txt", nameAttr.getValue());
        assertFalse(nameAttr.isRepeating());
    }

    @Test
    void getObject_nonExistingObject_throwsException() {
        when(objectService.getObject("session-123", "nonexistent"))
            .thenThrow(new ObjectNotFoundException("nonexistent"));

        assertThrows(ObjectNotFoundException.class, () -> {
            controller.getObject("nonexistent", "session-123");
        });
    }

    @Test
    void getCabinets_returnsCabinetList() {
        List<ObjectInfo> cabinets = Arrays.asList(
            ObjectInfo.builder().objectId("0c01234567890001").name("System").type("dm_cabinet").build(),
            ObjectInfo.builder().objectId("0c01234567890002").name("Temp").type("dm_cabinet").build(),
            ObjectInfo.builder().objectId("0c01234567890003").name("Templates").type("dm_cabinet").build()
        );
        when(objectService.getCabinets("session-123")).thenReturn(cabinets);

        ResponseEntity<List<ObjectInfo>> response = controller.getCabinets("session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals("System", response.getBody().get(0).getName());
        assertEquals("dm_cabinet", response.getBody().get(0).getType());
    }

    @Test
    void getCabinets_empty_returnsEmptyList() {
        when(objectService.getCabinets("session-123")).thenReturn(Collections.emptyList());

        ResponseEntity<List<ObjectInfo>> response = controller.getCabinets("session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void listFolderContentsById_returnsContentsList() {
        List<ObjectInfo> contents = Arrays.asList(
            ObjectInfo.builder().objectId("0b01234567890001").name("subfolder1").type("dm_folder").build(),
            ObjectInfo.builder().objectId("0901234567890001").name("document.txt").type("dm_document").build(),
            ObjectInfo.builder().objectId("0901234567890002").name("image.png").type("dm_document").build()
        );
        when(objectService.listFolderContentsById("session-123", "0b01234567890000")).thenReturn(contents);

        ResponseEntity<List<ObjectInfo>> response = controller.listFolderContentsById("0b01234567890000", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        assertEquals("subfolder1", response.getBody().get(0).getName());
        assertEquals("dm_folder", response.getBody().get(0).getType());
    }

    @Test
    void listFolderContentsById_emptyFolder_returnsEmptyList() {
        when(objectService.listFolderContentsById("session-123", "0b01234567890000"))
            .thenReturn(Collections.emptyList());

        ResponseEntity<List<ObjectInfo>> response = controller.listFolderContentsById("0b01234567890000", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void listFolderContentsById_nonExistingFolder_throwsException() {
        when(objectService.listFolderContentsById("session-123", "nonexistent"))
            .thenThrow(new ObjectNotFoundException("Folder not found: nonexistent"));

        assertThrows(ObjectNotFoundException.class, () -> {
            controller.listFolderContentsById("nonexistent", "session-123");
        });
    }

    @Test
    void listTypes_returnsTypeList() {
        List<TypeInfo> types = Arrays.asList(
            TypeInfo.builder().name("dm_document").superType("dm_sysobject").build(),
            TypeInfo.builder().name("dm_folder").superType("dm_sysobject").build()
        );
        when(typeService.listTypes("session-123", null)).thenReturn(types);

        ResponseEntity<List<TypeInfo>> response = controller.listTypes("session-123", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void listTypes_withPattern_returnsFilteredList() {
        List<TypeInfo> types = Collections.singletonList(
            TypeInfo.builder().name("dm_document").superType("dm_sysobject").build()
        );
        when(typeService.listTypes("session-123", "dm_doc")).thenReturn(types);

        ResponseEntity<List<TypeInfo>> response = controller.listTypes("session-123", "dm_doc");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("dm_document", response.getBody().get(0).getName());
    }

    @Test
    void getTypeInfo_existingType_returnsTypeInfo() {
        TypeInfo type = TypeInfo.builder()
            .name("dm_document")
            .superType("dm_sysobject")
            .build();
        when(typeService.getTypeInfo("session-123", "dm_document")).thenReturn(type);

        ResponseEntity<TypeInfo> response = controller.getTypeInfo("dm_document", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("dm_document", response.getBody().getName());
        assertEquals("dm_sysobject", response.getBody().getSuperType());
    }
}
