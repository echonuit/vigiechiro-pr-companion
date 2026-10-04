Deux lots du chantier #5596, une demande de fusion chacun : le groupe 1 est #5676 (lot 14), le groupe 2 est #5682 (lot 15).

## 1. Un seul point d'action (#5676)

- [x] 1.1 Écrire le cas rouge : après un dépôt connecté complet, l'écran ne montre qu'un bouton « Lancer la participation », et le titre de l'étape 4 est « 4. Lancer la participation ». Rouge avant le correctif, sur les deux boutons et sur le titre resté « Marquer le passage déposé ».
- [x] 1.2 Retirer l'action du compte rendu du dépôt, qui nomme alors la prochaine étape sans bouton. Fait quand 1.1 passe sur le nombre de boutons et que les tests existants du compte rendu restent verts.
- [x] 1.3 Lier le titre de l'étape 4 au lien de participation. Fait quand 1.1 passe en entier, et qu'un cas témoin sans participation liée lit « 4. Marquer le passage déposé » et « Marquer déposé ».
- [x] 1.4 Régénérer les captures du lot touchées, les ouvrir, et ajuster `docs/ecrans/lot.md`, qui parle de l'étape 4. Fait quand les captures montrent un seul bouton et le nouveau titre, sans troncature.

## 2. Le résultat se dit près du bouton (#5682)

- [x] 2.1 Soumettre au porteur les textes de la zone de retour de l'étape 4 et l'explication du bouton grisé, avant tout code. Fait quand il les a validés : c'est fait le 3 octobre 2026, les textes sont en D6 du design.
- [ ] 2.2 Écrire le cas rouge : une demande acceptée, une analyse déjà demandée, puis un refus avec motif, chacun lu dans la zone de l'étape 4, et le refus citant le motif. Rouge avant le correctif, où le résultat ne va qu'au bandeau du haut et où le motif est tu.
- [ ] 2.3 Donner au résultat du lancement sa propre zone dans l'étape 4, hors du bandeau. Fait quand 2.2 passe et que le bandeau ne répète pas le résultat.
- [ ] 2.4 Écrire le cas rouge du blocage : un relevé « analyse planifiée » grise le bouton, et son explication renvoie à la carte du traitement ; puis un second qui rouvre l'écran sur un relevé « en cours » enregistré et constate le blocage sans relevé réseau.
- [ ] 2.5 Dériver l'état « analyse demandée » du dernier relevé, et y lier le bouton avec le blocage existant. Fait quand 2.4 passe et que les tests de `TraitementViewModel` restent verts.
- [ ] 2.6 Régénérer les captures, poser la case de recette S4 qui suit le lancement réel, et dire dans `docs/ecrans/lot.md` où se lit le résultat. Fait quand les captures des trois issues se lisent sans troncature.
