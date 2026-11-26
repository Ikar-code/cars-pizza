
PRAGMA foreign_keys = ON;

-- =========================================
-- 1. TABLE PIZZA
-- =========================================

CREATE TABLE pizza (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    description TEXT,

    -- Prix en centimes
    price INTEGER NOT NULL CHECK (price >= 0),

    -- Temps de préparation estimé en minutes
    preparation_time INTEGER NOT NULL DEFAULT 15
        CHECK (preparation_time > 0),

    is_available INTEGER NOT NULL DEFAULT 1
        CHECK (is_available IN (0, 1)),

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT
);


-- =========================================
-- 2. TABLE TICKET
-- =========================================

CREATE TABLE ticket (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_number TEXT NOT NULL UNIQUE,

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING',
            'PREPARING',
            'COMPLETED',
            'CANCELLED'
        )),

    -- Prix total en centimes
    total_price INTEGER NOT NULL DEFAULT 0
        CHECK (total_price >= 0)
);


-- =========================================
-- 3. TABLE TICKET_ITEM
-- =========================================

CREATE TABLE ticket_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    ticket_id INTEGER NOT NULL,
    pizza_id INTEGER,

    -- Informations conservées au moment de la commande
    pizza_name TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),

    -- Prix unitaire en centimes
    unit_price INTEGER NOT NULL CHECK (unit_price >= 0),

    -- Sous-total en centimes
    subtotal INTEGER NOT NULL CHECK (subtotal >= 0),

    FOREIGN KEY (ticket_id)
        REFERENCES ticket(id)
        ON DELETE CASCADE,

    FOREIGN KEY (pizza_id)
        REFERENCES pizza(id)
        ON DELETE SET NULL
);


-- =========================================
-- 4. TABLE INGREDIENT
-- =========================================

CREATE TABLE ingredient (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,

    -- Unités : g = grammes
    --          ml = millilitres
    --          pcs = pièces
    unit TEXT NOT NULL
        CHECK (unit IN ('g', 'ml', 'pcs')),

    current_quantity INTEGER NOT NULL DEFAULT 0
        CHECK (current_quantity >= 0),

    minimum_quantity INTEGER NOT NULL DEFAULT 0
        CHECK (minimum_quantity >= 0),

    -- Prix d'achat en centimes
    purchase_price INTEGER NOT NULL DEFAULT 0
        CHECK (purchase_price >= 0),

    is_available INTEGER NOT NULL DEFAULT 1
        CHECK (is_available IN (0, 1))
);


-- =========================================
-- 5. TABLE PIZZA_INGREDIENT
-- =========================================

CREATE TABLE pizza_ingredient (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    pizza_id INTEGER NOT NULL,
    ingredient_id INTEGER NOT NULL,

    -- Quantité nécessaire pour une pizza
    quantity_required INTEGER NOT NULL
        CHECK (quantity_required > 0),

    FOREIGN KEY (pizza_id)
        REFERENCES pizza(id)
        ON DELETE CASCADE,

    FOREIGN KEY (ingredient_id)
        REFERENCES ingredient(id)
        ON DELETE CASCADE,

    UNIQUE (pizza_id, ingredient_id)
);


-- =========================================
-- 6. TABLE STOCK_MOVEMENT
-- =========================================

CREATE TABLE stock_movement (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    ingredient_id INTEGER NOT NULL,

    movement_type TEXT NOT NULL
        CHECK (movement_type IN (
            'IN',
            'OUT',
            'ADJUSTMENT'
        )),

    -- Quantité positive pour IN et OUT.
    -- Quantité signée pour ADJUSTMENT.
    quantity INTEGER NOT NULL CHECK (
        (movement_type IN ('IN', 'OUT') AND quantity > 0)
        OR movement_type = 'ADJUSTMENT'
    ),

    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,

    FOREIGN KEY (ingredient_id)
        REFERENCES ingredient(id)
        ON DELETE RESTRICT
);


-- =========================================
-- 7. TABLE TICKET_STATUS_HISTORY
-- =========================================
-- TABLE INDÉPENDANTE
-- AUCUNE CLÉ ÉTRANGÈRE

CREATE TABLE ticket_status_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- Identifiant du ticket conservé comme simple valeur
    ticket_id INTEGER NOT NULL,

    old_status TEXT,
    new_status TEXT NOT NULL,

    changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CHECK (
        old_status IS NULL
        OR old_status IN (
            'PENDING',
            'PREPARING',
            'COMPLETED',
            'CANCELLED'
        )
    ),

    CHECK (new_status IN (
        'PENDING',
        'PREPARING',
        'COMPLETED',
        'CANCELLED'
    ))
);


-- =========================================
-- 8. INDEX POUR OPTIMISER LES RECHERCHES
-- =========================================

CREATE INDEX idx_ticket_status
ON ticket(status);

CREATE INDEX idx_ticket_created_at
ON ticket(created_at);

CREATE INDEX idx_ticket_item_ticket
ON ticket_item(ticket_id);

CREATE INDEX idx_stock_movement_ingredient
ON stock_movement(ingredient_id);

CREATE INDEX idx_pizza_ingredient_pizza
ON pizza_ingredient(pizza_id);

CREATE INDEX idx_history_ticket
ON ticket_status_history(ticket_id);

CREATE INDEX idx_history_changed_at
ON ticket_status_history(changed_at);