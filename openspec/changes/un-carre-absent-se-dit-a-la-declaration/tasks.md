Toutes ces tâches forment un seul lot, #5607, livré par une seule demande de fusion.

## 1. Classer une fois

- [x] 1.1 Écrire les cas rouges de la classification : liste vide = absent ; un site Routier seul = autre protocole, qui nomme le site ; un site Point Fixe parmi d'autres = Point Fixe. Fait quand les trois passent sur la fonction de `sites/model`.
- [x] 1.2 Faire classer par cette fonction la vérification (`RechercheCarreExistant`), le rapatriement (`RapatriementCarre`) et `creer-site`. Fait quand leurs tests existants restent verts.

## 2. Dire ce que le verdict implique

- [x] 2.1 Écrire les cas rouges de la vérification, un par cas, sur le texte rendu et la gravité : absent et autre protocole portent la phrase sur le portail, en avertissement ; Point Fixe inchangé ; injoignable inchangé. Rouges avant le correctif : « vous pouvez le déclarer ici » en succès, et « existe déjà, récupérez-le » pour un carré Routier.
- [x] 2.2 Écrire la phrase sur le portail une fois, et la faire lire par la vérification et par le rapatriement. Fait quand 2.1 passe et que « Récupérer ce carré » ne s'offre que sur un carré en Point Fixe.
- [x] 2.3 Écrire le cas rouge de `creer-site` sur un carré absent : sortie 0, sortie standard réduite à l'identifiant, phrase sur le portail en sortie d'erreur. Puis le faire passer, et ajouter le cas `bats` qui le lance.

## 3. Créer vérifie ce qui ne l'a pas été

- [x] 3.1 Écrire les cas rouges de la modale, en comptant les appels au portail : « Créer » sans verdict sur un carré absent interroge une fois et crée ; sur un carré en Point Fixe, ne crée pas et propose « Récupérer ce carré » ; après un verdict affiché, n'interroge pas de nouveau ; portail injoignable, crée.
- [x] 3.2 Faire vérifier « Créer » hors du fil JavaFX, et passer le verdict au bandeau de retour de « Mes sites ». Fait quand 3.1 passe, et que `SiteEditSituerPositionTest` reste vert.

## 4. Ce qui le montre

- [x] 4.1 Captures de la modale, une par message : `apercu-sites-modale-site-carre-absent.png` (neuve), `apercu-sites-modale-site-autre-protocole.png` (refaite, le verdict de la vérification sans passer par « Récupérer »), `apercu-sites-modale-site-carre-non-verifie.png` (neuve), et `apercu-sites-modale-site-carre-existant.png` relue. Plus le bandeau de « Mes sites » après une création sur un carré absent. Fait quand chacune est ouverte et se lit en entier.
- [x] 4.2 Poser les cases de recette dans la session propriétaire de la déclaration d'un site : les quatre verdicts, et « Créer » sans avoir vérifié.
- [x] 4.3 Dire dans la doc utilisateur des sites ce que chaque verdict implique pour le dépôt, et le geste du portail.
