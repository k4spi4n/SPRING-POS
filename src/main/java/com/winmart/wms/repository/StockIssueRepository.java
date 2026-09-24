package com.winmart.wms.repository;

import com.winmart.wms.entity.StockIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {
    Optional<StockIssue> findByIssueCode(String issueCode);
    List<StockIssue> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);
}
