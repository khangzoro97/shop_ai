-- Customer options for product placement, steered background removal and multiple variants.
ALTER TABLE generation_job ADD COLUMN placement varchar(16);
ALTER TABLE generation_job ADD COLUMN product_name varchar(100);
ALTER TABLE generation_job ADD COLUMN variants integer NOT NULL DEFAULT 1;
-- Shown with the result when OCR finds label text the AI changed.
ALTER TABLE generation_job ADD COLUMN quality_warning varchar(500);

-- MAIN steps keep one URL per variant; OCR steps keep text.
ALTER TABLE generation_step ALTER COLUMN output_url TYPE varchar(2000);
ALTER TABLE generation_step ADD COLUMN output_text varchar(4000);
