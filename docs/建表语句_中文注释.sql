-- SeatKeeper七张业务表的中文注释阅读版，文件采用UTF-8编码。
-- 本文件不在Flyway迁移目录中，不会随应用启动执行。正式建库请使用原始V1迁移。
-- 历史迁移已经执行过，保留原文是为了维持已有数据库的校验和；本阅读版只增加说明。
-- 注释不参与数据库约束，业务规则以原始SQL、事务实现及团队确认结果为准。

-- 七张业务表；Flyway 的历史表不计入业务表。
-- 用户表：保存学生账户和公开资料，登录与权限规则由上层实现。
CREATE TABLE app_user (
    -- 用户主键，由数据库自动递增。
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 学号或学生账户标识，唯一索引禁止重复。
    student_no VARCHAR(32) NOT NULL,
    -- 用户姓名，使用UTF-8字符集保存中文。
    name VARCHAR(64) NOT NULL,
    -- 密码哈希；演示数据使用禁用登录的占位标记，不能当作实际口令。
    password_hash VARCHAR(255) NOT NULL,
    -- 用户角色，默认学生；角色权限由成员三的认证服务判断。
    role VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    -- 账户状态，默认启用；具体状态变更由上层业务维护。
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (id),
    -- 同一学号只允许一个账户。
    UNIQUE KEY uk_user_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 座位表：使用阅览室、楼层和座位号定位一个实体座位。
CREATE TABLE seat (
    -- 座位主键，由数据库自动递增。
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 阅览室名称，也是座位筛选和位置唯一索引的首列。
    room VARCHAR(64) NOT NULL,
    -- 座位所在楼层。
    floor INT NOT NULL,
    -- 阅览室与楼层内的座位编号。
    seat_no VARCHAR(32) NOT NULL,
    -- 座位状态，默认可用；演示中的DISABLED表示停用。
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (id),
    -- 同一阅览室、楼层和座位号只能定义一个座位。
    UNIQUE KEY uk_seat_location (room, floor, seat_no),
    -- 支持按阅览室、楼层和状态筛选座位，末尾标识用于稳定定位。
    KEY idx_seat_room_floor_status (room, floor, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 固定时段表：预约必须引用已经登记的开始、结束时间组合。
CREATE TABLE slot (
    -- 时段主键，由数据库自动递增。
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 固定时段开始时间，精确到微秒，按上海本地时间传入。
    start_time DATETIME(6) NOT NULL,
    -- 固定时段结束时间，必须晚于开始时间。
    end_time DATETIME(6) NOT NULL,
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (id),
    -- 固定时段的开始时间唯一，便于按开始时间准确取得结束时间。
    UNIQUE KEY uk_slot_start (start_time),
    -- 开始和结束时间组合唯一，供预约的组合外键引用。
    UNIQUE KEY uk_slot_interval (start_time, end_time),
    -- 禁止结束时间早于或等于开始时间。
    CONSTRAINT ck_slot_interval CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 预约表：唯一约束负责同座位、同用户在相同开始时段的活动预约互斥。
CREATE TABLE reservation (
    -- 预约主键，由数据库自动递增。
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 预约所属用户，必须引用已有用户。
    user_id BIGINT NOT NULL,
    -- 预约座位，必须引用已有座位。
    seat_id BIGINT NOT NULL,
    -- 预约时段开始时间，必须出自固定时段表。
    slot_start DATETIME(6) NOT NULL,
    -- 预约时段结束时间，插入时从固定时段取得，不能由客户端任意指定。
    slot_end DATETIME(6) NOT NULL,
    -- 预约状态：已预约、已签到、已取消、已过期、已完成；完整流转规则由上层校验。
    status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    -- 签到截止时间，由上层业务计算后传入。
    check_in_deadline DATETIME(6) NOT NULL,
    -- 乐观锁版本号，条件更新必须匹配旧版本，成功后递增。
    version INT NOT NULL DEFAULT 0,
    -- 活动座位生成列：仅已预约或已签到状态保存座位标识，其他状态生成空值。
    -- 终止状态生成空值；MySQL唯一索引允许多条空值，使取消或过期后的时段可以重新预约。
    active_seat_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('RESERVED', 'CHECKED_IN') THEN seat_id ELSE NULL END) STORED,
    -- 活动用户生成列：仅已预约或已签到状态保存用户标识，其他状态生成空值。
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('RESERVED', 'CHECKED_IN') THEN user_id ELSE NULL END) STORED,
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (id),
    -- 同一座位在相同开始时段只能存在一条活动预约，数据库裁决并发竞争。
    UNIQUE KEY uk_reservation_active_seat (active_seat_id, slot_start),
    -- 同一用户在相同开始时段只能存在一条活动预约，防止同时占用多个座位。
    UNIQUE KEY uk_reservation_active_user (active_user_id, slot_start),
    -- 支持按用户查询预约历史，并按时段及标识排序。
    KEY idx_reservation_user_slot (user_id, slot_start, id),
    -- 支持预约组合外键校验及固定时间组合查找。
    KEY idx_reservation_slot_interval (slot_start, slot_end),
    -- 预约必须引用已有用户。
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    -- 预约必须引用已有座位。
    CONSTRAINT fk_reservation_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    -- 预约开始时间必须已经登记为固定时段。
    CONSTRAINT fk_reservation_slot_start FOREIGN KEY (slot_start) REFERENCES slot (start_time),
    -- 开始、结束时间必须属于同一个已登记时段，拒绝伪造结束时间。
    CONSTRAINT fk_reservation_slot_interval FOREIGN KEY (slot_start, slot_end) REFERENCES slot (start_time, end_time),
    -- 限定允许存储的预约状态；并不代替上层状态机的流转校验。
    CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED', 'COMPLETED')),
    -- 预约结束时间必须晚于开始时间。
    CONSTRAINT ck_reservation_interval CHECK (slot_end > slot_start),
    -- 版本号不得为负数。
    CONSTRAINT ck_reservation_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用账户表：每个用户一行，既保存信用状态，也作为同用户信用与通知写入的锁行。
CREATE TABLE credit (
    -- 用户标识，同时是主键与外键；注册时应在同一事务中建立信用账户。
    user_id BIGINT NOT NULL,
    -- 当前信用分，默认一百分，数据库限制范围为零至一百。
    score INT NOT NULL DEFAULT 100,
    -- 信用限制截止时间，空值表示未设置截止时间；是否限制由上层规则判断。
    restricted_until DATETIME(6) NULL,
    -- 信用状态版本号，余额与限制截止时间共用此版本，修改成功后递增。
    version INT NOT NULL DEFAULT 0,
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (user_id),
    -- 信用账户必须属于已有用户。
    CONSTRAINT fk_credit_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    -- 信用余额必须在零至一百之间，应用层校验之外再由数据库兜底。
    CONSTRAINT ck_credit_score CHECK (score BETWEEN 0 AND 100),
    -- 信用状态版本号不得为负数。
    CONSTRAINT ck_credit_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 信用流水表：记录每次信用变化，按预约与原因的唯一组合实现数据库幂等防重。
CREATE TABLE credit_log (
    -- 流水主键，由数据库自动递增。
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 流水所属用户，写入前必须验证与预约归属一致。
    user_id BIGINT NOT NULL,
    -- 引起信用变化的预约标识。
    reservation_id BIGINT NOT NULL,
    -- 本次信用分变化量，负数扣分、正数加分。
    delta INT NOT NULL,
    -- 稳定的业务原因码，使用区分大小写的排序规则；同预约不同原因可分别记账。
    reason VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    -- 本次变动后的信用余额，与账户余额在同一事务内保存。
    balance_after INT NOT NULL,
    -- 流水创建时间，默认数据库当前时间，精确到微秒。
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (id),
    -- 同一预约、同一原因只允许一条流水；重放时还须核验用户与分值一致。
    UNIQUE KEY uk_credit_log_reservation_reason (reservation_id, reason),
    -- 支持按用户检索并按流水标识排序。
    KEY idx_credit_log_user_id (user_id, id),
    -- 流水必须属于已有用户。
    CONSTRAINT fk_credit_log_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    -- 流水必须引用已有预约。
    CONSTRAINT fk_credit_log_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    -- 流水记录的变动后余额也必须在零至一百之间。
    CONSTRAINT ck_credit_log_balance CHECK (balance_after BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 通知表：记录用户通知，按用户和递增标识分页或补拉，历史清理只处理已读记录。
CREATE TABLE notification (
    -- 通知全局自增主键，用作补拉游标；同用户标识不要求连续。
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 通知所属用户，读取和已读更新必须同时带用户条件。
    user_id BIGINT NOT NULL,
    -- 通知类型，由上层约定稳定的业务类型码。
    type VARCHAR(32) NOT NULL,
    -- 通知正文，最多一千个字符。
    content VARCHAR(1000) NOT NULL,
    -- 是否已读，默认为未读；数据库只接受零或一。
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    -- 通知创建时间，用于保留期清理，精确到微秒。
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- 主键保证记录唯一，并作为InnoDB聚簇索引。
    PRIMARY KEY (id),
    -- 支持用户隔离、标识倒序分页和标识正序游标补拉。
    KEY idx_notification_user_id (user_id, id),
    -- 支持先过滤已读状态，再按创建时间与标识有界清理。
    KEY idx_notification_read_created (read_flag, created_at, id),
    -- 通知必须属于已有用户。
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    -- 已读标记只允许零或一。
    CONSTRAINT ck_notification_read CHECK (read_flag IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
