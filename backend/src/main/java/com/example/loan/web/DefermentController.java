package com.example.loan.web;

import com.example.loan.api.DefermentRecordView;
import com.example.loan.api.DefermentRequest;
import com.example.loan.api.DefermentResponse;
import com.example.loan.api.ReproduceResult;
import com.example.loan.service.DefermentRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 宽限与延期模拟接口：模拟计算、历史记录与按快照复现。
 */
@RestController
@RequestMapping("/api/deferments")
public class DefermentController {

    private final DefermentRecordService defermentRecordService;

    public DefermentController(DefermentRecordService defermentRecordService) {
        this.defermentRecordService = defermentRecordService;
    }

    /** 生成原方案 / 延期方案对比，保存政策快照与结果。 */
    @PostMapping("/simulate")
    public DefermentResponse simulate(@Valid @RequestBody DefermentRequest req) {
        return defermentRecordService.simulateAndSave(req);
    }

    /** 历史模拟记录（列表视图）。 */
    @GetMapping
    public List<DefermentRecordView> list() {
        return defermentRecordService.listRecords();
    }

    @GetMapping("/{id}")
    public DefermentResponse get(@PathVariable long id) {
        return defermentRecordService.getRecord(id);
    }

    /** 按保存的政策快照复现历史结果（政策之后被修改也不受影响）。 */
    @GetMapping("/{id}/reproduce")
    public ReproduceResult reproduce(@PathVariable long id) {
        return defermentRecordService.reproduce(id);
    }
}
