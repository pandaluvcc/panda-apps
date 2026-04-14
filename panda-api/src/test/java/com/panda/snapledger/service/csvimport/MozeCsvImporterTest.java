package com.panda.snapledger.service.csvimport;

import com.panda.PandaApplication;
import com.panda.snapledger.domain.Account;
import com.panda.snapledger.domain.Category;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.AccountRepository;
import com.panda.snapledger.repository.CategoryRepository;
import com.panda.snapledger.repository.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PandaApplication.class)
@Transactional
class MozeCsvImporterTest {

    @Autowired
    private MozeCsvImporter mozeCsvImporter;

    @Autowired
    private RecordRepository recordRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        recordRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void testImportGbkEncodedCsv() throws Exception {
        String csvContent = "账户,币种,记录类型,主类别,子类别,金额,手续费,折扣,名称,商家,日期,时间,项目,描述,标签,对象\n" +
                "招商银行信用卡,CNY,支出,饮食,午餐,-50.00,0,0,,,2024/1/15,12:30,生活开支,,,\n" +
                "招商银行°,CNY,收入,收入,薪水,10000,0,0,,,2024/1/15,09:00,生活开支,,,\n";

        byte[] gbkBytes = csvContent.getBytes(Charset.forName("GBK"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", gbkBytes);

        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);

        assertEquals(2, result.getRecordCount());
        assertEquals(2, result.getAccountCount());
        assertEquals(2, result.getCategoryCount());

        assertEquals(2, recordRepository.count());
        assertTrue(accountRepository.existsByName("招商银行信用卡"));
        assertTrue(accountRepository.existsByName("招商银行"));

        Category expenseCat = categoryRepository.findByMainCategoryAndSubCategoryAndType("饮食", "午餐", "支出");
        assertNotNull(expenseCat);

        Category incomeCat = categoryRepository.findByMainCategoryAndSubCategoryAndType("收入", "薪水", "收入");
        assertNotNull(incomeCat);
    }

    @Test
    void testImportUtf8EncodedCsv() throws Exception {
        String csvContent = "账户,币种,记录类型,主类别,子类别,金额,手续费,折扣,名称,商家,日期,时间,项目,描述,标签,对象\n" +
                "支付宝,CNY,支出,购物,日用品,-100.00,0,0,,,2024/1/16,14:00,生活开支,,,\n";

        byte[] utf8Bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", utf8Bytes);

        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);

        assertEquals(1, result.getRecordCount());
        assertEquals(1, result.getAccountCount());
        assertTrue(accountRepository.existsByName("支付宝"));
    }

    @Test
    void testCategoryWithTypeDeduplication() throws Exception {
        String csvContent = "账户,币种,记录类型,主类别,子类别,金额,手续费,折扣,名称,商家,日期,时间,项目,描述,标签,对象\n" +
                "账户A,CNY,支出,个人,借款,-100,0,0,,,2024/1/1,12:00,,,,\n" +
                "账户A,CNY,收入,个人,借款,200,0,0,,,2024/1/2,12:00,,,,\n";

        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", bytes);

        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);

        assertEquals(2, result.getCategoryCount());

        Category expenseCat = categoryRepository.findByMainCategoryAndSubCategoryAndType("个人", "借款", "支出");
        Category incomeCat = categoryRepository.findByMainCategoryAndSubCategoryAndType("个人", "借款", "收入");

        assertNotNull(expenseCat);
        assertNotNull(incomeCat);
        assertNotEquals(expenseCat.getId(), incomeCat.getId());
    }

    @Test
    void testAccountDeduplication() throws Exception {
        String csvContent = "账户,币种,记录类型,主类别,子类别,金额,手续费,折扣,名称,商家,日期,时间,项目,描述,标签,对象\n" +
                "同一账户,CNY,支出,饮食,午餐,-50,0,0,,,2024/1/1,12:00,,,,\n" +
                "同一账户,CNY,支出,购物,日用品,-100,0,0,,,2024/1/2,12:00,,,,\n" +
                "同一账户,CNY,收入,收入,薪水,5000,0,0,,,2024/1/3,12:00,,,,\n";

        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", bytes);

        MozeCsvImporter.ImportResult result = mozeCsvImporter.importFromCsv(file);

        assertEquals(3, result.getRecordCount());
        assertEquals(1, result.getAccountCount());
        assertEquals(1, accountRepository.count());
    }

    @Test
    void testSpecialCharactersInAccountName() throws Exception {
        String csvContent = "账户,币种,记录类型,主类别,子类别,金额,手续费,折扣,名称,商家,日期,时间,项目,描述,标签,对象\n" +
                "招商银行°,CNY,支出,饮食,午餐,-50,0,0,,,2024/1/1,12:00,,,,\n";

        byte[] gbkBytes = csvContent.getBytes(Charset.forName("GBK"));
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", gbkBytes);

        mozeCsvImporter.importFromCsv(file);

        assertTrue(accountRepository.existsByName("招商银行"));

        Record record = recordRepository.findAll().get(0);
        assertEquals("招商银行", record.getAccount());
    }
}
