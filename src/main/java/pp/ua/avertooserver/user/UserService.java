package pp.ua.avertooserver.user;


import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pp.ua.avertooserver.common.exception.InvalidCredentialsException;
import pp.ua.avertooserver.common.exception.UserNotFoundException;
import pp.ua.avertooserver.security.JwtService;
import pp.ua.avertooserver.security.RefreshToken;
import pp.ua.avertooserver.security.RefreshTokenRepository;
import pp.ua.avertooserver.security.RefreshTokenService;
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

    public User createUser(CreateUserRequest request) {

        String passwordHash = passwordEncoder.encode(request.password());

        User user = User.builder()
                .passwordHash(passwordHash)
                .build();

        return userRepository.save(user);
    }


    public User getUserByUserId(Long userId){
        return userRepository.findByUserId(userId)
                .orElseThrow(
                        () -> new UserNotFoundException(userId)
                );
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUserId(request.userId())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(
                user.getUserId()
        );

        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken
        );
    }

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

    @Transactional
    public void logout(Long userId) {

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        refreshTokenRepository.deleteByUser(user);
    }

}
