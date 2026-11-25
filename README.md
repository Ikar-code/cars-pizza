# VoxResto — prise de commande vocale pour restaurant

Application Android native (Kotlin, Jetpack Compose) : le serveur **dicte la commande à voix haute**,
l'appli la comprend, remplit un ticket, répond à voix haute, puis conserve tout dans un **historique**.

> « Deux samoussas et un coca, c'est tout. » → 2 × Samoussa + 1 × Coca ajoutés, ticket n°1 validé, total annoncé.

## Fonctionnalités

| Écran | Ce qu'il fait |
|---|---|
| **Commande** | Micro (reconnaissance vocale française) ou saisie clavier, ticket en cours en direct, boutons +/−, validation/annulation, réponses de l'assistant à voix haute (coupables) |
| **Menu** | Carte en base de données groupée par catégorie, plat disponible / indisponible (interrupteur), ajout d'un plat avec ses **synonymes vocaux** |
| **Historique** | Tickets validés/annulés, filtre par statut, détail des lignes, **journal d'audit** (heure, action, origine voix / clavier / tactile), passage « Prêt » ou « Annulé » |

### Commandes vocales comprises

| Intention | Exemples |
|---|---|
| Ajouter | « une pizza margherita », « je voudrais deux samoussas et un coca », « 3 bonbons piment » |
| Retirer | « enlève le coca », « retire un samoussa » |
| Valider | « valide la commande », « c'est tout » (utilisable dans la même phrase que l'ajout) |
| Annuler | « annule la commande », « recommence » |
| Récapitulatif / prix | « c'est combien », « fais-moi le récapitulatif » |
| Cas ambigu | « une pizza » → l'assistant demande : *Pizza Margherita ou Pizza Reine ?* |

## Design

L'interface reprend la maquette Figma **« Commande vocale pizzeria »** (restaurant fictif *Il Forno*), adaptée à Compose. Seul l'habillage vient de la maquette : la logique vocale, la base Room et les statuts (`DRAFT / CONFIRMED / READY / CANCELLED`) ne changent pas.

