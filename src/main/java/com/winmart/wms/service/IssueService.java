package com.winmart.wms.service;

import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.Audited;
import com.winmart.wms.common.DocumentCodeGenerator;
import com.winmart.wms.dto.issue.IssueCreateRequest;
import com.winmart.wms.dto.issue.IssueItemRequest;
import com.winmart.wms.dto.issue.IssueItemResponse;
import com.winmart.wms.dto.issue.IssueResponse;
import com.winmart.wms.entity.*;
import com.winmart.wms.entity.enums.IssueStatus;
import com.winmart.wms.entity.enums.IssueType;
import com.winmart.wms.entity.enums.MovementType;
import com.winmart.wms.event.StockChangedEvent;
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
 * Nghiệp vụ xuất kho FEFO. Mặc định mọi phương thức chỉ đọc ({@code readOnly}); các phương thức ghi
 * khai báo {@code @Transactional} riêng: toàn bộ (phiếu + trừ tồn + nhật ký biến động) commit
 * hoặc rollback cùng nhau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueService {

    private final StockIssueRepository issueRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final DocumentCodeGenerator codeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final WmsMetrics metrics;

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
     * <ul>
     *   <li>STORE_TRANSFER: chỉ xuất lô CÒN hạn (không đưa hàng hết hạn ra cửa hàng)</li>
     *   <li>DISPOSAL_EXPIRED: chỉ xuất (hủy) lô ĐÃ hết hạn</li>
     *   <li>RETURN_SUPPLIER: xuất mọi lô (trả hàng lỗi/hết hạn cho nhà cung cấp)</li>
     * </ul>
     * Các dòng tồn kho được khóa bi quan (FOR UPDATE) nên hai phiếu xuất đồng thời không thể
     * cùng "nhìn thấy" một lượng tồn và xuất lố.
     */
    @Audited(action = AuditAction.CREATE, entity = "StockIssue")
    @Transactional
    public IssueResponse createIssue(IssueCreateRequest request, String username) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho hàng với ID: " + request.getWarehouseId()));

        User issuedBy = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));

        String issueCode = codeGenerator.next("PXK", issueRepository::existsByIssueCode);
        IssueType issueType = request.getIssueType();
        LocalDate today = LocalDate.now();

        StockIssue issue = StockIssue.builder()
                .issueCode(issueCode)
                .warehouse(warehouse)
                .issuedBy(issuedBy)
                .destination(request.getDestination())
                .issueType(issueType)
                .status(IssueStatus.COMPLETED)
                .note(request.getNote())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        // Luôn khóa các sản phẩm theo thứ tự id tăng dần => hai phiếu đồng thời không thể khóa chéo nhau (deadlock)
        List<IssueItemRequest> orderedItems = request.getItems().stream()
                .sorted(Comparator.comparing(IssueItemRequest::getProductId))
                .toList();

        for (IssueItemRequest itemReq : orderedItems) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));

            // 1. Khóa & lấy các lô còn hàng theo FEFO (hạn gần nhất trước)
            List<Inventory> lockedLots = inventoryRepository.findAvailableStockForUpdate(warehouse.getId(), product.getId());

            // 2. Lọc các lô được phép xuất theo loại phiếu
            List<Inventory> eligibleLots = lockedLots.stream()
                    .filter(inv -> isEligible(issueType, inv.getBatch().getExpiryDate(), today))
                    .toList();
            int eligibleStock = eligibleLots.stream().mapToInt(Inventory::getQuantity).sum();

            if (eligibleStock < itemReq.getQuantity()) {
                throw rejectStock(issueType, product, lockedLots, eligibleStock, itemReq.getQuantity(), today);
            }

            int remainingToDeduct = itemReq.getQuantity();
            BigDecimal unitPrice = resolveUnitPrice(itemReq, product, issueType);

            // 3. Phân bổ trừ dần số lượng theo từng lô
            for (Inventory inv : eligibleLots) {
                if (remainingToDeduct <= 0) {
                    break;
                }

                int deduct = Math.min(inv.getQuantity(), remainingToDeduct);
                inv.setQuantity(inv.getQuantity() - deduct); // dirty checking tự UPDATE khi commit
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
                movementRepository.save(StockMovement.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .batch(inv.getBatch())
                        .movementType(MovementType.OUTBOUND)
                        .quantity(-deduct)
                        .balanceAfter(inv.getQuantity())
                        .referenceCode(issueCode)
                        .note(truncate(String.format("Xuất kho theo phiếu %s tới %s (Lô: %s - HSD: %s)",
                                issueCode, request.getDestination(), inv.getBatch().getBatchNumber(),
                                inv.getBatch().getExpiryDate()), 255))
                        .build());
            }

            eventPublisher.publishEvent(new StockChangedEvent(warehouse.getId(), product.getId()));
        }

        issue.setTotalAmount(totalAmount);
        StockIssue savedIssue = issueRepository.save(issue);
        metrics.count("issue_created", "type", issueType.name());
        log.info("Đã tạo phiếu xuất {} ({}) gồm {} dòng lô, tổng tiền {}", issueCode, issueType,
                savedIssue.getItems().size(), totalAmount);

        return mapToResponse(savedIssue);
    }

    /**
     * Hủy phiếu xuất đã hoàn thành: hoàn trả đúng số lượng về đúng các lô đã xuất
     * và ghi bút toán điều chỉnh. Phiếu chỉ hủy được 1 lần (khóa dòng phiếu khi xử lý).
     */
    @Audited(action = AuditAction.CANCEL, entity = "StockIssue")
    @Transactional
    public IssueResponse cancelIssue(Long id, String reason, String username) {
        StockIssue issue = issueRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu xuất kho với ID: " + id));

        if (issue.getStatus() != IssueStatus.COMPLETED) {
            throw new InvalidStateException("Chỉ có thể hủy phiếu xuất ở trạng thái COMPLETED (hiện tại: "
                    + issue.getStatus() + ")");
        }

        Warehouse warehouse = issue.getWarehouse();
        // Khóa tồn kho theo thứ tự id lô tăng dần để tránh deadlock với giao dịch khác
        List<IssueItem> orderedItems = issue.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getBatch().getId()))
                .toList();

        for (IssueItem item : orderedItems) {
            Inventory inventory = inventoryRepository
                    .findByWarehouseAndBatchForUpdate(warehouse.getId(), item.getBatch().getId())
                    .orElseGet(() -> Inventory.builder()
                            .warehouse(warehouse)
                            .batch(item.getBatch())
                            .quantity(0)
                            .build());

            inventory.setQuantity(inventory.getQuantity() + item.getQuantity());
            inventoryRepository.save(inventory);

            movementRepository.save(StockMovement.builder()
                    .warehouse(warehouse)
                    .product(item.getProduct())
                    .batch(item.getBatch())
                    .movementType(MovementType.ADJUSTMENT)
                    .quantity(item.getQuantity())
                    .balanceAfter(inventory.getQuantity())
                    .referenceCode(issue.getIssueCode())
                    .note(truncate("Hoàn tồn do hủy phiếu xuất " + issue.getIssueCode() + ": " + reason, 255))
                    .build());
        }

        issue.setStatus(IssueStatus.CANCELLED);
        issue.setCancelReason(reason);
        issue.setCancelledBy(username);
        issue.setCancelledAt(LocalDateTime.now());
        StockIssue saved = issueRepository.save(issue);

        metrics.count("issue_cancelled");
        log.info("Đã hủy phiếu xuất {} bởi {}: {}", issue.getIssueCode(), username, reason);
        return mapToResponse(saved);
    }

    private static boolean isEligible(IssueType type, LocalDate expiryDate, LocalDate today) {
        boolean expired = expiryDate.isBefore(today);
        return switch (type) {
            case STORE_TRANSFER -> !expired;
            case DISPOSAL_EXPIRED -> expired;
            case RETURN_SUPPLIER -> true;
        };
    }

    private RuntimeException rejectStock(IssueType type, Product product, List<Inventory> allLots,
                                         int eligibleStock, int requested, LocalDate today) {
        int totalStock = allLots.stream().mapToInt(Inventory::getQuantity).sum();
        metrics.count("stock_rejected", "reason", "insufficient");

        if (type == IssueType.STORE_TRANSFER && totalStock > eligibleStock) {
            int expiredQty = totalStock - eligibleStock;
            String msg = String.format(
                    "Sản phẩm [%s - %s]: chỉ còn %d trong các lô còn hạn (có %d đã hết hạn không được điều chuyển). Yêu cầu xuất: %d",
                    product.getSku(), product.getName(), eligibleStock, expiredQty, requested);
            return eligibleStock == 0 ? new ProductExpiredException(msg) : new InsufficientStockException(msg);
        }
        if (type == IssueType.DISPOSAL_EXPIRED) {
            return new InsufficientStockException(String.format(
                    "Sản phẩm [%s - %s]: chỉ có %d hàng đã hết hạn để hủy, yêu cầu hủy: %d",
                    product.getSku(), product.getName(), eligibleStock, requested));
        }
        return new InsufficientStockException(String.format(
                "Sản phẩm [%s - %s] không đủ tồn kho. Tồn hiện tại: %d, Yêu cầu xuất: %d",
                product.getSku(), product.getName(), eligibleStock, requested));
    }

    /** Đơn giá: ưu tiên giá client gửi; mặc định giá bán khi điều chuyển, giá vốn khi hủy/trả NCC. */
    private static BigDecimal resolveUnitPrice(IssueItemRequest itemReq, Product product, IssueType type) {
        if (itemReq.getUnitPrice() != null) {
            return itemReq.getUnitPrice();
        }
        BigDecimal fallback = type == IssueType.STORE_TRANSFER ? product.getSellingPrice() : product.getPurchasePrice();
        return fallback != null ? fallback : BigDecimal.ZERO;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
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
                .createdBy(issue.getCreatedBy())
                .cancelReason(issue.getCancelReason())
                .cancelledBy(issue.getCancelledBy())
                .cancelledAt(issue.getCancelledAt())
                .items(itemResponses)
                .build();
    }
}
