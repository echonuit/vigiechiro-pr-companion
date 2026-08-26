---
type: adr
title: "Le régime de restauration suit la place disponible"
status: stable
article: A15
chantier: "#3563, lot 1 (#3559) du chantier #3518 ; trouvé à la passe 3 de la clôture du lot"
decided_at: 2026-08-10
verification: certaine
enforced_by:
  - "BesoinDePlaceTest#la_borne_de_la_plus_grosse_est_inclusive"
verified:
  - by: machine:ci
    at: 2026-08-10
relations:
  amende: ["2727"]
---

# Le régime de restauration suit la place disponible

## Contexte

L'ADR 2727 avait écarté la zone temporaire, **explicitement et avec un chiffre** :

> La zone temporaire est écartée pour une raison de place, mesurée sur le cas ordinaire : restaurer
> 40 Go **par-dessus ses propres 40 Go** demanderait 80 Go libres. Elle ferait donc échouer la
> restauration la plus courante, celle où l'on remet ses propres données, pour protéger d'un cas plus
> rare. **Un dispositif de sûreté qui empêche l'usage normal n'est pas un dispositif de sûreté.**

#3514 l'a pourtant introduite, et **personne n'a relu 2727**. Le cadrage du chantier, repris d'un
audit indépendant, disait « il manque la zone temporaire que le rapport initial prescrivait » : il
présupposait un oubli là où c'était une décision motivée. L'audit ne pouvait pas le savoir ; la
passe 0 du lot, si - elle n'a regardé que les ADR **nées pendant** le chantier, pas celles que le
sujet du lot régissait.

Résultat mesuré : `BasculeRacines.preparer` copie chaque racine vers `<destination>.en-cours` et
`basculer` ne supprime l'ancienne qu'ensuite ; comme `replacer` prépare **toutes** les racines avant
d'en basculer une seule, le pic devient sauvegarde + originaux + copies, soit environ **trois fois**
la donnée là où l'ADR refusait deux fois. Sans aucun garde d'espace, alors que le port `EspaceDisque`
existait et servait déjà à l'import et au lot.

## Décision

**Le régime n'est plus un choix d'architecture, c'est une conséquence de la place libre.**

| Place libre là où les nuits atterrissent | Régime | Ce qu'une panne laisserait |
|---|---|---|
| ≥ ce que pèsent toutes les nuits qui y vont | tout étaler, tout vérifier, puis tout basculer | des temporaires, et l'état d'avant |
| ≥ la plus grosse d'entre elles | une nuit à la fois | les premières en place, pas les dernières |
| en dessous | refus **chiffré** | rien |

Le calcul est **pur** : le manifeste porte les octets de chaque racine, donc le régime se décide sans
toucher au disque - un refus tardif laisserait une destination à moitié écrite.

### Ce que cette ADR retient de 2727, et ce qu'elle dépasse

Elle **retient le reproche** : refuser ce qui aurait été possible rend rigide un dispositif censé
protéger. C'est pourquoi le rang du milieu existe - la souplesse n'est pas un renoncement, c'est le
refus d'imposer à tous le coût du cas rare.

Elle **dépasse le remède** : 2727 tranchait une fois pour toutes, faute d'avoir envisagé qu'on puisse
mesurer avant de choisir. On peut, et le manifeste le permettait déjà.

### Le besoin se compte par dossier d'accueil

Une nuit dont le disque externe est rebranché y retourne ; les autres vont dans le dossier de travail.
Un total unique confronté à la seule place du dossier de travail se tromperait **dans le sens
dangereux** : il annoncerait que tout tient alors que le disque externe est plein, et la copie
échouerait à mi-parcours - exactement la panne que l'étalement existe pour éviter.

### La garantie dégradée se dit

Le compte rendu porte le régime employé, et les deux surfaces l'affichent. Sans cette phrase,
l'utilisateur croit avoir eu la garantie forte, et un incident ultérieur le trouverait sans
explication.

**Dès la première bascule, « rien n'a été touché » cesse d'être vrai.** Un refus survenu ensuite est
requalifié en incident : le laisser passer pour un refus donnerait à un script un code de sortie qui
promet un état intact au-dessus d'un état partiel (convention #2294).

## Ce que cette décision coûte

Le rang du bas **refuse**, et c'est assumé. En dessous de la plus grosse nuit, aucun régime ne tient :
le seul plus permissif serait la copie en place d'avant #3514, qui rendrait à l'utilisateur la
destination à moitié écrite dont ce chemin vient de le débarrasser. Le refus est donc **chiffré** -
combien il manque, et où - pour qu'il soit actionnable plutôt que subi.

Et le régime dégradé n'est pas gratuit : il rend possible un état partiel que le régime nominal
excluait. C'est le prix de ne pas refuser, et il est **écrit dans le compte rendu** plutôt que caché.
