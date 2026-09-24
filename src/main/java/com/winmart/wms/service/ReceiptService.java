package com.winmart.wms.service;

import com.winmart.wms.dto.receipt.ReceiptCreateRequest;
import com.winmart.wms.dto.receipt.ReceiptItemRequest;
import com.winmart.wms.dto.receipt.ReceiptItemResponse;
import com.winmart.wms.dto.receipt.ReceiptResponse;
import com.winmart.wms.entity.*;
import com.winmart.wms.entity.enums.MovementType;
import com.winmart.wms.entity.enums.ReceiptStatus;
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
public class ReceiptService {

    private final StockReceiptRepository receiptRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;

    public List<ReceiptResponse> getAllReceipts() {
        return receiptRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ReceiptResponse> getReceiptsByWarehouse(Long warehouseId) {
        return receiptRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ReceiptResponse getReceiptById(Long id) {
        StockReceipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu nhập kho với ID: " + id));
        return mapToResponse(receipt);
    }

    @Transactional
    public ReceiptResponse createReceipt(ReceiptCreateRequest request, String username) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng với ID: " + request.getWarehouseId()));

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));

        User receivedBy = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));

        String receiptCode = generateReceiptCode();

        StockReceipt receipt = StockReceipt.builder()
                .receiptCode(receiptCode)
                .warehouse(warehouse)
                .supplier(supplier)
                .receivedBy(receivedBy)
                .status(ReceiptStatus.COMPLETED)
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ReceiptItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));

            // 1. Tìm hoặc tạo mới Lô hàng (ProductBatch)
            ProductBatch batch = batchRepository
                    .findByProductIdAndBatchNumber(product.getId(), itemReq.getBatchNumber())
                    .orElseGet(() -> batchRepository.save(ProductBatch.builder()
                            .batchNumber(itemReq.getBatchNumber())
                            .product(product)
                            .manufacturingDate(itemReq.getManufacturingDate())
                            .expiryDate(itemReq.getExpiryDate())
                            .build()));

            // 2. Cập nhật tồn kho (Inventory)
            Inventory inventory = inventoryRepository
                    .findByWarehouseIdAndBatchId(warehouse.getId(), batch.getId())
                    .orElseGet(() -> Inventory.builder()
                            .warehouse(warehouse)
                            .batch(batch)
                            .quantity(0)
                            .build());

            int updatedQuantity = inventory.getQuantity() + itemReq.getQuantity();
            inventory.setQuantity(updatedQuantity);
            inventoryRepository.save(inventory);

            // 3. Ghi vết biến động kho (StockMovement)
            StockMovement movement = StockMovement.builder()
                    .warehouse(warehouse)
                    .product(product)
                    .batch(batch)
                    .movementType(MovementType.INBOUND)
                    .quantity(itemReq.getQuantity())
                    .balanceAfter(updatedQuantity)
                    .referenceCode(receiptCode)
                    .note("Nhập kho theo phiếu " + receiptCode + " từ NCC " + supplier.getName())
                    .build();
            movementRepository.save(movement);

            // 4. Tạo chi tiết phiếu nhập (ReceiptItem)
            BigDecimal subTotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subTotal);

            ReceiptItem item = ReceiptItem.builder()
                    .product(product)
                    .batchNumber(itemReq.getBatchNumber())
                    .manufacturingDate(itemReq.getManufacturingDate())
                    .expiryDate(itemReq.getExpiryDate())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .subTotal(subTotal)
                    .build();

            receipt.addItem(item);
        }

        receipt.setTotalAmount(totalAmount);
        StockReceipt savedReceipt = receiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    private String generateReceiptCode() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomNum = 1000 + new Random().nextInt(9000);
        return "PNK-" + dateStr + "-" + randomNum;
    }

    public ReceiptResponse mapToResponse(StockReceipt receipt) {
        List<ReceiptItemResponse> itemResponses = receipt.getItems().stream()
                .map(item -> ReceiptItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productSku(item.getProduct().getSku())
                        .productName(item.getProduct().getName())
                        .unit(item.getProduct().getUnit())
                        .batchNumber(item.getBatchNumber())
                        .manufacturingDate(item.getManufacturingDate())
                        .expiryDate(item.getExpiryDate())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subTotal(item.getSubTotal())
                        .build())
                .toList();

        return ReceiptResponse.builder()
                .id(receipt.getId())
                .receiptCode(receipt.getReceiptCode())
                .warehouseId(receipt.getWarehouse().getId())
                .warehouseName(receipt.getWarehouse().getName())
                .supplierId(receipt.getSupplier().getId())
                .supplierName(receipt.getSupplier().getName())
                .receivedBy(receipt.getReceivedBy().getFullName())
                .status(receipt.getStatus().name())
                .totalAmount(receipt.getTotalAmount())
                .note(receipt.getNote())
                .createdAt(receipt.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
