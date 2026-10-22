package com.winmart.wms.repository;

import com.winmart.wms.entity.StockIssue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {
    Optional<StockIssue> findByIssueCode(String issueCode);
    boolean existsByIssueCode(String issueCode);

    /** Nạp luôn chi tiết phiếu bằng 1 câu SELECT, tránh N+1 khi liệt kê. */
    @Override
    @EntityGraph(attributePaths = "items")
    List<StockIssue> findAll();

    @EntityGraph(attributePaths = "items")
    List<StockIssue> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    /** Khóa dòng phiếu khi hủy để 2 yêu cầu hủy đồng thời không hoàn tác tồn kho 2 lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockIssue s WHERE s.id = :id")
    Optional<StockIssue> findByIdForUpdate(@Param("id") Long id);
}
