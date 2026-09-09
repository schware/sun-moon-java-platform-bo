package com.sunmoon.bo.domain.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content) {
}
