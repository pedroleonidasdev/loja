package com.pdvgenerico.dto;

import jakarta.validation.constraints.Size;

/** Emoji da reação; vazio ou nulo remove a reação. */
public record ReacaoRequest(@Size(max = 16) String emoji) {
}
