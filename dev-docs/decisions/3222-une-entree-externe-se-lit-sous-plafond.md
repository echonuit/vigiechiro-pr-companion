# ADR 3222 - Une entrée externe se lit **sous plafond**, et le plafond vient d'une **mesure**

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #3222, suite de la passe 7 du lot 2 (#2722) du chantier de dette #2720
- **Vérification** : certaine - `LectureBorneeTest#texte_refuse_avant_d_avoir_tout_lu`

> Ce test est le seul qui distingue borner une lecture de **constater** qu'elle était trop grosse : il
> compte les octets réellement demandés à la source. Déplacer le contrôle après la boucle le fait
> rougir, alors que le refus, lui, tombe toujours.

## Contexte

L'[ADR 2732](2732-on-n-ecrit-pas-plus-que-ce-qui-est-declare.md) a borné la décompression d'une
archive en **ressources** : plus rien ne s'écrit sur le disque sans qu'on ait accepté combien.
L'audit d'harmonisation du lot a cherché la même forme ailleurs et l'a trouvée **six fois**, en
**mémoire** cette fois :

- trois clients HTTP - la plateforme VigieChiro, GBIF, l'API Géo - lisaient chaque réponse avec
  `BodyHandlers.ofString()` ;
- trois lecteurs de fichiers de la carte SD - `AnalyseurLogPR`, `InspecteurDossier` et le relevé
  climatique de `MoteurImport` - faisaient `Files.readAllLines`.

Aucun des six n'est exploitable par un tiers : les premiers supposent que le serveur réponde n'importe
quoi, les seconds que l'utilisateur fournisse un fichier aberrant. Ce n'est pas une faille, c'est une
**absence de borne** - et le lot venait précisément de décider qu'une entrée externe se borne.

## Décision

### 1. Le plafond vient d'une mesure sur une carte réelle, pas d'une intuition

C'est la clause centrale, et elle est écrite ici parce que #2732 a montré le coût de l'oubli : un
plafond de taux de compression posé sans mesure y refusait des cartes légitimes, et a dû être retiré.

Mesures prises sur `Car640380-2026-Pass2-Z1`, une nuit complète (4 031 observations, la nuit de
référence des bancs de performance) :

| Ce qui est lu | Fichier réel | Taille |
|---|---|---|
| Journal du capteur | `LogPR1925492.txt` | **1 862 o**, 22 lignes |
| Relevé climatique | `PaRecPR1925492_THLog.csv` | **2 062 o**, 71 lignes |
| Le plus gros corps que la plateforme rend | CSV d'observations d'une participation | **446 Kio**, 4 032 lignes |

La mesure a corrigé la crainte qui motivait l'issue. Le journal **ne grossit pas avec le nombre de
séquences** : il consigne des événements de session (démarrage, mode, batterie, veille), pas un
enregistrement par fichier. Une saison de 250 nuits sur la même carte pèse ~465 Ko ; dix ans
d'exploitation continue, ~4,7 Mo. Le « journal de saison » redouté est **petit**.

D'où **32 Mio** pour les fichiers de la carte (17 000 fois une nuit réelle, 70 fois une saison de dix
ans) et **64 Mio** pour un corps de réponse (147 fois le plus gros mesuré).

### 2. Deux gardes, comme pour l'archive

**Ce qui annonce sa taille est refusé avant d'être lu** : un fichier par `Files.size`, une réponse par
son `Content-Length`. **Ce qui ne l'annonce pas est compté pendant la lecture** : une réponse en
encodage par blocs n'a pas de `Content-Length`, et un plafond vérifié après avoir tout chargé ne
protège de rien, puisque la mémoire est déjà prise.

C'est la même paire qu'à l'extraction, pour la même raison : le premier garde lit une **déclaration**,
le second la confronte au **réel**.

### 3. Un dépassement est un refus **définitif**, pas une panne

Le transport traduit le dépassement en `ReponseApi.Refuse`, pas en `Injoignable`. La distinction n'est
pas cosmétique : `Injoignable` est **rejoué** par la politique de reprise (#2354), et réémettre une
requête dont la réponse est trop grosse la redonne trop grosse. Trois tentatives au lieu d'une, pour
le même refus.

### 4. Le plafond est surchargeable, mais n'est pas un réglage

Chaque plafond se surcharge par propriété système (`vigiechiro.import.journal.max-octets`,
`vigiechiro.reseau.corps.max-octets`), patron des bornes de #2732. Il n'y a délibérément pas de réglage
dans l'écran Réglages : un naturaliste n'a pas à choisir une taille de corps de réponse. C'est le
**message de refus** qui nomme la limite, le chiffre observé et la surcharge - sans quoi la seule issue
serait de renoncer au fichier.

### 5. Une classe qu'on modifie est une classe qu'on peut éprouver

`ClientGbif` construisait son client HTTP en dur et n'avait **aucun test**. Plutôt que de le modifier à
l'aveugle, la couture d'injection de `ResolveurCommuneApiGeo` y a été posée, et cinq cas écrits. La
règle vaut au-delà de cette ADR : on ne touche pas à ce qu'on ne peut pas mettre en situation.

## Conséquences

- Les six lectures passent par `LectureBornee` (fichiers) ou `CorpsReponse` (HTTP), qui portent la
  paire de gardes une seule fois. Une septième lecture d'entrée externe a désormais un endroit
  évident où aller.
- Les fausses réponses des tests rendent un **flux**, pas une chaîne. Elles en rendent un **neuf à
  chaque appel** : servie deux fois par une reprise, une réponse rendrait sinon un flux déjà consommé,
  donc un corps vide - un test vert pour la mauvaise raison.
- Les deux résolutions best-effort (GBIF, API Géo) traitent le dépassement comme toute autre anomalie :
  résolution vide, l'appelant retombe sur son repli. Une fiche d'espèce est un confort, pas une
  garantie.
- Ce que cette ADR **ne fait pas** : elle ne borne pas les lectures de fichiers **produits par
  l'application** (base, manifeste, sauvegarde). Ceux-là ne viennent pas du dehors ; les borner
  reviendrait à se protéger de soi-même.
