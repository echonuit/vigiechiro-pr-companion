# Expression du besoin

## Contexte

Le département Informatique de l'IUT d'Aix-Marseille sollicite ses étudiants de BUT1 pour réaliser, en partenariat avec un chercheur du CEREMA, une **preuve de concept** d'un compagnon logiciel destiné aux possesseurs de Passive Recorder. Aujourd'hui, ces utilisateurs jonglent entre l'explorateur de fichiers, un tableur, un lecteur audio et la plateforme web VigieChiro pour gérer leurs campagnes - et passent un temps important en manipulations répétitives à chaque nuit traitée.

Plusieurs scripts ad hoc circulent dans la communauté (Python, R), et il existe des outils commerciaux comme [Kaleidoscope Pro](https://www.wildlifeacoustics.com/products/kaleidoscope-pro), mais aucune application gratuite et open-source dédiée au pipeline VigieChiro / PR Teensy n'est disponible. C'est cette lacune que la SAE 2.01 cherche à combler - en livrant un prototype crédible que la communauté pourrait reprendre et faire évoluer après le projet.

## Données fournies

Un jeu de données réel issu d'une session de capture nocturne (PR n° 1925492, nuit du 22 au 23 avril 2026, point fixe en zone Z1 de la carrée 640380 du protocole Vigie-Chiro Carré) est mis à votre disposition comme support de développement et de test tout au long du projet. Il existe en deux variantes :

- **[`samples/`](https://github.com/IUTInfoAix-S201/brief/tree/main/samples)** (518 Mo, versionné dans le dépôt) : sous-ensemble représentatif (191 WAV, 473 observations sur tous les taxa principaux). Disponible immédiatement après `git clone`.
- **`data/`** (~10 Go zippés / ~11 Go décompressés, à télécharger - gitignored) : full dataset (1572 WAV bruts + 2114 WAV redécoupés + 4031 observations). Nécessaire pour valider la volumétrie. Cf. l'[accueil](index.md#donnees-dexemple-fournies).

Le tableau ci-dessous décrit la structure du **full dataset** ; le sample suit la même structure (sans `wav/` et avec les CSV filtrés).

| Fichier / dossier | Contenu |
|---|---|
| `LogPR1925492.txt` | Log technique du PR : démarrage, paramètres d'acquisition (`Acquisi. 20:25-07:47, Fe384kHz, FL N, FPH 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%`), tensions batterie, mises en veille, alarmes wakeup. Une ligne par évènement, format `JJ/MM/AA - HH:MM:SS PR<sn> <message>`. |
| `PaRecPR1925492_THLog.csv` | Log température / hygrométrie produit par la sonde embarquée. Une mesure toutes les 600 s, colonnes `Date;Hour;Temperature;Humidity`. |
| `wav/` | 1572 fichiers WAV bruts, format `Car<carre>-<annee>-<passage>-<zone>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` (ex. `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`). Mono 16 bits 384 kHz. Durée variable (2 à 30 s, déclenchement sur seuil). |
| `kal/` | 2114 fichiers : les WAV redécoupés par Tadarida (suffixe `_000`, `_001`...) **plus** deux CSV d'observations. |
| `kal/8a4fa…-observations.csv` | CSV des observations brutes Tadarida. Colonnes : `nom du fichier;temps_debut;temps_fin;frequence_mediane;tadarida_taxon;tadarida_probabilite;tadarida_taxon_autre;observateur_taxon;observateur_probabilite;validateur_taxon;validateur_probabilite`. ~4031 lignes. Champs `observateur_*` et `validateur_*` vides au départ - c'est ce que votre application va aider à remplir. |
| `kal/…-observations_Vu.csv` | Même format que ci-dessus, mais avec encodage CSV légèrement différent (séparateur `;` sans guillemets, doubles guillemets vides `""""` pour les champs nuls). Format réinjectable par la plateforme VigieChiro. |

Distribution des classifications Tadarida sur cette session, à titre indicatif :

| Taxon | Effectif | % | Nature |
|---|--:|--:|---|
| `noise` | 2102 | 52 % | Bruit ambiant (vent, mécanique, éclats) |
| `piaf` | 649 | 16 % | Oiseau (toutes espèces confondues) |
| `Pippip` | 638 | 16 % | *Pipistrellus pipistrellus* (Pipistrelle commune) |
| `Nyclei` | 139 | 3 % | *Nyctalus leisleri* (Noctule de Leisler) |
| `Tadten` | 89 | 2 % | *Tadarida teniotis* (Molosse de Cestoni) |
| `Rhihip` | 80 | 2 % | *Rhinolophus hipposideros* (Petit rhinolophe) |
| `Tetvir` | 67 | 2 % | *Tettigonia viridissima* (Sauterelle, oui ça siffle aussi en ultrason) |
| autres | ~268 | 7 % | 20+ taxons rares, dont *Phanan*, *Rhifer*, *Phogri*, *Pipkuh*, *Leppun*, *Myomys*, *Eptser*, etc. |

> 📚 La nomenclature des taxons utilisée par Tadarida est un code à 6 lettres (3 + 3) : trois premières lettres du genre + trois premières lettres de l'espèce. La correspondance complète est documentée par le MNHN.

## Fonctionnalités attendues

L'application **doit** offrir les fonctionnalités suivantes (priorité MUST). Les fonctionnalités SHOULD et COULD sont soumises à arbitrage en phase 1.

### MUST (périmètre minimum viable)

- **Importer une session** depuis un dossier sur le disque : détection automatique du `LogPR*.txt`, du `PaRec*_THLog.csv` et du dossier `wav/`, métadonnées extraites (numéro de PR, date de la nuit, paramètres d'acquisition) et stockage en base.
- **Afficher la liste des sessions** importées avec leurs caractéristiques principales (date, durée, nombre de WAV, statut).
- **Charger un CSV d'observations Tadarida** et l'associer à une session existante.
- **Parcourir les observations** dans une vue tabulaire avec tri et filtrage par taxon, par probabilité, par fichier.
- **Écouter un évènement sonore** : sélectionner une observation, lecture du WAV correspondant en mode ralenti pour rendre l'ultrason audible (vitesse paramétrable).
- **Valider ou corriger** la classification d'une observation : remplir les champs `observateur_taxon` et `observateur_probabilite`.
- **Exporter** un CSV au format `observations_Vu.csv` réinjectable par VigieChiro.

### SHOULD (utilité reconnue, à arbitrer)

- **Visualiser** les courbes de température et d'hygrométrie de la nuit.
- **Afficher** un spectrogramme statique de l'évènement sonore en cours d'écoute.
- **Filtrer** les observations par plage horaire (utile pour ne traiter que la première moitié de nuit, par exemple).
- **Annoter** une session avec un commentaire libre (contexte météo, intervention humaine, problème matériel...).

### COULD (idées d'extension)

- **Spectrogramme interactif** : zoom, sélection d'une région, lecture de la sélection seule.
- **Statistiques globales** sur l'ensemble des sessions d'un utilisateur (nombre d'espèces détectées, courbe d'activité par heure, comparaison entre points fixes).
- **Comparateur** : lecture en parallèle de l'évènement courant et d'un évènement de référence du même taxon, pour faciliter la confirmation visuelle/auditive.

### WON'T (hors périmètre de cette première version)

- **Pas** de communication directe avec la plateforme VigieChiro (les échanges se font par téléversement / téléchargement de fichiers).
- **Pas** de gestion multi-utilisateur, pas de comptes, pas de synchronisation cloud.
- **Pas** de calcul propre de classification automatique (Tadarida fait son travail en amont, l'application l'exploite).
- **Pas** de déploiement web ou mobile.

## Et après ?

Le présent document est l'**expression brute du besoin**, telle qu'elle pourrait être recueillie auprès du client. Il a été traduit par l'équipe pédagogique en un **dossier d'analyse et de conception opérationnel** que vous trouverez dans [`Analyse et conception/`](Analyse%20et%20conception/README.md) :

- 3 [personas](Analyse%20et%20conception/Personas.md) représentatifs des utilisateurs cibles
- les [parcours utilisateurs](Analyse%20et%20conception/Parcours%20utilisateurs.md) attendus
- une [cartographie des histoires utilisateurs](Analyse%20et%20conception/Story%20mapping.md) (épopées + stories INVEST + critères d'acceptation + estimations en story points)
- le [périmètre fonctionnel MVP](Analyse%20et%20conception/Périmètre%20MVP.md) arbitré
- la [planification prospective](Analyse%20et%20conception/Planification.md) sous forme de Gantt

C'est ce dossier qui pilote votre développement. Le présent document reste utile pour comprendre **d'où viennent** les choix qui y sont actés.

Quelques points d'inspiration ergonomique à explorer si besoin :

- Les outils d'analyse audio scientifique : [Audacity](https://www.audacityteam.org/), [Raven Lite](https://www.ravensoundsoftware.com/software/raven-lite/), [Kaleidoscope Pro](https://www.wildlifeacoustics.com/products/kaleidoscope-pro).
- L'interface web actuelle de VigieChiro (à découvrir si vous avez un compte test).
- Les conventions ergonomiques d'un client mail ou d'un explorateur de photos (liste à gauche, détail à droite, navigation clavier).
