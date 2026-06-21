package br.com.gems.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Envelope padrão de resposta de sucesso das APIs GEMS.
 * <p>
 * Padroniza o corpo das respostas bem-sucedidas, complementando o
 * {@code ExceptionResponseDTO} (do módulo {@code gems-exception}) usado nas falhas.
 * </p>
 *
 * @param <T> tipo do dado retornado.
 */
@Getter
@Builder
@AllArgsConstructor
public class ApiResponseDTO<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    /**
     * Cria uma resposta de sucesso apenas com os dados.
     */
    public static <T> ApiResponseDTO<T> ok(T data) {
        return ApiResponseDTO.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Cria uma resposta de sucesso com dados e uma mensagem descritiva.
     */
    public static <T> ApiResponseDTO<T> ok(T data, String message) {
        return ApiResponseDTO.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
