package com.spire.restbridge.dto;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DqlRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void builder_defaultsToHundredRowsFromRowZero() {
        DqlRequest request = DqlRequest.builder().sessionId("session-1").query("SELECT 1").build();

        assertEquals(100, request.getMaxRows());
        assertEquals(0, request.getStartRow());
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void noArgsConstructor_defaultsToHundredRowsFromRowZero() {
        DqlRequest request = new DqlRequest();

        assertEquals(100, request.getMaxRows());
        assertEquals(0, request.getStartRow());
    }

    @Test
    void zeroMaxRows_failsValidation() {
        DqlRequest request = DqlRequest.builder().sessionId("session-1").query("SELECT 1").maxRows(0).build();

        assertEquals(1, validator.validate(request).size());
    }

    @Test
    void negativeStartRow_failsValidation() {
        DqlRequest request = DqlRequest.builder().sessionId("session-1").query("SELECT 1").startRow(-1).build();

        assertEquals(1, validator.validate(request).size());
    }
}
