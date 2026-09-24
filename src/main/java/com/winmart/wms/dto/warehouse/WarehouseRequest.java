package com.winmart.wms.dto.warehouse;

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
public class WarehouseRequest {

    @NotBlank(message = "Mã kho không được để trống")
    @Size(max = 30, message = "Mã kho tối đa 30 ký tự")
    private String code;

    @NotBlank(message = "Tên kho không được để trống")
    @Size(max = 150, message = "Tên kho tối đa 150 ký tự")
    private String name;

    @NotBlank(message = "Địa chỉ kho không được để trống")
    private String location;

    private Double capacity;

    @Builder.Default
    private Boolean active = true;
}
