-- =========================================
-- MENU DE DÉPART
-- =========================================
-- Insérée une seule fois, à la création de la base.
-- Prix en centimes, temps de préparation en minutes.
-- Le menu reste modifiable depuis l'écran « Menu » de l'application.

INSERT INTO pizza (name, description, price, preparation_time) VALUES
    ('Margherita',   'Tomate, mozzarella, basilic',                          900, 12),
    ('Reine',        'Tomate, mozzarella, jambon, champignons',             1100, 14),
    ('Napolitaine',  'Tomate, mozzarella, anchois, câpres, olives',         1100, 13),
    ('Calzone',      'Pizza soufflée : jambon, mozzarella, oeuf',           1200, 16),
    ('Végétarienne', 'Tomate, mozzarella, poivrons, courgettes, aubergines', 1150, 14),
    ('Orientale',    'Tomate, mozzarella, merguez, poivrons, oignons',      1200, 15),
    ('Chèvre miel',  'Crème, mozzarella, chèvre, miel',                     1250, 14),
    ('Savoyarde',    'Crème, pommes de terre, lardons, reblochon',          1300, 15);
