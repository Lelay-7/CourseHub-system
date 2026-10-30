"""Deterministic local-only experience fixtures. Runs once, inside one SQL transaction."""
from datetime import datetime, timedelta
from pathlib import Path
import json
import uuid


def quote(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def pdf(text):
    """A small genuine PDF with an ordinary text stream, no external dependency."""
    stream = ("BT /F1 14 Tf 50 760 Td (" + text.replace("(", "[").replace(")", "]") + ") Tj ET").encode("ascii")
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>", b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>", b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>", b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream"]
    output = b"%PDF-1.4\n"
    offsets = [0]
    for index, item in enumerate(objects, 1):
        offsets.append(len(output))
        output += str(index).encode() + b" 0 obj\n" + item + b"\nendobj\n"
    xref = len(output)
    output += b"xref\n0 6\n0000000000 65535 f \n" + b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
    output += f"trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return output


def seed_preview(config, runtime, execute):
    database, nonce = config["database"], config["nonce"]
    if execute(f"SELECT nonce FROM `{database}`.local_preview_guard;") != nonce:
        raise RuntimeError("Refusing fixtures outside owned preview database")
    # A previous interrupted supervisor may already have committed these exact fixtures.
    if execute(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{database}' AND table_name='product_preview_seed';") == "1":
        saved_nonce = execute(f"SELECT nonce FROM `{database}`.product_preview_seed;")
        if saved_nonce == nonce:
            return
        if saved_nonce:
            raise RuntimeError("Preview seed ownership mismatch")
        # A crash between marker-table creation and transaction commit leaves no data.
        execute(f"DROP TABLE `{database}`.product_preview_seed;")
    accounts = config["accounts"]
    a, b, teacher, admin = [row["user_id"] for row in accounts]
    now = datetime.now().replace(microsecond=0)
    stamp = lambda days=0, minutes=0: (now - timedelta(days=days, minutes=minutes)).strftime("%Y-%m-%d %H:%M:%S")
    statements = [f"USE `{database}`;", "START TRANSACTION;"]
    def insert(table, **values):
        statements.append(f"INSERT INTO `{table}` ({','.join('`'+key+'`' for key in values)}) VALUES ({','.join(quote(value) for value in values.values())});")
    uploads = Path(runtime) / "uploads/preview"
    uploads.mkdir(parents=True, exist_ok=True)
    # Replace the init.sql placeholder files with real files and repair their snapshots.
    for mid in range(1, 7):
        data = pdf(f"CourseHub original preview document {mid}. Local fixture only.")
        (uploads / f"original-{mid}.pdf").write_bytes(data)
        fields = f"file_url=NULL,storage_key='preview/original-{mid}.pdf',original_file_name='preview-original-{mid}.pdf',content_type='application/pdf',file_size={len(data)}"
        statements.append(f"UPDATE material SET {fields} WHERE material_id={mid};")
        statements.append(f"UPDATE material_revision SET {fields} WHERE material_id={mid};")
    for account in accounts:
        uid = account["user_id"]
        statements.append(f"UPDATE user SET user_type={account['user_type']},email={quote(account['email'])},recovery_email={quote(account['email'])},recovery_email_verified_at=NOW(),department='产品体验学院',class_name='体验一班',title={quote('讲师' if uid == teacher else None)},real_name={quote(account['nickname'])} WHERE user_id={uid};")
        statements.append(f"INSERT INTO contact_auth(user_id,phone_visible,email_visible,contact_visible) VALUES({uid},2,2,2) ON DUPLICATE KEY UPDATE phone_visible=2,email_visible=2,contact_visible=2;")
    # More than two pages in every relevant administrative list.
    for index in range(30):
        statements.append(f"INSERT INTO user(user_id,username,password_hash,nickname,user_type,department,status) SELECT {1000+index},'preview_user_{index:02d}',password_hash,'体验同学{index+1:02d}',1,'产品体验学院',{0 if index == 29 else 1} FROM user WHERE user_id={a};")
    for index in range(54):
        cid = 1000 + index
        insert("course", course_id=cid, course_code=f"PV{index+1:03d}", course_name=f"体验课程 {index+1:02d}：{['程序设计','数据分析','工程协作'][index%3]}", course_type=index%3+1, credit=index%4+1, teacher_id=teacher, semester="2026-2027-1", schedule=f"周{['一','二','三','四','五'][index%5]} {1+index%4*2}-{2+index%4*2} 节", location="体验教学楼 A101", outline="课程目标：通过本地练习理解知识点。\n第一单元：基础概念\n第二单元：实验与讨论\n考核：课程实践。", description="仅供产品体验，收藏、评价和加入个人课程清单均不会进行正式教务选课。", category_id=index%6+1, status=0 if index == 53 else 1, create_time=stamp(10, index))
    for index in range(28):
        insert("goods", goods_id=1000+index, goods_name=f"体验兑换品 {index+1:02d}", category=index%3+1, price_score=10+index*3, stock=0 if index == 27 else 30, exchange_limit=0, description="本地测试兑换品，不实际发货或发放权益。兑换后可在订单查看管理员的测试处理说明。", status=0 if index == 26 else 1)
    statements.append("UPDATE goods SET description=CONCAT('【本地演示，不实际发货或发券】',description) WHERE goods_id<1000;")
    for index in range(36):
        mid, revision = 1000+index, 2 if index == 1 else 1
        status = [2, 0, 1, 3][index%4] if index < 8 else 1
        owner = b if index == 2 else (teacher if index%5 == 4 else a)
        reason = "请补充资料来源并检查正文后重新提交。" if status == 2 else None
        name = f"体验资料 {index+1:02d}：{['复习提纲','课堂笔记','实验说明'][index%3]}"
        data = pdf(f"CourseHub preview material {mid}, revision {revision}. Local review and download fixture.")
        key = f"preview/material-{mid}-v{revision}.pdf"
        (Path(runtime)/"uploads"/key).write_bytes(data)
        common = dict(material_id=mid, revision=revision, material_name=name, material_type=index%6+1, course_id=1000+index%12, uploader_id=owner, storage_key=key, original_file_name=f"material-{mid}-v{revision}.pdf", content_type="application/pdf", file_size=len(data), download_score=0 if index%3 == 0 else 10, description="产品体验使用的可下载 PDF，可检查文件、审核、驳回修改和历史版本。", keyword="体验 资料 PDF", status=status, reject_reason=reason, create_time=stamp(4, index))
        insert("material", **common, upload_rewarded=1 if status in [1,3] else 0)
        insert("material_revision", **common)
        if revision == 2:
            old = dict(common, revision=1, status=2, reject_reason="旧版本缺少来源，已由上传者修改后重新提交。", storage_key=f"preview/material-{mid}-v1.pdf", original_file_name=f"material-{mid}-v1.pdf")
            old_data = pdf(f"CourseHub preview material {mid}, original rejected revision 1.")
            (Path(runtime)/"uploads"/old["storage_key"]).write_bytes(old_data)
            old["file_size"] = len(old_data)
            insert("material_revision", **old)
            insert("material_audit_log", material_id=mid, revision=1, reviewer_id=admin, action="reject", reason=old["reject_reason"], create_time=stamp(3))
        elif status in [1,2,3]:
            insert("material_audit_log", material_id=mid, revision=1, reviewer_id=admin, action="reject" if status == 2 else "approve", reason=reason, create_time=stamp(3))
        if status == 3:
            insert("material_audit_log", material_id=mid, revision=1, reviewer_id=admin, action="deactivate", reason="测试历史版本内容已不适用。", create_time=stamp(2))
    for index in range(25):
        insert("material_report", material_id=1002, reporter_id=a if index%2 == 0 else b, reason=f"体验举报 {index+1:02d}：请核对资料来源。", status=2 if index%3 == 0 else 0, resolution_action="dismiss" if index%3 == 0 else None, resolution_reason="已核查，本测试资料已注明来源。" if index%3 == 0 else None, handled_by=admin if index%3 == 0 else None, handled_time=stamp(1) if index%3 == 0 else None, create_time=stamp(2, index))
        insert("feedback", user_id=a if index%2 == 0 else b, type="功能建议" if index%2 == 0 else "问题反馈", content=f"体验反馈 {index+1:02d}：希望资料说明更清晰。", contact=accounts[index%2]["email"], status=index%2, reply="已收到建议，体验版本已补充说明。" if index%2 else None, reply_time=stamp(1) if index%2 else None, create_time=stamp(2,index))
        insert("news", title=f"体验学习资讯 {index+1:02d}", summary="本地资讯列表与详情体验。", content="这是本地测试资讯。可体验阅读、分类筛选及管理员编辑，所有内容仅保存在隔离数据库。", category_id=index%2+2, status=0 if index == 24 else 1, publish_time=stamp(1,index), create_by=admin)
        insert("square_post", post_id=1000+index, user_id=a if index%2 == 0 else b, content=f"体验讨论 {index+1:02d}：课程练习有哪些值得分享的方法？", course_id=1000+index%12, course_name=f"体验课程 {index%12+1:02d}：{['程序设计','数据分析','工程协作'][index%12%3]}", tags="学习,体验", status=0 if index == 24 else 1, create_time=stamp(1,index))
        insert("square_comment", post_id=1000+index, user_id=b if index%2 == 0 else a, content="可以先整理知识点，再结合实践复习。", create_time=stamp(1,index-1))
    for account in accounts:
        uid, balance, total = account["user_id"], 3000, 3000
        insert("score_record", user_id=uid, change_type=1, change_amount=3000, balance_after=balance, source_desc="体验环境初始积分", create_time=stamp(65))
        for index in range(65):
            change = 5 if index%2 == 0 else -2
            balance += change
            total += max(change,0)
            insert("score_record", user_id=uid, change_type=1 if change>0 else 2, change_amount=change, balance_after=balance, source_desc="体验学习奖励" if change>0 else "体验资料下载", create_time=stamp(64-index))
        if uid in [a,b]:
            for index in range(13):
                oid = (1000 if uid == a else 2000) + index
                status = index%4+1
                insert("order", order_id=oid, order_no=f"PREVIEW{oid}", user_id=uid, goods_id=1000, goods_name="体验兑换品 01", score_amount=10, status=status, remark="本地体验订单", fulfillment_note="测试已处理：无需线下领取，不实际发货。" if status in [2,3] else None, complete_time=stamp() if status == 3 else None, cancel_time=stamp() if status == 4 else None, create_time=stamp(0,50-index))
                balance -= 10
                insert("score_record", user_id=uid, change_type=2, change_amount=-10, balance_after=balance, source_desc="兑换体验兑换品 01", related_id=oid, create_time=stamp(0,50-index))
                if status == 4:
                    balance += 10
                    insert("score_record", user_id=uid, change_type=3, change_amount=10, balance_after=balance, source_desc="取消订单退还积分", related_id=oid, create_time=stamp(0,49-index))
        statements.append(f"UPDATE user SET score_balance={balance},total_score={total} WHERE user_id={uid};")
    for cid in [1000,1001]:
        insert("course_selection", course_id=cid, user_id=a, status=1)
        statements.append(f"UPDATE course SET selection_count=selection_count+1 WHERE course_id={cid};")
    insert("favorite", user_id=a, target_type=1, target_id=1000)
    insert("favorite", user_id=a, target_type=2, target_id=1002)
    insert("friend", user_id=a, target_id=b, status=1)
    insert("friend", user_id=teacher, target_id=a, status=0)
    insert("chat_conversation", conversation_id=1000, conversation_type="PRIVATE", private_user_low_id=min(a,b), private_user_high_id=max(a,b), last_sequence=36, last_message_at=stamp(), created_at=stamp(1), updated_at=stamp())
    for uid in [a,b]:
        insert("chat_conversation_member", conversation_id=1000, user_id=uid, last_read_sequence=34, joined_at=stamp(1), updated_at=stamp())
    for index in range(36):
        insert("chat_message", conversation_id=1000, sequence=index+1, sender_id=a if index%2 == 0 else b, client_message_id=str(uuid.uuid4()), message_type="TEXT", content=json.dumps({"text": f"体验历史 {index+1:02d}：检查滚动、分页与未读。"}, ensure_ascii=False), created_at=stamp(0,36-index))
    # Marker uses a table created before the transaction because MySQL DDL auto-commits.
    execute(f"CREATE TABLE `{database}`.product_preview_seed(nonce CHAR(32) PRIMARY KEY);")
    insert("product_preview_seed", nonce=nonce)
    statements.append("COMMIT;")
    try:
        execute("\n".join(statements))
    except Exception:
        # mysql.exe disconnect rolls back the transaction; allow a clean retry.
        execute(f"DROP TABLE `{database}`.product_preview_seed;")
        raise
