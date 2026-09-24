package com.winmart.wms.dto.receipt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemRequest {

    @NotNull(message = "Sản phẩm không được để trống")
    private Long productId;

    @NotBlank(message = "Số lô sản xuất không được để trống")
    private String batchNumber;

    private LocalDate manufacturingDate;

    @NotNull(message = "Hạn sử dụng không được để trống")
    private LocalDate expiryDate;

    @NotNull(message = "Số lượng nhập không được để trống")
    @Min(value = 1, message = "Số lượng nhập tối thiểu là 1")
    private Integer quantity;

    @NotNull(message = "Đơn giá nhập không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Đơn giá nhập phải lớn hơn 0")
    private BigDecimal unitPrice;
}
