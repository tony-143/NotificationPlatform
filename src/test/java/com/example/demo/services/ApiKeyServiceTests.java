package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
