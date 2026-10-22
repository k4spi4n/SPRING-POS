package com.winmart.wms.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Metric nghiệp vụ xuất qua Actuator: {@code GET /actuator/metrics/wms.business.events?tag=event:issue_created}
 */
@Component
@RequiredArgsConstructor
public class WmsMetrics {

    public static final String BUSINESS_EVENTS = "wms.business.events";

    private final MeterRegistry registry;

    /** Đếm 1 sự kiện nghiệp vụ, ví dụ "receipt_created", "issue_created", "login_failed". */
    public void count(String event) {
        registry.counter(BUSINESS_EVENTS, "event", event).increment();
    }

    /** Đếm sự kiện theo thêm 1 chiều phân loại, ví dụ count("stock_rejected", "reason", "insufficient"). */
    public void count(String event, String tagKey, String tagValue) {
        registry.counter(BUSINESS_EVENTS, "event", event, tagKey, tagValue).increment();
    }
}
