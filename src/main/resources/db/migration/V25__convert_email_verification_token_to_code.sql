-- V24(이전 링크 방식)는 이미 PR #31로 main에 merge되어 배포됐을 수 있으므로, 기존 row에
-- 36자 UUID token 값이 남아있을 가능성을 배제할 수 없다 - CHANGE COLUMN이 MySQL strict
-- mode에서 실패하지 않도록(Data truncated) 먼저 비운다. 이 테이블은 최대 5분짜리 인증
-- 코드만 담으므로 지워도 되는 데이터다.
DELETE FROM email_verification_token;
ALTER TABLE email_verification_token DROP INDEX uk_email_verification_token_token;
ALTER TABLE email_verification_token CHANGE COLUMN token code VARCHAR(6) NOT NULL;
ALTER TABLE email_verification_token ADD INDEX idx_email_verification_token_email (email);
