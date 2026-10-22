package com.winmart.wms.service;

import com.winmart.wms.dto.supplier.SupplierRequest;
import com.winmart.wms.dto.supplier.SupplierResponse;
import com.winmart.wms.entity.Supplier;
import com.winmart.wms.exception.BadRequestException;
import com.winmart.wms.exception.ResourceNotFoundException;
import com.winmart.wms.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.winmart.wms.audit.AuditAction;
import com.winmart.wms.audit.Audited;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));
        return mapToResponse(supplier);
    }

    @Transactional
    @Audited(action = AuditAction.CREATE, entity = "Supplier")
    public SupplierResponse createSupplier(SupplierRequest request) {
        if (supplierRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Mã nhà cung cấp đã tồn tại: " + request.getCode());
        }

        Supplier supplier = Supplier.builder()
                .code(request.getCode())
                .name(request.getName())
                .contactName(request.getContactName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .build();

        return mapToResponse(supplierRepository.save(supplier));
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entity = "Supplier")
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));

        if (!supplier.getCode().equals(request.getCode()) && supplierRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Mã nhà cung cấp đã tồn tại: " + request.getCode());
        }

        supplier.setCode(request.getCode());
        supplier.setName(request.getName());
        supplier.setContactName(request.getContactName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());

        return mapToResponse(supplierRepository.save(supplier));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entity = "Supplier")
    public void deleteSupplier(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id);
        }
        supplierRepository.deleteById(id);
    }

    public SupplierResponse mapToResponse(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .code(supplier.getCode())
                .name(supplier.getName())
                .contactName(supplier.getContactName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .createdAt(supplier.getCreatedAt())
                .build();
    }
}
