package com.winmart.wms.service;

import com.winmart.wms.common.PageResponse;
import com.winmart.wms.dto.product.ProductRequest;
import com.winmart.wms.dto.product.ProductResponse;
import com.winmart.wms.entity.Category;
import com.winmart.wms.entity.Product;
import com.winmart.wms.entity.Supplier;
import com.winmart.wms.exception.BadRequestException;
import com.winmart.wms.exception.ResourceNotFoundException;
import com.winmart.wms.repository.CategoryRepository;
import com.winmart.wms.repository.ProductRepository;
import com.winmart.wms.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.Audited;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    public PageResponse<ProductResponse> getProducts(String search, Pageable pageable) {
        Page<Product> productPage;
        if (search != null && !search.isBlank()) {
            productPage = productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(search, search, pageable);
        } else {
            productPage = productRepository.findAll(pageable);
        }
        return PageResponse.of(productPage.map(this::mapToResponse));
    }

    public List<ProductResponse> getAllProductsList() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));
        return mapToResponse(product);
    }

    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với SKU: " + sku));
        return mapToResponse(product);
    }

    public ProductResponse getProductByBarcode(String barcode) {
        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với Barcode: " + barcode));
        return mapToResponse(product);
    }

    @Transactional
    @Audited(action = AuditAction.CREATE, entity = "Product")
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new BadRequestException("Mã SKU đã tồn tại: " + request.getSku());
        }
        if (request.getBarcode() != null && productRepository.existsByBarcode(request.getBarcode())) {
            throw new BadRequestException("Mã vạch (Barcode) đã tồn tại: " + request.getBarcode());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngành hàng với ID: " + request.getCategoryId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));
        }

        Product product = Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .barcode(request.getBarcode())
                .unit(request.getUnit())
                .purchasePrice(request.getPurchasePrice())
                .sellingPrice(request.getSellingPrice())
                .minStockLevel(request.getMinStockLevel())
                .category(category)
                .supplier(supplier)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entity = "Product")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id));

        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new BadRequestException("Mã SKU đã tồn tại: " + request.getSku());
        }
        if (request.getBarcode() != null && !request.getBarcode().equals(product.getBarcode())
                && productRepository.existsByBarcode(request.getBarcode())) {
            throw new BadRequestException("Mã vạch đã tồn tại: " + request.getBarcode());
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngành hàng với ID: " + request.getCategoryId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + request.getSupplierId()));
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setBarcode(request.getBarcode());
        product.setUnit(request.getUnit());
        product.setPurchasePrice(request.getPurchasePrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setMinStockLevel(request.getMinStockLevel());
        product.setCategory(category);
        product.setSupplier(supplier);
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entity = "Product")
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + id);
        }
        productRepository.deleteById(id);
    }

    public ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .barcode(product.getBarcode())
                .unit(product.getUnit())
                .purchasePrice(product.getPurchasePrice())
                .sellingPrice(product.getSellingPrice())
                .minStockLevel(product.getMinStockLevel())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .supplierId(product.getSupplier() != null ? product.getSupplier().getId() : null)
                .supplierName(product.getSupplier() != null ? product.getSupplier().getName() : null)
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
