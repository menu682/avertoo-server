package pp.ua.avertooserver.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pp.ua.avertooserver.user.dto.*;

/**
 * REST controller responsible for user-related operations.
 *
 * <p>Handles user registration, authentication, token refresh,
 * password changes and logout.</p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Creates a new user account.
     *
     * <p>The client's IP address is extracted from the HTTP request
     * and passed to the service layer for registration rate limiting.</p>
     *
     * @param request registration data
     * @param httpRequest current HTTP request
     * @return created user's public identifier
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest
    ) {

        String clientIp = httpRequest.getRemoteAddr();

        User user = userService.createUser(
                request,
                clientIp
        );

        UserResponse response = new UserResponse(
                user.getUserId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Returns information about the currently authenticated user.
     *
     * @param authentication authenticated user's security context
     * @return current user's public identifier
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getUser(
            Authentication authentication
    ) {

        String username = authentication.getName();

        Long userId = Long.valueOf(username);

        User user = userService.getUserByUserId(userId);

        UserResponse response = new UserResponse(
                user.getUserId()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Authenticates a user and creates an access/refresh token pair.
     *
     * @param request login credentials
     * @return access and refresh tokens
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        LoginResponse response = userService.login(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Refreshes the access and refresh tokens using a valid refresh token.
     *
     * @param request refresh token request
     * @return new access and refresh tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {

        LoginResponse response = userService.refresh(
                request.refreshToken()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Changes the password of the currently authenticated user.
     *
     * @param request password change data
     * @param authentication authenticated user's security context
     * @return new access and refresh tokens
     */
    @PostMapping("/password")
    public ResponseEntity<LoginResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {

        Long userId = Long.valueOf(authentication.getName());

        LoginResponse response =
                userService.changePassword(userId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * Invalidates all refresh tokens belonging to the current user.
     *
     * @param authentication authenticated user's security context
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        userService.logout(userId);
    }
}