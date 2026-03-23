package com.panda.snapledger.repository;

import com.panda.snapledger.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByType(String type);

    List<Category> findByMainCategory(String mainCategory);

    Category findByMainCategoryAndSubCategory(String mainCategory, String subCategory);

    Category findByMainCategoryAndSubCategoryAndType(String mainCategory, String subCategory, String type);

    @Query("SELECT CONCAT(c.mainCategory, '|||', COALESCE(c.subCategory, ''), '|||', c.type) FROM Category c")
    Set<String> findAllCategoryKeys();
}
