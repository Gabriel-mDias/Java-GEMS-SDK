package br.com.gems.aws.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que encapsula as informações solicitadas por um cliente para a criação
 * de uma URL de upload pré-assinada (Presigned URL) para o S3.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GenerateUploadUrlRequestDTO {
    private String fileName;
    private String contentType;
    private String directory; // Optional folder path
}
