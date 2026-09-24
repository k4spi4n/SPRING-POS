package com.winmart.wms.dto.supplier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {
    private Long id;
    private String code;
    private String name;
    private String contactName;
    private String phone;
    private String email;
    private String address;
    private LocalDateTime createdAt;
}
