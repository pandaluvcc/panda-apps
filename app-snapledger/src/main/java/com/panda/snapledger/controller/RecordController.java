package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/snapledger/records")
@CrossOrigin(origins = "*")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    @Operation(summary = "获取记账记录列表")
    public Page<RecordDTO> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recordService.findAll(page, size);
    }

    @GetMapping("/date/{date}")
    @Operation(summary = "按日期获取记账记录")
    public List<RecordDTO> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return recordService.findByDate(date);
    }

    @GetMapping("/month/{year}/{month}")
    @Operation(summary = "按月份获取记账记录")
    public List<RecordDTO> getByMonth(@PathVariable int year, @PathVariable int month) {
        return recordService.findByYearMonth(year, month);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取记账记录")
    public RecordDTO getById(@PathVariable Long id) {
        return recordService.findById(id);
    }

    @PostMapping
    @Operation(summary = "创建记账记录")
    public RecordDTO create(@RequestBody RecordDTO dto) {
        return recordService.create(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新记账记录")
    public RecordDTO update(@PathVariable Long id, @RequestBody RecordDTO dto) {
        return recordService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除记账记录")
    public void delete(@PathVariable Long id) {
        recordService.delete(id);
    }
}
