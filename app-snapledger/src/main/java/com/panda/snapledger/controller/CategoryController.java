package com.panda.snapledger.controller;

import com.panda.snapledger.domain.Category;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapledger/categories")
@CrossOrigin(origins = "*")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final RecordRepository recordRepository;

    public CategoryController(CategoryRepository categoryRepository, RecordRepository recordRepository) {
        this.categoryRepository = categoryRepository;
        this.recordRepository = recordRepository;
    }

    @GetMapping
    @Operation(summary = "获取所有分类")
    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "按类型获取分类")
    public List<Category> getByType(@PathVariable String type) {
        return categoryRepository.findByType(type);
    }

    @GetMapping("/main-categories/{recordType}")
    @Operation(summary = "获取主分类列表")
    public List<String> getMainCategories(@PathVariable String recordType) {
        return recordRepository.findDistinctMainCategoriesByRecordType(recordType);
    }

    @GetMapping("/sub-categories/{mainCategory}")
    @Operation(summary = "获取子分类列表")
    public List<String> getSubCategories(@PathVariable String mainCategory) {
        return recordRepository.findDistinctSubCategoriesByMainCategory(mainCategory);
    }

    @PostMapping
    @Operation(summary = "创建分类")
    public Category create(@RequestBody Category category) {
        return categoryRepository.save(category);
    }
}
