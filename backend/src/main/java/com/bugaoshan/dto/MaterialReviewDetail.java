package com.bugaoshan.dto;

import com.bugaoshan.entity.Material;
import com.bugaoshan.entity.MaterialAuditLog;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class MaterialReviewDetail extends Material {
    private List<Material> revisions;
    private List<MaterialAuditLog> auditHistory;
}
