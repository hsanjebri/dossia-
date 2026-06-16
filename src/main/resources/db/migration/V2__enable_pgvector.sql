CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE procedures ADD COLUMN embedding vector(768);
