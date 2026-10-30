package com.bugaoshan.service;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.dto.MaterialReviewDetail;
import com.bugaoshan.entity.*;
import com.bugaoshan.mapper.*;
import com.bugaoshan.storage.DownloadFile;
import com.bugaoshan.storage.FileStorageService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** All administrative material mutations share the current material row lock and revision checks. */
@Service
public class MaterialReviewService {
    private final MaterialMapper materials;
    private final MaterialHistoryMapper history;
    private final UserMapper users;
    private final ScoreRecordMapper scores;
    private final FileStorageService files;

    public MaterialReviewService(MaterialMapper materials, MaterialHistoryMapper history, UserMapper users,
                                 ScoreRecordMapper scores, FileStorageService files) {
        this.materials=materials; this.history=history; this.users=users; this.scores=scores; this.files=files;
    }

    public MaterialReviewDetail detail(Long materialId, Long actorId) {
        requireAdmin(actorId);
        Material material=materials.selectById(materialId);
        if(material==null) throw new BusinessException("资料不存在");
        var detail=new MaterialReviewDetail(); BeanUtils.copyProperties(material,detail);
        detail.setRevisions(history.selectRevisions(materialId));
        detail.setAuditHistory(history.selectAuditHistory(materialId));
        return detail;
    }

    public DownloadFile file(Long materialId, Integer revision, Long actorId) {
        requireAdmin(actorId);
        Material current=materials.selectById(materialId);
        if(current==null) throw new BusinessException("资料不存在");
        Material material=revision==null ? current : history.selectRevision(materialId,revision);
        if(material==null || material.getStorageKey()==null || material.getStorageKey().isBlank()) {
            throw new BusinessException("资料版本或文件不存在");
        }
        return new DownloadFile(files.load(material.getStorageKey()),
                material.getOriginalFileName()==null ? material.getMaterialName() : material.getOriginalFileName(),
                material.getContentType()==null ? "application/octet-stream" : material.getContentType(), null);
    }

    @Transactional
    public void approve(Long materialId, Integer revision, Long actorId) {
        requireAdmin(actorId);
        Material material=lockPending(materialId,revision);
        if(materials.reviewRevision(materialId,revision,1,null)!=1) throw changed();
        if(materials.markUploadRewarded(materialId)==1) {
            if(users.earnScore(material.getUploaderId(),2)!=1) throw new BusinessException("上传奖励发放失败");
            User uploader=users.selectById(material.getUploaderId());
            ScoreRecord record=new ScoreRecord(); record.setUserId(material.getUploaderId());
            record.setChangeType(1); record.setChangeAmount(2); record.setBalanceAfter(uploader.getScoreBalance());
            // The record field is shorter than material_name; do not fail approval of a valid long name.
            String description="上传资料审核通过："+material.getMaterialName();
            record.setSourceDesc(description.substring(0,Math.min(description.length(),200)));
            record.setRelatedId(materialId); record.setCreateTime(LocalDateTime.now()); record.setUpdateTime(record.getCreateTime());
            scores.insert(record);
        }
        audit(material,actorId,"approve",null);
    }

    @Transactional
    public void reject(Long materialId, Integer revision, Long actorId, String reason) {
        requireAdmin(actorId); reason=reason(reason);
        Material material=lockPending(materialId,revision);
        if(materials.reviewRevision(materialId,revision,2,reason)!=1) throw changed();
        audit(material,actorId,"reject",reason);
    }

    @Transactional
    public void deactivate(Long materialId, Long actorId, String reason) {
        requireAdmin(actorId); reason=reason(reason);
        Material material=materials.selectForUpdate(materialId);
        if(material==null) throw new BusinessException("资料不存在");
        if(!Integer.valueOf(1).equals(material.getStatus()) || materials.transitionStatus(materialId,1,3)!=1) {
            throw new BusinessException(409,"仅可下架已发布资料");
        }
        audit(material,actorId,"deactivate",reason);
    }

    private Material lockPending(Long id,Integer revision) {
        if(revision==null || revision<1) throw new BusinessException("请提供有效的资料版本");
        Material material=materials.selectForUpdate(id);
        if(material==null) throw new BusinessException("资料不存在");
        if(!revision.equals(material.getRevision()) || !Integer.valueOf(0).equals(material.getStatus())) throw changed();
        return material;
    }

    private BusinessException changed() { return new BusinessException(409,"资料版本或状态已变化，请刷新后重新检查"); }
    private String reason(String value) {
        if(value==null || value.isBlank() || value.trim().length()>500) throw new BusinessException("处理原因应为1至500字");
        return value.trim();
    }
    private void requireAdmin(Long actorId) {
        User actor=actorId==null ? null : users.selectById(actorId);
        if(actor==null || !Integer.valueOf(3).equals(actor.getUserType()) || !Integer.valueOf(1).equals(actor.getStatus())
                || Integer.valueOf(1).equals(actor.getIsDeleted())) throw new BusinessException(403,"仅管理员可审核资料");
    }
    private void audit(Material material,Long actor,String action,String reason) {
        MaterialAuditLog log=new MaterialAuditLog(); log.setMaterialId(material.getMaterialId()); log.setRevision(material.getRevision());
        log.setReviewerId(actor); log.setAction(action); log.setReason(reason); log.setCreateTime(LocalDateTime.now());
        history.insertAudit(log);
    }
}
