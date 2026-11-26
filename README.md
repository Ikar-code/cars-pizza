# VoxResto — prise de commande vocale pour pizzeria

Application Android native (Kotlin, Jetpack Compose) pour une pizzeria où **un seul employé** prépare les pizzas
et prend les commandes. Il dicte la commande à voix haute, les mains libres : l'application crée un **ticket**,
il le **valide** (ou le corrige / l'annule), puis le ticket rejoint la **liste des commandes à préparer**.

> « Deux margherita et une reine, c'est tout. » → 2 × Margherita + 1 × Reine, ticket validé, total annoncé.

Documents du projet : [`docs/cahier-des-charges.md`](docs/cahier-des-charges.md) (v1.0, novembre 2026) et
[`docs/schema.sql`](docs/schema.sql) (schéma SQLite).

## Fonctionnalités (cahier des charges §6)

| Réf. | Fonctionnalité | Où |
|---|---|---|
| F1 | Commande vocale : bouton micro, dictée → texte | **Gemini 3.8 Live** (`voice/GeminiLiveClient.kt`), repli sur la reconnaissance Android |
| F2 | Micro externe (USB / filaire) s'il est branché, sinon micro de l'appareil | `voice/AudioCapture.kt` (`MicSelector`) |
| F3 | Analyse de la commande : pizzas du menu + quantités | outils Gemini (`voice/OrderTools.kt`) + `voice/CommandParser.kt` |
| F4 | Création du ticket : numéro, date/heure, lignes, total | `data/OrderRepository.kt` |
| F5 | Validation : relire, valider, corriger (+/−) ou annuler avant enregistrement | écran **Commande** |
| F6 | Liste des commandes, triée par ordre d'arrivée | écran **Commandes** → « En cours » |
| F7 | Suivi de l'état : en attente → en préparation → terminée | écran **Commandes** |
| F8 | Saisie manuelle si la voix échoue (champ texte + « Ajouter à la main ») | écran **Commande** |
| F9 | Gestion du menu : ajout, modification, suppression, prix, disponibilité | écran **Menu** |
| F10 | Historique des commandes terminées (et annulées) | écran **Commandes** → « Historique » |

### Cycle de vie d'un ticket (§7)

1. L'employé appuie sur le micro et dicte → 2. transcription + reconnaissance des pizzas et quantités →
3. la commande en cours s'affiche pour relecture → 4. il valide, corrige ou annule →
5. à la validation, le ticket est **enregistré en base** (`PENDING`) et ajouté à la liste →
6. `PENDING` → `PREPARING` → `COMPLETED` (ou `CANCELLED`).

La commande en cours de dictée vit **en mémoire** ; rien n'est écrit en base tant qu'elle n'est pas validée.
La validation écrit en **une seule transaction** le ticket, ses lignes et la première ligne d'historique :
une commande validée n'est jamais perdue ni à moitié enregistrée (§9, fiabilité).

## Voix : Gemini 3.8 Live

```
micro (PCM 16 kHz) ──▶ WebSocket Gemini Live ──▶ appels d'outils ──▶ OrderViewModel ──▶ OrderRepository (SQLite)
                         ▲        │                add_pizzas / remove_pizzas / read_order /
   voix du modèle (24 kHz) ◀──────┘                validate_order / cancel_order
```

- Le modèle (`gemini-3.8-live`) comprend la phrase et répond à voix haute, en phrases très courtes.
- **Il ne modifie jamais le ticket lui-même** : il appelle des outils. C'est l'application qui rapproche le nom dit
  du menu en base (`CommandParser.resolve`, tolérant aux erreurs de reconnaissance), applique la modification et
  renvoie le résultat (pizza inconnue, ambiguë, indisponible, total, numéro de ticket…). Les prix et le total
  viennent donc toujours de la base, jamais du modèle.
- Le menu disponible (noms, prix) est envoyé au modèle au début de chaque session.
- Le micro est coupé automatiquement après 3 minutes sans voix.
- **Repli** : sans clé API, sans réseau ou avec Gemini désactivé, l'application utilise la reconnaissance vocale
  d'Android + `CommandParser`. Le champ texte et « Ajouter à la main » fonctionnent toujours.

### Clé API Gemini

Saisir la clé dans l'application (icône ⚙ de l'écran **Commande**). Elle est stockée **sur l'appareil uniquement**
et n'est jamais dans le code ni dans le dépôt.

