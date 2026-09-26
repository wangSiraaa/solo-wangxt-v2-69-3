package com.example.loan.web;

import com.example.loan.api.DeferralPolicyRequest;
import com.example.loan.api.DeferralPolicyView;
import com.example.loan.api.DeferralRecordSummaryView;
import com.example.loan.api.DeferralReplayResponse;
import com.example.loan.api.DeferralSimulateRequest;
import com.example.loan.api.DeferralSimulationResponse;
import com.example.loan.service.DeferralService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 宽限与延期模拟接口：延期政策维护、模拟计算与历史记录。
 */
@RestController
@RequestMapping("/api/deferral")
public class DeferralController {

    private final DeferralService deferralService;

    public DeferralController(DeferralService deferralService) {
        this.deferralService = deferralService;
    }

    // ---------- 延期政策 ----------

    @GetMapping("/policies")
    public List<DeferralPolicyView> listPolicies() {
        return deferralService.listPolicies();
    }

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public DeferralPolicyView createPolicy(@Valid @RequestBody DeferralPolicyRequest req) {
        return deferralService.createPolicy(req);
    }

    /** 修改政策（不影响已保存模拟记录的快照）。 */
    @PutMapping("/policies/{id}")
    public DeferralPolicyView updatePolicy(@PathVariable long id,
                                           @Valid @RequestBody DeferralPolicyRequest req) {
        return deferralService.updatePolicy(id, req);
    }

    // ---------- 模拟与历史记录 ----------

    /** 生成原方案 / 延期方案对比并保存记录（含政策快照）。 */
    @PostMapping("/simulations")
    public DeferralSimulationResponse simulate(@Valid @RequestBody DeferralSimulateRequest req) {
        return deferralService.simulateAndSave(req);
    }

    /** 历史模拟记录（列表视图）。 */
    @GetMapping("/simulations")
    public List<DeferralRecordSummaryView> listRecords() {
        return deferralService.listRecords();
    }

    @GetMapping("/simulations/{id}")
    public DeferralSimulationResponse getRecord(@PathVariable long id) {
        return deferralService.getRecord(id);
    }

    /** 按记录保存的政策快照重算，验证历史结果可复现。 */
    @PostMapping("/simulations/{id}/replay")
    public DeferralReplayResponse replay(@PathVariable long id) {
        return deferralService.replay(id);
    }
}
