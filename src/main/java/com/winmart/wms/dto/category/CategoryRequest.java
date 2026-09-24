package com.winmart.wms.dto.category;

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
public class CategoryRequest {

    @NotBlank(message = "Mã ngành hàng không được để trống")
    @Size(max = 30, message = "Mã ngành hàng tối đa 30 ký tự")
    private String code;

    @NotBlank(message = "Tên ngành hàng không được để trống")
    @Size(max = 100, message = "Tên ngành hàng tối đa 100 ký tự")
    private String name;

    private String description;
}
