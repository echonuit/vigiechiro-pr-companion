# Expression du besoin

## Contexte

*VigieChiro Companion* est un **compagnon logiciel** destiné aux possesseurs de Passive Recorder, né d'un besoin exprimé par un chercheur du CEREMA. Aujourd'hui, ces utilisateurs jonglent entre l'explorateur de fichiers, un tableur, un lecteur audio et la plateforme web VigieChiro pour gérer leurs campagnes - et passent un temps important en manipulations répétitives à chaque nuit traitée.

Plusieurs scripts ad hoc circulent dans la communauté (Python, R), et il existe des outils commerciaux comme [Kaleidoscope Pro](https://www.wildlifeacoustics.com/products/kaleidoscope-pro), mais aucune application gratuite et open-source dédiée au pipeline VigieChiro / PR Teensy n'est disponible. C'est cette lacune que *VigieChiro Companion* comble - un logiciel libre que la communauté peut reprendre et faire évoluer.

## Données fournies

Un jeu de données réel issu d'une session d'enregistrement nocturne (PR n° 1925492, nuit du 22 au 23 avril 2026, point fixe en zone Z1 de la carrée 640380 du protocole Vigie-Chiro Carré) est fourni comme jeu de test de référence. Il existe en deux variantes :

- **Échantillon** versionné dans le dépôt [`vigiechiro-pr-companion-exemple-nuit`](https://github.com/echonuit/vigiechiro-pr-companion-exemple-nuit) : sous-ensemble représentatif (audio réduit, observations complètes sur tous les taxa principaux). Disponible immédiatement après `git clone` de ce dépôt.
- **`data/`** (~4,2 Go zippés / ~11 Go décompressés, à télécharger depuis Zenodo, gitignored) : full dataset (1572 WAV bruts + 2109 WAV redécoupés + 4031 observations). Nécessaire pour valider la volumétrie. Cf. l'[accueil](index.md#donnees-dexemple-fournies).

Le tableau ci-dessous décrit la structure du **full dataset**, conforme à [R22](Analyse%20et%20conception/Modèle%20conceptuel/Règles%20métier.md#r22) ; l'échantillon suit la même arborescence avec un sous-ensemble des fichiers audio.

| Fichier / dossier | Contenu |
|---|---|
| `LogPR1925492.txt` | Log technique du PR : démarrage, paramètres d'acquisition (`Acquisi. 20:25-07:47, Fe384kHz, FL N, FPH 00, S. R. 16dB 1dt. GN0, Bd. Freq. 8-120kHz, Wav 2-30s SD 99%`), tensions batterie, mises en veille, alarmes wakeup. Une ligne par évènement, format `JJ/MM/AA - HH:MM:SS PR<sn> <message>`. **Note** : la place sur le PR étant limitée, le log écrase de l'information au fur et à mesure quand la SD sature - l'ordre exact d'éviction reste à confirmer auprès du concepteur du firmware. |
| `PaRecPR1925492_THLog.csv` | Log température / hygrométrie produit par la sonde embarquée. Une mesure toutes les 600 s, colonnes `Date;Hour;Temperature;Humidity`. |
| `bruts/` | 1572 fichiers WAV originaux ([C7](Analyse%20et%20conception/Modèle%20conceptuel/C7%20-%20Enregistrement%20original.md)), format `Car<carre>-<annee>-<passage>-<zone>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` (ex. `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`). Mono 16 bits 384 kHz. Durée variable (2 à 30 s, déclenchement sur seuil). |
| `transformes/` | 2109 séquences d'écoute ([C8](Analyse%20et%20conception/Modèle%20conceptuel/C8%20-%20Séquence%20d%27écoute.md)) : les WAV bruts **renommés, découpés en séquences de 5 s et ralentis ×10** (suffixe `_000`, `_001`...) + 2 CSV d'observations Tadarida ([C12](Analyse%20et%20conception/Modèle%20conceptuel/C12%20-%20Résultats%20d%27identification.md), cf. [R23](Analyse%20et%20conception/Modèle%20conceptuel/Règles%20métier.md#r23)). C'est cette version, prête au dépôt sur Vigie-Chiro, que Tadarida analyse. |
| `transformes/8a4fa…-observations.csv` | CSV des observations brutes Tadarida. Colonnes : `nom du fichier;temps_debut;temps_fin;frequence_mediane;tadarida_taxon;tadarida_probabilite;tadarida_taxon_autre;observateur_taxon;observateur_probabilite;validateur_taxon;validateur_probabilite`. ~4031 lignes. Champs `observateur_*` et `validateur_*` vides au départ. Les champs `observateur_*` sont ceux que l'application aide à remplir. Les champs `validateur_*`, eux, sont **réservés à un expert du MNHN** : la plateforme refuse (403) qu'un compte `Observateur` les pose. L'application les **lit** - elle affiche le verdict de l'expert à côté de la correction de l'observateur - mais ne les écrit jamais. Sur une séquence de 5 s, **plusieurs lignes peuvent coexister** (1 ligne par espèce distincte identifiée), avec timing début/fin précis dans la séquence. |
| `transformes/…-observations_Vu.csv` | Même format que ci-dessus, mais avec encodage CSV légèrement différent (séparateur `;` sans guillemets, doubles guillemets vides `""""` pour les champs nuls). Format réinjectable par la plateforme VigieChiro. |

Distribution des classifications Tadarida sur cette session, à titre indicatif. Cette distribution donne **l'identification la plus probable** d'un des sons ou d'une des séquences détectées dans la séquence ralentie de 5 s, mais **des erreurs sont possibles dans tous les sens** (faux positifs, faux négatifs) et plusieurs espèces ou natures de sons peuvent cohabiter dans une même séquence :

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

L'application **doit** offrir les fonctionnalités suivantes (priorité MUST). Les fonctionnalités SHOULD et COULD sont soumises à arbitrage selon les priorités.

### MUST (chaîne fil rouge - remplace LupasRename + Kaléidoscope)

La chaîne minimale livrable est la **chaîne fil rouge** : depuis la récupération de la carte SD jusqu'au dépôt sur Vigie-Chiro, sans aucun outil tiers.

- **Déclarer un site de suivi** dans l'application : n° de carré (6 chiffres) et codes des points d'écoute (1 lettre + 1 chiffre). Le site doit avoir été créé en amont sur le portail Vigie-Chiro pour récupérer ces identifiants.
- **Importer une nuit d'enregistrement** depuis un dossier (typiquement la carte SD) : détection automatique du `LogPR*.txt`, du `PaRec*_THLog.csv` et des WAV bruts ; **copie protégée** des fichiers (aucune écriture sur la SD source) ; **renommage** automatique avec le préfixe `CarXXXXXX-AAAA-PassN-YY-` ; **transformation** de chaque WAV brut en séquences de 5 s ralenties ×10 (expansion temporelle).
- **Vérifier l'enregistrement par échantillonnage** : sound check avant dépôt. L'application propose automatiquement une dizaine de séquences réparties sur la nuit, l'utilisateur en écoute quelques-unes pour confirmer que la qualité est exploitable, et saisit un **verdict global** (`OK`, `Utilisable`, `Inexploitable`).
- **Préparer le dépôt** : vérification de cohérence (préfixes conformes, journal et climat présents, etc.), affichage du chemin du dossier, téléversement **direct sur Vigie-Chiro** depuis l'application (création de la participation, envoi reprenable, lancement de l'analyse), avec un **repli** par ouverture du dossier dans l'explorateur pour un dépôt navigateur hors connexion.
- **Tracer le dépôt** : marquer le passage comme `Déposé` avec date de dépôt, pour distinguer ce qui a été livré de ce qui reste à traiter.

### SHOULD (utilité reconnue)

Une fois la chaîne fil rouge livrée, ces capacités étendent la valeur en cas de marge :

- **Naviguer dans plusieurs sites et passages** via une vue tabulaire performante avec tri, filtres et actions de masse. **Devient MUST de fait** dès qu'on dépasse 3-4 sites (cas Karim et Samuel).
- **Diagnostiquer le matériel** : visualiser les courbes de température et d'hygrométrie de la nuit, les niveaux de batterie début/fin, la liste des évènements anormaux du `LogPR*.txt` (réveils non programmés, erreurs SD, redémarrages).
- **Vérifier la cohérence des horaires astronomiques** : le PR doit s'allumer 30 min avant le coucher du soleil et s'éteindre 30 min après son lever. L'application calcule localement ces heures d'après les coordonnées GPS du point et les compare avec celles loguées dans le `LogPR*.txt`. (Idée Samuel mai 2026.)
- **Valider les résultats Tadarida** (cible étirable principale, filet de sécurité au-delà du fil rouge) : charger le CSV de résultats récupéré depuis Vigie-Chiro 24-48 h après le dépôt, parcourir les observations avec sonogramme + spectrogramme + zoom, valider ou corriger la classification, exporter un CSV `*_Vu.csv` réinjectable par la plateforme.
- **Filtrer** les observations Tadarida par taxon, par groupe taxonomique (Pipistrelles, Murins, Noctules), par seuil de probabilité, par plage horaire.
- **Annoter** une nuit avec un commentaire libre (contexte météo, intervention humaine, problème matériel) ainsi qu'avec les **données météo structurées** attendues par Vigie-Chiro (température début/fin de nuit, couverture nuageuse, vent).

### COULD (idées d'extension, productivité avancée)

À engager uniquement si la chaîne MUST et les SHOULD sont solides :

- **Modifier rétroactivement** le rattachement (site / point / année / n° passage) d'un passage déjà importé, avec re-renommage automatique de tous les fichiers.
- **Regrouper les nuits successives** d'un même point pour valider en un coup les espèces communes (productivité Samuel).
- **Mode inventaire vs activité** pour la validation Tadarida : inventaire = liste des espèces présentes (valider une fois), activité = quantifier toutes les détections.
- **Exporter une bibliothèque de sons de référence** : sélection des observations validées de très bonne qualité, export par espèce sous forme de dossiers WAV nommés, utile pour transmettre des cas-types aux débutants.
- **Statistiques globales** sur l'ensemble des sessions (nombre d'espèces détectées, courbe d'activité par heure, comparaison entre points fixes).
- **Filtres avancés multi-critères** avec sauvegarde de vues nommées.
- **Reprise sur erreur** : un import ou une validation interrompus peuvent être repris au démarrage suivant.

### WON'T (hors périmètre de cette première version)

- **Pas** de communication directe avec la plateforme VigieChiro (les échanges se font par téléversement / téléchargement de fichiers).
- **Pas** de gestion multi-utilisateur (l'application reste mono-poste). En revanche elle se connecte au **compte Vigie-Chiro** de l'observateur, par jeton, pour déposer et synchroniser.
- **Pas** de calcul propre de classification automatique (Tadarida fait son travail en amont, l'application l'exploite).
- **Pas** de déploiement web ou mobile.

## Et après ?

Le présent document est l'**expression brute du besoin**, telle qu'elle pourrait être recueillie auprès du commanditaire. Il est décliné en un **dossier d'analyse et de conception** dans [`Analyse et conception/`](Analyse%20et%20conception/index.md) :

- le [modèle conceptuel](Analyse%20et%20conception/Modèle%20conceptuel/index.md) - vocabulaire et données
- 3 [personas](Analyse%20et%20conception/Personas/index.md) représentatifs des utilisateurs cibles
- les [parcours utilisateurs](Analyse%20et%20conception/Parcours%20utilisateurs/index.md) attendus
- les [maquettes](Analyse%20et%20conception/Maquettes/index.md) des écrans
- une [cartographie des histoires utilisateurs](Analyse%20et%20conception/Story%20mapping/index.md) (épopées, stories et critères d'acceptation)

Le présent document reste utile pour comprendre **d'où viennent** les choix actés dans ce dossier.

Quelques points d'inspiration ergonomique à explorer si besoin :

- Les outils d'analyse audio scientifique : [Audacity](https://www.audacityteam.org/), [Raven Lite](https://www.ravensoundsoftware.com/software/raven-lite/), [Kaleidoscope Pro](https://www.wildlifeacoustics.com/products/kaleidoscope-pro).
- L'interface web actuelle de VigieChiro (à découvrir si vous avez un compte test).
- Les conventions ergonomiques d'un client mail ou d'un explorateur de photos (liste à gauche, détail à droite, navigation clavier).
