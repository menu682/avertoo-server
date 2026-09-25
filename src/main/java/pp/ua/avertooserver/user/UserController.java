package pp.ua.avertooserver.user;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pp.ua.avertooserver.user.dto.ChangePasswordRequest;
import pp.ua.avertooserver.user.dto.CreateUserRequest;
import pp.ua.avertooserver.user.dto.LoginRequest;
import pp.ua.avertooserver.user.dto.LoginResponse;
import pp.ua.avertooserver.user.dto.RefreshTokenRequest;
import pp.ua.avertooserver.user.dto.UserResponse;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
            ){

        User user = userService.createUser(request);

        UserResponse response = new UserResponse(
                user.getUserId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getUser(
            Authentication authentication
    ) {

        String username = authentication.getName();

        Long userId = Long.valueOf(username);

        User user = userService.getUserByUserId(userId);

        UserResponse response = new UserResponse(user.getUserId());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = userService.login(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        LoginResponse response = userService.refresh(
                request.refreshToken()
        );

        return ResponseEntity.ok(response);
    }

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


    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        userService.logout(userId);
    }

}
