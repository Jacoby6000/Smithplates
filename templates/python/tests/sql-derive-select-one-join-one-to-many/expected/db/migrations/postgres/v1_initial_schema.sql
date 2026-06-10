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