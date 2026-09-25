package pp.ua.avertooserver.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pp.ua.avertooserver.user.UserRepository;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        Long userId = Long.valueOf(username);

        return userRepository.findByUserId(userId)
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(String.valueOf(user.getUserId()))
                        .password(user.getPasswordHash())
                        .authorities("USER")
                        .build()
                )
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found")
                );
    }
}
