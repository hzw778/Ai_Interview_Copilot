package interview.guide.infrastructure.file;

import interview.guide.common.config.StorageConfigProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Local file storage service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageConfigProperties storageConfig;

    @PostConstruct
    void initDirectories() {
        createDirectoryIfMissing(resolveBaseDir());
        createDirectoryIfMissing(resolveConfiguredStorageDir(storageConfig.getResumeDir()));
        createDirectoryIfMissing(resolveConfiguredStorageDir(storageConfig.getKnowledgeDir()));
    }

    public String uploadResume(MultipartFile file) {
        return uploadFile(file, resolveStoragePrefix(storageConfig.getResumeDir()));
    }

    public void deleteResume(String fileKey) {
        deleteFile(fileKey);
    }

    public String uploadKnowledgeBase(MultipartFile file) {
        return uploadFile(file, resolveStoragePrefix(storageConfig.getKnowledgeDir()));
    }

    public void deleteKnowledgeBase(String fileKey) {
        deleteFile(fileKey);
    }

    public byte[] downloadFile(String fileKey) {
        if (!fileExists(fileKey)) {
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "鏂囦欢涓嶅瓨鍦? " + fileKey);
        }

        try {
            return Files.readAllBytes(resolveStoredPath(fileKey));
        } catch (IOException e) {
            log.error("涓嬭浇鏂囦欢澶辫触: {} - {}", fileKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "鏂囦欢涓嬭浇澶辫触: " + e.getMessage());
        }
    }

    public boolean fileExists(String fileKey) {
        return Files.exists(resolveStoredPath(fileKey));
    }

    public long getFileSize(String fileKey) {
        try {
            return Files.size(resolveStoredPath(fileKey));
        } catch (IOException e) {
            log.error("鑾峰彇鏂囦欢澶у皬澶辫触: {} - {}", fileKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "鑾峰彇鏂囦欢淇℃伅澶辫触");
        }
    }

    public String getFileUrl(String fileKey) {
        return storageConfig.getPublicUrlPrefix() + "/" + fileKey.replace("\\", "/");
    }

    public void ensureBucketExists() {
        initDirectories();
    }

    private String uploadFile(MultipartFile file, String prefix) {
        String originalFilename = file.getOriginalFilename();
        String fileKey = generateFileKey(originalFilename, prefix);
        Path targetPath = resolveBaseDir().resolve(fileKey).normalize();
        createDirectoryIfMissing(targetPath.getParent());

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("鏂囦欢涓婁紶鎴愬姛: {} -> {}", originalFilename, targetPath);
            return fileKey;
        } catch (IOException e) {
            log.error("涓婁紶鏂囦欢澶辫触: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, "鏂囦欢瀛樺偍澶辫触: " + e.getMessage());
        }
    }

    private void deleteFile(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            log.debug("鏂囦欢閿负绌猴紝璺宠繃鍒犻櫎");
            return;
        }

        Path path = resolveStoredPath(fileKey);
        if (!Files.exists(path)) {
            log.warn("鏂囦欢涓嶅瓨鍦紝璺宠繃鍒犻櫎: {}", fileKey);
            return;
        }

        try {
            Files.deleteIfExists(path);
            log.info("鏂囦欢鍒犻櫎鎴愬姛: {}", fileKey);
        } catch (IOException e) {
            log.error("鍒犻櫎鏂囦欢澶辫触: {} - {}", fileKey, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_DELETE_FAILED, "鏂囦欢鍒犻櫎澶辫触: " + e.getMessage());
        }
    }

    private String generateFileKey(String originalFilename, String prefix) {
        LocalDateTime now = LocalDateTime.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeName = sanitizeFilename(originalFilename);
        return String.format("%s/%s/%s_%s", prefix, datePath, uuid, safeName);
    }

    private Path resolveStoredPath(String fileKey) {
        return secureResolve(resolveBaseDir(), fileKey);
    }

    private Path secureResolve(Path root, String fileKey) {
        Path path = root.resolve(fileKey).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "闈炴硶鏂囦欢璺緞: " + fileKey);
        }
        return path;
    }

    private Path resolveBaseDir() {
        return Paths.get(storageConfig.getBaseDir()).toAbsolutePath().normalize();
    }

    private Path resolveConfiguredStorageDir(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return path.toAbsolutePath().normalize();
    }

    private String resolveStoragePrefix(String configuredPath) {
        Path path = Paths.get(configuredPath).normalize();
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, "鏃犳硶瑙ｆ瀽瀛樺偍鐩綍: " + configuredPath);
        }
        return fileName.toString();
    }

    private void createDirectoryIfMissing(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, "鍒涘缓瀛樺偍鐩綍澶辫触: " + directory, e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unknown";
        }
        return convertToPinyin(filename);
    }

    private String convertToPinyin(String input) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

        StringBuilder result = new StringBuilder();
        for (char ch : input.toCharArray()) {
            try {
                String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, format);
                if (pinyins != null && pinyins.length > 0) {
                    result.append(capitalize(pinyins[0]));
                } else {
                    result.append(sanitizeChar(ch));
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                result.append(sanitizeChar(ch));
            }
        }
        return result.toString();
    }

    private char sanitizeChar(char ch) {
        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
            || (ch >= '0' && ch <= '9') || ch == '.' || ch == '_' || ch == '-') {
            return ch;
        }
        return '_';
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
