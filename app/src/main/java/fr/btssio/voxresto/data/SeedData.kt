package fr.btssio.voxresto.data

/** Carte de démonstration, insérée au premier lancement. Modifiable ensuite depuis l'écran « Menu ». */
object SeedData {
    val menu: List<MenuItem> = listOf(
        MenuItem(name = "Samoussa", category = "Entrées", priceCents = 150, aliases = "samosa"),
        MenuItem(name = "Bonbon piment", category = "Entrées", priceCents = 120),
        MenuItem(name = "Rougail saucisse", category = "Plats", priceCents = 1150, aliases = "rougail"),
        MenuItem(name = "Cari poulet", category = "Plats", priceCents = 1200, aliases = "carry poulet,cari"),
        MenuItem(name = "Pizza Margherita", category = "Plats", priceCents = 950),
        MenuItem(name = "Pizza Reine", category = "Plats", priceCents = 1100),
        MenuItem(name = "Burger maison", category = "Plats", priceCents = 1050, aliases = "burger"),
        MenuItem(name = "Salade composée", category = "Plats", priceCents = 800, aliases = "salade"),
        MenuItem(name = "Frites", category = "Accompagnements", priceCents = 350, aliases = "portion de frites"),
        MenuItem(name = "Gâteau patate", category = "Desserts", priceCents = 400),
        MenuItem(name = "Glace vanille", category = "Desserts", priceCents = 350, aliases = "glace"),
        MenuItem(name = "Coca", category = "Boissons", priceCents = 250, aliases = "coca cola"),
        MenuItem(name = "Jus de litchi", category = "Boissons", priceCents = 300),
        MenuItem(name = "Eau", category = "Boissons", priceCents = 150, aliases = "eau plate"),
        MenuItem(name = "Dodo", category = "Boissons", priceCents = 400, aliases = "bière"),
    )
}
