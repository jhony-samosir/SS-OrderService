-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- TABLE: orders
-- =============================================================================
CREATE TABLE orders (
    id                         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                  UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(255),
    updated_at                 TIMESTAMPTZ,
    updated_by                 VARCHAR(255),
    deleted_at                 TIMESTAMPTZ,
    deleted_by                 VARCHAR(255),

    user_id                    INT NOT NULL,
    user_public_id             UUID NOT NULL,
    status                     VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'awaiting_payment', 'processing', 'shipped', 'completed', 'cancelled', 'refunded')),
    
    total_amount               DECIMAL(18, 2) NOT NULL,
    subtotal                   DECIMAL(18, 2) NOT NULL,
    tax_amount                 DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    shipping_amount            DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    discount_amount            DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    currency_code              CHAR(3) NOT NULL DEFAULT 'IDR',

    payment_method             VARCHAR(100),
    payment_status             VARCHAR(50) NOT NULL DEFAULT 'unpaid' CHECK (payment_status IN ('unpaid', 'paid', 'failed', 'refunded')),
    payment_reference          VARCHAR(255),

    shipping_courier           VARCHAR(100),
    shipping_service           VARCHAR(100),
    shipping_tracking_number   VARCHAR(255),
    notes                      TEXT,
    estimated_delivery         TIMESTAMPTZ
);

COMMENT ON TABLE orders IS 'Main orders table tracking purchase transactions';
COMMENT ON COLUMN orders.id IS 'Internal autoincrement ID for efficient indexing and FK joins';
COMMENT ON COLUMN orders.public_id IS 'UUID exposed to API layers for external communication';
COMMENT ON COLUMN orders.user_id IS 'Cross-reference ID for user in SS-AuthService';
COMMENT ON COLUMN orders.user_public_id IS 'UUID of the user in SS-AuthService';
COMMENT ON COLUMN orders.status IS 'Lifecycle state: pending | awaiting_payment | processing | shipped | completed | cancelled | refunded';

CREATE INDEX idx_orders_status ON orders (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_user_id ON orders (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_user_public_id ON orders (user_public_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_deleted_at ON orders (id, deleted_at);

-- =============================================================================
-- TABLE: order_items
-- =============================================================================
CREATE TABLE order_items (
    id                  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(255),
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(255),

    order_id            INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          INT NOT NULL,
    product_public_id   UUID NOT NULL,
    product_name        VARCHAR(500) NOT NULL,
    variant_id          INT,
    variant_public_id   UUID,
    variant_name        VARCHAR(255),
    sku                 VARCHAR(255),
    image_url           TEXT,

    unit_price          DECIMAL(18, 2) NOT NULL,
    quantity            INT NOT NULL CHECK (quantity > 0),
    subtotal            DECIMAL(18, 2) NOT NULL,
    discount_amount     DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    total_price         DECIMAL(18, 2) NOT NULL,

    seller_id           INT,
    seller_name         VARCHAR(255)
);

COMMENT ON TABLE order_items IS 'Line items belonging to an order';
COMMENT ON COLUMN order_items.order_id IS 'FK to orders table';
COMMENT ON COLUMN order_items.product_id IS 'Cross-reference to SS-CatalogService products';
COMMENT ON COLUMN order_items.seller_id IS 'Cross-reference to SS-CatalogService sellers';

CREATE INDEX idx_order_items_order_id ON order_items (order_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_items_product_id ON order_items (product_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_order_items_deleted_at ON order_items (id, deleted_at);

-- =============================================================================
-- TABLE: order_addresses
-- =============================================================================
CREATE TABLE order_addresses (
    id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMPTZ,
    deleted_by      VARCHAR(255),

    order_id        INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    type            VARCHAR(50) NOT NULL CHECK (type IN ('shipping', 'billing')),
    recipient_name  VARCHAR(255) NOT NULL,
    phone_number    VARCHAR(50) NOT NULL,
    street_address  TEXT NOT NULL,
    city            VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    postal_code     VARCHAR(20) NOT NULL,
    country_code    CHAR(2) NOT NULL DEFAULT 'ID'
);

COMMENT ON TABLE order_addresses IS 'Historical snapshot of order shipping and billing addresses';

CREATE INDEX idx_order_addresses_order_id ON order_addresses (order_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- TABLE: order_status_histories
-- =============================================================================
CREATE TABLE order_status_histories (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    order_id    INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(50),
    to_status   VARCHAR(50) NOT NULL,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255)
);

COMMENT ON TABLE order_status_histories IS 'Auditable logging of all order lifecycle changes';

CREATE INDEX idx_order_status_histories_order_id ON order_status_histories (order_id);

-- =============================================================================
-- TABLE: outbox_events (Transactional Outbox Pattern)
-- =============================================================================
CREATE TABLE outbox_events (
    id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   INT NOT NULL,
    payload        JSONB NOT NULL,
    status         VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count    INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ,
    error_message  TEXT
);

COMMENT ON TABLE outbox_events IS 'Transactional Outbox: guarantees at-least-once delivery for domain events to message broker';

CREATE INDEX idx_outbox_events_pending ON outbox_events (status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

-- =============================================================================
-- TABLE: inbox_events (Idempotent Event Consumption)
-- =============================================================================
CREATE TABLE inbox_events (
    message_id     VARCHAR(255) PRIMARY KEY,
    event_type     VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(100),
    payload        JSONB NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(50) NOT NULL DEFAULT 'PROCESSED'
);

COMMENT ON TABLE inbox_events IS 'Guarantees message deduplication and idempotency';

CREATE INDEX idx_inbox_events_status ON inbox_events(status);