> Pour la version 1 (appareil unique de la pizzeria), la clé est utilisée directement par l'application. Si
> l'application était diffusée plus largement, il faudrait passer par un petit serveur intermédiaire ou des
> jetons éphémères pour ne pas exposer la clé.

## Base de données (Room / SQLite)

Le schéma de [`docs/schema.sql`](docs/schema.sql) est la référence : **une entité Room par table**
(`data/Entities.kt`), mêmes colonnes, mêmes clés étrangères, mêmes index, avec leurs DAOs (`data/Daos.kt`).
Au premier lancement, le menu de départ (`assets/db/seed.sql`, 8 pizzas modifiables) est inséré.
Room exporte son schéma dans `app/schemas/` au premier build (utile pour le MLD du dossier).

| Table | Rôle |
|---|---|
| `pizza` | Menu : nom, description, prix (centimes), temps de préparation, disponibilité |
| `ticket` | Commande : numéro (`AAAAMMJJ-NNN`, repart à 001 chaque jour), date, état, total |
| `ticket_item` | Lignes d'un ticket. Nom et prix unitaire **copiés** : modifier le menu ne réécrit pas les anciens tickets |
| `ticket_status_history` | Chaque changement d'état (affiché en touchant un ticket). Table indépendante, sans clé étrangère |
| `ingredient`, `pizza_ingredient`, `stock_movement` | Stock : présentes dans le schéma, **pas encore utilisées par l'application** (hors périmètre v1) |

Points d'attention :

- Prix en **centimes** (entiers) : aucune erreur d'arrondi.
- Room active les clés étrangères à l'ouverture (équivalent du `PRAGMA foreign_keys = ON` du script).
- **Contraintes `CHECK`** (quantité > 0, statuts autorisés, prix ≥ 0…) : Room ne peut pas les générer depuis une
  `@Entity`. Elles restent dans `docs/schema.sql` ; dans l'application elles sont appliquées par le code
  (enum `TicketStatus`, formulaires du menu, `OrderRepository`).
- Un ticket avec ses lignes et son historique se lit en une requête grâce à `@Relation` (`TicketWithRelations`).
- Supprimer une pizza du menu met `ticket_item.pizza_id` à `NULL` sans toucher aux anciens tickets.

## Architecture

- **MVVM** : écrans Compose → ViewModels → `OrderRepository` → DAOs → `PizzeriaDatabase` (Room / KSP).
- `CommandParser` n'a aucune dépendance Android : testé en JVM pur (`CommandParserTest`, 20 tests).
- Flux réactifs : les DAOs renvoient des `Flow`, les listes se mettent à jour toutes seules.
- Transactions : `db.withTransaction { … }` (validation d'un ticket, changement d'état + historique).
- Dépendance ajoutée : OkHttp (WebSocket Gemini Live).

## Lancer le projet

1. Ouvrir le dossier dans **Android Studio**, laisser Gradle se synchroniser (JDK 17, SDK Android 35).
2. Lancer sur un **téléphone réel** (micro) ; autoriser le micro au premier appui.
3. Saisir la clé Gemini via l'icône ⚙ pour activer Gemini Live (Internet requis).
4. Tests : `./gradlew test`.

## Limites connues (v1)

- Gemini Live demande une connexion Internet. Hors ligne, c'est la reconnaissance Android (hors ligne selon l'appareil).
- Micro externe : USB et casque filaire ; le Bluetooth n'est pas géré.
- Pas d'encaissement, de synchronisation, d'impression de tickets ni de gestion de stock (hors périmètre v1).
- À valider sur appareil réel : qualité de la reconnaissance en cuisine (bruit) et annulation d'écho voix du modèle / micro.
