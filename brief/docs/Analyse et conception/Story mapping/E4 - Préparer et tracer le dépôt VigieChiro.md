# E4 - 📦 Préparer et tracer le dépôt VigieChiro

[← Retour au sommaire story mapping](index.md) · **Parcours principal** : [P4 - Préparer le dépôt](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)

**Portée** : finaliser le travail post-vérification pour produire un **dépôt directement déposable sur le portail Vigie-Chiro** et tracer le dépôt. L'application **ne dialogue pas** avec le portail web : le téléversement final est manuel via navigateur. L'application se contente de préparer les fichiers à un emplacement connu, vérifier leur cohérence, et mémoriser la date de dépôt déclarée par l'utilisateur.

**Persona principal** : tous (Marie pour ses 1-2 dépôts annuels, Karim et Samuel pour leur cadence intensive de plusieurs dizaines de dépôts par saison).

**Pré-requis** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3) (DAO passages avec statut), [E3.S5](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s5) (passage avec verdict OK ou Utilisable).

## E4.S1 - Vérifier la cohérence du passage avant préparation du dépôt { #e4s1 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application contrôle automatiquement que mon passage est complet et conforme au protocole avant de me proposer la préparation du dépôt

**Afin de** ne pas déposer un dépôt incomplet ou invalide qui serait rejeté par Vigie-Chiro

**Critères d'acceptation** :

