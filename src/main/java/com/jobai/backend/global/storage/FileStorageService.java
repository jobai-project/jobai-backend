package com.jobai.backend.global.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String upload(MultipartFile file, String key);
    void delete(String fileUrl);
}
