package com.sky.config;

import com.sky.properties.AliOssProperties;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置类：把 AliOssUtil 注册成 Spring 单例 Bean。
 *
 * <p>AliOssProperties 上已经有 @Component 和
 * @ConfigurationProperties(prefix = "sky.alioss")，会被自动扫描并完成属性绑定，
 * 所以这里直接作为方法参数注入即可，不需要再加 @EnableConfigurationProperties。
 *
 * <p>AliOssUtil 中的 OSSClient 是懒加载的：第一次调用上传/下载时才会真正创建客户端，
 * 因此配置项不完整不会导致项目启动失败，只会在真正调用时报错。
 */
@Configuration
@Slf4j
public class OssConfiguration {

    @Bean
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
        log.info("开始创建阿里云文件上传工具类对象：bucketName={}, endpoint={}",
                aliOssProperties.getBucketName(), aliOssProperties.getEndpoint());
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName());
    }
}
