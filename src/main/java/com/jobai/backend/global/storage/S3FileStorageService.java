package com.jobai.backend.global.storage;

import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Profile("!local")
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class S3FileStorageService implements FileStorageService {

    private final S3Template s3Template;

    @Value("${app.s3.bucket}")
    private String bucketName;

    @Value("${app.s3.region:ap-northeast-2}")
    private String region;

    @Override
    public String upload(MultipartFile file, String key) {
        try {
            ObjectMetadata metadata = ObjectMetadata.builder()
                    .contentType(file.getContentType())
                    .build();
            s3Template.upload(bucketName, key, file.getInputStream(), metadata);
            return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
        } catch (IOException e) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.");
        }
    }

    @Override
    public void delete(String fileUrl) {
        String key = fileUrl.substring(fileUrl.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());
        s3Template.deleteObject(bucketName, key);
    }
}
