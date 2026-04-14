package com.panda.snapledger.controller.dto;

import java.util.List;

/**
 * 批量更新子账户请求 DTO
 */
public class BatchUpdateSubRequest {
    private Long masterId;
    private List<Long> subAccountIds;
    private String action;  // "LINK" 或 "UNLINK"

    public Long getMasterId() {
        return masterId;
    }

    public void setMasterId(Long masterId) {
        this.masterId = masterId;
    }

    public List<Long> getSubAccountIds() {
        return subAccountIds;
    }

    public void setSubAccountIds(List<Long> subAccountIds) {
        this.subAccountIds = subAccountIds;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
