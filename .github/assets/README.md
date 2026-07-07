# Galerie des captures d'écran (références UI)

Captures de **référence** des écrans de VigieChiro PR Companion, à comparer aux
maquettes du brief SAÉ 2.01. Chaque image est rendue **hors-écran** et de façon
**déterministe** via la *Headless Platform* de JavaFX 26 (`glass.platform=Headless`,
rendu logiciel) : aucune fenêtre ni serveur d'affichage requis (utilisable en CI,
conteneur ou session Wayland).

- **Régénérer** : `./.github/assets/capture-screenshots.sh` (lancer avec un **JDK 25
  standard** ; le rendu est déterministe, deux exécutions produisent des PNG
  identiques au bit près).
- **Complétude garantie** : [`captures.manifest`](captures.manifest) liste chaque vue
  `**/view/*.fxml` avec au moins une capture, et [`check-captures.sh`](check-captures.sh)
  vérifie en CI qu'aucune vue n'est livrée sans capture de référence.
- **Outil enseignant** (présent sur la branche `solution`). Les écrans sont seedés
  puis pilotés par les classes `fr.univ_amu.iut.<feature>.outils.Capture*`.

> Chaque vue est présentée avec ses **états pertinents côte à côte** (vide/peuplé,
> initial/avancé, verrouillé/déverrouillé...). Cliquer sur une vignette ouvre le PNG
> en pleine résolution.

---

## Accueil

Bandeau applicatif et cartes d'activités donnant accès aux features.

<a href="apercu-accueil.png"><img src="apercu-accueil.png" width="640" alt="Écran d'accueil : 4 cartes d'activités"></a>

`commun/view/MainView.fxml`

---

## Mes sites

### Liste des sites (`sites/view/MesSites.fxml`)

<table>
<tr>
<th>Peuplée : cartes des sites</th>
<th>État initial : onboarding « premier site »</th>
</tr>
<tr>
<td><a href="apercu-sites-mes-sites.png"><img src="apercu-sites-mes-sites.png" width="440" alt="M-Sites peuplée"></a></td>
<td><a href="apercu-sites-mes-sites-vide.png"><img src="apercu-sites-mes-sites-vide.png" width="440" alt="M-Sites vide"></a></td>
</tr>
</table>

### Détail d'un site (`sites/view/SiteDetail.fxml`)

<table>
<tr>
<th>Avec passages : fiche + points + tableau</th>
<th>Sans passage : tableau vide</th>
</tr>
<tr>
<td><a href="apercu-sites-detail.png"><img src="apercu-sites-detail.png" width="440" alt="M-Site-détail avec passages"></a></td>
<td><a href="apercu-sites-detail-sans-passage.png"><img src="apercu-sites-detail-sans-passage.png" width="440" alt="M-Site-détail sans passage"></a></td>
</tr>
</table>

### Modale point d'écoute (`sites/view/ModalePoint.fxml`)

<table>
<tr>
<th>Édition : champs pré-remplis</th>
<th>Création : formulaire vierge</th>
</tr>
<tr>
<td><a href="apercu-sites-modale-point.png"><img src="apercu-sites-modale-point.png" width="380" alt="Modale point, édition"></a></td>
<td><a href="apercu-sites-modale-point-creation.png"><img src="apercu-sites-modale-point-creation.png" width="380" alt="Modale point, création"></a></td>
</tr>
</table>

---

## Importer une nuit

Assistant d'import d'une nuit de Passive Recorder (`importation/view/Importation.fxml`).

<table>
<tr>
<th>Assistant : cas standard</th>
<th>Import en cours : progression déterministe, formulaire gelé</th>
</tr>
<tr>
<td><a href="apercu-import-assistant.png"><img src="apercu-import-assistant.png" width="440" alt="M-Import assistant"></a></td>
<td><a href="apercu-import-en-cours.png"><img src="apercu-import-en-cours.png" width="440" alt="M-Import en cours"></a></td>
</tr>
<tr>
<th>Cas « mélange » : 2 enregistreurs, avertissement non bloquant</th>
<th>Cas « incohérence » : journal/relevé en désaccord avec les WAV</th>
</tr>
<tr>
<td><a href="apercu-import-melange.png"><img src="apercu-import-melange.png" width="440" alt="M-Import mélange"></a></td>
<td><a href="apercu-import-incoherence.png"><img src="apercu-import-incoherence.png" width="440" alt="M-Import incohérence"></a></td>
</tr>
<tr>
<th>Source « .zip » : décompression avec progression</th>
<th>Rapport d'import : fichiers rejetés listés (import résilient)</th>
</tr>
<tr>
<td><a href="apercu-import-decompression.png"><img src="apercu-import-decompression.png" width="440" alt="M-Import décompression .zip"></a></td>
<td><a href="apercu-import-rejets.png"><img src="apercu-import-rejets.png" width="440" alt="M-Import rapport de rejets"></a></td>
</tr>
</table>

---

## Qualification

Sélection et écoute des séquences, pose du verdict (`qualification/view/Qualification.fxml`).

<table>
<tr>
<th>État initial : sélection générée, rien d'écouté, sans verdict</th>
<th>Avancé : séquences écoutées, verdict OK posé</th>
</tr>
<tr>
<td><a href="apercu-qualification-initial.png"><img src="apercu-qualification-initial.png" width="440" alt="M-Qualification initial"></a></td>
<td><a href="apercu-qualification.png"><img src="apercu-qualification.png" width="440" alt="M-Qualification avancé"></a></td>
</tr>
</table>

---

## Passage

### Pivot du workflow (`passage/view/Passage.fxml`)

