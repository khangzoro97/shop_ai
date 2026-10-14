-- Simple mode: the customer's description and the settings a language model derived from it.
ALTER TABLE generation_job ADD COLUMN brief varchar(1500);
ALTER TABLE generation_job ADD COLUMN prompt_plan varchar(8000);