| Élément de la maquette | Où dans le code |
|---|---|
| Palette : fond crème `#F7F5F0`, rouge four `#D8472C`, vert `#4B8A5B`, ambre `#AA6A11`, encre `#252823`, bordures `#DEDBD2` | `ui/Theme.kt` (le mode sombre est une déclinaison, il n'existe pas dans la maquette) |
| Polices **Manrope** (titres) et **DM Sans** (texte), licence OFL | `res/font/`, `ui/Theme.kt` |
| Cartes blanches à fine bordure, petits titres en capitales espacées, pastille de statut | `ui/Components.kt` (`VoxCard`, `Overline`, `StatusBadge`) |
| En-tête : pastille rouge avec pizza + « Prise de commande » | `ui/VoxRestoUi.kt` |
| Ticket en cours : sélecteur de quantité, barre « Total à payer » sombre, boutons Annuler / Valider, halo du micro pendant l'écoute | `ui/OrderScreen.kt` |
| Carte du menu : pastille d'icône, prix, synonymes, point « Disponible » | `ui/MenuScreen.kt` |
| Tickets : pastille de numéro, lignes avec quantité en rouge, pied « Total » avec actions, deux compteurs en tête | `ui/HistoryScreen.kt` |

Non repris de la maquette : le **mode écoute mains libres** (kit micro, mots « Nouvelle commande » / « Valider » / « Corriger » prononcés sans toucher l'écran), qui demande une reconnaissance vocale continue ; la carte 100 % pizzas et ses statuts *En attente / En préparation / Terminée*.

## Architecture

```
┌────────────┐   micro    ┌───────────────┐  phrases   ┌───────────────┐
│ SpeechMgr  │ ─────────▶ │ OrderViewModel│ ─────────▶ │ CommandParser │  (Kotlin pur, testé)
│ (STT fr-FR)│            │               │ ◀───────── │  → VoiceCmd   │
└────────────┘            │               │  commandes └───────────────┘
┌────────────┐  réponses  │               │
│ SpeechOut  │ ◀───────── │               │ ───────────▶ OrderRepository ──▶ Room (SQLite)
│ (TTS fr-FR)│            └───────┬───────┘               (transactions)      menu · tickets · lignes · événements
└────────────┘                    │ StateFlow
                          Jetpack Compose (Commande · Menu · Historique)
```

- **MVVM** : écrans Compose → ViewModels → Repository → Room.
- **`CommandParser`** n'a aucune dépendance Android : il est testable en JVM pur (`CommandParserTest`).
- **Transactions** : ajouter une ligne + écrire l'événement + mettre à jour le ticket se fait en un bloc « tout ou rien ».
- **Flux réactifs** : l'interface observe la BDD (`Flow`), elle se met à jour toute seule.

## Base de données (Room / SQLite)

```
menu_items            tickets                    ticket_lines                 ticket_events
──────────            ───────                    ────────────                 ─────────────
id (PK)               id (PK)                    id (PK)                      id (PK)
name (unique)         number (n° du jour)        ticket_id  (FK → tickets)    ticket_id (FK → tickets)
category              status (DRAFT|CONFIRMED    menu_item_id (FK → menu_items,  timestamp
price_cents            |READY|CANCELLED)          SET NULL)                   type (CREATED|ITEM_ADDED|…)
aliases (synonymes)   created_at / updated_at    name  (copie du nom)         detail
available             confirmed_at               unit_price_cents (copie)     source (VOICE|TEXT|TOUCH)
                                                 quantity
```

Choix à expliquer à l'oral :

- **Prix en centimes (entier)** : aucune erreur d'arrondi sur les montants.
- **Nom et prix copiés dans la ligne** : modifier la carte ne réécrit pas l'historique.
- **`ticket_events`** : journal d'audit, base de l'écran Historique ; il garde l'origine de chaque action.
- **Un seul ticket `DRAFT`** à la fois = la commande en cours de dictée ; la validation lui attribue un numéro, remis à 1 chaque jour.
- **Cycle de vie** : `DRAFT → CONFIRMED → READY`, ou `→ CANCELLED` ; les transitions interdites sont refusées par le Repository.
- Le schéma exporté par Room est généré dans `app/schemas/` au premier build (utile pour le dossier : MLD/MCD).

## Comment fonctionne la compréhension vocale

1. Android `SpeechRecognizer` renvoie jusqu'à 3 hypothèses de texte (français).
2. `CommandParser` normalise (minuscules, sans accents, sans ponctuation), repère les ordres globaux (valider, annuler, récapitulatif), découpe sur « et / puis », lit la quantité (chiffres ou « deux », « trois »…).
3. Chaque morceau est rapproché de la carte (noms + synonymes) avec une **distance de Levenshtein tolérante** : « pizza margarita » ou « samoussas » sont compris malgré l'erreur de reconnaissance.
4. `parseBest` garde l'hypothèse la mieux comprise (le plus de commandes valides, le moins d'inconnues).

## Lancer le projet

1. Ouvrir le dossier dans **Android Studio** (version récente), laisser Gradle se synchroniser.
2. Lancer sur un **téléphone réel** (recommandé : micro et moteur vocal fiables) ou un émulateur avec *Google Play* (activer le micro dans les réglages de l'émulateur).
3. Autoriser le micro au premier appui. La reconnaissance vocale de Google peut nécessiter du réseau selon l'appareil ; le champ de saisie sert de plan B.
4. Tests : `./gradlew test` (tests du parseur).

Prérequis : JDK 17, SDK Android 35. Si Android Studio propose de mettre à jour AGP/Kotlin/dépendances, vous pouvez accepter.

## Script de démonstration (3 minutes)

1. Écran **Menu** : montrer la carte, passer « Dodo » indisponible, ajouter un plat avec un synonyme.
2. Écran **Commande** : dire « deux samoussas et un coca » → le ticket se remplit et l'assistant répond.
3. Dire « enlève le coca » puis « une pizza » (cas ambigu) puis « une pizza reine ».
4. Dire « c'est combien » puis « c'est tout » → ticket n°1 validé.
5. Écran **Historique** : ouvrir le ticket, montrer le journal (icône micro = action vocale), le passer en « Prêt ».
6. Montrer le test unitaire du parseur qui passe.

## Pistes d'amélioration (pour la conclusion du dossier)

- Reconnaissance **hors ligne** (modèles Vosk / Whisper embarqués) pour ne dépendre d'aucun réseau.
- **Mot d'activation** (« Hey Vox ») pour mains libres complètement.
- Synchronisation des tickets vers une cuisine (API REST + base MySQL/Supabase, écran cuisine).
- Modificateurs (« sans piment », « bien cuit »), tables, paiement, statistiques de ventes.
- Tests instrumentés Room et tests d'interface Compose.

## Limites connues

- Une seule langue (français) et une seule commande vocale à la fois.
- Les nombres composés (« vingt et un ») ne sont pas gérés ; les quantités vont de 1 à 99 en chiffres ou jusqu'à « vingt » en lettres.
- L'icône de l'application est celle par défaut (à remplacer via *Image Asset* dans Android Studio).
