-- 签到时间：V1 已执行且不可修改，因此以新增迁移的方式补列。
-- 仅 RESERVED 转 CHECKED_IN 时由服务层写入，历史数据保持 NULL。
ALTER TABLE reservation
    ADD COLUMN checked_in_at DATETIME(6) NULL AFTER check_in_deadline;
