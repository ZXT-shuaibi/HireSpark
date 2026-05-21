-- Add phone based authentication support.

ALTER TABLE t_user
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_phone
    ON t_user (phone)
    WHERE phone IS NOT NULL AND deleted = 0;

COMMENT ON COLUMN t_user.phone IS '手机号，唯一';
