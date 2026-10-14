-- Stock photos showed real brands (Act+Acre, Curology...); DataSeeder replaces them with demos made by the pipeline.
DELETE FROM portfolio_item WHERE sample = true;
