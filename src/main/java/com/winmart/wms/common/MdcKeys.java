package com.winmart.wms.common;

/**
 * Các khóa MDC dùng chung cho logging/auditing/exception handling (hiển thị trong mọi dòng log).
 */
public final class MdcKeys {

    public static final String REQUEST_ID = "requestId";
    public static final String USERNAME = "username";
    public static final String CLIENT_IP = "clientIp";

    private MdcKeys() {
    }
}
