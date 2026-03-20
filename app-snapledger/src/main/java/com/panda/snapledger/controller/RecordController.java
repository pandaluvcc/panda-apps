package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.service.RecordService;
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
    public Page<RecordDTO> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recordService.findAll(page, size);
    }

    @GetMapping("/date/{date}")
    public List<RecordDTO> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return recordService.findByDate(date);
    }

    @GetMapping("/month/{year}/{month}")
    public List<RecordDTO> getByMonth(@PathVariable int year, @PathVariable int month) {
        return recordService.findByYearMonth(year, month);
    }

    @PostMapping
    public RecordDTO create(@RequestBody RecordDTO dto) {
        return recordService.create(dto);
    }

    @PutMapping("/{id}")
    public RecordDTO update(@PathVariable Long id, @RequestBody RecordDTO dto) {
        return recordService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        recordService.delete(id);
    }
}
