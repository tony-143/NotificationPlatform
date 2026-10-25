package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NotificationRequestValidatorTests {

    private final NotificationRequestValidator validator = new NotificationRequestValidator();

    @Test
    void normalizesSupportedTypeAndRecipient() {
        NotificationRequestValidator.ValidatedRequest result = validator.validate(
                new NotificationService.NotificationRequest(" email ", " person@example.test ", "hello", null));

        assertEquals("EMAIL", result.type());
        assertEquals("person@example.test", result.recipient());
    }

    @Test
    void rejectsUnsupportedTypesAndMissingFields() {
        ResponseStatusException unsupportedType = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(
                        new NotificationService.NotificationRequest("PUSH", "recipient", "message", null)));
        ResponseStatusException missingMessage = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(
                        new NotificationService.NotificationRequest("EMAIL", "recipient", " ", null)));

        assertEquals(400, unsupportedType.getStatusCode().value());
        assertEquals(400, missingMessage.getStatusCode().value());
    }
}
