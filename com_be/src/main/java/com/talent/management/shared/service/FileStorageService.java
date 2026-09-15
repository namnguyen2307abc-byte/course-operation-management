package com.talent.management.shared.service;

import com.talent.management.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageLocation = Paths.get("uploads").toAbsolutePath().normalize();

    public FileStorageService() {
        try {
            Files.createDirectories(this.storageLocation);
        } catch (Exception ex) {
            throw new BusinessException("Không thể khởi tạo thư mục lưu trữ file: " + ex.getMessage());
        }
    }

    public String storeFile(MultipartFile file, String subFolder) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            Path targetDir = this.storageLocation.resolve(subFolder);
            Files.createDirectories(targetDir);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path targetPath = targetDir.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + subFolder + "/" + uniqueFilename;
        } catch (IOException ex) {
            throw new BusinessException("Lưu trữ file thất bại: " + ex.getMessage());
        }
    }
}
