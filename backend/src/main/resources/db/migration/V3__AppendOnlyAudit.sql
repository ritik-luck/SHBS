-- Add new columns for rich compliance metadata
ALTER TABLE audit_log ADD COLUMN actor VARCHAR(255);
ALTER TABLE audit_log ADD COLUMN request_id VARCHAR(255);
ALTER TABLE audit_log ADD COLUMN ip_address VARCHAR(255);
ALTER TABLE audit_log ADD COLUMN target_resource VARCHAR(255);
ALTER TABLE audit_log ADD COLUMN reason TEXT;
ALTER TABLE audit_log ADD COLUMN before_after_state TEXT;

-- Restrict application database user from updating or deleting audit logs
-- (Assuming the application user is 'shbs_user'. Replace 'shbs_user' with the actual runtime user if different, 
-- but for MySQL root it won't apply directly. In a real-world scenario, you use least privilege).
-- Note: We wrap this in a safe block or just comment it out if 'shbs_user' doesn't exist in local dev.
-- REVOKE UPDATE, DELETE ON audit_log FROM 'shbs_user'@'%';
