# Galerie des captures d'écran

Captures de **référence** de tous les états d'écran de VigieChiro Companion, rendues **hors-écran** et
de façon **déterministe** via la *Headless Platform* de JavaFX 26 : deux exécutions produisent des PNG
identiques au bit près. C'est ce qui permet de comparer d'une version à l'autre.

## S'en servir pour une passe visuelle

Ce document est fait pour être **parcouru de haut en bas**. Il suit le trajet d'une nuit dans
l'application - on se connecte, on prépare le terrain, on rapatrie, on contrôle, on dépose, on écoute,
on lit la saison - et non l'ordre alphabétique des fichiers.

Pour chaque écran, l'**état nominal vient en premier**, les variantes ensuite : état vide, refus,
interruption, reprise. Ce sont ces variantes qui se dégradent sans bruit, parce qu'aucun test ne
regarde une capture. Cliquer une vignette ouvre le PNG en pleine résolution.

Ce qu'une passe cherche, et qu'aucun test ne voit : un libellé **tronqué**, un glyphe **absent**, un
alignement qui a glissé, un bandeau qui a changé de sévérité, une colonne disparue.

## Régénérer

```bash
./.github/assets/capture-screenshots.sh    # JDK 25 standard, aucun serveur d'affichage requis
```

La CI les régénère à chaque poussée sur `main` (`capture-vues.yml`), donc une capture obsolète ici
signale un écart réel, pas un oubli de commande.

## Complétude

Trois garde-fous, vérifiés par [`check-captures.sh`](check-captures.sh) :

1. chaque vue `**/view/*.fxml` figure au [manifeste](captures.manifest) avec au moins une capture ;
2. chaque capture déclarée au manifeste existe sur le disque ;
3. **chaque capture du disque est présentée dans ce document** - sans quoi la galerie se met à couvrir
   une partie du produit en laissant croire qu'elle le couvre tout entier. C'est ce qui était arrivé :
   33 captures présentées sur 126. Le compte exact n'est pas répété ici : il bouge à chaque chantier,
   et `check-captures.sh` le rend à chaque exécution. Un nombre écrit en prose dérive tout seul.

## Sommaire