<table>
<tr>
<th>Statut Vérifié : préparer le dépôt actif, validation verrouillée</th>
<th>Statut Déposé : dépôt fait, validation déverrouillée</th>
</tr>
<tr>
<td><a href="apercu-passage.png"><img src="apercu-passage.png" width="440" alt="M-Passage vérifié"></a></td>
<td><a href="apercu-passage-depose.png"><img src="apercu-passage-depose.png" width="440" alt="M-Passage déposé"></a></td>
</tr>
</table>

### Modale rattachement (`passage/view/RattachementModale.fxml`)

Modifier le rattachement d'un passage : année + n° de passage.

<a href="apercu-passage-rattachement.png"><img src="apercu-passage-rattachement.png" width="360" alt="Modale modifier le rattachement"></a>

---

## Diagnostic

Conditions de la nuit et anomalies (`diagnostic/view/Diagnostic.fxml`).

<table>
<tr>
<th>Relevé présent : courbe climat + anomalies + GPS</th>
<th>Relevé absent : absence signalée, anomalies seules</th>
</tr>
<tr>
<td><a href="apercu-diagnostic.png"><img src="apercu-diagnostic.png" width="440" alt="M-Diagnostic avec relevé"></a></td>
<td><a href="apercu-diagnostic-sans-releve.png"><img src="apercu-diagnostic-sans-releve.png" width="440" alt="M-Diagnostic sans relevé"></a></td>
</tr>
</table>

---

## Sons & validation

Vue audio unifiée (`audio/view/SonsValidation.fxml`) : écoute, validation / correction et corpus de
sons de référence, alimentée par le passage, l'analyse, le multisite et l'accueil.

<table>
<tr>
<th>Sur le corpus de référence : table, écoute pleine largeur, actions</th>
</tr>
<tr>
<td><a href="apercu-sons-validation.png"><img src="apercu-sons-validation.png" width="560" alt="Sons & validation"></a></td>
</tr>
</table>

---

## Lot

Préparation et dépôt du lot vers la plateforme (`lot/view/Lot.fxml`).

Workflow du dépôt étape par étape : ① préparer · ② générer les archives · ③ téléverser · ④ marquer
déposé (+ cas bloquant).

<table>
<tr>
<th>① Vérifié : « Vérifier et préparer le lot »</th>
<th>② Prêt à déposer : « Générer les archives » actif</th>
<th>② Génération en cours : indicateur, actions gelées</th>
</tr>
<tr>
<td><a href="apercu-lot-preparer.png"><img src="apercu-lot-preparer.png" width="300" alt="M-Lot préparer"></a></td>
<td><a href="apercu-lot-deposer.png"><img src="apercu-lot-deposer.png" width="300" alt="M-Lot déposer"></a></td>
<td><a href="apercu-lot-generation.png"><img src="apercu-lot-generation.png" width="300" alt="M-Lot génération en cours"></a></td>
</tr>
<tr>
<th>③ Archives générées : liste ZIP, « Ouvrir le dossier »</th>
<th>④ Déposé : toutes les étapes franchies</th>
<th>Vérifié incohérent : alertes de cohérence R14</th>
</tr>
<tr>
<td><a href="apercu-lot-archives.png"><img src="apercu-lot-archives.png" width="300" alt="M-Lot archives générées"></a></td>
<td><a href="apercu-lot-depose.png"><img src="apercu-lot-depose.png" width="300" alt="M-Lot déposé"></a></td>
<td><a href="apercu-lot-alertes.png"><img src="apercu-lot-alertes.png" width="300" alt="M-Lot alertes"></a></td>
</tr>
</table>

---

## Vue multi-sites

### Tableau agrégé (`multisite/view/Multisite.fxml`)

<table>
<tr>
<th>Vue agrégée : tableau complet, filtres, tri, export</th>
<th>Filtré par verdict OK : résumé recalculé</th>
</tr>
<tr>
<td><a href="apercu-multisite.png"><img src="apercu-multisite.png" width="440" alt="M-Multisite agrégé"></a></td>
<td><a href="apercu-multisite-filtre.png"><img src="apercu-multisite-filtre.png" width="440" alt="M-Multisite filtré"></a></td>
</tr>
</table>

---

## Espèces & observations

### Inventaire (`analyse/view/Analyse.fxml`)

Inventaire transverse des espèces détectées (prisme biodiversité), regroupable **par espèce** ou
**par carré** (richesse spécifique) et filtrable par statut.

<table>
<tr>
<th>Par espèce : détections, passages, carrés, période</th>
<th>Par carré : richesse spécifique</th>
</tr>
<tr>
<td><a href="apercu-analyse.png"><img src="apercu-analyse.png" width="440" alt="Analyse par espèce"></a></td>
<td><a href="apercu-analyse-carre.png"><img src="apercu-analyse-carre.png" width="440" alt="Analyse par carré"></a></td>
</tr>
</table>

### Carte de répartition

Bascule cartographique : richesse par carré sur la carte (espèce sélectionnée surlignée).

<a href="apercu-analyse-carte.png"><img src="apercu-analyse-carte.png" width="560" alt="Carte de répartition"></a>

---

## Recherche globale

Recherche transverse (sites, points, passages) accessible au clavier (Ctrl+F).

<a href="apercu-recherche.png"><img src="apercu-recherche.png" width="560" alt="Recherche globale"></a>

---

## Autres ressources de ce dossier

Ce dossier contient aussi des illustrations hors galerie UI :

- `logo.png` : logo du projet.
- `create_codespace_on_main.png`, `codespace_vscode.png`,
  `codespace_vscode_nouveau_terminal.png` : captures du parcours d'ouverture d'un
  Codespace (référencées par la documentation d'installation).
