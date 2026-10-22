package com.winmart.wms.service;

import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.Audited;
import com.winmart.wms.common.DocumentCodeGenerator;
import com.winmart.wms.dto.receipt.ReceiptCreateRequest;
import com.winmart.wms.dto.receipt.ReceiptItemRequest;
import com.winmart.wms.dto.receipt.ReceiptItemResponse;
import com.winmart.wms.dto.receipt.ReceiptResponse;
import com.winmart.wms.entity.*;
import com.winmart.wms.entity.enums.MovementType;
import com.winmart.wms.entity.enums.ReceiptStatus;
import com.winmart.wms.event.StockChangedEvent;
import com.winmart.wms.exception.BadRequestException;
import com.winmart.wms.exception.InsufficientStockException;
import com.winmart.wms.exception.InvalidStateException;
import com.winmart.wms.exception.ProductExpiredException;
import com.winmart.wms.exception.ResourceNotFoundException;
import com.winmart.wms.monitoring.WmsMetrics;
import com.winmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Nghiệp vụ nhập kho. Mặc định chỉ đọc ({@code readOnly}); các phương thức ghi khai báo
 * {@code @Transactional} riêng: phiếu + lô + tồn kho + nhật ký biến động cùng commit hoặc cùng rollback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptService {

    private final StockReceiptRepository receiptRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final DocumentCodeGenerator codeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final WmsMetrics metrics;

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

    @Audited(action = AuditAction.CREATE, entity = "StockReceipt")
    @Transactional
    public ReceiptResponse createReceipt(ReceiptCreateRequest request, String username) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng với ID: " + request.getWarehouseId()));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new BadRequestException("Kho [" + warehouse.getCode() + "] đang ngừng hoạt động, không thể nhập hàng");
        }

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));

        User receivedBy = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));

        String receiptCode = codeGenerator.next("PNK", receiptRepository::existsByReceiptCode);
        LocalDate today = LocalDate.now();

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
            validateItem(itemReq, product, today);

            // 1. Tìm hoặc tạo mới Lô hàng (ProductBatch)
            ProductBatch batch = batchRepository
                    .findByProductIdAndBatchNumber(product.getId(), itemReq.getBatchNumber())
                    .map(existing -> {
                        if (!existing.getExpiryDate().equals(itemReq.getExpiryDate())) {
                            throw new BadRequestException(String.format(
                                    "Lô [%s] của sản phẩm [%s] đã tồn tại với HSD %s, khác HSD %s trong phiếu nhập",
                                    existing.getBatchNumber(), product.getSku(), existing.getExpiryDate(), itemReq.getExpiryDate()));
                        }
                        return existing;
                    })
                    .orElseGet(() -> batchRepository.save(ProductBatch.builder()
                            .batchNumber(itemReq.getBatchNumber())
                            .product(product)
                            .manufacturingDate(itemReq.getManufacturingDate())
                            .expiryDate(itemReq.getExpiryDate())
                            .build()));

            // 2. Cập nhật tồn kho (Inventory) - khóa dòng để cộng dồn an toàn khi nhiều phiếu nhập cùng lô
            Inventory inventory = inventoryRepository
                    .findByWarehouseAndBatchForUpdate(warehouse.getId(), batch.getId())
                    .orElseGet(() -> Inventory.builder()
                            .warehouse(warehouse)
                            .batch(batch)
                            .quantity(0)
                            .build());

            int updatedQuantity = inventory.getQuantity() + itemReq.getQuantity();
            inventory.setQuantity(updatedQuantity);
            inventoryRepository.save(inventory);

            // 3. Ghi vết biến động kho (StockMovement)
            movementRepository.save(StockMovement.builder()
                    .warehouse(warehouse)
                    .product(product)
                    .batch(batch)
                    .movementType(MovementType.INBOUND)
                    .quantity(itemReq.getQuantity())
                    .balanceAfter(updatedQuantity)
                    .referenceCode(receiptCode)
                    .note(truncate("Nhập kho theo phiếu " + receiptCode + " từ NCC " + supplier.getName(), 255))
                    .build());

            // 4. Tạo chi tiết phiếu nhập (ReceiptItem)
            BigDecimal subTotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subTotal);

            receipt.addItem(ReceiptItem.builder()
                    .product(product)
                    .batchNumber(itemReq.getBatchNumber())
                    .manufacturingDate(itemReq.getManufacturingDate())
                    .expiryDate(itemReq.getExpiryDate())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .subTotal(subTotal)
                    .build());
        }

        receipt.setTotalAmount(totalAmount);
        StockReceipt savedReceipt = receiptRepository.save(receipt);
        metrics.count("receipt_created");
        log.info("Đã tạo phiếu nhập {} từ NCC {} gồm {} dòng, tổng tiền {}", receiptCode, supplier.getCode(),
                savedReceipt.getItems().size(), totalAmount);

        return mapToResponse(savedReceipt);
    }

    /**
     * Hủy phiếu nhập đã hoàn thành: trừ lại tồn kho của đúng các lô đã nhập.
     * Nếu hàng đã được xuất đi (tồn lô không đủ để trừ) thì từ chối, toàn bộ thao tác rollback.
     */
    @Audited(action = AuditAction.CANCEL, entity = "StockReceipt")
    @Transactional
    public ReceiptResponse cancelReceipt(Long id, String reason, String username) {
        StockReceipt receipt = receiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu nhập kho với ID: " + id));

        if (receipt.getStatus() != ReceiptStatus.COMPLETED) {
            throw new InvalidStateException("Chỉ có thể hủy phiếu nhập ở trạng thái COMPLETED (hiện tại: "
                    + receipt.getStatus() + ")");
        }

        Warehouse warehouse = receipt.getWarehouse();
        // Khóa theo thứ tự ổn định để tránh deadlock
        List<ReceiptItem> orderedItems = receipt.getItems().stream()
                .sorted(Comparator.comparing((ReceiptItem item) -> item.getProduct().getId())
                        .thenComparing(ReceiptItem::getBatchNumber))
                .toList();

        for (ReceiptItem item : orderedItems) {
            ProductBatch batch = batchRepository
                    .findByProductIdAndBatchNumber(item.getProduct().getId(), item.getBatchNumber())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô " + item.getBatchNumber()));
            Inventory inventory = inventoryRepository
                    .findByWarehouseAndBatchForUpdate(warehouse.getId(), batch.getId())
                    .orElseThrow(() -> new InsufficientStockException(String.format(
                            "Không thể hủy: lô [%s] của sản phẩm [%s] không còn tồn tại kho",
                            item.getBatchNumber(), item.getProduct().getSku())));

            if (inventory.getQuantity() < item.getQuantity()) {
                metrics.count("stock_rejected", "reason", "cancel_receipt");
                throw new InsufficientStockException(String.format(
                        "Không thể hủy phiếu nhập: lô [%s] của sản phẩm [%s] chỉ còn %d, cần hoàn %d (hàng đã được xuất đi)",
                        item.getBatchNumber(), item.getProduct().getSku(), inventory.getQuantity(), item.getQuantity()));
            }

            inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
            inventoryRepository.save(inventory);

            movementRepository.save(StockMovement.builder()
                    .warehouse(warehouse)
                    .product(item.getProduct())
                    .batch(batch)
                    .movementType(MovementType.ADJUSTMENT)
                    .quantity(-item.getQuantity())
                    .balanceAfter(inventory.getQuantity())
                    .referenceCode(receipt.getReceiptCode())
                    .note(truncate("Trừ tồn do hủy phiếu nhập " + receipt.getReceiptCode() + ": " + reason, 255))
                    .build());

            eventPublisher.publishEvent(new StockChangedEvent(warehouse.getId(), item.getProduct().getId()));
        }

        receipt.setStatus(ReceiptStatus.CANCELLED);
        receipt.setCancelReason(reason);
        receipt.setCancelledBy(username);
        receipt.setCancelledAt(LocalDateTime.now());
        StockReceipt saved = receiptRepository.save(receipt);

        metrics.count("receipt_cancelled");
        log.info("Đã hủy phiếu nhập {} bởi {}: {}", receipt.getReceiptCode(), username, reason);
        return mapToResponse(saved);
    }

    private void validateItem(ReceiptItemRequest itemReq, Product product, LocalDate today) {
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new BadRequestException("Sản phẩm [" + product.getSku() + "] đã ngừng kinh doanh, không thể nhập kho");
        }
        if (!itemReq.getExpiryDate().isAfter(today)) {
            metrics.count("stock_rejected", "reason", "expired_receipt");
            throw new ProductExpiredException(String.format(
                    "Lô [%s] của sản phẩm [%s] có HSD %s đã hết hạn, không thể nhập kho",
                    itemReq.getBatchNumber(), product.getSku(), itemReq.getExpiryDate()));
        }
        LocalDate mfg = itemReq.getManufacturingDate();
        if (mfg != null && (mfg.isAfter(today) || mfg.isAfter(itemReq.getExpiryDate()))) {
            throw new BadRequestException(String.format(
                    "Ngày sản xuất của lô [%s] không hợp lệ (không được ở tương lai hoặc sau hạn sử dụng)",
                    itemReq.getBatchNumber()));
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
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
                .createdBy(receipt.getCreatedBy())
                .cancelReason(receipt.getCancelReason())
                .cancelledBy(receipt.getCancelledBy())
                .cancelledAt(receipt.getCancelledAt())
                .items(itemResponses)
                .build();
    }
}
