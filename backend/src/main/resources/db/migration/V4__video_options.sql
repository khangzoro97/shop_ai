-- Product videos: length, resolution and background music chosen by the customer.
ALTER TABLE generation_job ADD COLUMN video_seconds integer;
ALTER TABLE generation_job ADD COLUMN video_resolution varchar(8);
ALTER TABLE generation_job ADD COLUMN music varchar(16);
