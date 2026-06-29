package com.jobai.backend.global.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("local")
public class LocalFileStorageService implements FileStorageService {

    @Override
    public String upload(MultipartFile file, String key) {
        return "http://localhost/mock-s3/" + key;
    }

    @Override
    public void delete(String fileUrl) {
        // no-op in local
    }
}
