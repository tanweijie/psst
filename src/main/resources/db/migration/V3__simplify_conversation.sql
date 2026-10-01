ALTER TABLE conversation DROP CONSTRAINT direct_pair;
ALTER TABLE conversation DROP CONSTRAINT one_direct_thread;
ALTER TABLE conversation DROP COLUMN user_low_id;
ALTER TABLE conversation DROP COLUMN user_high_id;
ALTER TABLE conversation DROP COLUMN kind;
