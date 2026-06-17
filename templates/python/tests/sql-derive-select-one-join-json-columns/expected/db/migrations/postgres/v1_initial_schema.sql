-- example#Order
CREATE TABLE orders (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    label TEXT NOT NULL,

    PRIMARY KEY (id)
);

-- example#OrderLine
CREATE TABLE order_lines (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL /* FK -> orders (id) */,
    sku TEXT NOT NULL,
    fulfillment JSONB NOT NULL,
    ship_to JSONB NOT NULL,

    PRIMARY KEY (id)
);

-- example#OrderLine
ALTER TABLE order_lines
    ADD CONSTRAINT fk_order_lines_order_id
    FOREIGN KEY (order_id) REFERENCES orders (id);