package br.com.gems.aws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que representa a resposta padronizada para operações de criação
 * de URLs pré-assinadas, contendo a URL temporária gerada e a chave do arquivo no S3.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresignedUrlResponseDTO {
    private String url;
    private String fileKey;
}
