-- 七张业务表；Flyway 的历史表不计入业务表。
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_no VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'STUDENT',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_student_no (student_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seat (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room VARCHAR(64) NOT NULL,
    floor INT NOT NULL,
    seat_no VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    PRIMARY KEY (id),
    UNIQUE KEY uk_seat_location (room, floor, seat_no),
    KEY idx_seat_room_floor_status (room, floor, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE slot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_slot_start (start_time),
    UNIQUE KEY uk_slot_interval (start_time, end_time),
    CONSTRAINT ck_slot_interval CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    slot_start DATETIME(6) NOT NULL,
    slot_end DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    check_in_deadline DATETIME(6) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    active_seat_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('RESERVED', 'CHECKED_IN') THEN seat_id ELSE NULL END) STORED,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('RESERVED', 'CHECKED_IN') THEN user_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservation_active_seat (active_seat_id, slot_start),
    UNIQUE KEY uk_reservation_active_user (active_user_id, slot_start),
    KEY idx_reservation_user_slot (user_id, slot_start, id),
    KEY idx_reservation_slot_interval (slot_start, slot_end),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_reservation_seat FOREIGN KEY (seat_id) REFERENCES seat (id),
    CONSTRAINT fk_reservation_slot_start FOREIGN KEY (slot_start) REFERENCES slot (start_time),
    CONSTRAINT fk_reservation_slot_interval FOREIGN KEY (slot_start, slot_end) REFERENCES slot (start_time, end_time),
    CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED', 'COMPLETED')),
    CONSTRAINT ck_reservation_interval CHECK (slot_end > slot_start),
    CONSTRAINT ck_reservation_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE credit (
    user_id BIGINT NOT NULL,
    score INT NOT NULL DEFAULT 100,
    restricted_until DATETIME(6) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_credit_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_credit_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_credit_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE credit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    balance_after INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_credit_log_reservation_reason (reservation_id, reason),
    KEY idx_credit_log_user_id (user_id, id),
    CONSTRAINT fk_credit_log_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_credit_log_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT ck_credit_log_balance CHECK (balance_after BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_notification_user_id (user_id, id),
    KEY idx_notification_read_created (read_flag, created_at, id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_notification_read CHECK (read_flag IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
