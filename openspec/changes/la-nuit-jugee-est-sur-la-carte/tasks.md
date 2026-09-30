Ces tâches réalisent le sous-chantier #5629 : le groupe 1 est le lot #5600, le groupe 2 le lot #5631,
une demande de fusion chacun. Chaque tâche commence par son test rouge (article A7).

## 1. L'inspection juge les nuits présentes (#5600)

- [x] 1.1 Reproduire sur la carte de Samuel (journal du 19 août, nuits du 22 au 24) : l'avertissement
      nomme 202016 G1, une nuit présente déjà importée n'est pas nommée, le contrôle du n° reconnaît
      une nuit récupérée absente. Trois tests rouges, commit `79d085f60`.
- [x] 1.2 Les identités des nuits de la table remplacent `identiteNuit` ; l'avertissement et la
      question de confirmation les jugent. Vert : les deux tests d'inspection de 1.1.
- [ ] 1.3 Un cas où la seule nuit déjà importée est décochée : pas de confirmation. Un cas à deux nuits
      cochées déjà importées : les deux nommées avec leur date. Rouges avant 1.4.
- [ ] 1.4 La rédaction multi-nuits dans `AvertissementsInspection`, le texte mono-nuit inchangé.
      Vert : 1.3, et `AvertissementsInspectionTest` reste vert.
- [ ] 1.5 `ControleNumeroPassage` reçoit les nuits cochées. Vert : le test de 1.1, et le cas #2580
      existant reste vert.
- [ ] 1.6 Mutation : revenir à `journal.dateDebut()` fait rougir ; PIT sur les classes touchées,
      survivants lus ; aperçu de l'inspection sur une carte réutilisée relu ; batterie ; PR.

## 2. Le contrôle de cohérence juge les nuits racontées (#5631)

- [x] 2.1 Reproduire : un vrai journal circulaire (19 puis 22 août), des WAV du 22 seul, date jugée
      incohérente ; contrôle négatif (journal du 1er avril seul) incohérent. Commit `ba2351fc6`.
- [ ] 2.2 `AnalyseCoherence` reçoit les nuits des cycles du journal, repli sur `dateDebut` sans cycle.
      Vert : 2.1, le contrôle négatif et les cas existants de `AnalyseCoherenceTest`.
- [ ] 2.3 Mutation, PIT sur `AnalyseCoherence`, batterie, PR.
