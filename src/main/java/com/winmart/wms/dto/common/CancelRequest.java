package com.winmart.wms.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {

    @NotBlank(message = "Lý do hủy không được để trống")
    @Size(max = 500, message = "Lý do hủy tối đa 500 ký tự")
    private String reason;
}