- [ ] Le bouton « Vérifier et préparer le dépôt » est **désactivé** tant que le passage n'a pas de verdict (statut < `Vérifié`).
- [ ] Si le verdict est `Inexploitable`, le bouton reste désactivé avec un message explicite « Ce passage ne peut pas être déposé ([R14](../Modèle%20conceptuel/Règles%20métier.md#r14)). Modifiez le verdict via la vue de vérification si vous changez d'avis. »
- [ ] Au clic sur « Vérifier et préparer le dépôt », l'application enchaîne une série de **vérifications** et affiche un rapport :
    - tous les enregistrements originaux ont-ils été transformés en séquences d'écoute ?
    - le préfixe `CarXXXXXX-AAAA-PassN-YY-` est-il présent et conforme sur tous les fichiers ([R6](../Modèle%20conceptuel/Règles%20métier.md#r6), [R7](../Modèle%20conceptuel/Règles%20métier.md#r7), [R8](../Modèle%20conceptuel/Règles%20métier.md#r8)) ?
    - le journal du capteur et le relevé climatique sont-ils présents (ou explicitement signalés absents) ?
    - le dossier de sortie est-il accessible en écriture ?
- [ ] Chaque vérification est affichée en ligne avec une icône ✅ ou ❌ et un message court.
- [ ] Si **au moins une vérification échoue**, le passage au statut `Prêt à déposer` est bloqué et l'utilisateur est invité à corriger (lien direct vers le parcours concerné pour les corrections faisables in-app).
- [ ] Si **toutes les vérifications passent**, le passage transitionne vers `Prêt à déposer` et l'écran [E4.S2](#e4s2) s'ouvre.

**Parcours rattaché** : [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md), étapes 1-2<br>
**Maquettes cibles** : [M-Lot](../Maquettes/M-Lot.md) (rapport de vérifications avec ✅/❌ par ligne)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E2.S5](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s5), [E2.S6](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s6), [E3.S5](E3%20-%20Vérifier%20la%20qualité%20d%27enregistrement.md#e3s5)<br>

---

## E4.S2 - Voir le récapitulatif du dépôt et ouvrir le dossier dans l'explorateur { #e4s2 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** voir un récapitulatif clair du dépôt (volume, nombre de fichiers, emplacement sur disque) et pouvoir ouvrir directement le dossier dans mon explorateur de fichiers

**Afin de** sélectionner facilement les fichiers à téléverser sur Vigie-Chiro depuis mon navigateur

**Critères d'acceptation** :

- [ ] L'écran « Dépôt » affiche : nombre de séquences d'écoute, taille totale, chemin absolu du dossier de sortie sur le disque local.
- [ ] Un bouton **« Ouvrir le dossier dans l'explorateur »** (libellé exact à voir avec la maquette) déclenche l'ouverture du dossier dans l'explorateur natif de l'OS (Files sous Linux, Finder sous macOS, Explorer sous Windows).
- [ ] Le chemin du dossier est aussi affiché sous forme **copiable** (clic icône « copier ») pour les cas où le bouton « Ouvrir » ne fonctionne pas (ex. environnement sans bureau graphique).
- [ ] Un rappel explicite indique que l'application **ne dialogue pas** avec Vigie-Chiro : « Téléversez ces fichiers manuellement sur https://vigiechiro.herokuapp.com/ depuis votre navigateur. »
- [ ] Un lien direct vers le portail Vigie-Chiro est mis à disposition (s'ouvre dans le navigateur par défaut).
- [ ] Tests d'intégration : le bouton « Ouvrir le dossier » se résout-il correctement sur Linux/macOS/Windows ? (à arbitrer selon l'OS de référence)

**Parcours rattaché** : [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md), étapes 3-4<br>
**Maquettes cibles** : [M-Lot](../Maquettes/M-Lot.md) (récapitulatif + bouton « Ouvrir le dossier » + lien externe Vigie-Chiro)<br>
**Dépendances** : [E4.S1](#e4s1)<br>

---

## E4.S3 - Marquer le passage comme déposé après téléversement manuel { #e4s3 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir confirmer à l'application que j'ai effectivement déposé sur Vigie-Chiro, avec mémorisation de la date de dépôt

**Afin de** clôturer ce passage dans mon suivi et le distinguer des passages encore en attente de dépôt

**Critères d'acceptation** :

- [ ] Sur l'écran « Dépôt », un bouton **« J'ai déposé »** (ou libellé équivalent) est mis en avant.
- [ ] Le clic ouvre une confirmation explicite : « Confirmez-vous avoir téléversé tous les fichiers sur Vigie-Chiro ? Cette action passe le passage au statut `Déposé` et mémorise la date du jour comme date de dépôt. »
- [ ] À la confirmation, le passage transitionne vers le statut `Déposé` ([E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3)) et la date courante est persistée comme **date de dépôt déclarée**.
- [ ] Le bouton est ensuite remplacé par une mention « Déposé le JJ/MM/AAAA » (modifiable via une action « Corriger la date de dépôt » pour gérer les cas où le téléversement a eu lieu un autre jour).
- [ ] Un passage `Déposé` apparaît distinctement dans la liste des passages (badge vert ou similaire).
- [ ] L'utilisateur peut **annuler** la déclaration de dépôt (retour au statut `Prêt à déposer`) en cas d'erreur, avec confirmation explicite.

**Parcours rattaché** : [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md), étape 5<br>
**Maquettes cibles** : [M-Lot](../Maquettes/M-Lot.md) (bouton « J'ai déposé » + variante état déposé)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E4.S1](#e4s1)<br>

---

## E4.S4 - Visualiser le statut d'avancement et la chronologie d'un passage { #e4s4 }

**En tant que** [Karim](../Personas/Karim.md) ou [Samuel](../Personas/Samuel.md) (qui jongle entre plusieurs dizaines de passages à différents stades)

**Je veux** voir en un coup d'œil où en est un passage dans le cycle de vie (Importé → Transformé → Vérifié → Prêt à déposer → Déposé) et l'historique des transitions

**Afin de** ne pas perdre la trace de ce qui a été fait sur quel passage et identifier rapidement les passages bloqués

**Critères d'acceptation** :

- [ ] La fiche détail d'un passage affiche **clairement** le statut d'avancement courant (badge coloré).
- [ ] Un mini-bandeau visuel (type indicateur d’étapes horizontal) montre les **5 statuts du cycle** avec le statut courant mis en évidence et les précédents marqués comme franchis.
- [ ] Pour chaque transition franchie, on voit **la date** de la transition (ex. « Importé le 15/06, Transformé le 15/06, Vérifié le 16/06 avec verdict OK, Déposé le 17/06 »).
- [ ] Si le passage est bloqué (verdict `Inexploitable`, vérifications échouées en [E4.S1](#e4s1)), un indicateur explicite signale la raison du blocage.
- [ ] Dans la vue tabulaire multi-sites (cf. [E5](index.md)), le statut est une colonne triable et filtrable.
- [ ] Le statut courant et l'historique sont **persistés** en BD et survivent aux redémarrages.

**Parcours rattaché** : [P4](../Parcours%20utilisateurs/P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md) (transverse - pertinent aussi pour P2, P3 et P5)<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (indicateur d’étapes de statut + chronologie), [M-MultiSite](../Maquettes/M-MultiSite.md) (colonne statut filtrable)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3)<br>
