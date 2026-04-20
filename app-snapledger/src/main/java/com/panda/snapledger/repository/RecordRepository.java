package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {

    List<Record> findByDateOrderByTimeDesc(LocalDate date);

    List<Record> findByDateBetweenOrderByDateDescTimeDesc(LocalDate start, LocalDate end);

    @Query("SELECT r FROM Record r WHERE YEAR(r.date) = :year AND MONTH(r.date) = :month ORDER BY r.date DESC, r.time DESC")
    List<Record> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT SUM(r.amount) FROM Record r WHERE r.date = :date AND r.recordType = :recordType")
    BigDecimal sumAmountByDateAndRecordType(@Param("date") LocalDate date, @Param("recordType") String recordType);

    @Query("SELECT SUM(r.amount) FROM Record r WHERE YEAR(r.date) = :year AND MONTH(r.date) = :month AND r.recordType = :recordType")
    BigDecimal sumAmountByYearMonthAndRecordType(@Param("year") int year, @Param("month") int month, @Param("recordType") String recordType);

    @Query("SELECT DISTINCT r.mainCategory FROM Record r WHERE r.recordType = :recordType")
    List<String> findDistinctMainCategoriesByRecordType(@Param("recordType") String recordType);

    @Query("SELECT DISTINCT r.subCategory FROM Record r WHERE r.mainCategory = :mainCategory")
    List<String> findDistinctSubCategoriesByMainCategory(@Param("mainCategory") String mainCategory);

    @Query("SELECT r FROM Record r WHERE YEAR(r.date) = :year AND MONTH(r.date) = :month AND r.recordType = :type ORDER BY r.date DESC, r.time DESC")
    List<Record> findByYearAndMonthAndType(@Param("year") int year, @Param("month") int month, @Param("type") String type);

    @Query("SELECT SUM(r.amount) FROM Record r WHERE YEAR(r.date) = :year AND MONTH(r.date) = :month AND r.recordType = '支出'")
    BigDecimal sumExpenseByMonth(@Param("year") int year, @Param("month") int month);

    // 按账户和对账状态查询
    List<Record> findByAccountAndReconciliationStatus(String account, String status);

    // 按账户和对账状态排除查询
    List<Record> findByAccountAndReconciliationStatusNot(String account, String status);

    // 按账户和日期范围查询（用于账单周期）
    List<Record> findByAccountAndDateBetween(String account, LocalDate startDate, LocalDate endDate);

    // 按账户、日期范围和对账状态查询
    List<Record> findByAccountAndDateBetweenAndReconciliationStatusNot(
        String account, LocalDate startDate, LocalDate endDate, String status);

    // 按 ID 列表查询
    List<Record> findByIdIn(List<Long> ids);

    // 按账户查询（按日期时间倒序）
    List<Record> findByAccountOrderByDateDescTimeDesc(String account);

    // 按 target 和对账状态排除查询（用于余额计算中的手动转账记录）
    List<Record> findByTargetAndReconciliationStatusNot(String target, String status);

    // 双向查询转账类记录，按日期时间倒序
    // 转账类 recordType：转账/还款/转出/转入/应付款项/应收款项/分期还款
    @Query("SELECT r FROM Record r WHERE (r.account = :account OR r.target = :account) " +
           "AND r.date BETWEEN :start AND :end " +
           "AND r.recordType IN ('转账','还款','转出','转入','应付款项','应收款项','分期还款') " +
           "ORDER BY r.date DESC, r.time DESC")
    List<Record> findTransfersByAccountOrTargetAndDateBetween(
            @Param("account") String account,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    // 查询一般记录（排除所有转账类 recordType）
    @Query("SELECT r FROM Record r WHERE r.account = :account " +
           "AND r.date BETWEEN :start AND :end " +
           "AND r.recordType NOT IN ('转账','还款','转出','转入','应付款项','应收款项','分期还款') " +
           "ORDER BY r.date DESC, r.time DESC")
    List<Record> findNonTransfersByAccountAndDateBetween(
            @Param("account") String account,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    // 查询收到的还款/转账，排除 POSTPONED，用于计算已还金额
    // ① target=本账户(手动录入) ② account=本账户 AND recordType='转入'(Moze导入)
    @Query("SELECT r FROM Record r WHERE " +
           "((r.target = :account AND r.recordType IN ('转账','还款','应付款项','应收款项')) " +
           " OR (r.account = :account AND r.recordType IN ('转入','分期还款'))) " +
           "AND r.date BETWEEN :start AND :end " +
           "AND (r.reconciliationStatus IS NULL OR r.reconciliationStatus != :status) " +
           "ORDER BY r.date DESC, r.time DESC")
    List<Record> findIncomingTransfersByTargetAndDateBetweenAndStatusNot(
            @Param("account") String account,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("status") String status);

    // 周期事件关联查询
    List<Record> findByRecurringEventIdOrderByDateDesc(Long recurringEventId);

    List<Record> findByRecurringEventIdAndDateAfter(Long recurringEventId, LocalDate date);

    List<Record> findByNameAndRecurringEventIdIsNull(String name);

    // 分期事件关联查询
    List<Record> findByInstallmentEventIdOrderByDateAsc(Long installmentEventId);

    // 删除分期事件前解绑所有关联记录（通过 @Modifying 在 Service 中调用）
    List<Record> findByInstallmentEventId(Long installmentEventId);

    // ========== 应收应付款项 ==========

    List<Record> findByParentRecordId(Long parentRecordId);

    List<Record> findByParentRecordIdIn(List<Long> parentIds);

    @Query("SELECT r FROM Record r WHERE r.parentRecordId IS NULL " +
           "AND ((r.recordType = '应收款项' AND r.amount < 0) " +
           "  OR (r.recordType = '应付款项' AND r.amount > 0)) " +
           "ORDER BY r.date DESC, r.time DESC")
    List<Record> findAllReceivableParents();

    @Query("SELECT r FROM Record r WHERE r.recordType IN ('应收款项','应付款项') " +
           "ORDER BY r.account, r.name, r.recordType, r.date, r.time")
    List<Record> findAllReceivableForLinking();

    @Modifying
    @Query("UPDATE Record r SET r.parentRecordId = NULL " +
           "WHERE r.recordType IN ('应收款项','应付款项')")
    int clearAllReceivableParentLinks();
}
