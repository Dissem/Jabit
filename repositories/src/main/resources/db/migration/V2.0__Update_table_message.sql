ALTER TABLE Message ADD COLUMN initial_hash BINARY(64);
ALTER TABLE Message ADD CONSTRAINT initial_hash_unique UNIQUE(initial_hash);