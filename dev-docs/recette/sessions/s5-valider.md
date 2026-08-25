# S5 · Valider (Sons & validation, fiche espèce)

!!! warning "Session partielle"
    Seul le parcours « **envoyer un sous-ensemble à un expert** » (EPIC #2790 : filtre espèce × lieu,
    export ZIP « observations + sons », parité CLI) y est déroulé pour l'instant. Le cœur de la revue
    (écoute, correction, certitude, références, publication) reste à écrire.

## Objectif

Vérifier ce qu'aucun test ne peut affirmer sur l'export d'archive : la **durée réelle** sur une
saison entière (et ce que la fenêtre de progression fait vivre pendant ce temps), la **manipulation
de l'archive hors application** (elle est faite pour être envoyée), et la lisibilité du compte rendu
quand une partie des sons a quitté le disque.

## Environnement

Une **vraie saison multi-nuits** dont une partie de l'audio n'est **plus sur le disque** (nuit
archivée ou support débranché) : c'est le cas réel du destinataire, et le seul qui exerce la
politique « introuvable compté, jamais bloquant ». Le jeu de recette de référence : 2 passages,
9 025 séquences, 24 542 observations, dont 1 336 grands Rhinolophes répartis sur les deux nuits -
l'audio d'une seule des deux étant présent.

## Mesures observées (2026-07-30, jeu de recette ci-dessus)

Consignées lors de la clôture du lot E (#2796), CLI sur machine de développement (SSD) :

| Fait | Observé |
|---|---|
| `exporter-sons --espece Rhifer` | exit 0, **4 min 40 s** |
| Contenu annoncé et livré | 1 336 observation(s), **721 son(s) copiés**, 657,9 Mo |
| Sons introuvables (nuit sans audio) | **615**, comptés dans le compte rendu, export jamais bloqué |
| Archive relue | 722 entrées (`observations.csv` + 721 WAV), une seule session en `sons/` |
| CSV de l'archive | 1 337 lignes (en-tête + 1 336), colonne « Commune » présente |

## Le script (une case = un fait observable)

**Étape 1 · Isoler le sous-ensemble (vue audio)**

- **S5-01** · *geste: filtrer-les-observations-par-lieu* · Depuis « Espèces & observations », un clic sur l'espèce ouvre la vue audio sur elle (toutes nuits).
- **S5-02** · *geste: filtrer-les-observations-par-lieu* · La puce « Lieu » liste communes, carrés et points réellement présents, dans cet ordre ; un carré
  nommé paraît sous ses deux étiquettes, « 640380 · Vallon », et un carré sans nom sous son numéro
  seul.
- **S5-03** · *geste: filtrer-les-observations-par-lieu* · Cocher une commune restreint la table ; la barre de statut suit le sous-ensemble affiché.

**Étape 2 · Exporter (menu principal (☰))**

- **S5-04** · *geste: exporter-observations-et-sons* · L'item « Exporter les observations et les sons (ZIP)… » est actif dès qu'une observation est
  affichée, et grisé avec explication quand la table est vide.
- **S5-05** · *geste: exporter-observations-et-sons* · La première ligne de la fenêtre de progression annonce le contenu (« N observation(s) ·
  M son(s) · ~X Mo ») **avant** toute copie.
- **S5-06** · *geste: exporter-observations-et-sons* · La barre avance fichier par fichier, le nom du son en cours se lit.
- **S5-07** · *hors-portée: la durée réelle d'un export sur une saison entière - « plusieurs minutes » - que l'objectif de la session nomme lui-même comme ce qu'aucun test ne peut affirmer. Un export de fixture dure une seconde, et une fenêtre vivante pendant une seconde ne prouve rien* · Pendant l'export (plusieurs minutes sur une saison), la fenêtre reste vivante et « Annuler »
  reste cliquable.
- **S5-08** · *hors-portée: un fait de DISQUE que l'écran ne montre pas : ce que le cas observe est le contenu de la destination, pas ce que l'application affiche* · Annuler en cours de copie : aucun fichier partiel ne subsiste à la destination.
- **S5-09** · *geste: exporter-observations-et-sons* · Relancer et laisser finir : le compte rendu chiffre observations, sons copiés, sons introuvables
  et taille de l'archive.

**Étape 3 · Ouvrir l'archive hors application**

- **S5-10** · *hors-portée: un outil du système - archiveur, tableur, lecteur audio - hors du banc* · L'archive s'ouvre avec l'outil d'archive du système (double-clic), sans avertissement.
- **S5-11** · *hors-portée: un outil du système - archiveur, tableur, lecteur audio - hors du banc* · `observations.csv` s'ouvre dans un tableur, accents corrects, colonne « Commune » remplie là où
  le lot 0 a résolu.
- **S5-12** · *hors-portée: un outil du système - archiveur, tableur, lecteur audio - hors du banc* · Les sons sont rangés par dossier de nuit ; un WAV pris au hasard s'écoute avec le lecteur du
  système et correspond à sa ligne CSV (nom de fichier).
- **S5-13** · *hors-portée: un fait de DISQUE que l'écran ne montre pas : ce que le cas observe est le contenu de la destination, pas ce que l'application affiche* · Les sons introuvables annoncés sont bien **absents** de `sons/` mais **présents** dans le CSV.

**Étape 4 · Parité CLI**

- **S5-14** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `vigiechiro exporter-sons --espece <code> --sortie <zip>` produit une archive de même structure
  (mêmes dossiers, même CSV) que le geste IHM sur le même sous-ensemble.
- **S5-15** · *hors-portée: une réponse en texte dans un terminal : le banc filme une scène JavaFX, pas un shell* · `--passage` + `--espece` simultanés : refus expliqué, code 2, rien d'écrit.
