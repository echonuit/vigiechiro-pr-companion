# E2 - 📥 Importer et transformer une nuit

[← Retour au sommaire story mapping](index.md) · **Parcours principal** : [P2 - Importer une nuit d'enregistrement](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)

**Portée** : remplacer entièrement la chaîne d'outils manuels (LupasRename + Kaléidoscope 4.3.1). Inspecter un dossier source SD, rattacher la nuit à un site/point/passage, copier de manière protégée les fichiers, les renommer selon le préfixe Vigie-Chiro, et produire les séquences d'écoute (expansion ×10 + chunks 5 s). C'est l'**épopée la plus dense** et la plus à risque techniquement (volumétrie 40 Go, traitement audio).

**Persona principal** : tous (Marie en mono-site occasionnel, Karim et Samuel en chaîne intensive).

**Pré-requis** : E0.S1 (schéma BD), E0.S2 (DAO sites/points), E0.S3 (DAO passages), E0.S4 (DAO sélections/séquences), E1 (au moins un site déclaré pour rattacher).

## E2.S1 - Inspecter un dossier source en lecture seule { #e2s1 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application examine le dossier de ma carte SD avant de rien y écrire et m'affiche un récapitulatif de ce qu'elle a trouvé

**Afin de** vérifier que le contenu correspond bien à la nuit que je veux importer avant de m'engager

**Critères d'acceptation** :

- [ ] L'utilisateur peut sélectionner un dossier source via un sélecteur ou un glisser-déposer sur la modale d'import.
- [ ] L'application **n'écrit rien** sur le dossier source pendant l'inspection ([R9](../Modèle%20conceptuel/Règles%20métier.md#r9)).
- [ ] L'inspection détecte et signale : journal du capteur (`LogPR<n>.txt`), relevé climatique (`PaRecPR<sn>_THLog.csv`), N enregistrements WAV, taille totale du dossier, plage horaire couverte.
- [ ] Le **n° de série de l'enregistreur** est extrait du journal du capteur quand celui-ci est présent.
- [ ] Les paramètres d'acquisition (Fe, gain, bande de fréquence) sont extraits du journal et affichés dans le récapitulatif.
- [ ] L'**état du nommage** des fichiers est classifié en `sans préfixe` / `tous préfixés` / `mélangé` (cf. [R6](../Modèle%20conceptuel/Règles%20métier.md#r6)).
- [ ] Si le journal du capteur est absent ou illisible, l'inspection se poursuit avec un avertissement explicite (« le diagnostic matériel sera limité ») mais n'est pas bloquante.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 2<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (récapitulatif d'inspection du dossier source)<br>
**Dépendances** : aucune (lecture seule, pas besoin de la BD à ce stade)<br>

---

## E2.S2 - Rattacher la nuit à un site, point, année et n° de passage { #e2s2 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** indiquer à quelle session de session d'enregistrement (site, point, année, n° de passage) cette nuit appartient

**Afin que** les fichiers soient renommés correctement et que la nuit soit retrouvable plus tard dans la base

**Critères d'acceptation** :

- [ ] La modale d'import propose 4 champs : `Site` (combobox), `Point` (combobox dépendant du site), `Année` (par défaut année courante), `N° de passage` (par défaut auto-incrémenté).
- [ ] Le combobox `Site` ne propose que les sites déclarés en base (cf. [E1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md)). Si aucun site n'existe, l'option « + Créer un nouveau site » est proposée (cf. E1.S5).
- [ ] Si l'enregistreur (n° de série) a déjà été utilisé pour importer une nuit auparavant, la **dernière association connue (site + point) est présélectionnée** (cf. E2.S7).
- [ ] Le n° de passage est pré-rempli en **auto-incrément** (max + 1 sur les passages déjà enregistrés pour ce point cette année, ou 1 si aucun) et reste **modifiable librement**.
- [ ] La validation de la modale crée un nouveau passage en BD avec le statut `En cours d'import` et l'unicité du quadruplet `(carré, année, n° passage, point)` est vérifiée ([R5](../Modèle%20conceptuel/Règles%20métier.md#r5)). Conflit → message d'erreur explicite avec proposition d'aller modifier le n° de passage.
- [ ] Le clic sur « Importer » est bloqué tant que le quadruplet n'est pas valide.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 3 (cas « sans préfixe »)<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (formulaire de rattachement avec auto-incrément du n° de passage)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E1.S1](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s1), [E1.S2](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s2), [E2.S1](#e2s1)<br>

---

## E2.S3 - Extraire le rattachement depuis un dossier déjà préfixé { #e2s3 }

**En tant que** [Marie](../Personas/Marie.md) (re-important un dossier déjà passé chez LupasRename ou un dossier déjà importé une fois)

**Je veux** que l'application reconnaisse que les fichiers ont déjà un préfixe `CarXXXXXX-AAAA-PassN-YY-` et en extraie automatiquement les 4 informations de rattachement

**Afin de** ne pas avoir à les ressaisir manuellement et éviter les erreurs

**Critères d'acceptation** :

- [ ] Quand l'inspection (E2.S1) a classifié le dossier en `tous préfixés`, l'application **extrait** le quadruplet `(carré, année, n° passage, point)` depuis le préfixe d'un fichier représentatif et le présélectionne dans la modale.
- [ ] Si le carré ou le point extrait ne correspond à aucun site déclaré, l'option « + Créer un nouveau site » est proposée avec le n° de carré pré-rempli depuis le préfixe.
- [ ] Si l'utilisateur **modifie un des 4 champs** présélectionnés, l'application détecte l'**incohérence préfixe ↔ saisie** et affiche un avertissement.
- [ ] L'utilisateur a alors deux choix explicites : (a) **Réaligner les noms de fichiers sur la saisie** (re-renommage de tous les fichiers à l'étape suivante, cf. E2.S5), (b) **Restaurer les valeurs extraites du préfixe** (annule la modification).
- [ ] Si l'utilisateur valide sans modification, **aucun re-renommage** n'a lieu : les fichiers conservent leur nom d'origine après la copie protégée.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 3 (cas « déjà préfixés ») + étape 4 (incohérence)<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (avec affichage du quadruplet extrait + alerte d'incohérence)<br>
**Dépendances** : [E2.S1](#e2s1), [E2.S2](#e2s2)<br>

---

## E2.S4 - Copier de manière protégée les fichiers depuis la SD { #e2s4 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application copie tous les fichiers (WAV, journal, climat) de la carte SD vers son espace de travail sans toucher aux originaux

**Afin de** préserver les données brutes sur la SD en cas de problème pendant le traitement

**Critères d'acceptation** :

- [ ] La copie ne modifie **aucun fichier** sur la carte SD source ([R9](../Modèle%20conceptuel/Règles%20métier.md#r9)).
- [ ] Tous les fichiers du dossier source sont copiés (WAV, journal du capteur, relevé climatique).
- [ ] La copie se fait **en arrière-plan** sans freezer l'IHM, avec une barre de progression détaillée (fichier en cours, % global, taille restante, ETA) (cf. [O3](../../Objectifs%20qualités/Objectifs%20qualités/O3.md)).
- [ ] L'utilisateur peut fermer la fenêtre de progression sans annuler l'opération (la copie continue en tâche de fond).
- [ ] L'application doit tenir une nuit de **40 Go** sans freezer (cas Samuel en haute saison).
- [ ] Si la cible (espace de travail) manque d'espace disque, l'application le détecte avant de commencer et bloque proprement avec un message explicite.
- [ ] Si la copie est interrompue (fermeture inopinée, crash), elle peut reprendre au démarrage suivant grâce à E0.S6.
- [ ] Tests d'intégration avec un dossier source de plusieurs centaines de fichiers WAV.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 4<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (barre de progression et état de la copie)<br>
**Dépendances** : [E0.S6](E0%20-%20Fondations%20de%20persistance.md#e0s6) (pour la reprise), [E2.S2](#e2s2) ou [E2.S3](#e2s3) (rattachement validé)<br>

---

## E2.S5 - Renommer les fichiers avec le préfixe Vigie-Chiro { #e2s5 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que les fichiers copiés reçoivent automatiquement le préfixe `CarXXXXXX-AAAA-PassN-YY-` correspondant au rattachement saisi

**Afin que** mon dépôt sur le portail Vigie-Chiro soit conforme au protocole sans que je n'aie à renommer manuellement chaque fichier

**Critères d'acceptation** :

- [ ] Le préfixe `CarXXXXXX-AAAA-PassN-YY-` est appliqué à chaque fichier copié (WAV originaux, journal, climat) à partir du rattachement saisi (cf. E2.S2).
- [ ] Les **tirets** utilisés sont des « tirets du 6 » (`-`, U+002D HYPHEN-MINUS), pas des cadratins ni demi-cadratins ([R6](../Modèle%20conceptuel/Règles%20métier.md#r6)).
- [ ] Le suffixe original de l'enregistreur (`PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav`) est **conservé tel quel** après le préfixe ([R7](../Modèle%20conceptuel/Règles%20métier.md#r7)).
- [ ] Si les fichiers étaient **déjà préfixés** et l'utilisateur a validé sans modification (cf. E2.S3), aucun renommage n'a lieu.
- [ ] Si l'utilisateur a choisi « Réaligner les noms sur la saisie » (cf. E2.S3), tous les fichiers sont re-renommés avec le nouveau préfixe.
- [ ] Le renommage est **atomique** : soit tous les fichiers sont renommés, soit aucun ne l'est (en cas d'erreur, rollback).
- [ ] Tests d'intégration avec les 3 cas : sans préfixe, déjà préfixé sans modif, déjà préfixé avec réalignement.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 4<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (étape de progression « renommage » dans la barre)<br>
**Dépendances** : [E2.S4](#e2s4)<br>

---

## E2.S6 - Transformer chaque enregistrement en séquences d'écoute ralenties ×10 { #e2s6 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** que l'application transforme automatiquement chaque WAV brut en plusieurs séquences de 5 s ralenties ×10

**Afin de** pouvoir les écouter directement avec mon casque (l'oreille humaine n'entend pas les ultrasons à vitesse normale) sans avoir à utiliser Kaléidoscope ou un autre outil externe

**Critères d'acceptation** :

- [ ] Pour chaque enregistrement original copié (E2.S4), l'application produit `ceil(durée / 5)` séquences d'écoute, une par tranche de **5 s réelles** ([R10](../Modèle%20conceptuel/Règles%20métier.md#r10)).
- [ ] Chaque séquence est un fichier WAV portant **5 s réelles d'enregistrement, ralenties ×10** (expansion temporelle, pas un re-échantillonnage) : elle **dure donc 50 s à l'écoute**. Le découpage se fait au rythme d'**acquisition** du signal brut, et non sur le signal déjà ralenti. La dernière séquence d'un enregistrement peut être plus courte que 5 s.
- [ ] Le nom de chaque séquence reprend le nom de son enregistrement original source en insérant un suffixe `_000`, `_001`, etc. entre le nom de base et l'extension `.wav` ([R8](../Modèle%20conceptuel/Règles%20métier.md#r8)).
- [ ] La transformation est **déterministe** : relancer la transformation sur les mêmes enregistrements produit les mêmes séquences au bit près ([R11](../Modèle%20conceptuel/Règles%20métier.md#r11)).
- [ ] La transformation se fait **en arrière-plan** avec barre de progression (séquence en cours, % global). L'utilisateur peut fermer la fenêtre sans annuler.
- [ ] Si la transformation est interrompue, elle peut reprendre au démarrage suivant (E0.S6).
- [ ] Le passage passe au statut `Transformé` une fois toutes les séquences produites.
- [ ] Tests d'intégration : vérifier le nombre de séquences produites, leurs durées et leur déterminisme sur un jeu de données représentatif.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 5<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (barre de progression de la transformation)<br>
**Dépendances** : [E0.S4](E0%20-%20Fondations%20de%20persistance.md#e0s4), [E2.S4](#e2s4), [E2.S5](#e2s5)<br>

---

## E2.S7 - Mémoriser l'association enregistreur ↔ site/point { #e2s7 }

**En tant que** [Karim](../Personas/Karim.md) ou [Samuel](../Personas/Samuel.md) (qui réutilise plusieurs fois le même enregistreur sur les mêmes points)

**Je veux** que l'application se souvienne du dernier site/point sur lequel chaque enregistreur (n° de série) a été utilisé

**Afin que** la modale d'import présélectionne automatiquement le bon site/point la fois suivante et que je n'aie pas à le ressaisir

**Critères d'acceptation** :

- [ ] À chaque import réussi, l'association `(n° de série enregistreur → dernier site, dernier point)` est persistée en BD (cf. E0.S3).
- [ ] À l'inspection (E2.S1), si le n° de série de l'enregistreur correspond à une association déjà connue, le champ « Site » et « Point » de la modale d'import sont présélectionnés sur cette dernière association.
- [ ] L'utilisateur reste libre de modifier la présélection s'il a déplacé l'enregistreur sur un autre point.
- [ ] La modification du site/point réécrit l'association mémorisée pour les imports suivants.
- [ ] Tests d'intégration : 2 imports successifs avec le même n° de série → la 2e modale doit présélectionner ce qui a été choisi à la 1ère.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), étape 7 (mémorisation), réutilisé en étape 3 (présélection au prochain import)<br>
**Maquettes cibles** : [M-Import](../Maquettes/M-Import.md) (combobox avec valeur présélectionnée + indication discrète « dernière association connue pour cet enregistreur »)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E2.S2](#e2s2)<br>

---

## E2.S8 - Modifier rétroactivement le rattachement d'un passage déjà importé { #e2s8 }

**En tant que** [Marie](../Personas/Marie.md)

**Je veux** pouvoir corriger le rattachement (site, point, année, n° de passage) d'un passage déjà importé si je me rends compte d'une erreur

**Afin de** ne pas avoir à supprimer puis ré-importer toute la nuit, et pour que les fichiers soient re-renommés correctement

**Critères d'acceptation** :

- [ ] Depuis la fiche détail d'un passage, une action « Modifier le rattachement » est disponible.
- [ ] Le formulaire ré-utilise les mêmes contrôles que la modale d'import (cf. E2.S2) avec les valeurs actuelles présélectionnées.
- [ ] À la validation, l'application affiche un **récapitulatif explicite des conséquences** : « N fichiers vont être renommés, le passage va passer du quadruplet X au quadruplet Y ».
- [ ] L'utilisateur doit confirmer explicitement avant que l'opération ne s'exécute (action irréversible).
- [ ] Le re-renommage est atomique (rollback en cas d'erreur).
- [ ] L'unicité du nouveau quadruplet est vérifiée avant l'opération ([R5](../Modèle%20conceptuel/Règles%20métier.md#r5)) ; conflit → message clair sans modification.
- [ ] Tests d'intégration : passage importé → modification du rattachement → vérification que tous les fichiers sont renommés avec le nouveau préfixe et que le passage en BD a les nouvelles valeurs.

**Parcours rattaché** : [P2](../Parcours%20utilisateurs/P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md), notes importantes (« changer rétroactivement »)<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (avec action « Modifier le rattachement » et écran de confirmation)<br>
**Dépendances** : [E0.S3](E0%20-%20Fondations%20de%20persistance.md#e0s3), [E2.S2](#e2s2), [E2.S5](#e2s5)<br>
