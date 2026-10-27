package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.models.ApiKeys;
import com.example.demo.models.User;
import com.example.demo.repositeries.ApiKeyRepository;
import com.example.demo.repositeries.UserRepository;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTests {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @Test
    void generatedKeyIsReturnedOnceAndOnlyItsHashIsSaved() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(apiKeyRepository.findByUser_Id(42L)).thenReturn(Optional.empty());

        ApiKeyService.IssuedApiKey issued = apiKeyService.createOrRotate("user@example.com");

        ArgumentCaptor<ApiKeys> storedKey = ArgumentCaptor.forClass(ApiKeys.class);
        verify(apiKeyRepository).save(storedKey.capture());
        assertTrue(issued.secret().startsWith("np_"));
        assertNotEquals(issued.secret(), storedKey.getValue().getKeyHash());
        assertEquals(Sha256Hasher.hash(issued.secret()), storedKey.getValue().getKeyHash());
        assertEquals(issued.prefix(), storedKey.getValue().getKeyPrefix());
        assertEquals(ApiKeys.ACTIVE, storedKey.getValue().getKeyStatus());
        assertEquals("Default key", storedKey.getValue().getName());
        assertNull(storedKey.getValue().getLastUsedAt());
    }

    @Test
    void authenticatesApiKeyUsingItsStoredHash() {
        String rawKey = "np_test-key";
        ApiKeys storedKey = org.mockito.Mockito.mock(ApiKeys.class);
        when(apiKeyRepository.findUsableKey(
                eq(Sha256Hasher.hash(rawKey)),
                eq(ApiKeys.ACTIVE),
                any(Instant.class))).thenReturn(Optional.of(storedKey));

        assertEquals(storedKey, apiKeyService.authenticate(rawKey));
    }

    @Test
    void rotationIssuesANewSecretForTheOwnedKey() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));

        ApiKeys existing = new ApiKeys();
        existing.setKeyPrefix("np_oldprefix");
        existing.setKeyHash(Sha256Hasher.hash("np_old-secret"));
        existing.setKeyStatus(ApiKeys.REVOKED);
        when(apiKeyRepository.findByIdAndUser_Id(5L, 42L)).thenReturn(Optional.of(existing));

        ApiKeyService.IssuedApiKey issued = apiKeyService.rotate("user@example.com", 5L);

        assertTrue(issued.secret().startsWith("np_"));
        assertNotEquals("np_old-secret", issued.secret());
        assertEquals(Sha256Hasher.hash(issued.secret()), existing.getKeyHash());
        assertEquals(issued.prefix(), existing.getKeyPrefix());
        assertEquals(ApiKeys.ACTIVE, existing.getKeyStatus());
        verify(apiKeyRepository).save(existing);
    }

    @Test
    void rotationRefusesAKeyOwnedByAnotherAccount() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));
        when(apiKeyRepository.findByIdAndUser_Id(9L, 42L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> apiKeyService.rotate("user@example.com", 9L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void revokeMarksTheKeyWithoutDeletingIt() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(userRepository.findByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));

        ApiKeys existing = new ApiKeys();
        existing.setKeyStatus(ApiKeys.ACTIVE);
        when(apiKeyRepository.findByIdAndUser_Id(5L, 42L)).thenReturn(Optional.of(existing));

        apiKeyService.revoke("user@example.com", 5L);

        assertEquals(ApiKeys.REVOKED, existing.getKeyStatus());
        verify(apiKeyRepository).save(existing);
        verify(apiKeyRepository, never()).delete(any());
    }

    @Test
    void authenticateStampsLastUsedAtWhenItWasNeverRecorded() {
        ApiKeys storedKey = new ApiKeys();
        when(apiKeyRepository.findUsableKey(
                eq(Sha256Hasher.hash("np_fresh")),
                eq(ApiKeys.ACTIVE),
                any(Instant.class))).thenReturn(Optional.of(storedKey));

        apiKeyService.authenticate("np_fresh");

        assertNotNull(storedKey.getLastUsedAt());
        verify(apiKeyRepository).save(storedKey);
    }

    @Test
    void authenticateThrottlesLastUsedWrites() {
        ApiKeys storedKey = new ApiKeys();
        storedKey.setLastUsedAt(Instant.now().minusSeconds(5));
        when(apiKeyRepository.findUsableKey(
                eq(Sha256Hasher.hash("np_recent")),
                eq(ApiKeys.ACTIVE),
                any(Instant.class))).thenReturn(Optional.of(storedKey));

        apiKeyService.authenticate("np_recent");

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void rejectsBlankKeysWithoutHittingTheDatabase() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> apiKeyService.authenticate("  "));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(apiKeyRepository, never()).findUsableKey(any(), any(), any());
    }
}
