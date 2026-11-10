-- 仅供专用演示库；调用方可以预先设置固定演示日期。
SET @demo_date = COALESCE(@demo_date, DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY));

-- 这些虚构记录没有可登录密码，成员3接入认证后另行创建真实账户。
INSERT INTO app_user (id, student_no, name, password_hash, role, status) VALUES
    (10001, 'DEMO001', '演示学生甲', 'LOGIN_DISABLED_DEMO_ONLY', 'STUDENT', 'ACTIVE'),
    (10002, 'DEMO002', '演示学生乙', 'LOGIN_DISABLED_DEMO_ONLY', 'STUDENT', 'ACTIVE'),
    (10003, 'DEMO003', '演示学生丙', 'LOGIN_DISABLED_DEMO_ONLY', 'STUDENT', 'ACTIVE')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO seat (id, room, floor, seat_no, status) VALUES
    (10001, '第一阅览室', 1, 'A01', 'AVAILABLE'), (10002, '第一阅览室', 1, 'A02', 'AVAILABLE'),
    (10003, '第一阅览室', 1, 'A03', 'AVAILABLE'), (10004, '第一阅览室', 1, 'A04', 'AVAILABLE'),
    (10005, '第二阅览室', 2, 'B01', 'AVAILABLE'), (10006, '第二阅览室', 2, 'B02', 'AVAILABLE'),
    (10007, '第二阅览室', 2, 'B03', 'AVAILABLE'), (10008, '第二阅览室', 2, 'B04', 'DISABLED')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO slot (id, start_time, end_time) VALUES
    (10001, TIMESTAMP(@demo_date, '08:00:00'), TIMESTAMP(@demo_date, '10:00:00')),
    (10002, TIMESTAMP(@demo_date, '10:00:00'), TIMESTAMP(@demo_date, '12:00:00')),
    (10003, TIMESTAMP(@demo_date, '12:00:00'), TIMESTAMP(@demo_date, '14:00:00')),
    (10004, TIMESTAMP(@demo_date, '14:00:00'), TIMESTAMP(@demo_date, '16:00:00')),
    (10005, TIMESTAMP(@demo_date, '16:00:00'), TIMESTAMP(@demo_date, '18:00:00')),
    (10006, TIMESTAMP(@demo_date, '18:00:00'), TIMESTAMP(@demo_date, '20:00:00')),
    (10007, TIMESTAMP(@demo_date, '20:00:00'), TIMESTAMP(@demo_date, '22:00:00'))
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO reservation (id, user_id, seat_id, slot_start, slot_end, status, check_in_deadline, version) VALUES
    (10001, 10001, 10001, TIMESTAMP(@demo_date, '08:00:00'), TIMESTAMP(@demo_date, '10:00:00'), 'RESERVED', TIMESTAMP(@demo_date, '08:15:00'), 0),
    (10002, 10002, 10002, TIMESTAMP(@demo_date, '10:00:00'), TIMESTAMP(@demo_date, '12:00:00'), 'EXPIRED', TIMESTAMP(@demo_date, '10:15:00'), 1)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO credit (user_id, score, restricted_until, version) VALUES
    (10001, 100, NULL, 0), (10002, 90, NULL, 1), (10003, 100, NULL, 0)
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO credit_log (id, user_id, reservation_id, delta, reason, balance_after, created_at) VALUES
    (10001, 10002, 10002, -10, 'NO_SHOW', 90, TIMESTAMP(@demo_date, '10:16:00'))
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO notification (id, user_id, type, content, read_flag, created_at) VALUES
    (10001, 10001, 'RESERVATION', '预约成功：第一阅览室 A01，08:00—10:00。', FALSE, TIMESTAMP(@demo_date, '07:00:00')),
    (10002, 10002, 'CREDIT', '演示记录：预约过期，信用扣减10分。', TRUE, TIMESTAMP(@demo_date, '10:16:00'))
ON DUPLICATE KEY UPDATE id = id;
