Cahier des charges – Application Android de prise de commande vocale pour pizzeria
Projet : application mobile Android – Version 1.0 – Novembre 2026

1. Présentation du projet
Le projet consiste à développer une application mobile pour le système d'exploitation Android destinée à une pizzeria dans laquelle un seul employé assure à la fois la préparation des pizzas et la prise des commandes.

Pour que l'employé garde les mains libres pendant la préparation, l'application permet de dicter les commandes à la voix. La voix est captée soit par un micro extérieur, soit par le micro intégré de l'appareil. Chaque commande donne lieu à un ticket qui est créé, validé, puis ajouté à la liste des commandes en cours.

2. Contexte et besoin
Dans une pizzeria à un seul employé, il est difficile de prendre une commande (téléphone, comptoir) tout en préparant les pizzas. L'écriture manuelle des tickets fait perdre du temps, génère des oublis et des erreurs.

Besoin exprimé : disposer d'un outil simple, rapide et utilisable sans les mains, qui transforme la voix en ticket de commande et garde une liste claire des commandes à préparer.

3. Objectifs
Permettre la prise de commande à la voix.
Créer automatiquement un ticket de commande à partir de ce qui est dicté.
Permettre à l'employé de valider (ou corriger) le ticket avant son enregistrement.
Conserver et afficher la liste des commandes à préparer.
Réduire les oublis, les erreurs et le temps passé à noter les commandes.
4. Périmètre du projet
Inclus dans la version 1
Reconnaissance vocale (micro de l'appareil ou micro externe).
Création, modification, validation et annulation d'un ticket de commande.
Liste des commandes avec suivi de l'état (en attente, en préparation, terminée).
Gestion du menu (pizzas, prix) dans la base de données.
Stockage local des données avec SQLite.
Hors périmètre (évolutions possibles)
Paiement en ligne ou encaissement.
Commande par les clients depuis leur propre téléphone.
Synchronisation avec un serveur distant ou plusieurs appareils.
Impression des tickets sur imprimante thermique.
5. Utilisateurs
Utilisateur	Rôle
Employé de la pizzeria	Seul utilisateur : dicte les commandes, valide les tickets, prépare les pizzas, change l'état des commandes
L'utilisateur n'est pas forcément à l'aise avec l'informatique : l'interface doit être très simple, lisible et utilisable rapidement.

6. Besoins fonctionnels
Réf.	Fonctionnalité	Description	Priorité
F1	Commande vocale	L'employé appuie sur un bouton micro et dicte la commande (ex. « deux margherita et une reine »). L'application convertit la voix en texte.	Haute
F2	Choix du micro	L'application utilise le micro externe s'il est branché, sinon le micro de l'appareil.	Moyenne
F3	Analyse de la commande	Le texte dicté est analysé pour retrouver les pizzas du menu et les quantités.	Haute
F4	Création du ticket	Un ticket est généré automatiquement : numéro, date et heure, liste des pizzas, quantités, prix total.	Haute
F5	Validation du ticket	L'employé relit le ticket et le valide (ou le corrige / l'annule) avant enregistrement.	Haute
F6	Liste des commandes	Une fois validé, le ticket est ajouté à la liste des commandes, triée par ordre d'arrivée.	Haute
F7	Suivi de l'état	L'employé change l'état d'une commande : en attente, en préparation, terminée.	Moyenne
F8	Saisie manuelle	En cas d'échec de la reconnaissance vocale, possibilité d'ajouter des pizzas à la main.	Moyenne
F9	Gestion du menu	Ajout, modification et suppression des pizzas et de leurs prix.	Moyenne
F10	Historique	Consultation des commandes terminées.	Basse
7. Cycle de vie d'un ticket
L'employé appuie sur le bouton micro et dicte la commande.
L'application transcrit la voix en texte et reconnaît les pizzas et les quantités.
Un ticket de commande est créé et affiché pour relecture.
L'employé valide le ticket (ou le corrige, ou l'annule).
Le ticket validé est enregistré dans la base de données et ajouté à la liste des commandes.
L'employé fait évoluer l'état de la commande jusqu'à « terminée ».
8. Contraintes techniques
Élément	Choix
Plateforme	Android (application native)
Environnement de développement	Android Studio
Matériel de test	Téléphone Android physique équipé d'un micro (et micro externe)
Base de données	SQLite (stockage local sur l'appareil)
Reconnaissance vocale	API de reconnaissance vocale d'Android (à confirmer lors de la conception)
Permissions	Accès au micro (RECORD_AUDIO)
9. Exigences non fonctionnelles
Simplicité : prise en main en quelques minutes, gros boutons, peu d'écrans.
Rapidité : création du ticket en quelques secondes après la dictée.
Fiabilité : aucune commande validée ne doit être perdue, même si l'application se ferme.
Lisibilité : texte grand et contrasté, lisible dans une cuisine (lumière, distance).
Fonctionnement hors ligne : les données restent sur l'appareil (SQLite) ; la reconnaissance vocale hors ligne est à vérifier selon l'appareil.
Compatibilité : versions récentes d'Android (à préciser).
10. Livrables
Application Android (code source et fichier APK).
Base de données SQLite avec le menu de départ.
Cahier des charges (ce document) ; la charte graphique est fournie à part.
Documentation d'utilisation.