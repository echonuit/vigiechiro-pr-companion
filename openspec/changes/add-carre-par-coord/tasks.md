## 1. Ce qui doit être su avant d'écrire

- [x] 1.1 Le chantier n'a pas d'EPIC : rien ne porte son « pourquoi » public ni ne relie ces tâches
      entre elles. Ouvrir l'EPIC, y rattacher les issues issues de cette liste, et y renvoyer depuis
      `proposal.md` par son numéro. **Fait quand** : l'EPIC existe, chaque tâche ci-dessous a son issue
      rattachée, et `proposal.md` cite le numéro au lieu de #733 seul.
- [x] 1.2 On ne sait pas ce que le serveur mesure quand on lui donne un rayon : la grille stocke-t-elle
      un point par carré ou un polygone ? Le serrage du rayon repose sur cette hypothèse et tombe avec
      elle. **Fait quand** : une sonde live dans `ContratApiVigieChiroLiveTest` interroge la grille à
      plusieurs rayons décroissants autour d'une position connue et publie ce qu'elle observe. Cette
      sonde peut invalider la décision D2 du design : dans ce cas, revenir au design avant d'écrire la
      suite.

## 2. Lire une position collée

- [x] 2.1 Rien ne sait tirer une paire de coordonnées d'un texte collé depuis une carte. Écrire cette
      lecture comme une classe pure, sans réseau. **Fait quand** : des tests unitaires couvrent le
      décimal, le degré-minute-seconde, le refus d'une URL de carte et le refus d'un texte illisible,
      chacun avec le motif que le refus doit porter.
- [x] 2.2 Un analyseur de format passe vert en laissant survivre ses mutations sur les bornes, les
      signes et les séparateurs. **Fait quand** : PIT a tourné sur la classe pure, ses survivants sont
      lus un par un, et chaque survivant est soit tué par un test neuf, soit justifié par écrit.

## 3. Demander le carré au rayon serré

- [x] 3.1 Le rayon de l'appel à la grille est figé à 10 000 m, valeur choisie pour un contrôle et non
      pour une proposition. Le rendre paramétrable sans changer ce que reçoit l'appelant existant.
      **Fait quand** : `ControleCarreStoc` passe toujours 10 000 m et ses tests actuels restent verts
      sans être modifiés, et un appel au rayon serré est possible.
- [x] 3.2 Une position hors de la grille doit rendre une réponse vide, et rien ne le vérifie contre le
      vrai serveur. **Fait quand** : une sonde live confronte une position hors grille au serveur réel
      et constate une réponse vide, distincte d'une panne, à côté de la sonde qui existe déjà pour le
      cas nominal.
- [x] 3.3 Le numéro que rend la grille n'a pas le format que le reste de l'application exige : cinq
      chiffres contre six, dans les départements 01 à 09. **Fait quand** : un test unitaire sur un
      carré de département à un chiffre rougit si on retire le rembourrage, et la sonde live de #4574
      continue de passer.
- [x] 3.4 La convention de coordonnées de `grille_stoc` était réputée indécidable, et elle est
      mesurée : `[lon, lat]`, celle de GeoJSON, là où les localités d'un site sont en `[lat, lon]`.
      Le savoir vit dans une réponse brute que personne ne relira. **Fait quand** :
      `dev-docs/api-vigiechiro.md` porte la convention dans sa carte des ressources, la javadoc de
      `numeroCarreStoc` dit que ne pas lire `centre` reste un choix et non une ignorance, et le garde
      de documentation à jour est vert.

## 4. Le geste dans l'écran de déclaration

- [ ] 4.1 L'écran de déclaration n'offre aucun moyen de partir d'un lieu plutôt que d'un numéro.
      **Fait quand** : un test de scénario sur la modale colle une position, demande à situer, et
      constate le numéro à six chiffres dans son champ.
- [ ] 4.2 Un geste qui exige le réseau serait offert sans jeton, et répondrait « impossible » après
      avoir fait payer l'aller-retour. **Fait quand** : un test de scénario constate le geste fermé
      sans jeton, son motif au survol, la déclaration toujours possible, et la réouverture du geste
      dès qu'un jeton arrive sans rouvrir la fenêtre.
- [ ] 4.3 Un numéro tapé à la main disparaîtrait sans un mot si une position en déduisait un autre.
      **Fait quand** : un test de scénario constate que le remplacement nomme le numéro remplacé, et
      que le cas où les deux concordent le dit au lieu de se taire.
- [ ] 4.4 Situer une position pourrait faire partir seule la question « ce carré existe-t-il ? », que
      personne n'a posée. **Fait quand** : un test de scénario compte les interrogations du portail
      après un geste de position et n'en trouve qu'une.
- [ ] 4.5 Sur une frontière, deux carrés sont candidats à distance strictement égale, et l'application
      en dépose un au hasard sans le dire. Mesuré le 2026-08-27 : 997,7 m chacun au milieu d'un côté,
      1 412 m pour quatre au coin, et 5 m de décalage suffisent à faire basculer le premier.
      **Fait quand** : un test unitaire rougit si un carré est déposé alors que deux sont proches, un
      autre rougit si rien n'est déposé alors qu'un seul l'emporte nettement, et un test de scénario
      constate que le message nomme les deux numéros.

## 5. Ce qui doit rester vrai après

- [ ] 5.1 Le contrôle en aval d'un point ne doit rien perdre à ce changement. **Fait quand** : les
      tests de `ControleCarreStoc` et son verdict `Concorde` / `Diverge` / `HorsGrille` sont verts
      sans avoir été retouchés.
- [ ] 5.2 La documentation de l'écran décrit un geste qui n'existera plus tel quel. **Fait quand** :
      la section « Déclarer un site » de `docs/ecrans/sites.md` décrit le nouveau geste, ses refus et
      sa fermeture hors connexion, et le garde de documentation à jour est vert.
- [ ] 5.3 Un geste d'écran qu'aucun clip ne montre ne se vérifie que sur parole. **Fait quand** : le
      parcours « coller une position, obtenir un carré, déclarer le site » a son cas de recette et son
      clip, contrôle négatif compris.
- [ ] 5.4 Le motif durable du rayon - celui d'une proposition n'est pas celui d'un contrôle - ne vit
      que dans une note de changement, qui sera archivée. **Fait quand** : une ADR de
      `dev-docs/decisions/`, numérotée par l'issue du chantier, porte cette décision et déclare
      comment elle est vérifiée.