- **Point d'entrée** : [Accueil et chrome applicatif](#accueil-et-chrome-applicatif) · [Connexion à Vigie-Chiro](#connexion-à-vigie-chiro) · [Réglages](#réglages)
- **Préparer le terrain** : [Mes sites](#mes-sites) · [Détail d'un site](#détail-dun-site) · [Modale site](#modale-site) · [Modale point d'écoute](#modale-point-découte) · [Saison](#saison)
- **Rapatrier une nuit** : [Importer une nuit](#importer-une-nuit) · [Qualification](#qualification) · [Modale de sélection](#modale-de-sélection)
- **Le pivot du workflow** : [Passage](#passage) · [Modale de rattachement](#modale-de-rattachement) · [Modale de réactivation](#modale-de-réactivation) · [Modale des campagnes](#modale-des-campagnes)
- **Contrôler et déposer** : [Diagnostic](#diagnostic) · [Audit de cohérence](#audit-de-cohérence) · [Lot : préparer et déposer](#lot--préparer-et-déposer)
- **Écouter et valider** : [Sons & validation](#sons--validation)
- **Lire la saison** : [Carte & passages](#carte--passages) · [Modale de reconstruction](#modale-de-reconstruction) · [Espèces & observations](#espèces--observations) · [Activité de la nuit](#activité-de-la-nuit) · [Synthèse](#synthèse)
- **Composants transverses** : [Bandeau de retour](#bandeau-de-retour) · [Compte rendu d'opération](#compte-rendu-dopération) · [Relevé multi-sites](#relevé-multi-sites)


---

# Point d'entrée

## Accueil et chrome applicatif

Le bandeau, les cartes d'activités, la recherche transverse et les menus qui les surplombent.

<sub>`commun/view/MainView.fxml` &middot; 8 capture(s)</sub>

<table>
<tr>
<th width="50%">Accueil</th>
<th width="50%">Recherche transverse</th>
</tr>
<tr>
<td><a href="apercu-accueil.png"><img src="apercu-accueil.png" width="430" alt="Accueil"></a></td>
<td><a href="apercu-recherche.png"><img src="apercu-recherche.png" width="430" alt="Recherche transverse"></a></td>
</tr>
<tr>
<th width="50%">Accueil, compteurs renseignés</th>
<th width="50%"></th>
</tr>
<tr>
<td><a href="apercu-accueil-compteurs.png"><img src="apercu-accueil-compteurs.png" width="430" alt="Accueil avec le bandeau de compteurs : deux renseignés, deux à zéro en gris atténué"></a></td>
<td></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Garde de saisie en cours</th>
<th width="50%">Recherche espèces</th>
</tr>
<tr>
<td><a href="apercu-navigation-garde-saisie.png"><img src="apercu-navigation-garde-saisie.png" width="430" alt="Garde de saisie en cours"></a></td>
<td><a href="apercu-recherche-especes.png"><img src="apercu-recherche-especes.png" width="430" alt="Recherche espèces"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Fiche espèce, source</th>
<th width="50%">Menu Outils</th>
</tr>
<tr>
<td><a href="apercu-fiche-espece-source.png"><img src="apercu-fiche-espece-source.png" width="430" alt="Fiche espèce, source"></a></td>
<td><a href="apercu-menu-outils.png"><img src="apercu-menu-outils.png" width="430" alt="Menu Outils"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Annonce de mise à jour</th>
<th width="50%">À propos</th>
</tr>
<tr>
<td><a href="apercu-annonce-maj.png"><img src="apercu-annonce-maj.png" width="430" alt="Annonce de mise à jour"></a></td>
<td><a href="apercu-a-propos.png"><img src="apercu-a-propos.png" width="430" alt="À propos"></a></td>
</tr>
</table>

<table>
<tr>
<th width="100%">Annonce de mise à jour, variante Windows</th>
</tr>
<tr>
<td><a href="apercu-annonce-maj-windows.png"><img src="apercu-annonce-maj-windows.png" width="870" alt="Annonce de mise à jour sous Windows : le message porte en plus « fermez l'application avant d'installer » et le geste winget, et s'enroule donc sur deux lignes"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Dossier de travail déjà ouvert</th>
<th width="50%">Restauration : nuits déplacées</th>
</tr>
<tr>
<td><a href="apercu-demarrage-dossier-occupe.png"><img src="apercu-demarrage-dossier-occupe.png" width="430" alt="Dossier de travail déjà ouvert"></a></td>
<td><a href="apercu-restauration-nuits-deplacees.png"><img src="apercu-restauration-nuits-deplacees.png" width="430" alt="Restauration : nuits déplacées"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Restauration : version trop récente</th>
<th width="50%">Restauration : quelle sauvegarde ?</th>
</tr>
<tr>
<td><a href="apercu-restauration-version-trop-recente.png"><img src="apercu-restauration-version-trop-recente.png" width="430" alt="Restauration : version trop récente"></a></td>
<td><a href="apercu-restauration-choix-sauvegarde.png"><img src="apercu-restauration-choix-sauvegarde.png" width="430" alt="Restauration : choix d'une sauvegarde, avec date, taille et total"></a></td>
<td><a href="apercu-restauration-une-nuit-a-la-fois.png"><img src="apercu-restauration-une-nuit-a-la-fois.png" width="430" alt="Restauration menée une nuit à la fois, faute de place"></a></td>
<td><a href="apercu-restauration-place-insuffisante.png"><img src="apercu-restauration-place-insuffisante.png" width="430" alt="Refus faute de place : combien libérer et où"></a></td>
</tr>
</table>

## Connexion à Vigie-Chiro

La modale de jeton, son bandeau de refus et sa progression.

<sub>`connexion/view/ConnexionModale.fxml` &middot; 3 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Bandeau de refus</th>
</tr>
<tr>
<td><a href="apercu-connexion.png"><img src="apercu-connexion.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-connexion-bandeau.png"><img src="apercu-connexion-bandeau.png" width="430" alt="Bandeau de refus"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Progression</th>
</tr>
<tr>
<td><a href="apercu-connexion-progression.png"><img src="apercu-connexion-progression.png" width="430" alt="Progression"></a></td>
</tr>
</table>

## Réglages

Un onglet par famille de préférences.

<sub>`commun/view/EcranReglages.fxml` &middot; 5 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Import</th>
</tr>
<tr>
<td><a href="apercu-reglages.png"><img src="apercu-reglages.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-reglages-import.png"><img src="apercu-reglages-import.png" width="430" alt="Import"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Dépôt</th>
<th width="50%">Emplacements</th>
</tr>
<tr>
<td><a href="apercu-reglages-depot.png"><img src="apercu-reglages-depot.png" width="430" alt="Dépôt"></a></td>
<td><a href="apercu-reglages-emplacements.png"><img src="apercu-reglages-emplacements.png" width="430" alt="Emplacements"></a></td>
</tr>
<tr>
<th colspan="2">Emplacements personnalisés : « Rétablir les emplacements par défaut » devient actif</th>
</tr>
<tr>
<td colspan="2"><a href="apercu-reglages-emplacements-personnalises.png"><img src="apercu-reglages-emplacements-personnalises.png" width="760" alt="Onglet Emplacements avec une configuration personnalisée : le bouton Rétablir est actif"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Fonctionnalités</th>
</tr>
<tr>
<td><a href="apercu-reglages-fonctionnalites.png"><img src="apercu-reglages-fonctionnalites.png" width="430" alt="Fonctionnalités"></a></td>
</tr>
</table>


---

# Préparer le terrain

## Mes sites

La liste des carrés suivis, peuplée et à l'état initial.

<sub>`sites/view/MesSites.fxml` &middot; 3 capture(s)</sub>

<table>
<tr>
<th width="50%">Mes sites</th>
<th width="50%">Mes sites vide</th>
</tr>
<tr>
<td><a href="apercu-sites-mes-sites.png"><img src="apercu-sites-mes-sites.png" width="430" alt="Mes sites"></a></td>
<td><a href="apercu-sites-mes-sites-vide.png"><img src="apercu-sites-mes-sites-vide.png" width="430" alt="Mes sites vide"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Synchro progression</th>
</tr>
<tr>
<td><a href="apercu-sites-synchro-progression.png"><img src="apercu-sites-synchro-progression.png" width="430" alt="Synchro progression"></a></td>
</tr>
</table>

## Détail d'un site

<sub>`sites/view/SiteDetail.fxml` &middot; 2 capture(s)</sub>

<table>
<tr>
<th width="50%">Détail</th>
<th width="50%">Détail sans passage</th>
</tr>
<tr>
<td><a href="apercu-sites-detail.png"><img src="apercu-sites-detail.png" width="430" alt="Détail"></a></td>
<td><a href="apercu-sites-detail-sans-passage.png"><img src="apercu-sites-detail-sans-passage.png" width="430" alt="Détail sans passage"></a></td>
</tr>
</table>

## Modale site

<sub>`sites/view/ModaleSite.fxml` &middot; 5 capture(s)</sub>

<table>
<tr>
<th width="50%">Modale site</th>
<th width="50%">Modale site création</th>
</tr>
<tr>
<td><a href="apercu-sites-modale-site.png"><img src="apercu-sites-modale-site.png" width="430" alt="Modale site"></a></td>
<td><a href="apercu-sites-modale-site-creation.png"><img src="apercu-sites-modale-site-creation.png" width="430" alt="Modale site création"></a></td>
</tr>
<tr>
<th width="50%">Modale site : carré déjà déclaré</th>
<th width="50%">Modale site : carré sous un autre protocole</th>
</tr>
<tr>
<td><a href="apercu-sites-modale-site-carre-existant.png"><img src="apercu-sites-modale-site-carre-existant.png" width="430" alt="Modale site : le carré vérifié existe déjà sur Vigie-Chiro, et peut être récupéré"></a></td>
<td><a href="apercu-sites-modale-site-autre-protocole.png"><img src="apercu-sites-modale-site-autre-protocole.png" width="430" alt="Modale site : le carré existe en Routier, protocole que Companion ne gère pas"></a></td>
</tr>
<tr>
<th width="50%">Modale site : position située</th>
<th width="50%">Modale site : position sur une frontière</th>
</tr>
<tr>
<td><a href="apercu-sites-modale-site-position-situee.png"><img src="apercu-sites-modale-site-position-situee.png" width="430" alt="Modale site : une position collée a rempli le numéro de carré, sans rien demander au réseau"></a></td>
<td><a href="apercu-sites-modale-site-position-frontiere.png"><img src="apercu-sites-modale-site-position-frontiere.png" width="430" alt="Modale site : la position est sur une frontière, les deux carrés candidats sont nommés et aucun n'est déposé"></a></td>
</tr>
<tr>
<th width="50%">Compte rendu : carré récupéré</th>
<th width="50%"></th>
</tr>
<tr>
<td><a href="apercu-sites-carre-recupere.png"><img src="apercu-sites-carre-recupere.png" width="430" alt="Mes sites après une récupération : le carré paraît dans la liste et le bandeau annonce ses points"></a></td>
<td></td>
</tr>
</table>

## Modale point d'écoute

<sub>`sites/view/ModalePoint.fxml` &middot; 3 capture(s)</sub>

<table>
<tr>
<th width="50%">Modale point</th>
<th width="50%">Modale point création</th>
</tr>
<tr>
<td><a href="apercu-sites-modale-point.png"><img src="apercu-sites-modale-point.png" width="430" alt="Modale point"></a></td>
<td><a href="apercu-sites-modale-point-creation.png"><img src="apercu-sites-modale-point-creation.png" width="430" alt="Modale point création"></a></td>
</tr>
<tr>
<th width="50%">Modale point carré divergent</th>
<th width="50%"></th>
</tr>
<tr>
<td><a href="apercu-sites-modale-point-carre-divergent.png"><img src="apercu-sites-modale-point-carre-divergent.png" width="430" alt="Modale point carré divergent"></a></td>
<td></td>
</tr>
</table>

## Saison

<sub>`saison/view/Saison.fxml` &middot; 1 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
</tr>
<tr>
<td><a href="apercu-saison.png"><img src="apercu-saison.png" width="430" alt="État nominal"></a></td>
</tr>
</table>


---

# Rapatrier une nuit

## Importer une nuit

L'assistant et **tous ses chemins non nominaux** : c'est la vue la plus riche en états, et celle où une régression se voit le moins.

<sub>`importation/view/Importation.fxml` &middot; 12 capture(s)</sub>

<table>
<tr>
<th width="50%">Assistant</th>
<th width="50%">En cours</th>
</tr>
<tr>
<td><a href="apercu-import-assistant.png"><img src="apercu-import-assistant.png" width="430" alt="Assistant"></a></td>
<td><a href="apercu-import-en-cours.png"><img src="apercu-import-en-cours.png" width="430" alt="En cours"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Mélange</th>
<th width="50%">Incohérence</th>
</tr>
<tr>
<td><a href="apercu-import-melange.png"><img src="apercu-import-melange.png" width="430" alt="Mélange"></a></td>
<td><a href="apercu-import-incoherence.png"><img src="apercu-import-incoherence.png" width="430" alt="Incohérence"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Multi nuits</th>
<th width="50%">Rattachement avertissements</th>
</tr>
<tr>
<td><a href="apercu-import-multi-nuits.png"><img src="apercu-import-multi-nuits.png" width="430" alt="Multi nuits"></a></td>
<td><a href="apercu-import-rattachement-avertissements.png"><img src="apercu-import-rattachement-avertissements.png" width="430" alt="Rattachement avertissements"></a></td>
</tr>
<tr>
<td><a href="apercu-import-lecture-seule.png"><img src="apercu-import-lecture-seule.png" width="430" alt="Support en lecture seule"></a></td>
<td></td>
</tr>
</table>

<table>
<tr>
<th width="100%">Nommage : fichiers déjà préfixés</th>
</tr>
<tr>
<td><a href="apercu-import-prefixe.png"><img src="apercu-import-prefixe.png" width="870" alt="Inspection d'une carte dont les bruts portent déjà le préfixe Car130711-2026-Pass1-Z1 : « État du nommage : fichiers déjà préfixés (seront copiés et transformés) », l'aperçu du préfixe trouvé, et le refus qui bloque l'import parce que ce préfixe désigne un autre carré que le rattachement choisi"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Décompression</th>
<th width="50%">Rejets</th>
</tr>
<tr>
<td><a href="apercu-import-decompression.png"><img src="apercu-import-decompression.png" width="430" alt="Décompression"></a></td>
<td><a href="apercu-import-rejets.png"><img src="apercu-import-rejets.png" width="430" alt="Rejets"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Décompression d'un gros fichier (volume écrit)</th>
<th width="50%">Archive refusée : espace disque</th>
</tr>
<tr>
<td><a href="apercu-import-decompression-volume.png"><img src="apercu-import-decompression-volume.png" width="430" alt="Décompression d'un gros fichier : le volume écrit s'affiche"></a></td>
<td><a href="apercu-import-archive-espace-disque.png"><img src="apercu-import-archive-espace-disque.png" width="430" alt="Archive refusée faute de place disque"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Archive qui ment sur sa taille</th>
<th width="50%"></th>
</tr>
<tr>
<td><a href="apercu-import-archive-menteuse.png"><img src="apercu-import-archive-menteuse.png" width="430" alt="Archive interrompue : elle écrit plus qu'elle n'annonçait"></a></td>
<td></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Doublon</th>
<th width="50%">Écrasement principe</th>
</tr>
<tr>
<td><a href="apercu-import-doublon.png"><img src="apercu-import-doublon.png" width="430" alt="Doublon"></a></td>
<td><a href="apercu-import-ecrasement-principe.png"><img src="apercu-import-ecrasement-principe.png" width="430" alt="Écrasement principe"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Écrasement</th>
<th width="50%">Transformés question</th>
</tr>
<tr>
<td><a href="apercu-import-ecrasement.png"><img src="apercu-import-ecrasement.png" width="430" alt="Écrasement"></a></td>
<td><a href="apercu-import-transformes-question.png"><img src="apercu-import-transformes-question.png" width="430" alt="Transformés question"></a></td>
</tr>
</table>

## Qualification

<sub>`qualification/view/Qualification.fxml` &middot; 3 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Initial</th>
</tr>
<tr>
<td><a href="apercu-qualification.png"><img src="apercu-qualification.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-qualification-initial.png"><img src="apercu-qualification-initial.png" width="430" alt="Initial"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">À jeter</th>
</tr>
<tr>
<td><a href="apercu-qualification-a-jeter.png"><img src="apercu-qualification-a-jeter.png" width="430" alt="À jeter"></a></td>
</tr>
</table>

## Modale de sélection

<sub>`qualification/view/ModaleSelection.fxml` &middot; 1 capture(s)</sub>

<table>
<tr>
<th width="50%">Personnaliser</th>
</tr>
<tr>
<td><a href="apercu-qualification-personnaliser.png"><img src="apercu-qualification-personnaliser.png" width="430" alt="Personnaliser"></a></td>
</tr>
</table>


---

# Le pivot du workflow

## Passage

L'écran qui porte l'état d'avancement d'une nuit.

<sub>`passage/view/Passage.fxml` &middot; 4 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Déposé</th>
</tr>
<tr>
<td><a href="apercu-passage.png"><img src="apercu-passage.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-passage-depose.png"><img src="apercu-passage-depose.png" width="430" alt="Déposé"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Retour</th>
<th width="50%">Squelette</th>
</tr>
<tr>
<td><a href="apercu-passage-retour.png"><img src="apercu-passage-retour.png" width="430" alt="Retour"></a></td>
<td><a href="apercu-passage-squelette.png"><img src="apercu-passage-squelette.png" width="430" alt="Squelette"></a></td>
</tr>
</table>

## Modale de rattachement

<sub>`passage/view/RattachementModale.fxml` &middot; 4 capture(s)</sub>

<table>
<tr>
<th width="50%">Rattachement</th>
<th width="50%">Rattachement, connecté</th>
</tr>
<tr>
<td><a href="apercu-passage-rattachement.png"><img src="apercu-passage-rattachement.png" width="430" alt="Rattachement"></a></td>
<td><a href="apercu-passage-rattachement-connecte.png"><img src="apercu-passage-rattachement-connecte.png" width="430" alt="Rattachement, connecté"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Rattachement squelette</th>
<th width="50%">Rattachement retour</th>
</tr>
<tr>
<td><a href="apercu-passage-rattachement-squelette.png"><img src="apercu-passage-rattachement-squelette.png" width="430" alt="Rattachement squelette"></a></td>
<td><a href="apercu-passage-rattachement-retour.png"><img src="apercu-passage-rattachement-retour.png" width="430" alt="Rattachement retour"></a></td>
</tr>
</table>

## Modale de réactivation

<sub>`passage/view/ReactivationModale.fxml` &middot; 3 capture(s)</sub>

<table>
<tr>
<th width="50%">Réactivation</th>
<th width="50%">Réactivation compte rendu</th>
</tr>
<tr>
<td><a href="apercu-passage-reactivation.png"><img src="apercu-passage-reactivation.png" width="430" alt="Réactivation"></a></td>
<td><a href="apercu-passage-reactivation-compte-rendu.png"><img src="apercu-passage-reactivation-compte-rendu.png" width="430" alt="Réactivation compte rendu"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Réactivation lacunes</th>
</tr>
<tr>
<td><a href="apercu-passage-reactivation-lacunes.png"><img src="apercu-passage-reactivation-lacunes.png" width="430" alt="Réactivation lacunes"></a></td>
</tr>
</table>

## Modale des campagnes

<sub>`passage/view/GestionCampagnesModale.fxml` &middot; 1 capture(s)</sub>

<table>
<tr>
<th width="50%">Campagnes</th>
</tr>
<tr>
<td><a href="apercu-passage-campagnes.png"><img src="apercu-passage-campagnes.png" width="430" alt="Campagnes"></a></td>
</tr>
</table>


---

# Contrôler et déposer

## Diagnostic

<sub>`diagnostic/view/Diagnostic.fxml` &middot; 6 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Sans relevé</th>
</tr>
<tr>
<td><a href="apercu-diagnostic.png"><img src="apercu-diagnostic.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-diagnostic-sans-releve.png"><img src="apercu-diagnostic-sans-releve.png" width="430" alt="Sans relevé"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Sans GPS</th>
<th width="50%">Hors nuit</th>
</tr>
<tr>
<td><a href="apercu-diagnostic-sans-gps.png"><img src="apercu-diagnostic-sans-gps.png" width="430" alt="Sans GPS"></a></td>
<td><a href="apercu-diagnostic-protocole-non-couvert.png"><img src="apercu-diagnostic-protocole-non-couvert.png" width="430" alt="Protocole non couvert"></a></td>
<td><a href="apercu-diagnostic-nuit-interrompue.png"><img src="apercu-diagnostic-nuit-interrompue.png" width="430" alt="Nuit interrompue"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Début non couvert</th>
<th width="50%">Fin non couverte</th>
</tr>
<tr>
<td><a href="apercu-diagnostic-debut-non-couvert.png"><img src="apercu-diagnostic-debut-non-couvert.png" width="430" alt="Le début manque, la fin est couverte"></a></td>
<td><a href="apercu-diagnostic-fin-non-couverte.png"><img src="apercu-diagnostic-fin-non-couverte.png" width="430" alt="La fin manque, le début est couvert"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Retour</th>
<th width="50%">Export</th>
</tr>
<tr>
<td><a href="apercu-diagnostic-retour.png"><img src="apercu-diagnostic-retour.png" width="430" alt="Retour"></a></td>
<td><a href="apercu-diagnostic-export.png"><img src="apercu-diagnostic-export.png" width="430" alt="Export"></a></td>
</tr>
<tr>
<th colspan="2">Erreur d'ouverture, rendue dans le chrome : la barre de statut dit de quel passage il s'agit</th>
</tr>
<tr>
<td colspan="2"><a href="apercu-diagnostic-erreur-statut.png"><img src="apercu-diagnostic-erreur-statut.png" width="860" alt="Erreur d'ouverture avec la barre de statut"></a></td>
</tr>
</table>

## Audit de cohérence

<sub>`audit/view/Audit.fxml` &middot; 1 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
</tr>
<tr>
<td><a href="apercu-audit.png"><img src="apercu-audit.png" width="430" alt="État nominal"></a></td>
</tr>
</table>

## Lot : préparer et déposer

Le second gros pourvoyeur d'états : préparation, téléversement, reprise, interruption.

<sub>`lot/view/Lot.fxml` &middot; 13 capture(s)</sub>

<table>
<tr>
<th width="50%">Préparer</th>
<th width="50%">Déposer</th>
</tr>
<tr>
<td><a href="apercu-lot-preparer.png"><img src="apercu-lot-preparer.png" width="430" alt="Préparer"></a></td>
<td><a href="apercu-lot-deposer.png"><img src="apercu-lot-deposer.png" width="430" alt="Déposer"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Génération</th>
<th width="50%">Archives</th>
</tr>
<tr>
<td><a href="apercu-lot-generation.png"><img src="apercu-lot-generation.png" width="430" alt="Génération"></a></td>
<td><a href="apercu-lot-archives.png"><img src="apercu-lot-archives.png" width="430" alt="Archives"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Téléverser</th>
<th width="50%">Téléverser sans archives</th>
</tr>
<tr>
<td><a href="apercu-lot-televerser.png"><img src="apercu-lot-televerser.png" width="430" alt="Téléverser"></a></td>
<td><a href="apercu-lot-televerser-sans-archives.png"><img src="apercu-lot-televerser-sans-archives.png" width="430" alt="Téléverser sans archives"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Déposé</th>
<th width="50%">Participation</th>
</tr>
<tr>
<td><a href="apercu-lot-depose.png"><img src="apercu-lot-depose.png" width="430" alt="Déposé"></a></td>
<td><a href="apercu-lot-participation.png"><img src="apercu-lot-participation.png" width="430" alt="Participation"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Alertes</th>
<th width="50%">Retour</th>
</tr>
<tr>
<td><a href="apercu-lot-alertes.png"><img src="apercu-lot-alertes.png" width="430" alt="Alertes"></a></td>
<td><a href="apercu-lot-retour.png"><img src="apercu-lot-retour.png" width="430" alt="Retour"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Reprise</th>
<th width="50%">Dépôt compte rendu</th>
</tr>
<tr>
<td><a href="apercu-lot-reprise.png"><img src="apercu-lot-reprise.png" width="430" alt="Reprise"></a></td>
<td><a href="apercu-lot-depot-compte-rendu.png"><img src="apercu-lot-depot-compte-rendu.png" width="430" alt="Dépôt compte rendu"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Dépôt interrompu</th>
</tr>
<tr>
<td><a href="apercu-lot-depot-interrompu.png"><img src="apercu-lot-depot-interrompu.png" width="430" alt="Dépôt interrompu"></a></td>
<td><a href="apercu-lot-refus-rattachement.png"><img src="apercu-lot-refus-rattachement.png" width="430" alt="Refus de dépôt : site non rattaché, le message dit par quel geste récupérer le carré"></a></td>
</tr>
<tr>
<th width="50%">Dépôt incomplet : des archives refusées</th>
</tr>
<tr>
<td><a href="apercu-lot-depot-refus-definitif.png"><img src="apercu-lot-depot-refus-definitif.png" width="430" alt="Dépôt incomplet : onze archives en ligne sur quatorze, trois refusées par Vigie-Chiro, dont deux qui redeviendront reprenables après une reconnexion"></a></td>
</tr>
</table>


---

# Écouter et valider

## Sons & validation

L'écran de revue, ses filtres, ses menus et ses états de sélection.

<sub>`audio/view/SonsValidation.fxml` &middot; 19 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Avis validateur</th>
</tr>
<tr>
<td><a href="apercu-sons-validation.png"><img src="apercu-sons-validation.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-sons-validation-avis-validateur.png"><img src="apercu-sons-validation-avis-validateur.png" width="430" alt="Avis validateur"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Validation Tadarida</th>
<th width="50%">Filtres</th>
</tr>
<tr>
<td><a href="apercu-validation-tadarida.png"><img src="apercu-validation-tadarida.png" width="430" alt="Validation Tadarida"></a></td>
<td><a href="apercu-sons-validation-filtres.png"><img src="apercu-sons-validation-filtres.png" width="430" alt="Filtres"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Puce « Lieu » fermée</th>
<th width="50%">Puce « Lieu » déroulée</th>
</tr>
<tr>
<td><a href="apercu-sons-validation-lieu.png"><img src="apercu-sons-validation-lieu.png" width="430" alt="Puce « Lieu » fermée"></a></td>
<td><a href="apercu-liste-lieu.png"><img src="apercu-liste-lieu.png" width="430" alt="Puce « Lieu » déroulée"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Colonnes</th>
<th width="50%">Substituée</th>
</tr>
<tr>
<td><a href="apercu-sons-validation-colonnes.png"><img src="apercu-sons-validation-colonnes.png" width="430" alt="Colonnes"></a></td>
<td><a href="apercu-sons-validation-substituee.png"><img src="apercu-sons-validation-substituee.png" width="430" alt="Substituée"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Lot</th>
<th width="50%">Commentaire</th>
</tr>
<tr>
<td><a href="apercu-sons-validation-lot.png"><img src="apercu-sons-validation-lot.png" width="430" alt="Lot"></a></td>
<td><a href="apercu-sons-validation-commentaire.png"><img src="apercu-sons-validation-commentaire.png" width="430" alt="Commentaire"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Espèce</th>
<th width="50%">Menu d'une ligne</th>
</tr>
<tr>
<td><a href="apercu-fiche-espece.png"><img src="apercu-fiche-espece.png" width="430" alt="Espèce"></a></td>
<td><a href="apercu-menu-ligne.png"><img src="apercu-menu-ligne.png" width="430" alt="Menu d'une ligne"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Menu actions</th>
<th width="50%">Publication des corrections, confirmation</th>
</tr>
<tr>
<td><a href="apercu-sons-validation-menu-actions.png"><img src="apercu-sons-validation-menu-actions.png" width="430" alt="Menu actions"></a></td>
<td><a href="apercu-publication-confirmation.png"><img src="apercu-publication-confirmation.png" width="430" alt="Publication des corrections, confirmation"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Publication des corrections, progression</th>
<th width="50%">Publication des corrections, compte rendu</th>
</tr>
<tr>
<td><a href="apercu-publication-progression.png"><img src="apercu-publication-progression.png" width="430" alt="Publication des corrections, progression"></a></td>
<td><a href="apercu-publication-compte-rendu.png"><img src="apercu-publication-compte-rendu.png" width="430" alt="Publication des corrections, compte rendu"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Compte rendu d'import VigieChiro</th>
<th width="50%">Export des sons, progression</th>
</tr>
<tr>
<td><a href="apercu-import-vigiechiro-compte-rendu.png"><img src="apercu-import-vigiechiro-compte-rendu.png" width="430" alt="Compte rendu d'import VigieChiro"></a></td>
<td><a href="apercu-export-sons-progression.png"><img src="apercu-export-sons-progression.png" width="430" alt="Export des sons, progression"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Menu du corpus de référence</th>
</tr>
<tr>
<td><a href="apercu-menu-references.png"><img src="apercu-menu-references.png" width="430" alt="Menu du corpus de référence"></a></td>
</tr>
</table>


---

# Lire la saison

## Carte & passages

<sub>`multisite/view/Multisite.fxml` &middot; 7 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Filtre</th>
</tr>
<tr>
<td><a href="apercu-multisite.png"><img src="apercu-multisite.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-multisite-filtre.png"><img src="apercu-multisite-filtre.png" width="430" alt="Filtre"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Année illisible : la puce est marquée, la table reste entière</th>
</tr>
<tr>
<td><a href="apercu-multisite-annee-invalide.png"><img src="apercu-multisite-annee-invalide.png" width="430" alt="Année invalide"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Valeur hors jeu : cochée, mise à part, elle ne ramène plus rien</th>
</tr>
<tr>
<td><a href="apercu-valeur-hors-jeu.png"><img src="apercu-valeur-hors-jeu.png" width="430" alt="Valeur hors jeu"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Édition</th>
<th width="50%">Carte pleine</th>
</tr>
<tr>
<td><a href="apercu-multisite-edition.png"><img src="apercu-multisite-edition.png" width="430" alt="Édition"></a></td>
<td><a href="apercu-multisite-carte-pleine.png"><img src="apercu-multisite-carte-pleine.png" width="430" alt="Carte pleine"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Tableau plein</th>
<th width="50%">Menu actions</th>
</tr>
<tr>
<td><a href="apercu-multisite-tableau-plein.png"><img src="apercu-multisite-tableau-plein.png" width="430" alt="Tableau plein"></a></td>
<td><a href="apercu-multisite-menu-actions.png"><img src="apercu-multisite-menu-actions.png" width="430" alt="Menu actions"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Menu sélection</th>
</tr>
<tr>
<td><a href="apercu-multisite-menu-selection.png"><img src="apercu-multisite-menu-selection.png" width="430" alt="Menu sélection"></a></td>
</tr>
</table>

## Modale de reconstruction

<sub>`multisite/view/ReconstructionModale.fxml` &middot; 3 capture(s)</sub>

<table>
<tr>
<th width="50%">Reconstruction</th>
<th width="50%">Reconstruction groupe</th>
</tr>
<tr>
<td><a href="apercu-multisite-reconstruction.png"><img src="apercu-multisite-reconstruction.png" width="430" alt="Reconstruction"></a></td>
<td><a href="apercu-multisite-reconstruction-groupe.png"><img src="apercu-multisite-reconstruction-groupe.png" width="430" alt="Reconstruction groupe"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Reconstruction interrompu</th>
</tr>
<tr>
<td><a href="apercu-multisite-reconstruction-interrompu.png"><img src="apercu-multisite-reconstruction-interrompu.png" width="430" alt="Reconstruction interrompu"></a></td>
</tr>
</table>

## Espèces & observations

<sub>`analyse/view/Analyse.fxml` &middot; 5 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Carré</th>
</tr>
<tr>
<td><a href="apercu-analyse.png"><img src="apercu-analyse.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-analyse-carre.png"><img src="apercu-analyse-carre.png" width="430" alt="Carré"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Carte</th>
<th width="50%">Colonnes</th>
</tr>
<tr>
<td><a href="apercu-analyse-carte.png"><img src="apercu-analyse-carte.png" width="430" alt="Carte"></a></td>
<td><a href="apercu-analyse-colonnes.png"><img src="apercu-analyse-colonnes.png" width="430" alt="Colonnes"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Lieu</th>
</tr>
<tr>
<td><a href="apercu-analyse-lieu.png"><img src="apercu-analyse-lieu.png" width="430" alt="Lieu"></a></td>
</tr>
</table>

## Activité de la nuit

<sub>`analyse/view/Activite.fxml` &middot; 6 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Vide</th>
</tr>
<tr>
<td><a href="apercu-activite.png"><img src="apercu-activite.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-activite-vide.png"><img src="apercu-activite-vide.png" width="430" alt="Vide"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Export</th>
<th width="50%">Transverse</th>
</tr>
<tr>
<td><a href="apercu-activite-export.png"><img src="apercu-activite-export.png" width="430" alt="Export"></a></td>
<td><a href="apercu-activite-transverse.png"><img src="apercu-activite-transverse.png" width="430" alt="Transverse"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Retour</th>
<th width="50%">Lieu</th>
</tr>
<tr>
<td><a href="apercu-activite-retour.png"><img src="apercu-activite-retour.png" width="430" alt="Retour"></a></td>
<td><a href="apercu-activite-lieu.png"><img src="apercu-activite-lieu.png" width="430" alt="Lieu"></a></td>
</tr>
</table>

## Synthèse

<sub>`analyse/view/Synthese.fxml` &middot; 2 capture(s)</sub>

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Sans référentiel</th>
</tr>
<tr>
<td><a href="apercu-synthese.png"><img src="apercu-synthese.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-synthese-sans-referentiel.png"><img src="apercu-synthese-sans-referentiel.png" width="430" alt="Sans référentiel"></a></td>
</tr>
</table>


---

# Composants transverses

Des éléments qui n'appartiennent à aucun écran en particulier, et qu'on ne pense donc à regarder
nulle part.

## Bandeau de retour

Le véhicule par défaut de tout compte rendu d'opération (ADR 0023). Ce qu'il faut y regarder : la sévérité (couleur et pictogramme), et **la hauteur** quand le message est long, puisqu'il pousse le contenu vers le bas.

<table>
<tr>
<th width="50%">Retour refus</th>
<th width="50%">Retour externe</th>
</tr>
<tr>
<td><a href="apercu-bandeau-retour-refus.png"><img src="apercu-bandeau-retour-refus.png" width="430" alt="Retour refus"></a></td>
<td><a href="apercu-bandeau-retour-externe.png"><img src="apercu-bandeau-retour-externe.png" width="430" alt="Retour externe"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Retour borne</th>
<th width="50%">Vue amputée</th>
</tr>
<tr>
<td><a href="apercu-bandeau-retour-borne.png"><img src="apercu-bandeau-retour-borne.png" width="430" alt="Retour borne"></a></td>
<td><a href="apercu-bandeau-vue-amputee.png"><img src="apercu-bandeau-vue-amputee.png" width="430" alt="Vue amputée"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Vue amputée longue</th>
<th width="50%">Vue amputée, deux causes</th>
</tr>
<tr>
<td><a href="apercu-bandeau-vue-amputee-longue.png"><img src="apercu-bandeau-vue-amputee-longue.png" width="430" alt="Vue amputée longue"></a></td>
<td><a href="apercu-bandeau-vue-amputee-deux-causes.png"><img src="apercu-bandeau-vue-amputee-deux-causes.png" width="430" alt="Vue amputée, deux causes"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Filtres non repris (transport)</th>
</tr>
<tr>
<td><a href="apercu-bandeau-filtres-non-repris.png"><img src="apercu-bandeau-filtres-non-repris.png" width="430" alt="Filtres non repris"></a></td>
</tr>
</table>

## Compte rendu d'opération

<table>
<tr>
<th width="50%">État nominal</th>
<th width="50%">Sans rejet</th>
</tr>
<tr>
<td><a href="apercu-compte-rendu.png"><img src="apercu-compte-rendu.png" width="430" alt="État nominal"></a></td>
<td><a href="apercu-compte-rendu-sans-rejet.png"><img src="apercu-compte-rendu-sans-rejet.png" width="430" alt="Sans rejet"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Motifs</th>
<th width="50%">Échec</th>
</tr>
<tr>
<td><a href="apercu-compte-rendu-motifs.png"><img src="apercu-compte-rendu-motifs.png" width="430" alt="Motifs"></a></td>
<td><a href="apercu-compte-rendu-echec.png"><img src="apercu-compte-rendu-echec.png" width="430" alt="Échec"></a></td>
</tr>
</table>

<table>
<tr>
<th width="50%">Participation créée, et ce qu'il reste à y faire</th>
<th width="50%">Plusieurs nuits, plusieurs participations</th>
</tr>
<tr>
<td><a href="apercu-import-participation.png"><img src="apercu-import-participation.png" width="430" alt="Une participation créée sur Vigie-Chiro, avec le rappel de compléter la fiche sur le portail"></a></td>
<td><a href="apercu-import-participations-multi-nuits.png"><img src="apercu-import-participations-multi-nuits.png" width="430" alt="Trois participations créées, la forme plurielle du même rappel"></a></td>
</tr>
</table>

## Relevé multi-sites

<table>
<tr>
<th width="50%">Relevé complet</th>
<th width="50%">Relevé partiel</th>
</tr>
<tr>
<td><a href="apercu-multisite-releve-complet.png"><img src="apercu-multisite-releve-complet.png" width="430" alt="Relevé complet"></a></td>
<td><a href="apercu-multisite-releve-partiel.png"><img src="apercu-multisite-releve-partiel.png" width="430" alt="Relevé partiel"></a></td>
</tr>
</table>


---

# Autres ressources de ce dossier

Hors galerie UI :

- `logo.png` : logo du projet ;
- `create_codespace_on_main.png`, `codespace_vscode.png`, `codespace_vscode_nouveau_terminal.png` :
  parcours d'ouverture d'un Codespace, référencés par la documentation d'installation.
