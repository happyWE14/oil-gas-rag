package com.wong.collector.infrastructure.util;

import cn.hutool.crypto.digest.DigestUtil;

public final class ChecksumUtils {
    private ChecksumUtils() {
    }

    public static String md5(String content) {
        return DigestUtil.md5Hex(content == null ? "" : content);
    }
}
