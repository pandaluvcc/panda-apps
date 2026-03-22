package com.panda.snapledger.controller;

import com.panda.snapledger.controller.dto.CalendarMonthDTO;
import com.panda.snapledger.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/snapledger/calendar")
@CrossOrigin(origins = "*")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/{year}/{month}")
    @Operation(summary = "获取日历月度数据")
    public CalendarMonthDTO getMonthCalendar(@PathVariable int year, @PathVariable int month) {
        return calendarService.getMonthCalendar(year, month);
    }
}
