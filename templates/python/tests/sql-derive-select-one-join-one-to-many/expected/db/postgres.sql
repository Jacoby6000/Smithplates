-- example#Order
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,

    PRIMARY KEY (id)
);

-- example#OrderLine
CREATE TABLE order_lines (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID,
    sku TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- Queries

-- example#CreateOrder
INSERT INTO orders (label) VALUES ($1) RETURNING id;

-- example#GetOrder
SELECT orders.id, orders.label, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.sku AS ol_sku
FROM orders AS orders
LEFT JOIN order_lines AS ol ON orders.id = ol.order_id
WHERE orders.id = $1;