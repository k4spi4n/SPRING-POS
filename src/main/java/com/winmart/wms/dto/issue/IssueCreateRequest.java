package com.winmart.wms.dto.issue;

import com.winmart.wms.entity.enums.IssueType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class IssueCreateRequest {

    @NotNull(message = "Kho xuất không được để trống")
    private Long warehouseId;

    @NotBlank(message = "Nơi nhận / Điểm đến không được để trống")
    private String destination;

    @NotNull(message = "Loại xuất kho không được để trống")
    private IssueType issueType;

    private String note;

    @NotEmpty(message = "Danh sách hàng hóa xuất kho không được để trống")
    @Valid
    private List<IssueItemRequest> items;
}
