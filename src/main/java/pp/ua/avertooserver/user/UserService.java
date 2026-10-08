package pp.ua.avertooserver.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pp.ua.avertooserver.common.exception.*;
import pp.ua.avertooserver.security.JwtService;
import pp.ua.avertooserver.security.RefreshToken;
import pp.ua.avertooserver.security.RefreshTokenRepository;
import pp.ua.avertooserver.security.RefreshTokenService;
import pp.ua.avertooserver.security.captcha.CaptchaService;
import pp.ua.avertooserver.security.ratelimit.LoginRateLimiter;
import pp.ua.avertooserver.security.ratelimit.RegistrationRateLimiter;
import pp.ua.avertooserver.user.dto.ChangePasswordRequest;
import pp.ua.avertooserver.user.dto.CreateUserRequest;
import pp.ua.avertooserver.user.dto.LoginRequest;
import pp.ua.avertooserver.user.dto.LoginResponse;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CaptchaService captchaService;
    private final RegistrationRateLimiter registrationRateLimiter;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * Creates a new user after successful CAPTCHA verification
     * and registration rate-limit validation.
     *
     * <p>The CAPTCHA token is verified first. Only after successful
     * CAPTCHA verification is the registration rate limit checked.</p>
     *
     * @param request registration data
     * @param clientIp IP address of the client making the request
     * @return newly created user
     * @throws CaptchaVerificationException when CAPTCHA verification fails
     */
    public User createUser(
            CreateUserRequest request,
            String clientIp
    ) {

        if (!captchaService.verify(request.captchaToken())) {
            throw new CaptchaVerificationException();
        }

        if (!registrationRateLimiter.isAllowed(clientIp)) {
            throw new RegistrationRateLimitExceededException();
        }

        String passwordHash =
                passwordEncoder.encode(request.password());

        User user = User.builder()
                .passwordHash(passwordHash)
                .build();

        return userRepository.save(user);
    }

    /**
     * Finds a user by their public user ID.
     *
     * @param userId user identifier
     * @return found user
     * @throws UserNotFoundException when the user does not exist
     */
    public User getUserByUserId(Long userId) {

        return userRepository.findByUserId(userId)
                .orElseThrow(
                        () -> new UserNotFoundException(userId)
                );
    }

    /**
     * Authenticates a user and creates a new access/refresh token pair.
     *
     * <p>The login rate limit is checked before password verification.
     * Failed password verification attempts are recorded in Redis.
     * Successful authentication does not increase the counter.</p>
     *
     * @param request login credentials
     * @return access and refresh tokens
     * @throws InvalidCredentialsException when credentials are invalid
     * @throws LoginRateLimitExceededException when the login limit is exceeded
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUserId(request.userId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!loginRateLimiter.isAllowed(user.getUserId())) {
            throw new LoginRateLimitExceededException();
        }

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            loginRateLimiter.recordFailedAttempt(
                    user.getUserId()
            );

            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(
                user.getUserId()
        );

        String refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken
        );
    }

    /**
     * Validates a refresh token and creates a new token pair.
     *
     * @param refreshToken current refresh token
     * @return new access and refresh tokens
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {

        RefreshToken currentToken =
                refreshTokenService.validateRefreshToken(refreshToken);

        User user = currentToken.getUser();

        String newRefreshToken =
                refreshTokenService.createRefreshToken(user);

        String accessToken =
                jwtService.generateAccessToken(user.getUserId());

        return new LoginResponse(
                accessToken,
                newRefreshToken
        );
    }

    /**
     * Changes the user's password after verifying the current password.
     *
     * <p>A new token pair is issued after a successful password change.</p>
     *
     * @param userId user identifier
     * @param request password change data
     * @return new access and refresh tokens
     */
    @Transactional
    public LoginResponse changePassword(
            Long userId,
            ChangePasswordRequest request
    ) {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        String newPasswordHash =
                passwordEncoder.encode(request.newPassword());

        user.changePassword(newPasswordHash);

        String accessToken =
                jwtService.generateAccessToken(user.getUserId());

        String refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken
        );
    }

    /**
     * Invalidates all refresh tokens belonging to the user.
     *
     * @param userId user identifier
     * @throws UserNotFoundException when the user does not exist
     */
    @Transactional
    public void logout(Long userId) {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(
                        () -> new UserNotFoundException(userId)
                );

        refreshTokenRepository.deleteByUser(user);
    }
}