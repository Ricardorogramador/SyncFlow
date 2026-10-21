package com.example.pasaas2.service;

import com.example.pasaas2.config.JwtService;
import com.example.pasaas2.dto.AuthRequest;
import com.example.pasaas2.dto.AuthResponse;
import com.example.pasaas2.dto.RegisterRequest;
import com.example.pasaas2.model.User;
import com.example.pasaas2.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserSuccessfully() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Ricardo")
                .email("ricardo@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.getPassword()))
                .thenReturn("encoded-password");

        when(jwtService.generateToken(any(User.class)))
                .thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("Ricardo", savedUser.getName());
        assertEquals("ricardo@example.com", savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPasswordHash());
        assertEquals("INACTIVE", savedUser.getSubscriptionStatus());
        assertEquals(0, savedUser.getPausedDaysRemaining());

        verify(jwtService).generateToken(any(User.class));
    }

    @Test
    void shouldRejectRegistrationWhenEmailAlreadyExists() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Ricardo")
                .email("ricardo@example.com")
                .password("password123")
                .build();

        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(true);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> authService.register(request)
        );

        assertEquals("El email ya está registrado", exception.getMessage());

        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void shouldLoginUserSuccessfully() {
        AuthRequest request = AuthRequest.builder()
                .email("ricardo@example.com")
                .password("password123")
                .build();

        User user = User.builder()
                .email("ricardo@example.com")
                .name("Ricardo")
                .passwordHash("encoded-password")
                .build();

        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtService.generateToken(user))
                .thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());

        verify(authenticationManager).authenticate(any());
        verify(userRepository).findByEmail(request.getEmail());
        verify(jwtService).generateToken(user);
    }

    @Test
    void shouldFailLoginWhenUserDoesNotExist() {
        AuthRequest request = AuthRequest.builder()
                .email("unknown@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> authService.login(request)
        );

        assertEquals("Usuario no encontrado", exception.getMessage());

        verify(jwtService, never()).generateToken(any(User.class));
    }
}