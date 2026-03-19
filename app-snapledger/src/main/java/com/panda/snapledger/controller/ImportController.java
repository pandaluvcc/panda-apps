package com.panda.snapledger.controller;

import com.panda.snapledger.service.import.MozeCsvImporter;
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
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("recordCount", result.getRecordCount());
        response.put("accountCount", result.getAccountCount());
        response.put("categoryCount", result.getCategoryCount());
        return response;
    }
}
