package com.wong.collector.infrastructure.storage;

import cn.hutool.core.date.LocalDateTimeUtil;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.infrastructure.persistence.mapper.FilePartDetailMapper;
import com.wong.collector.infrastructure.persistence.po.FilePartDetailPO;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * 保存手动分片上传的分片信息。
 */
@Service
@RequiredArgsConstructor
public class FilePartDetailService extends ServiceImpl<FilePartDetailMapper, FilePartDetailPO> {

    private final ObjectMapper objectMapper;

    /**
     * 保存文件分片信息
     *
     * @param info 文件分片信息
     */
    @SneakyThrows
    public void saveFilePart(FilePartInfo info) {
        FilePartDetailPO detail = toFilePartDetail(info);
        if (save(detail)) {
            info.setId(detail.getId());
        }
    }

    /**
     * 删除文件分片信息
     */
    public void deleteFilePartByUploadId(String uploadId) {
        lambdaUpdate().eq(FilePartDetailPO::getUploadId, uploadId).remove();
    }

    /**
     * 将 FilePartInfo 转成 FilePartDetailPO
     */
    public FilePartDetailPO toFilePartDetail(FilePartInfo info) throws JsonProcessingException {
        FilePartDetailPO detail = new FilePartDetailPO();
        detail.setId(info.getId());
        detail.setPlatform(info.getPlatform());
        detail.setUploadId(info.getUploadId());
        detail.setETag(info.getETag());
        detail.setPartNumber(info.getPartNumber());
        detail.setPartSize(info.getPartSize());
        detail.setHashInfo(valueToJson(info.getHashInfo()));
        detail.setCreateTime(LocalDateTimeUtil.of(info.getCreateTime()));
        return detail;
    }

    public String valueToJson(Object value) throws JsonProcessingException {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }
}
