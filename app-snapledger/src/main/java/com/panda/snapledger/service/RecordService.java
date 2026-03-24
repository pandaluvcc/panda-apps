package com.panda.snapledger.service;

import com.panda.snapledger.controller.dto.RecordDTO;
import com.panda.snapledger.domain.Record;
import com.panda.snapledger.repository.RecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecordService {

    private final RecordRepository recordRepository;

    public RecordService(RecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    public List<RecordDTO> findByDate(LocalDate date) {
        return recordRepository.findByDateOrderByTimeDesc(date).stream()
                .map(RecordDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<RecordDTO> findByYearMonth(int year, int month) {
        return recordRepository.findByYearAndMonth(year, month).stream()
                .map(RecordDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public RecordDTO findById(Long id) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        return RecordDTO.fromEntity(record);
    }

    public Page<RecordDTO> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending().and(Sort.by("time").descending()));
        return recordRepository.findAll(pageable).map(RecordDTO::fromEntity);
    }

    @Transactional
    public RecordDTO create(RecordDTO dto) {
        Record record = dto.toEntity();
        if (record.getDate() == null) {
            record.setDate(LocalDate.now());
        }
        Record saved = recordRepository.save(record);
        return RecordDTO.fromEntity(saved);
    }

    @Transactional
    public RecordDTO update(Long id, RecordDTO dto) {
        Record record = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        record.setAccount(dto.getAccount());
        record.setCurrency(dto.getCurrency());
        record.setRecordType(dto.getRecordType());
        record.setMainCategory(dto.getMainCategory());
        record.setSubCategory(dto.getSubCategory());
        record.setAmount(dto.getAmount());
        record.setFee(dto.getFee());
        record.setDiscount(dto.getDiscount());
        record.setName(dto.getName());
        record.setMerchant(dto.getMerchant());
        record.setDate(dto.getDate());
        record.setTime(dto.getTime());
        record.setProject(dto.getProject());
        record.setCount(dto.getCount());
        record.setDescription(dto.getDescription());
        record.setTags(dto.getTags());
        record.setTarget(dto.getTarget());
        return RecordDTO.fromEntity(recordRepository.save(record));
    }

    @Transactional
    public void delete(Long id) {
        recordRepository.deleteById(id);
    }
}
