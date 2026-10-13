-- Stored copies of the uploaded images ("slot=url" per line), used by "Làm lại" (retry).
ALTER TABLE generation_job ADD COLUMN input_images varchar(2000);
