-- Create the products table if not present
CREATE TABLE IF NOT EXISTS products (
  id        SERIAL PRIMARY KEY,
  name      VARCHAR(40) NOT NULL,
  stock     BIGINT
);

DELETE FROM products;

INSERT INTO products (name, stock) values ('iPhone', 10);
INSERT INTO products (name, stock) values ('Android', 10);
INSERT INTO products (name, stock) values ('Blackberry', 10);
