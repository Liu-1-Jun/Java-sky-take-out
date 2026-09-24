package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.exception.UploadFailedException;
import com.sky.service.CommonService;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
public class CommonServiceImpl implements CommonService {
    @Autowired
    private AliOssUtil aliOssUtil;

    @Override
    public String upload(MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String objectName = aliOssUtil.generateObjectName(UUID.randomUUID().toString() + originalFilename);
            String url = aliOssUtil.upload(file.getBytes(), objectName);
            return url;
        } catch (Exception e) {
            log.error("上传图片失败", e);
            throw new UploadFailedException(e.getMessage());
        }
    }
}
