-- example#Order
CREATE TABLE orders (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    label TEXT,

    PRIMARY KEY (id)
);

-- example#OrderLine
CREATE TABLE order_lines (
    id TEXT NOT NULL DEFAULT (lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6)))),
    order_id TEXT,
    sku TEXT,

    PRIMARY KEY (id),
    FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- Queries

-- example#CreateOrder
INSERT INTO orders (label) VALUES (?) RETURNING id;

-- example#GetOrder
SELECT orders.id, orders.label, ol.id AS ol_id, ol.order_id AS ol_order_id, ol.sku AS ol_sku
FROM orders AS orders
LEFT JOIN order_lines AS ol ON orders.id = ol.order_id
WHERE orders.id = ?;