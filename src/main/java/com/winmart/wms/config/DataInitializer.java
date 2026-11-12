package com.winmart.wms.config;

import com.winmart.wms.entity.*;
import com.winmart.wms.entity.enums.MovementType;
import com.winmart.wms.entity.enums.RoleName;
import com.winmart.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        log.info(">> Đang khởi tạo dữ liệu mẫu cho Hệ thống Quản lý Kho WinMart (WinMart WMS)...");

        // 1. Khởi tạo Roles
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_ADMIN)
                        .description("Quản trị viên toàn quyền hệ thống kho WinMart")
                        .build()));

        Role managerRole = roleRepository.findByName(RoleName.ROLE_MANAGER)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_MANAGER)
                        .description("Quản lý kho / Thủ kho trưởng WinMart")
                        .build()));

        Role staffRole = roleRepository.findByName(RoleName.ROLE_STAFF)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(RoleName.ROLE_STAFF)
                        .description("Nhân viên vận hành kho WinMart (Nhập/Xuất/Kiểm đếm)")
                        .build()));

        // 2. Khởi tạo Users mẫu
        if (userRepository.count() == 0) {
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Nguyễn Văn Quản Trị")
                    .email("admin@winmart.vn")
                    .phone("0901234567")
                    .role(adminRole)
                    .active(true)
                    .build());

            userRepository.save(User.builder()
                    .username("manager")
                    .password(passwordEncoder.encode("manager123"))
                    .fullName("Trần Thị Thu Trang (Thủ Kho)")
                    .email("trang.manager@winmart.vn")
                    .phone("0912345678")
                    .role(managerRole)
                    .active(true)
                    .build());

            userRepository.save(User.builder()
                    .username("staff")
                    .password(passwordEncoder.encode("staff123"))
                    .fullName("Lê Hoàng Nam (Nhân Viên Kho)")
                    .email("nam.staff@winmart.vn")
                    .phone("0923456789")
                    .role(staffRole)
                    .active(true)
                    .build());
        }

        // 3. Khởi tạo Kho mẫu (Warehouses)
        Warehouse hubWarehouse = warehouseRepository.findByCode("WH-MB-01")
                .orElseGet(() -> warehouseRepository.save(Warehouse.builder()
                        .code("WH-MB-01")
                        .name("Kho Tổng WinMart Miền Bắc (KCN Quang Minh)")
                        .location("Lô 28, KCN Quang Minh, Mê Linh, Hà Nội")
                        .capacity(15000.0)
                        .active(true)
                        .build()));

        warehouseRepository.findByCode("WH-HN-02")
                .orElseGet(() -> warehouseRepository.save(Warehouse.builder()
                        .code("WH-HN-02")
                        .name("Kho Phân Phối WinMart Nam Hà Nội")
                        .location("Số 15 Tam Trinh, Hoàng Mai, Hà Nội")
                        .capacity(5000.0)
                        .active(true)
                        .build()));

        // 4. Khởi tạo Nhà cung cấp mẫu (Suppliers)
        Supplier vnmSupplier = supplierRepository.findByCode("SUP-VNM")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .code("SUP-VNM")
                        .name("Công ty Cổ phần Sữa Việt Nam (Vinamilk)")
                        .contactName("Nguyễn Đức Thịnh")
                        .phone("02854155555")
                        .email("contact@vinamilk.com.vn")
                        .address("Số 10 Tân Trào, P. Tân Phú, Quận 7, TP.HCM")
                        .build()));

        Supplier msnSupplier = supplierRepository.findByCode("SUP-MSN")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .code("SUP-MSN")
                        .name("Công ty Cổ phần Hàng Tiêu dùng Masan (Masan Consumer)")
                        .contactName("Phạm Văn Hoàng")
                        .phone("02862563862")
                        .email("contact@masangroup.com")
                        .address("Tầng 12, Tòa nhà MPlaza Saigon, 39 Lê Duẩn, Q1, TP.HCM")
                        .build()));

        Supplier ackSupplier = supplierRepository.findByCode("SUP-ACK")
                .orElseGet(() -> supplierRepository.save(Supplier.builder()
                        .code("SUP-ACK")
                        .name("Công ty Cổ phần Acecook Việt Nam")
                        .contactName("Trần Thanh Sơn")
                        .phone("02838154064")
                        .email("info@acecookvietnam.com")
                        .address("Lô II-3, KCN Tân Bình, Tây Thạnh, Tân Phú, TP.HCM")
                        .build()));

        // 5. Khởi tạo Ngành hàng (Categories)
        Category catMilk = categoryRepository.findByCode("CAT-MILK")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .code("CAT-MILK")
                        .name("Sữa & Chế phẩm từ sữa")
                        .description("Sữa tươi, sữa chua, bơ sữa phô mai các loại")
                        .build()));

        Category catNoodle = categoryRepository.findByCode("CAT-NOODLE")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .code("CAT-NOODLE")
                        .name("Mì ăn liền & Thực phẩm khô")
                        .description("Mì tôm, phở gói, miến, bún khô")
                        .build()));

        Category catSauce = categoryRepository.findByCode("CAT-SAUCE")
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .code("CAT-SAUCE")
                        .name("Gia vị & Nước chấm")
                        .description("Nước mắm, dầu ăn, hạt nêm, tương ớt")
                        .build()));

        // 6. Khởi tạo Sản phẩm mẫu (Products)
        Product p1 = productRepository.findBySku("VNM-100-1L")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .sku("VNM-100-1L")
                        .name("Sữa tươi tiệt trùng Vinamilk 100% Không đường 1L")
                        .barcode("8934673123456")
                        .unit("Hộp")
                        .purchasePrice(new BigDecimal("30000"))
                        .sellingPrice(new BigDecimal("38000"))
                        .minStockLevel(50)
                        .category(catMilk)
                        .supplier(vnmSupplier)
                        .active(true)
                        .build()));

        Product p2 = productRepository.findBySku("ACK-HH-TC")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .sku("ACK-HH-TC")
                        .name("Mì Hảo Hảo tôm chua cay 75g")
                        .barcode("8934563148261")
                        .unit("Gói")
                        .purchasePrice(new BigDecimal("3500"))
                        .sellingPrice(new BigDecimal("4500"))
                        .minStockLevel(200)
                        .category(catNoodle)
                        .supplier(ackSupplier)
                        .active(true)
                        .build()));

        Product p3 = productRepository.findBySku("MSN-NN-500")
                .orElseGet(() -> productRepository.save(Product.builder()
                        .sku("MSN-NN-500")
                        .name("Nước mắm Nam Ngư Đệ Nhị 500ml")
                        .barcode("8936017361234")
                        .unit("Chai")
                        .purchasePrice(new BigDecimal("22000"))
                        .sellingPrice(new BigDecimal("28000"))
                        .minStockLevel(40)
                        .category(catSauce)
                        .supplier(msnSupplier)
                        .active(true)
                        .build()));

        // 7. Khởi tạo Lô hàng mẫu (ProductBatches) & Tồn kho ban đầu
        // Đặc biệt: Tạo 2 lô Sữa Vinamilk với HSD khác nhau để chứng minh thuật toán FEFO và cảnh báo cận date!
        if (inventoryRepository.count() == 0) {
            LocalDate today = LocalDate.now();

            // Lô A: Cận date (còn 12 ngày) - 100 hộp
            ProductBatch batchA = batchRepository.save(ProductBatch.builder()
                    .batchNumber("LOT-VNM-2601")
                    .product(p1)
                    .manufacturingDate(today.minusMonths(5))
                    .expiryDate(today.plusDays(12)) // CẬN DATE <= 15 ngày!
                    .build());

            // Lô B: Date xa (còn 180 ngày) - 300 hộp
            ProductBatch batchB = batchRepository.save(ProductBatch.builder()
                    .batchNumber("LOT-VNM-2602")
                    .product(p1)
                    .manufacturingDate(today.minusDays(10))
                    .expiryDate(today.plusDays(180))
                    .build());

            // Lô C: Mì Hảo Hảo - 500 gói
            ProductBatch batchC = batchRepository.save(ProductBatch.builder()
                    .batchNumber("LOT-ACK-2601")
                    .product(p2)
                    .manufacturingDate(today.minusDays(20))
                    .expiryDate(today.plusDays(150))
                    .build());

            // Lô D: Nước mắm Nam Ngư - 150 chai
            ProductBatch batchD = batchRepository.save(ProductBatch.builder()
                    .batchNumber("LOT-MSN-2601")
                    .product(p3)
                    .manufacturingDate(today.minusMonths(1))
                    .expiryDate(today.plusDays(365))
                    .build());

            // Tồn kho tại Kho Tổng Quang Minh
            inventoryRepository.save(Inventory.builder().warehouse(hubWarehouse).batch(batchA).quantity(100).build());
            inventoryRepository.save(Inventory.builder().warehouse(hubWarehouse).batch(batchB).quantity(300).build());
            inventoryRepository.save(Inventory.builder().warehouse(hubWarehouse).batch(batchC).quantity(500).build());
            inventoryRepository.save(Inventory.builder().warehouse(hubWarehouse).batch(batchD).quantity(150).build());

            // Ghi biến động kho ban đầu
            movementRepository.save(StockMovement.builder()
                    .warehouse(hubWarehouse).product(p1).batch(batchA)
                    .movementType(MovementType.INBOUND).quantity(100).balanceAfter(100)
                    .referenceCode("INITIAL-SETUP").note("Nạp tồn kho ban đầu lô cận date").build());

            movementRepository.save(StockMovement.builder()
                    .warehouse(hubWarehouse).product(p1).batch(batchB)
                    .movementType(MovementType.INBOUND).quantity(300).balanceAfter(300)
                    .referenceCode("INITIAL-SETUP").note("Nạp tồn kho ban đầu lô date xa").build());

            movementRepository.save(StockMovement.builder()
                    .warehouse(hubWarehouse).product(p2).batch(batchC)
                    .movementType(MovementType.INBOUND).quantity(500).balanceAfter(500)
                    .referenceCode("INITIAL-SETUP").note("Nạp tồn kho ban đầu mì Hảo Hảo").build());

            movementRepository.save(StockMovement.builder()
                    .warehouse(hubWarehouse).product(p3).batch(batchD)
                    .movementType(MovementType.INBOUND).quantity(150).balanceAfter(150)
                    .referenceCode("INITIAL-SETUP").note("Nạp tồn kho ban đầu nước mắm Nam Ngư").build());
        }

        log.info(">> Khởi tạo dữ liệu mẫu WinMart WMS thành công!");
    }
}
