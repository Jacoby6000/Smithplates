-- example#Order
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT,

    PRIMARY KEY (id)
);

-- example#OrderLine
CREATE TABLE order_lines (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID /* FK -> orders (id) */,
    sku TEXT,

    PRIMARY KEY (id)
);

-- example#OrderLine
ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_order_id
    FOREIGN KEY (order_id) REFERENCES orders (id);