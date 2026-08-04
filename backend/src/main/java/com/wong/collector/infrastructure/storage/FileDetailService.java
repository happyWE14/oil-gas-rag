package com.wong.collector.infrastructure.storage;

import java.util.Map;

import org.dromara.x.file.storage.core.hash.HashInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.infrastructure.persistence.mapper.FileDetailMapper;
import com.wong.collector.infrastructure.persistence.po.FileDetailPO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.recorder.FileRecorder;

/**
 * 将文件上传记录持久化到数据库，供 x-file-storage download(url) 解析平台信息。
 */
@Service
@RequiredArgsConstructor
public class FileDetailService extends ServiceImpl<FileDetailMapper, FileDetailPO> implements FileRecorder {

    private final ObjectMapper objectMapper;
    private final FilePartDetailService filePartDetailService;

    @SneakyThrows
    @Override
    public boolean save(FileInfo info) {
        if (info.getId() == null) {
            info.setId(IdUtil.fastSimpleUUID());
        }
        FileDetailPO detail = toFileDetail(info);
        boolean saved = save(detail);
        if (saved) {
            info.setId(detail.getId());
        }
        return saved;
    }

    @SneakyThrows
    @Override
    public void update(FileInfo info) {
        FileDetailPO detail = toFileDetail(info);
        LambdaUpdateWrapper<FileDetailPO> qw = Wrappers.<FileDetailPO>lambdaUpdate()
            .eq(detail.getUrl() != null, FileDetailPO::getUrl, detail.getUrl())
            .eq(detail.getId() != null, FileDetailPO::getId, detail.getId());
        update(detail, qw);
    }

    @SneakyThrows
    @Override
    public FileInfo getByUrl(String url) {
        LambdaQueryWrapper<FileDetailPO> qw = Wrappers.<FileDetailPO>lambdaQuery()
            .eq(FileDetailPO::getUrl, url);
        return toFileInfo(getOne(qw));
    }

    @Override
    public boolean delete(String url) {
        lambdaUpdate().eq(FileDetailPO::getUrl, url).remove();
        return true;
    }

    @Override
    public void saveFilePart(FilePartInfo filePartInfo) {
        filePartDetailService.saveFilePart(filePartInfo);
    }

    @Override
    public void deleteFilePartByUploadId(String uploadId) {
        filePartDetailService.deleteFilePartByUploadId(uploadId);
    }

    public FileDetailPO toFileDetail(FileInfo info) throws JsonProcessingException {
        FileDetailPO detail = BeanUtil.copyProperties(
            info, FileDetailPO.class,
            "metadata", "userMetadata", "thMetadata", "thUserMetadata", "attr", "hashInfo");
        detail.setMetadata(valueToJson(info.getMetadata()));
        detail.setUserMetadata(valueToJson(info.getUserMetadata()));
        detail.setThMetadata(valueToJson(info.getThMetadata()));
        detail.setThUserMetadata(valueToJson(info.getThUserMetadata()));
        detail.setAttr(valueToJson(info.getAttr()));
        detail.setHashInfo(valueToJson(info.getHashInfo()));
        return detail;
    }

    public FileInfo toFileInfo(FileDetailPO detail) throws JsonProcessingException {
        if (detail == null) {
            return null;
        }
        FileInfo info = BeanUtil.copyProperties(
            detail, FileInfo.class,
            "metadata", "userMetadata", "thMetadata", "thUserMetadata", "attr", "hashInfo");
        info.setMetadata(jsonToMetadata(detail.getMetadata()));
        info.setUserMetadata(jsonToMetadata(detail.getUserMetadata()));
        info.setThMetadata(jsonToMetadata(detail.getThMetadata()));
        info.setThUserMetadata(jsonToMetadata(detail.getThUserMetadata()));
        info.setAttr(jsonToDict(detail.getAttr()));
        info.setHashInfo(jsonToHashInfo(detail.getHashInfo()));
        return info;
    }

    public String valueToJson(Object value) throws JsonProcessingException {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    public Map<String, String> jsonToMetadata(String json) throws JsonProcessingException {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
    }

    public Dict jsonToDict(String json) throws JsonProcessingException {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return objectMapper.readValue(json, Dict.class);
    }

    public HashInfo jsonToHashInfo(String json) throws JsonProcessingException {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return objectMapper.readValue(json, HashInfo.class);
    }
}
