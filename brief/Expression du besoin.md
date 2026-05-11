# Expression du besoin

## Contexte

Dans le cadre d'une convention informelle entre l'IUT d'Aix-Marseille et le réseau VigieChiro, le département Informatique sollicite ses étudiants de BUT1 pour réaliser une **preuve de concept** d'un compagnon logiciel destiné aux possesseurs de Passive Recorder. Aujourd'hui, ces utilisateurs jonglent entre l'explorateur de fichiers, un tableur, un lecteur audio et la plateforme web VigieChiro pour gérer leurs campagnes - et passent un temps important en manipulations répétitives à chaque nuit traitée.

Aucun outil unifié n'existe à ce jour. Plusieurs scripts ad hoc circulent dans la communauté (Python, R), mais aucune application avec interface graphique soignée n'est disponible. C'est cette lacune que la SAE 2.01 cherche à combler - en livrant un prototype crédible que la communauté pourrait reprendre et faire évoluer après le projet.

## Données fournies

Un jeu de données réel issu d'une session de capture nocturne (PR n° 1925492, nuit du 22 au 23 avril 2026, point fixe en zone Z1 de la carrée 640380 du protocole Vigie-Chiro Carré) est mis à votre disposition comme support de développement et de test tout au long du projet. Téléchargez l'archive `20260423-selected.zip` depuis la dernière [Release GitHub](../../releases) du dépôt (ou depuis AmeTICE), puis décompressez-la dans un dossier `data/` à la racine de votre clone (cf. note d'installation du [README](README.md#données-dexemple-fournies)).

| Fichier / dossier | Contenu |
|---|---|
| [`LogPR1925492.txt`](data/LogPR1925492.txt) | Log technique du PR : démarrage, paramètres d'acquisition (`Acquisi. 20:25-07:47, Fe384kHz, FL N, FPH 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%`), tensions batterie, mises en veille, alarmes wakeup. Une ligne par évènement, format `JJ/MM/AA - HH:MM:SS PR<sn> <message>`. |
| [`PaRecPR1925492_THLog.csv`](data/PaRecPR1925492_THLog.csv) | Log température / hygrométrie produit par la sonde embarquée. Une mesure toutes les 600 s, colonnes `Date;Hour;Temperature;Humidity`. |
| [`wav/`](data/wav/) | 1572 fichiers WAV bruts, format `Car<carre>-<annee>-<passage>-<zone>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` (ex. `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`). Mono 16 bits 384 kHz. Durée variable (2 à 30 s, déclenchement sur seuil). |
| [`kal/`](data/kal/) | 2114 fichiers : les WAV redécoupés par Tadarida (suffixe `_000`, `_001`...) **plus** deux CSV d'observations. |
| [`kal/8a4fa…-observations.csv`](data/kal/) | CSV des observations brutes Tadarida. Colonnes : `nom du fichier;temps_debut;temps_fin;frequence_mediane;tadarida_taxon;tadarida_probabilite;tadarida_taxon_autre;observateur_taxon;observateur_probabilite;validateur_taxon;validateur_probabilite`. ~4031 lignes. Champs `observateur_*` et `validateur_*` vides au départ - c'est ce que votre application va aider à remplir. |
| [`kal/…-observations_Vu.csv`](data/kal/) | Même format que ci-dessus, mais avec encodage CSV légèrement différent (séparateur `;` sans guillemets, doubles guillemets vides `""""` pour les champs nuls). Format réinjectable par la plateforme VigieChiro. |

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

Le présent document est l'**expression brute du besoin**, telle qu'elle pourrait être recueillie auprès du client. Il a été traduit par l'équipe pédagogique en un **dossier d'analyse et de conception opérationnel** que vous trouverez dans [`Analyse et conception/`](Analyse%20et%20conception/) :

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
