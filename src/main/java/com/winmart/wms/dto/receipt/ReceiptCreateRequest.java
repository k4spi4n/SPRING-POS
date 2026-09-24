package com.winmart.wms.dto.receipt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptCreateRequest {

    @NotNull(message = "Kho nhận không được để trống")
    private Long warehouseId;

    @NotNull(message = "Nhà cung cấp không được để trống")
    private Long supplierId;

    private String note;

    @NotEmpty(message = "Danh sách hàng hóa nhập kho không được để trống")
    @Valid
    private List<ReceiptItemRequest> items;
}
