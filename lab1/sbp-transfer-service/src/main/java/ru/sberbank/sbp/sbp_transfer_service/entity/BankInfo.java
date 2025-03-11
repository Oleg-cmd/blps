package ru.sberbank.sbp.sbp_transfer_service.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BankInfo {
    private String bankId;
    private String bankName;
    private String bankCode;
    private boolean supportsSbp;
}