Deux lots du chantier #5596, une demande de fusion chacun : le groupe 1 est #5688 (lot 20), le groupe 2 est #5687 (lot 19), qui s'appuie sur le premier.

## 1. Une seule manière de situer un point (#5688)

- [x] 1.1 Écrire les cas rouges de la règle enrichie : degrés-minutes décimales, cardinal après un décimal, virgule décimale refusée avec un motif qui dit d'écrire le point décimal. Rouges avant le correctif, où les deux premiers sont refusés et le troisième retombe sur le motif générique.
- [x] 1.2 Enrichir `PositionCollee` et `LecturePosition`. Fait quand 1.1 passe et que les six cas existants de `PositionColleeTest` restent verts.
- [x] 1.3 Écrire le cas rouge du ViewModel de point : un même texte, décimal puis degrés-minutes-secondes, donne les mêmes coordonnées que dans le site ; un texte illisible porte son motif et ferme l'enregistrement ; un champ vide enregistre un point sans position.
- [x] 1.4 Remplacer les deux propriétés par la propriété `position` dans `PointEditViewModel`, et les deux champs par le champ unique dans la modale. Fait quand 1.3 passe, et que `PointEditViewModelTest` et `ModalePointViewTest` sont repris sur le champ unique.
- [x] 1.5 Écrire le cas d'interface du marqueur (saisir une paire place le marqueur ; le glisser réécrit le champ ; l'édition montre la position enregistrée), puis le câblage. Reprendre `ScenarioFicheSiteTest` sur le champ unique. *Tenu ainsi* : saisir une paire place le marqueur dans `ModalePointViewTest` ; le glisser est tenu au ViewModel (`le_marqueur_ecrit_la_position`, par `placer`, que le contrôleur appelle), pas par un geste de souris sur la carte ; l'édition dans `enregistrer_edition`.
- [x] 1.6 Écrire les cas du code suivant (site vide, trou, codes non `Z`) et le cas de ViewModel qui lit le code proposé à l'ouverture d'une création ; puis le calcul et la proposition.
- [x] 1.7 Écrire les cas du voisinage (39 m signale, 41 m non, le plus proche est nommé) et le cas de ViewModel qui lit l'avertissement ; puis le calcul et l'avertissement.
- [x] 1.8 Retirer `AnalyseurCoordonnees` et son test. Fait quand plus rien ne le cite et que la mutation qui fait diverger la lecture du point de celle du site fait rougir 1.3.
- [x] 1.9 Régénérer les captures de la modale de point, les ouvrir ; dire dans `docs/ecrans/sites.md` comment se saisit une position et ce que signifie l'avertissement ; poser la case de recette S1.

## 2. Le premier point se crée avec son site (#5687)

- [x] 2.1 Écrire les cas rouges d'interface : position collée, case cochée, un site et un seul point `Z1` à cette position ; case décochée, un site sans point ; numéro saisi sans position et carré récupéré, pas de case. *Tenu ainsi* : la case cochée, décochée et la position effacée au ViewModel (`SiteEditPremierPointTest`) ; la case cochée et l'édition à l'interface (`ModaleSiteViewTest`). Un carré récupéré ne passe pas par « Créer » : « Récupérer ce carré » est un autre geste, que la case ne touche pas, et aucun cas ne le rejoue ici.
- [x] 2.2 Porter la case dans `SiteEditViewModel` et la modale de site, et créer le point après le site. Fait quand 2.1 passe et que les tests de la modale de site restent verts.
- [x] 2.3 Dire le cas où le site est créé et le point non : le retour nomme l'échec et renvoie à « Ajouter un point d'écoute ». Un cas de ViewModel le tient.
- [x] 2.4 Régénérer les captures de la modale de site, les ouvrir ; mettre à jour `docs/ecrans/sites.md` ; poser la case de recette S1.
