package com.winmart.wms.service;

import com.winmart.wms.dto.issue.IssueCreateRequest;
import com.winmart.wms.dto.issue.IssueItemRequest;
import com.winmart.wms.dto.issue.IssueItemResponse;
import com.winmart.wms.dto.issue.IssueResponse;
import com.winmart.wms.entity.*;
import com.winmart.wms.entity.enums.IssueStatus;
import com.winmart.wms.entity.enums.MovementType;
import com.winmart.wms.exception.InsufficientStockException;
import com.winmart.wms.exception.ResourceNotFoundException;
import com.winmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final StockIssueRepository issueRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;

    public List<IssueResponse> getAllIssues() {
        return issueRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<IssueResponse> getIssuesByWarehouse(Long warehouseId) {
        return issueRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public IssueResponse getIssueById(Long id) {
        StockIssue issue = issueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất kho với ID: " + id));
        return mapToResponse(issue);
    }

    /**
     * Xuất kho với thuật toán FEFO (First Expired, First Out):
     * Tự động ưu tiên xuất các lô hàng có hạn sử dụng gần nhất còn tồn trong kho.
     */
    @Transactional
    public IssueResponse createIssue(IssueCreateRequest request, String username) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng với ID: " + request.getWarehouseId()));

        User issuedBy = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));

        String issueCode = generateIssueCode();

        StockIssue issue = StockIssue.builder()
                .issueCode(issueCode)
                .warehouse(warehouse)
                .issuedBy(issuedBy)
                .destination(request.getDestination())
                .issueType(request.getIssueType())
                .status(IssueStatus.COMPLETED)
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (IssueItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));

            // 1. Kiểm tra tổng số lượng tồn kho của sản phẩm
            int totalStock = inventoryRepository.getTotalStockByWarehouseAndProduct(warehouse.getId(), product.getId());
            if (totalStock < itemReq.getQuantity()) {
                throw new InsufficientStockException(String.format(
                        "Sản phẩm [%s - %s] không đủ tồn kho. Tồn hiện tại: %d, Yêu cầu xuất: %d",
                        product.getSku(), product.getName(), totalStock, itemReq.getQuantity()));
            }

            // 2. Lấy danh sách tồn kho theo lô sắp xếp theo FEFO (Hạn sử dụng gần nhất xếp trước)
            List<Inventory> availableInventories = inventoryRepository
                    .findByWarehouseIdAndBatch_ProductIdAndQuantityGreaterThanOrderByBatch_ExpiryDateAsc(
                            warehouse.getId(), product.getId(), 0);

            int remainingToDeduct = itemReq.getQuantity();
            BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : product.getSellingPrice();

            // 3. Phân bổ trừ dần số lượng theo từng lô
            for (Inventory inv : availableInventories) {
                if (remainingToDeduct <= 0) {
                    break;
                }

                int deduct = Math.min(inv.getQuantity(), remainingToDeduct);
                inv.setQuantity(inv.getQuantity() - deduct);
                inventoryRepository.save(inv);
                remainingToDeduct -= deduct;

                BigDecimal subTotal = unitPrice.multiply(BigDecimal.valueOf(deduct));
                totalAmount = totalAmount.add(subTotal);

                // Tạo dòng chi tiết xuất kho gắn với Lô cụ thể
                IssueItem issueItem = IssueItem.builder()
                        .product(product)
                        .batch(inv.getBatch())
                        .quantity(deduct)
                        .unitPrice(unitPrice)
                        .subTotal(subTotal)
                        .build();
                issue.addItem(issueItem);

                // Ghi vết biến động kho
                StockMovement movement = StockMovement.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .batch(inv.getBatch())
                        .movementType(MovementType.OUTBOUND)
                        .quantity(-deduct)
                        .balanceAfter(inv.getQuantity())
                        .referenceCode(issueCode)
                        .note(String.format("Xuất kho theo phiếu %s tới %s (Lô: %s - HSD: %s)",
                                issueCode, request.getDestination(), inv.getBatch().getBatchNumber(), inv.getBatch().getExpiryDate()))
                        .build();
                movementRepository.save(movement);
            }
        }

        issue.setTotalAmount(totalAmount);
        StockIssue savedIssue = issueRepository.save(issue);

        return mapToResponse(savedIssue);
    }

    private String generateIssueCode() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomNum = 1000 + new Random().nextInt(9000);
        return "PXK-" + dateStr + "-" + randomNum;
    }

    public IssueResponse mapToResponse(StockIssue issue) {
        List<IssueItemResponse> itemResponses = issue.getItems().stream()
                .map(item -> IssueItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productSku(item.getProduct().getSku())
                        .productName(item.getProduct().getName())
                        .unit(item.getProduct().getUnit())
                        .batchId(item.getBatch().getId())
                        .batchNumber(item.getBatch().getBatchNumber())
                        .expiryDate(item.getBatch().getExpiryDate())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subTotal(item.getSubTotal())
                        .build())
                .toList();

        return IssueResponse.builder()
                .id(issue.getId())
                .issueCode(issue.getIssueCode())
                .warehouseId(issue.getWarehouse().getId())
                .warehouseName(issue.getWarehouse().getName())
                .issuedBy(issue.getIssuedBy().getFullName())
                .destination(issue.getDestination())
                .issueType(issue.getIssueType().name())
                .status(issue.getStatus().name())
                .totalAmount(issue.getTotalAmount())
                .note(issue.getNote())
                .createdAt(issue.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
