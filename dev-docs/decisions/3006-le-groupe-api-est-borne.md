# ADR 3006 - Le groupe `api` est borné, et ne dispense pas des commandes métier

- **Statut** : Accepté - 2026-07-31
- **Chantier** : #3006 (lot 4 de l'EPIC #2999)
- **Vérification** : certaine - `ArchitectureTest#lecture_brute_reservee_au_groupe_api`

## Contexte

Pour établir un fait sur les données réelles (un même code de point est-il porté par plusieurs
carrés ?), il a fallu écrire du `curl` à la main : la CLI exposait des gestes métier, jamais l'API
elle-même. Le porteur a demandé les **deux** surfaces - des commandes qui répondent, et un tuyau pour
explorer.

Le risque est connu : `ClientVigieChiro` n'a de valeur que parce qu'il **nomme** les points d'accès et
encode, une fois pour toutes, ce qu'il faut savoir avant d'appeler - le plafond de pagination
(`max_results > 100` → `422`, cause de #1277), l'ordre inversé des coordonnées, le filtre `where=` que
le serveur accepte puis ignore. Un tuyau générique posé à côté rend tout ce savoir à la tête de
l'utilisateur.

## Décision

### 1. La frontière passe par la structure, pas par une convention de nom

Ce qui vit **sous `api`** parle le langage de l'API - chemins, pages, `_items` - et rend le corps tel
quel. Ce qui vit **au premier niveau** parle celui du produit - sites, carrés, points - et rend des
projections, un recensement, un dénominateur. Le suffixe `-vigiechiro` continue d'y distinguer le
distant du local (`lister-sites` contre `lister-sites-vigiechiro`).

### 2. Lecture seule, et les pièges connus deviennent des refus

Aucun verbe configurable : `GET` uniquement (ADR 0020). Et `api lire` **refuse avant d'émettre** un
chemin portant `max_results` au-delà de 100, ou un `where=`. C'est la condition qui rend l'échappatoire
acceptable : elle rend la main sur les **chemins**, pas sur les **pièges**. Le second refus est le plus
important - une requête avec `where=` réussit, ne filtre rien, et laisse croire le contraire.

### 3. L'échappatoire est bornée par une règle, pas par la bonne volonté

`ClientVigieChiro.lectureBrute` est la seule méthode qui ne nomme pas ce qu'elle lit.
`ArchitectureTest#lecture_brute_reservee_au_groupe_api` interdit de l'appeler hors du groupe `api` :
une capacité nouvelle passe par une méthode **nommée**, jamais par le tuyau. Sans cette règle, le
groupe deviendrait le chemin de moindre résistance, et la couche qui protège tout le monde cesserait
de grandir.

### 4. Discrétion assumée, et sa contrepartie

Les sous-commandes d'`api` ne sont **ni comptées ni détaillées** dans le catalogue utilisateur : une
seule ligne pour le groupe dans `dev-docs/cli.md`, le détail dans `dev-docs/api-vigiechiro.md`. Un bas
niveau discret est aussi un bas niveau que rien ne surveille : la contrepartie est que le groupe reste
strictement borné et **ne grossit pas sans décision**.

### 5. Les sondes ne passent pas par notre client

**Ce qui teste l'API se passe de notre client ; ce qui teste notre client passe par lui.** Les sondes
de `ContratApiVigieChiroLiveTest` interrogent l'API en REST-assured direct, avec validation de schéma :
c'est ce qui leur permet de distinguer « l'API a changé » de « notre code a changé ». Les réécrire via
la CLI rendrait le vérificateur dépendant de ce qu'il vérifie - et deux d'entre elles deviendraient
**inécrivables**, puisque `api lire` refuse précisément les requêtes qu'elles vérifient.

## Conséquences

- L'exploration arbitraire reste possible sans quitter le produit, et sans qu'un script jetable
  reparte en Python.
- `api ressources` affiche la carte ([CatalogueApi], ADR du lot 0) et sait la confronter au serveur
  (`--sonder`) : la découvrabilité, qui était le vrai manque, ne dépend plus de la mémoire de qui a lu
  le source.
- Toute écriture reste hors du groupe. Le jour où elle se justifierait, ce serait une décision à
  prendre, pas une option à ajouter.

## Alternatives écartées

- **Le tuyau générique seul** : il transporte la question au lieu d'y répondre. « Z1 est-il partagé ? »
  redemande un comptage à la main, exactement ce que le chantier voulait supprimer.
- **Les commandes métier seules** : un point d'accès non projeté redevient inatteignable sans écrire
  du code, et l'exploration repart dans un terminal séparé.
- **Un sous-menu par ressource** (`api sites`, `api participations`…) : redondant avec `api lire`, et
  chaque ressource nouvelle demanderait une commande de plus pour zéro capacité supplémentaire.
