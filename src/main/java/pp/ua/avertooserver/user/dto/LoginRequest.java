package pp.ua.avertooserver.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotNull
        @Min(1_000_000_000L)
        Long userId,

        @Size(min = 8, max = 128)
        String password
) {
}
