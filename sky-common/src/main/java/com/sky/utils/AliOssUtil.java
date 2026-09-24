package com.sky.utils;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectResult;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.GetObjectResult;
import com.aliyun.sdk.service.oss2.models.HeadObjectRequest;
import com.aliyun.sdk.service.oss2.models.HeadObjectResult;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Request;
import com.aliyun.sdk.service.oss2.models.ListObjectsV2Result;
import com.aliyun.sdk.service.oss2.models.ObjectSummary;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.paginator.ListObjectsV2Iterable;
import com.aliyun.sdk.service.oss2.transfermanager.UploadResult;
import com.aliyun.sdk.service.oss2.transfermanager.Uploader;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传工具类（OSS Java SDK V2：com.aliyun:alibabacloud-oss-v2）。
 *
 * <p>功能一览：
 * <ul>
 *   <li>上传：字节数组 / 输入流 / 本地文件（大文件自动走分片上传）</li>
 *   <li>下载：下载为字节数组 / 下载到本地文件</li>
 *   <li>删除：删除单个文件</li>
 *   <li>查询：判断文件是否存在、获取文件大小、按前缀列举文件（自动翻页）</li>
 *   <li>URL：拼接公开访问 URL、生成预签名下载 URL / 预签名上传 URL</li>
 *   <li>辅助：生成 objectName（yyyy/MM/dd/uuid.ext）、取扩展名、推断 Content-Type</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@link OSSClient} 是线程安全的重量级对象（内部持有连接池），本类懒加载并全局复用，
 *       容器关闭时由 Spring 自动调用 {@link #close()} 释放；</li>
 *   <li>V2 的 region 是必填项且参与 V4 签名，本类会从 endpoint 自动解析
 *       （{@code oss-cn-hangzhou.aliyuncs.com -> cn-hangzhou}），因此配置文件里不需要写 region；</li>
 *   <li>所有操作失败都会抛出 {@link RuntimeException}，不会静默返回一个无效 URL。</li>
 * </ul>
 *
 * <p>对应的配置项（application.yml，前缀 sky.alioss，与 AliOssProperties 一一对应）：
 * <pre>
 * sky:
 *   alioss:
 *     endpoint: oss-cn-hangzhou.aliyuncs.com
 *     access-key-id: xxx
 *     access-key-secret: xxx
 *     bucket-name: xxx
 * </pre>
 */
@Slf4j
@Getter
@Setter
public class AliOssUtil {

    /** OSS 访问域名，如 oss-cn-hangzhou.aliyuncs.com（带不带 https:// 都可以） */
    private String endpoint;

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 预签名 URL 的默认有效期：1 小时 */
    public static final Duration DEFAULT_PRESIGN_EXPIRATION = Duration.ofHours(1);

    /** OSS 客户端：懒加载 + 全局复用，不能每次上传都新建 */
    private volatile OSSClient ossClient;

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 扩展名 -> Content-Type，避免图片上传后被浏览器当成附件下载 */
    private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>();

    static {
        CONTENT_TYPE_MAP.put("jpg", "image/jpeg");
        CONTENT_TYPE_MAP.put("jpeg", "image/jpeg");
        CONTENT_TYPE_MAP.put("png", "image/png");
        CONTENT_TYPE_MAP.put("gif", "image/gif");
        CONTENT_TYPE_MAP.put("webp", "image/webp");
        CONTENT_TYPE_MAP.put("bmp", "image/bmp");
        CONTENT_TYPE_MAP.put("svg", "image/svg+xml");
        CONTENT_TYPE_MAP.put("ico", "image/x-icon");
        CONTENT_TYPE_MAP.put("mp4", "video/mp4");
        CONTENT_TYPE_MAP.put("mov", "video/quicktime");
        CONTENT_TYPE_MAP.put("mp3", "audio/mpeg");
        CONTENT_TYPE_MAP.put("wav", "audio/wav");
        CONTENT_TYPE_MAP.put("pdf", "application/pdf");
        CONTENT_TYPE_MAP.put("txt", "text/plain; charset=utf-8");
        CONTENT_TYPE_MAP.put("csv", "text/csv; charset=utf-8");
        CONTENT_TYPE_MAP.put("json", "application/json; charset=utf-8");
        CONTENT_TYPE_MAP.put("xml", "application/xml; charset=utf-8");
        CONTENT_TYPE_MAP.put("html", "text/html; charset=utf-8");
        CONTENT_TYPE_MAP.put("doc", "application/msword");
        CONTENT_TYPE_MAP.put("docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        CONTENT_TYPE_MAP.put("xls", "application/vnd.ms-excel");
        CONTENT_TYPE_MAP.put("xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        CONTENT_TYPE_MAP.put("ppt", "application/vnd.ms-powerpoint");
        CONTENT_TYPE_MAP.put("pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        CONTENT_TYPE_MAP.put("zip", "application/zip");
    }

    public AliOssUtil() {
    }

    /**
     * 保留原 V1 版本的构造方式，配置类中直接使用：
     * <pre>
     * new AliOssUtil(properties.getEndpoint(), properties.getAccessKeyId(),
     *                properties.getAccessKeySecret(), properties.getBucketName());
     * </pre>
     */
    public AliOssUtil(String endpoint, String accessKeyId, String accessKeySecret, String bucketName) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
    }

    // ==================== 上传 ====================

    /**
     * 上传字节数组。方法签名与原 V1 版本一致，业务代码无需改动。
     *
     * @param bytes      文件字节内容
     * @param objectName OSS 上的对象名，如 a1b2c3d4.png；不要以 / 开头
     * @return 文件的访问 URL
     */
    public String upload(byte[] bytes, String objectName) {
        return upload(bytes, objectName, guessContentType(objectName));
    }

    /**
     * 上传字节数组（显式指定 Content-Type）。
     *
     * @param contentType MIME 类型，可为 null
     */
    public String upload(byte[] bytes, String objectName, String contentType) {
        if (bytes == null) {
            throw new IllegalArgumentException("上传内容不能为 null");
        }
        PutObjectRequest.Builder builder = PutObjectRequest.newBuilder()
                .bucket(bucketName)
                .key(objectName)
                .contentLength(bytes.length)
                .body(BinaryData.fromBytes(bytes));
        if (!isBlank(contentType)) {
            builder.contentType(contentType);
        }
        PutObjectResult result = doPutObject(builder.build(), objectName);
        return afterUpload(objectName, result.statusCode(), result.requestId(), result.eTag(),
                (long) bytes.length);
    }

    /**
     * 上传输入流，适合大文件，避免一次性读进内存。
     *
     * <p>注意：未提供长度，SDK 会使用 chunked 传输；单次 putObject 上限 5GB，
     * 更大的文件请用 {@link #uploadFile(Path, String)}（内部走分片上传）。
     * 输入流的关闭由调用方负责。
     */
    public String upload(InputStream inputStream, String objectName, String contentType) {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为 null");
        }
        PutObjectRequest.Builder builder = PutObjectRequest.newBuilder()
                .bucket(bucketName)
                .key(objectName)
                .body(BinaryData.fromStream(inputStream));
        if (!isBlank(contentType)) {
            builder.contentType(contentType);
        }
        PutObjectResult result = doPutObject(builder.build(), objectName);
        return afterUpload(objectName, result.statusCode(), result.requestId(), result.eTag(), null);
    }

    /** 上传本地文件（字符串绝对路径） */
    public String uploadFile(String localFilePath, String objectName) {
        if (isBlank(localFilePath)) {
            throw new IllegalArgumentException("本地文件路径不能为空");
        }
        return uploadFile(Paths.get(localFilePath), objectName);
    }

    /** 上传本地文件（File） */
    public String uploadFile(File localFile, String objectName) {
        if (localFile == null) {
            throw new IllegalArgumentException("本地文件不能为 null");
        }
        return uploadFile(localFile.toPath(), objectName);
    }

    /**
     * 上传本地文件（Path）。
     *
     * <p>使用 SDK 的 {@link Uploader}：小文件走简单上传，超过分片阈值的文件自动走
     * 并行分片上传，因此不需要自己判断文件大小。
     */
    public String uploadFile(Path localFile, String objectName) {
        if (localFile == null || !Files.isReadable(localFile)) {
            throw new IllegalArgumentException("本地文件不存在或不可读：" + localFile);
        }
        PutObjectRequest.Builder builder = PutObjectRequest.newBuilder()
                .bucket(bucketName)
                .key(objectName);
        String contentType = guessContentType(objectName);
        if (!isBlank(contentType)) {
            builder.contentType(contentType);
        }

        try {
            Uploader uploader = new Uploader(getClient());
            UploadResult result = uploader.uploadFile(builder.build(), localFile.toString());
            return afterUpload(objectName, result.statusCode(), null, result.etag(), fileSize(localFile));
        } catch (Exception e) {
            // Uploader 会抛出受检异常 UploadError，这里统一按 Exception 处理
            logOssError("文件上传失败（本地文件）", objectName, e);
            throw new RuntimeException("文件上传失败：" + objectName, e);
        }
    }

    // ==================== 下载 ====================

    /** 下载为字节数组，适合小文件 */
    public byte[] downloadBytes(String objectName) {
        try (GetObjectResult result = getClient().getObject(GetObjectRequest.newBuilder()
                .bucket(bucketName)
                .key(objectName)
                .build())) {
            byte[] data = readAll(result.body());
            log.info("文件下载成功：{}（{} 字节）", objectName, data.length);
            return data;
        } catch (Exception e) {
            logOssError("文件下载失败", objectName, e);
            throw new RuntimeException("文件下载失败：" + objectName, e);
        }
    }

    /** 下载到本地文件（字符串路径），父目录不存在会自动创建 */
    public void downloadToFile(String objectName, String localFilePath) {
        if (isBlank(localFilePath)) {
            throw new IllegalArgumentException("本地文件路径不能为空");
        }
        downloadToFile(objectName, Paths.get(localFilePath));
    }

    /** 下载到本地文件（Path），父目录不存在会自动创建 */
    public void downloadToFile(String objectName, Path target) {
        if (target == null) {
            throw new IllegalArgumentException("目标路径不能为 null");
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            try (GetObjectResult ignored = getClient().getObjectToFile(
                    GetObjectRequest.newBuilder().bucket(bucketName).key(objectName).build(), target)) {
                log.info("文件下载成功：{} -> {}", objectName, target);
            }
        } catch (Exception e) {
            logOssError("文件下载失败", objectName, e);
            throw new RuntimeException("文件下载失败：" + objectName, e);
        }
    }

    // ==================== 删除 ====================

    /** 删除文件。OSS 的删除是幂等的：删除不存在的对象同样返回成功。 */
    public void delete(String objectName) {
        try {
            DeleteObjectResult result = getClient().deleteObject(DeleteObjectRequest.newBuilder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            log.info("文件删除成功：{}（status={}）", objectName, result.statusCode());
        } catch (Exception e) {
            logOssError("文件删除失败", objectName, e);
            throw new RuntimeException("文件删除失败：" + objectName, e);
        }
    }

    // ==================== 查询 ====================

    /** 判断文件是否存在 */
    public boolean exists(String objectName) {
        try {
            return getClient().doesObjectExist(bucketName, objectName);
        } catch (Exception e) {
            logOssError("判断文件是否存在失败", objectName, e);
            throw new RuntimeException("判断文件是否存在失败：" + objectName, e);
        }
    }

    /**
     * 获取文件大小（字节）
     *
     * @return 文件不存在时返回 null
     */
    public Long getObjectSize(String objectName) {
        try {
            HeadObjectResult result = getClient().headObject(HeadObjectRequest.newBuilder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            return result.contentLength();
        } catch (Exception e) {
            ServiceException se = ServiceException.asCause(e);
            if (se != null && se.statusCode() == 404) {
                return null;
            }
            logOssError("获取文件大小失败", objectName, e);
            throw new RuntimeException("获取文件大小失败：" + objectName, e);
        }
    }

    /**
     * 按前缀列举文件（自动翻页，返回全部结果）。
     *
     * @param prefix 对象名前缀，如 "upload/2026/"；为 null 或空串表示列举整个 Bucket
     * @return objectName 列表
     */
    public List<String> listObjectNames(String prefix) {
        List<String> names = new ArrayList<>();
        try {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.newBuilder().bucket(bucketName);
            if (!isBlank(prefix)) {
                builder.prefix(prefix);
            }
            ListObjectsV2Iterable paginator = getClient().listObjectsV2Paginator(builder.build());
            for (ListObjectsV2Result result : paginator) {
                if (result.contents() == null) {
                    continue;
                }
                for (ObjectSummary summary : result.contents()) {
                    names.add(summary.key());
                }
            }
            log.info("列举文件完成：prefix={}, 共 {} 个", prefix, names.size());
            return names;
        } catch (Exception e) {
            log.error("列举文件失败：prefix={}", prefix, e);
            throw new RuntimeException("列举文件失败，prefix=" + prefix, e);
        }
    }

    // ==================== URL ====================

    /**
     * 拼接文件的公开访问 URL。
     *
     * <p>规则：标准 OSS 域名用 {@code https://{bucket}.{endpoint}/{objectName}}；
     * 自定义域名（CNAME）则用 {@code https://{domain}/{objectName}}。
     *
     * <p>注意：Bucket 为「私有」读写权限时，该 URL 无法匿名访问，请改用
     * {@link #generatePresignedUrl(String, Duration)}。
     */
    public String buildFileUrl(String objectName) {
        String host = stripScheme(endpoint);
        String key = encodeObjectName(objectName);
        if (host.endsWith(".aliyuncs.com")) {
            return "https://" + bucketName + "." + host + "/" + key;
        }
        return "https://" + host + "/" + key;
    }

    /** 生成预签名下载 URL（默认 1 小时有效），私有 Bucket 也能匿名访问 */
    public String generatePresignedUrl(String objectName) {
        return generatePresignedUrl(objectName, DEFAULT_PRESIGN_EXPIRATION);
    }

    /**
     * 生成预签名下载 URL。
     *
     * @param validity 有效期，如 {@code Duration.ofMinutes(30)}；为 null 时用默认 1 小时
     */
    public String generatePresignedUrl(String objectName, Duration validity) {
        try {
            PresignResult result = getClient().presign(
                    GetObjectRequest.newBuilder().bucket(bucketName).key(objectName).build(),
                    presignOptions(validity));
            log.info("生成预签名下载 URL 成功：{}，有效期 {}", objectName, effective(validity));
            return result.url();
        } catch (Exception e) {
            logOssError("生成预签名下载 URL 失败", objectName, e);
            throw new RuntimeException("生成预签名下载 URL 失败：" + objectName, e);
        }
    }

    /**
     * 生成预签名上传 URL：前端可以直接拿这个 URL 用 HTTP PUT 把文件传到 OSS，
     * 不必经过后端中转（注意前端 PUT 时要带上完全相同的 Content-Type）。
     */
    public String generatePresignedPutUrl(String objectName, Duration validity) {
        try {
            PresignResult result = getClient().presign(
                    PutObjectRequest.newBuilder().bucket(bucketName).key(objectName).build(),
                    presignOptions(validity));
            log.info("生成预签名上传 URL 成功：{}，有效期 {}", objectName, effective(validity));
            return result.url();
        } catch (Exception e) {
            logOssError("生成预签名上传 URL 失败", objectName, e);
            throw new RuntimeException("生成预签名上传 URL 失败：" + objectName, e);
        }
    }

    // ==================== objectName / Content-Type 辅助 ====================

    /**
     * 生成不重复的 objectName：{@code yyyy/MM/dd/uuid.ext}
     * 例：{@code 2026/02/01/3f2a9c...c1.png}
     *
     * @param originalFilename 原始文件名，用于取扩展名
     */
    public String generateObjectName(String originalFilename) {
        return generateObjectName("", originalFilename);
    }

    /**
     * 生成不重复的 objectName，并带一级业务目录
     * 例：{@code generateObjectName("dish", "a.png") -> dish/2026/02/01/uuid.png}
     *
     * @param dir              业务目录前缀，可为 null
     * @param originalFilename 原始文件名
     */
    public String generateObjectName(String dir, String originalFilename) {
        String ext = getExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return normalizeDir(dir) + LocalDate.now().format(DATE_DIR) + "/" + uuid
                + (ext.isEmpty() ? "" : "." + ext);
    }

    /** 取小写扩展名（不含点）；没有扩展名返回空串 */
    public static String getExtension(String filename) {
        if (isBlank(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 按扩展名推断 Content-Type，未知返回 null（交给 SDK 自动探测） */
    public static String guessContentType(String objectName) {
        String ext = getExtension(objectName);
        return ext.isEmpty() ? null : CONTENT_TYPE_MAP.get(ext);
    }

    // ==================== 客户端生命周期 ====================

    /**
     * 关闭客户端，释放连接池。
     * Spring 会为 @Bean 创建的对象自动推断 close() 作为销毁方法，容器关闭时自动调用。
     */
    public void close() {
        OSSClient client = this.ossClient;
        if (client != null) {
            try {
                client.close();
                log.info("OSS 客户端已关闭");
            } catch (Exception e) {
                log.warn("关闭 OSS 客户端时出现异常", e);
            } finally {
                this.ossClient = null;
            }
        }
    }

    /** 双重检查锁定，保证整个应用只创建一个客户端 */
    public OSSClient getClient() {
        OSSClient client = this.ossClient;
        if (client == null) {
            synchronized (this) {
                client = this.ossClient;
                if (client == null) {
                    client = createClient();
                    this.ossClient = client;
                }
            }
        }
        return client;
    }

    private OSSClient createClient() {
        if (isBlank(endpoint) || isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(bucketName)) {
            throw new IllegalStateException("OSS 配置不完整，请检查配置项 "
                    + "sky.alioss.endpoint / access-key-id / access-key-secret / bucket-name");
        }

        String ep = endpoint.trim();
        if (!ep.startsWith("http://") && !ep.startsWith("https://")) {
            ep = "https://" + ep;
        }

        OSSClientBuilder builder = OSSClient.newBuilder()
                .credentialsProvider(new StaticCredentialsProvider(accessKeyId, accessKeySecret))
                .endpoint(ep);

        // region 在 V2 中是必填项且参与 V4 签名，写错会报 SignatureDoesNotMatch
        String region = resolveRegion();
        if (region != null) {
            builder.region(region);
            log.info("初始化 OSS 客户端：endpoint={}, region={}, bucket={}", ep, region, bucketName);
        } else {
            log.warn("无法从 endpoint({}) 解析出 region，请确认使用标准 OSS 域名"
                    + "（如 oss-cn-hangzhou.aliyuncs.com）；若使用自定义域名，签名可能失败", endpoint);
        }
        return builder.build();
    }

    /**
     * 从标准 OSS 域名中解析地域 ID。
     * <pre>
     * oss-cn-hangzhou.aliyuncs.com          -> cn-hangzhou
     * oss-cn-hangzhou-internal.aliyuncs.com -> cn-hangzhou
     * oss-ap-southeast-1.aliyuncs.com       -> ap-southeast-1
     * </pre>
     *
     * @return 解析成功返回地域 ID，否则返回 null
     */
    private String resolveRegion() {
        String host = stripScheme(endpoint).toLowerCase(Locale.ROOT);
        int idx = host.indexOf(".aliyuncs.com");
        if (idx <= 0) {
            return null;
        }
        String prefix = host.substring(0, idx);
        if (prefix.endsWith("-internal")) {
            prefix = prefix.substring(0, prefix.length() - "-internal".length());
        }
        if (prefix.startsWith("oss-")) {
            prefix = prefix.substring("oss-".length());
        }
        // cn-hangzhou / ap-southeast-1 / us-west-1 这类才是地域，accelerate 之类不是
        return prefix.matches("[a-z]{2}-[a-z0-9-]+") ? prefix : null;
    }

    // ==================== 私有工具方法 ====================

    private PutObjectResult doPutObject(PutObjectRequest request, String objectName) {
        try {
            return getClient().putObject(request);
        } catch (Exception e) {
            logOssError("文件上传失败", objectName, e);
            throw new RuntimeException("文件上传失败：" + objectName, e);
        }
    }

    private String afterUpload(String objectName, int statusCode, String requestId,
                              String eTag, Long size) {
        String url = buildFileUrl(objectName);
        log.info("文件上传成功：{}（status={}, requestId={}, eTag={}, size={}）",
                url, statusCode, requestId, eTag, size == null ? "未知" : size);
        return url;
    }

    private PresignOptions presignOptions(Duration validity) {
        return PresignOptions.newBuilder().expiration(effective(validity)).build();
    }

    private static Duration effective(Duration validity) {
        return validity == null ? DEFAULT_PRESIGN_EXPIRATION : validity;
    }

    private static Long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        return out.toByteArray();
    }

    /** 区分「服务端拒绝」和「客户端/网络异常」，方便排查问题 */
    private void logOssError(String action, String objectName, Exception e) {
        ServiceException se = ServiceException.asCause(e);
        if (se != null) {
            log.error("{}（OSS 返回错误）：objectName={}, status={}, errorCode={}, requestId={}, message={}",
                    action, objectName, se.statusCode(), se.errorCode(), se.requestId(), se.errorMessage());
        } else {
            log.error("{}（客户端或网络异常）：objectName={}", action, objectName, e);
        }
    }

    private static String normalizeDir(String dir) {
        if (isBlank(dir)) {
            return "";
        }
        String value = stripLeadingSlash(dir.trim());
        return value.endsWith("/") ? value : value + "/";
    }

    /** 去掉协议前缀和结尾的 / */
    private static String stripScheme(String endpoint) {
        String host = endpoint == null ? "" : endpoint.trim();
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    /** 去掉开头的 /，并对每一段做 URL 编码（支持中文文件名） */
    private static String encodeObjectName(String objectName) {
        String name = stripLeadingSlash(isBlank(objectName) ? "" : objectName.trim());
        String[] segments = name.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(urlEncode(segments[i]));
        }
        return sb.toString();
    }

    private static String urlEncode(String segment) {
        try {
            // URLEncoder 会把空格编码成 +，而 URL 路径里必须是 %20
            return URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 一定存在，理论上不会执行到这里
            return segment;
        }
    }

    private static String stripLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
