package com.talentus.scout.service.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class R2FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private R2FileStorageService r2Service;

    @BeforeEach
    void setUp() {
        r2Service = new R2FileStorageService(
                "https://test.r2.cloudflarestorage.com",
                "testAccessKey",
                "testSecretKey",
                "talentus-public",
                "talentus-private-documents",
                "https://cdn.talentusscout.com",
                s3Client,
                s3Presigner
        );
    }

    @Test
    void storeFile_PublicDirectory_StoresInPublicBucketAndReturnsCdnUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.png",
                "image/png",
                "PNG Content".getBytes()
        );

        String resultUrl = r2Service.storeFile(file, "photos");

        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith("https://cdn.talentusscout.com/photos/"));
        assertTrue(resultUrl.endsWith(".png"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals("talentus-public", capturedRequest.bucket());
        assertTrue(capturedRequest.key().startsWith("photos/"));
        assertEquals("image/png", capturedRequest.contentType());
    }

    @Test
    void storeFile_PrivateDirectory_StoresInPrivateBucketAndReturnsUploadsPath() {
        MockMultipartFile file = new MockMultipartFile(
                "identityDoc",
                "cedula.pdf",
                "application/pdf",
                "PDF Content".getBytes()
        );

        String resultPath = r2Service.storeFile(file, "identity-documents/athletes");

        assertNotNull(resultPath);
        assertTrue(resultPath.startsWith("/uploads/identity-documents/athletes/"));
        assertTrue(resultPath.endsWith(".pdf"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals("talentus-private-documents", capturedRequest.bucket());
        assertTrue(capturedRequest.key().startsWith("identity-documents/athletes/"));
        assertEquals("application/pdf", capturedRequest.contentType());
    }

    @Test
    void storeFile_DisallowedExtension_ThrowsIllegalArgumentException() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "malicious",
                "script.sh",
                "application/x-sh",
                "echo hack".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> r2Service.storeFile(exeFile, "photos"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void storeFile_EmptyFile_ThrowsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "empty",
                "test.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class, () -> r2Service.storeFile(emptyFile, "photos"));
        verifyNoInteractions(s3Client);
    }

    @Test
    void generatePresignedUrl_PrivateDocument_GeneratesValidPresignedUrl() throws Exception {
        URL fakePresignedUrl = new URL("https://test.r2.cloudflarestorage.com/talentus-private-documents/identity-documents/athletes/test.pdf?X-Amz-Signature=xyz");
        when(presignedGetObjectRequest.url()).thenReturn(fakePresignedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);

        String result = r2Service.generatePresignedUrl("/uploads/identity-documents/athletes/test.pdf", Duration.ofMinutes(5));

        assertNotNull(result);
        assertEquals(fakePresignedUrl.toString(), result);

        ArgumentCaptor<GetObjectPresignRequest> presignCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(presignCaptor.capture());

        GetObjectPresignRequest captured = presignCaptor.getValue();
        assertEquals(Duration.ofMinutes(5), captured.signatureDuration());
        assertEquals("talentus-private-documents", captured.getObjectRequest().bucket());
        assertEquals("identity-documents/athletes/test.pdf", captured.getObjectRequest().key());
    }

    @Test
    void generatePresignedUrl_PublicCdnUrl_ReturnsDirectlyWithoutPresigning() {
        String publicUrl = "https://cdn.talentusscout.com/photos/avatar-123.jpg";
        String result = r2Service.generatePresignedUrl(publicUrl, Duration.ofMinutes(5));

        assertEquals(publicUrl, result);
        verifyNoInteractions(s3Presigner);
    }

    @Test
    void deleteFile_DeletesFromAppropriateBucket() {
        r2Service.deleteFile("https://cdn.talentusscout.com/photos/avatar-123.jpg");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        DeleteObjectRequest captured = deleteCaptor.getValue();
        assertEquals("talentus-public", captured.bucket());
        assertEquals("photos/avatar-123.jpg", captured.key());
    }
}
