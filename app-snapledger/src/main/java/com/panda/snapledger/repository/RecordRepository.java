package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Record;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
