package com.panda.snapledger.controller;

import com.panda.snapledger.domain.Category;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
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
    public List<Category> getAll() {
        return categoryRepository.findAll();
    }

    @GetMapping("/type/{type}")
    public List<Category> getByType(@PathVariable String type) {
        return categoryRepository.findByType(type);
    }

    @GetMapping("/main-categories/{recordType}")
    public List<String> getMainCategories(@PathVariable String recordType) {
        return recordRepository.findDistinctMainCategoriesByRecordType(recordType);
    }

    @GetMapping("/sub-categories/{mainCategory}")
    public List<String> getSubCategories(@PathVariable String mainCategory) {
        return recordRepository.findDistinctSubCategoriesByMainCategory(mainCategory);
    }

    @PostMapping
    public Category create(@RequestBody Category category) {
        return categoryRepository.save(category);
    }
}
