package com.bugaoshan.storage;

import com.bugaoshan.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${file.upload.upload-path:./uploads}") String uploadPath) {
        this.root = Path.of(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法初始化文件存储目录", e);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (category == null || !category.matches("[a-zA-Z0-9_-]+")) {
            throw new BusinessException("文件分类无效");
        }
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        LocalDate now = LocalDate.now();
        String storageKey = category + "/" + now.getYear() + "/"
                + String.format(Locale.ROOT, "%02d", now.getMonthValue()) + "/"
                + UUID.randomUUID() + extension;
        Path target = resolveSafe(storageKey);
        try {
            Files.createDirectories(target.getParent());
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(storageKey, originalFilename,
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                    file.getSize());
        } catch (IOException e) {
            try { Files.deleteIfExists(target); }
            catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw new BusinessException("文件保存失败");
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path file = resolveSafe(storageKey);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException("文件不存在");
            }
            return resource;
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (IOException e) {
            throw new BusinessException("文件删除失败");
        }
    }

    private Path resolveSafe(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException("文件标识无效");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("文件路径无效");
        }
        return resolved;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }
}
