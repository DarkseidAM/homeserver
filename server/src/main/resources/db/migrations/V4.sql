ALTER TABLE system_history ADD COLUMN storage_data TEXT NOT NULL DEFAULT '{}';
ALTER TABLE system_history ADD COLUMN network_data TEXT NOT NULL DEFAULT '{}';
ALTER TABLE system_history ADD COLUMN system_data TEXT NOT NULL DEFAULT '{}';
