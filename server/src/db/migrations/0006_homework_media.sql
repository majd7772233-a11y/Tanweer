-- Migration 0006: Add media_urls support to homeworks table
ALTER TABLE homeworks ADD COLUMN media_urls TEXT;
