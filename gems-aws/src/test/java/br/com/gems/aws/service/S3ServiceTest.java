package br.com.gems.aws.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.gems.aws.dto.GenerateUploadUrlRequestDTO;
import br.com.gems.aws.dto.PresignedUrlResponseDTO;
import br.com.gems.exception.exception.BusinessException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;

class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        s3Service = new S3Service(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3Service, "expirationMinutes", 15L);
    }

    @Test
    void generatePresignedUploadUrl_WithoutFileName_ThrowsException() {
        GenerateUploadUrlRequestDTO request = new GenerateUploadUrlRequestDTO();
        request.setFileName(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> 
            s3Service.generatePresignedUploadUrl(request)
        );

        assertTrue(exception.getMessage().contains("O nome do arquivo é obrigatório para gerar a URL de upload."));
    }

    @Test
    void generatePresignedUploadUrl_WithValidFileName_ReturnsUrl() throws Exception {
        GenerateUploadUrlRequestDTO request = new GenerateUploadUrlRequestDTO();
        request.setFileName("documento.pdf");
        request.setContentType("application/pdf");
        request.setDirectory("docs");

        PresignedPutObjectRequest presignedRequestMock = PresignedPutObjectRequest.builder()
                .expiration(java.time.Instant.now())
                .isBrowserExecutable(true)
                .signedHeaders(java.util.Map.of("Host", java.util.List.of("s3.amazonaws.com")))
                .httpRequest(software.amazon.awssdk.http.SdkHttpRequest.builder()
                        .uri(new java.net.URI("https://s3.amazonaws.com/test-bucket/docs/unique_documento.pdf"))
                        .method(software.amazon.awssdk.http.SdkHttpMethod.PUT)
                        .build())
                .build();
                
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequestMock);

        PresignedUrlResponseDTO response = s3Service.generatePresignedUploadUrl(request);

        assertNotNull(response);
        assertTrue(response.getUrl().contains("test-bucket"));
        assertTrue(response.getFileKey().endsWith("documento.pdf"));
        assertTrue(response.getFileKey().startsWith("docs/"));
    }

    @Test
    void deleteFile_WithoutKey_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
            s3Service.deleteFile("")
        );

        assertTrue(exception.getMessage().contains("A chave do arquivo (fileKey) é obrigatória para deletar o arquivo."));
    }

    @Test
    void generatePresignedDownloadUrl_WithoutKey_ThrowsException() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
            s3Service.generatePresignedDownloadUrl(null)
        );

        assertTrue(exception.getMessage().contains("A chave do arquivo (fileKey) é obrigatória para gerar a URL de download."));
    }

    @Test
    void generatePresignedDownloadUrl_WithValidKey_ReturnsUrl() throws Exception {
        PresignedGetObjectRequest presignedRequestMock = PresignedGetObjectRequest.builder()
                .expiration(java.time.Instant.now())
                .isBrowserExecutable(true)
                .signedHeaders(java.util.Map.of("Host", java.util.List.of("s3.amazonaws.com")))
                .httpRequest(software.amazon.awssdk.http.SdkHttpRequest.builder()
                        .uri(new java.net.URI("https://s3.amazonaws.com/test-bucket/docs/file.pdf"))
                        .method(software.amazon.awssdk.http.SdkHttpMethod.GET)
                        .build())
                .build();

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequestMock);

        PresignedUrlResponseDTO response = s3Service.generatePresignedDownloadUrl("docs/file.pdf");

        assertNotNull(response);
        assertTrue(response.getUrl().contains("test-bucket"));
        assertEquals("docs/file.pdf", response.getFileKey());
    }

    @Test
    void generatePresignedDownloadUrl_WhenPresignerFails_ThrowsBusinessException() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new RuntimeException("AWS down"));

        assertThrows(BusinessException.class, () -> s3Service.generatePresignedDownloadUrl("docs/file.pdf"));
    }

    @Test
    void fileExists_WhenObjectPresent_ReturnsTrue() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        assertTrue(s3Service.fileExists("docs/file.pdf"));
    }

    @Test
    void fileExists_WhenObjectMissing_ReturnsFalse() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertFalse(s3Service.fileExists("docs/missing.pdf"));
    }

    @Test
    void fileExists_WithNullKey_ReturnsFalse() {
        assertFalse(s3Service.fileExists(null));
    }
}
