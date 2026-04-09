package com.panda.snapledger.controller;

import com.panda.snapledger.service.csvimport.MozeCsvImporter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/snapledger/import")
@CrossOrigin(origins = "*")
public class ImportController {

    private final MozeCsvImporter mozeCsvImporter;

    public ImportController(MozeCsvImporter mozeCsvImporter) {
        this.mozeCsvImporter = mozeCsvImporter;
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入CSV记账数据")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("recordCount", result.getRecordCount());
        response.put("accountCount", result.getAccountCount());
        response.put("categoryCount", result.getCategoryCount());
        response.put("skippedCount", result.getSkippedCount());
        return response;
    }
}
