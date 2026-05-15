# Modèle conceptuel

Ce document pose le **vocabulaire**, le **modèle de données** et les **règles métier** sur lesquels s'aligne tout le reste du dossier d'analyse et de conception. Toute évolution du brief qui touche ces concepts doit être répercutée ici **avant** de s'attaquer aux parcours, aux stories ou aux maquettes.

---

## 1. Vue d'ensemble

L'application *VigieChiro PR Companion* organise les données autour d'un utilisateur unique (mono-utilisateur, hors-ligne). Cet utilisateur déclare un ou plusieurs **sites de suivi**, chaque site contenant un ou plusieurs **points d'écoute**. Sur chaque point, il réalise des **passages** (= une nuit complète d'enregistrement). Chaque passage produit un **lot de fichiers** : les WAV bruts copiés depuis la SD, les fichiers transformés (×10 + chunks 5 s) prêts à être déposés sur Vigie-Chiro, ainsi que le LogPR et le THLog du PR utilisé.

Une fois les chunks transformés produits, l'utilisateur **vérifie l'enregistrement** par échantillonnage (sound check global). S'il est satisfait, il prépare le **lot prêt à déposer** et téléverse manuellement sur le portail Vigie-Chiro. Le retour de **Tadarida** (CSV d'observations) arrive ensuite, et le passage entre alors en **validation taxonomique** (espèce par espèce).

```mermaid
erDiagram
    UTILISATEUR ||--o{ SITE_DE_SUIVI : possède
    SITE_DE_SUIVI ||--|{ POINT_D_ECOUTE : contient
    POINT_D_ECOUTE ||--o{ PASSAGE : "fait l'objet de"
    PR ||--o{ PASSAGE : "a produit"
    PASSAGE ||--|| LOT_DE_FICHIERS : "produit (1)"
    LOT_DE_FICHIERS ||--|{ WAV_BRUT : contient
    LOT_DE_FICHIERS ||--|{ CHUNK_TRANSFORME : contient
    LOT_DE_FICHIERS ||--|| LOG_PR : référence
    LOT_DE_FICHIERS ||--o| THLOG : référence
    WAV_BRUT ||--|{ CHUNK_TRANSFORME : "découpé en"
    PASSAGE ||--o| ECHANTILLON_DE_VERIFICATION : "à vérifier par"
    ECHANTILLON_DE_VERIFICATION }o--|{ CHUNK_TRANSFORME : "porte sur"
    PASSAGE ||--o| CSV_OBSERVATIONS : "annoté par (après Tadarida)"
    CSV_OBSERVATIONS ||--|{ OBSERVATION_TADARIDA : agrège
    OBSERVATION_TADARIDA }o--|| CHUNK_TRANSFORME : "détectée dans"
    OBSERVATION_TADARIDA }o--|| TAXON : "classée comme"
    TAXON }o--|| GROUPE_TAXONOMIQUE : appartient
```

> Ce diagramme rend visible la **séparation entre deux moments du workflow** : la chaîne `PASSAGE → LOT_DE_FICHIERS → CHUNK_TRANSFORME → ECHANTILLON_DE_VERIFICATION` (avant le dépôt VigieChiro, MUST du MVP), puis la chaîne `CSV_OBSERVATIONS → OBSERVATION_TADARIDA → TAXON` (après le retour Tadarida, SHOULD/cible étirable).

---

## 2. Cardinalités

| De | Association | Vers | Cardinalité | Sens métier |
|---|---|---|---|---|
| Utilisateur | possède | Site de suivi | 1..* | Marie en a 1, Karim 2-3, Samuel 36+ |
| Site de suivi | contient | Point d'écoute | 1..* | un site sans point n'a pas de sens |
| Point d'écoute | fait l'objet de | Passage | 0..* | un point peut n'avoir aucun passage encore |
| PR | a produit | Passage | 1..* | un même PR peut faire plusieurs nuits |
| Passage | produit | Lot de fichiers | 1..1 | un passage donne exactement un lot |
| Lot de fichiers | contient | WAV brut | 1..* | typiquement plusieurs centaines à plusieurs milliers |
| Lot de fichiers | contient | Chunk transformé | 1..* | typiquement 1,3 × le nombre de WAV bruts |
| WAV brut | découpé en | Chunk transformé | 1..* | un WAV brut donne 1 à N chunks de 5 s ralentis ×10 |
| Lot de fichiers | référence | LogPR | 1..1 | un seul LogPR par passage |
| Lot de fichiers | référence | THLog | 0..1 | absent si la sonde T°/H est défaillante |
| Passage | à vérifier par | Échantillon de vérification | 0..1 | créé au moment de la vérification utilisateur |
| Échantillon de vérification | porte sur | Chunk transformé | 1..* | typiquement 10-30 chunks |
| Passage | annoté par | CSV observations | 0..1 | rempli après retour Tadarida (SHOULD) |
| CSV observations | agrège | Observation Tadarida | 1..* | plusieurs milliers par CSV |
| Observation Tadarida | détectée dans | Chunk transformé | 1..1 | chaque observation référence un chunk précis |
| Observation Tadarida | classée comme | Taxon | 1..1 | au moment de l'import du CSV |
| Taxon | appartient | Groupe taxonomique | 1..1 | exemple : Pippip → Pipistrellus → Vespertilionidae |

---

## 3. Entités

### 3.1 Utilisateur

L'utilisateur unique de l'application. Mono-utilisateur, hors-ligne, pas de compte ni de mot de passe.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| identifiant local | UUID | unique, généré à l'install | Sert uniquement à associer les sites en base, jamais affiché |
| nom affiché | texte | optionnel, ≤ 60 car. | Cosmétique, repris dans la barre de titre |

### 3.2 Site de suivi

Un site est créé hors application sur <https://vigiechiro.herokuapp.com/>. Il fournit le numéro de **carré** et la liste des **codes points** que l'utilisateur déclare ensuite dans l'app.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° carré | texte | exactement 6 chiffres | Les 2 premiers chiffres = département. Ex. `040962` (département 04 = Alpes-de-Haute-Provence). **Leading zero obligatoire** pour les départements 1 à 9. |
| nom convivial | texte | optionnel, ≤ 100 car. | Pour aider Marie à reconnaître son site (ex. « Étang de la Tuilière »). |
| protocole | énum | `PointFixe` (seul supporté MVP) | Architecture extensible (Pédestre, Routier… plus tard). |
| commentaire libre | texte | optionnel | Contexte écologique, descriptif paysager. |
| date de création | date | obligatoire | Date locale (importante pour les anniversaires de passage). |

### 3.3 Point d'écoute

Un point dans un site, identifié par un code court fourni par Vigie-Chiro.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| code | texte | exactement 2 caractères : 1 lettre + 1 chiffre | Ex. `A1`, `C2`, `Z4`. |
| coordonnées GPS | décimal × 2 | optionnel | Utile pour le calcul astronomique (lever/coucher soleil) en COULD. |
| descriptif | texte | optionnel, ≤ 500 car. | « En lisière de bois, au-dessus du chemin », etc. |

### 3.4 PR (Passive Recorder)

Le matériel utilisé pour la nuit. L'application en mémorise l'identité pour suivre la santé du matériel dans le temps.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° de série | texte | unique, format libre | Lu dans le `LogPR<n>.txt`. Ex. `1925492`. |
| modèle / version | texte | optionnel | Ex. `V1.01, T4.1` extrait du LogPR. |
| commentaire libre | texte | optionnel | Anomalies récurrentes, dates de remise en état, etc. |

### 3.5 Passage

L'unité métier centrale : une nuit complète d'enregistrement sur un point d'un site, avec un PR, lors d'un n° de passage donné dans une année.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| n° de passage | entier | typiquement 1 ou 2 | Le protocole impose deux passages annuels (cf. règle métier P1). |
| année | entier | 4 chiffres | Ex. 2026. |
| date de capture | date | obligatoire | Date du **soir** où l'enregistrement démarre. |
| heure de début | heure | obligatoire | Lue du LogPR. |
| heure de fin | heure | obligatoire | Lue du LogPR. |
| paramètres d'acquisition | structure | extraits du LogPR | Fe, FL, FPH, S.R., gain, bande de fréquence, durée WAV, seuil SD. Sérialisés tels quels. |
| statut workflow | énum | `Importé` / `Transformé` / `Vérifié` / `Prêt à déposer` / `Déposé` | Progression de la chaîne pré-VigieChiro. |
| verdict de vérification | énum | `À vérifier` / `OK` / `Douteux` / `À jeter` | Saisi par l'utilisateur après écoute de l'échantillon. |
| commentaire de session | texte | optionnel, ≤ 2000 car. | Météo, intervention humaine, anomalie matérielle, etc. |
| données météo structurées | structure | optionnelles | T° début/fin nuit, couverture nuageuse, vent. À aligner sur les champs Vigie-Chiro pour faciliter le dépôt. |
| date de dépôt sur Vigie-Chiro | datetime | optionnelle | Tracée à l'export du lot. |

> **Note importante** : ce que les anciennes maquettes appelaient « session » est désormais nommé **passage** pour rester cohérent avec le vocabulaire Vigie-Chiro.

### 3.6 Lot de fichiers

L'agrégat de fichiers produit par un passage. Pas un objet visible directement par l'utilisateur, mais un concept structurant pour l'organisation sur disque.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin racine | texte | obligatoire | Dossier sur le disque local de l'utilisateur. |
| volume total bruts | entier (octets) | calculé | Indicatif (peut atteindre ~40 Go pour une grosse nuit). |
| volume total transformés | entier (octets) | calculé | Typiquement légèrement supérieur aux bruts (×10 en durée mais re-échantillonné). |

### 3.7 WAV brut

Un fichier audio sortant directement du PR, après **copie protégée** et **renommage** par l'application avec le préfixe `CarXXXXXX-AAAA-PassN-YY-`.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom de fichier | texte | format `Car<carre>-<annee>-<passage>-<point>-PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav` | Ex. `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`. |
| chemin sur disque | texte | obligatoire | Dans le sous-dossier `bruts/` du lot. |
| durée | décimal (s) | typiquement 2-30 s | Déclenchée par seuil sur le PR. |
| échantillonnage | entier (Hz) | 384 000 | Mono 16 bits. |
| empreinte SHA-256 | hex | optionnelle | Si l'on veut garantir l'intégrité bit-à-bit dans le temps. |

### 3.8 Chunk transformé

Un fichier audio dérivé d'un WAV brut par **expansion de temps ×10** suivie d'un **découpage régulier en séquences de 5 s**. C'est ce fichier qui est déposé sur Vigie-Chiro et que Tadarida analysera.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| nom de fichier | texte | suffixe `_000`, `_001`… ajouté au nom du WAV brut | Ex. `Car…_20260422_202623_000.wav`. |
| WAV brut source | référence | obligatoire | Pour la traçabilité. |
| index dans le source | entier | ≥ 0 | Ordre du chunk dans le WAV brut. |
| offset temporel dans le source | décimal (s) | calculé | Position du chunk dans le brut original (avant ×10). |
| durée | décimal (s) | typiquement 5 s | Le dernier chunk d'un brut peut être plus court. |
| chemin sur disque | texte | obligatoire | Dans le sous-dossier `transformes/` du lot. |
| inclus dans l'échantillon | booléen | par défaut `false` | Mis à `true` si le chunk est sélectionné pour la vérification d'enregistrement. |

### 3.9 LogPR

Le journal technique du PR pour la nuit, lu et structuré par l'application.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin sur disque | texte | obligatoire | Fichier `LogPR<n>.txt` à la racine du lot. |
| évènements parsés | liste | structurée | Démarrage, paramètres, batterie, mises en veille, alarmes, anomalies. |
| anomalies détectées | liste | dérivée | Réveils non programmés, erreurs SD, redémarrages, batterie critique. |

> **À noter** : le LogPR est circulaire (place limitée sur le PR), il efface de l'information au fur et à mesure quand la SD sature. L'ordre exact d'éviction reste à confirmer auprès du concepteur du firmware.

### 3.10 THLog

Le journal de température et d'hygrométrie produit par la sonde embarquée du PR. Optionnel : la sonde peut être absente ou défaillante.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin sur disque | texte | obligatoire si présent | Fichier `PaRec<sn>_THLog.csv`. |
| mesures | série temporelle | une mesure toutes les 600 s (10 min) | Date, heure, température (°C), humidité (%). |

### 3.11 Échantillon de vérification

Sous-ensemble de chunks transformés sélectionné pour permettre à l'utilisateur de **vérifier que l'enregistrement de la nuit est exploitable** (sound check global). Créé au moment où l'utilisateur ouvre la vue de vérification.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| méthode de constitution | énum | `RéparTemporel` (par défaut) / `Aléatoire` / `Manuel` | RéparTemporel = N chunks répartis uniformément sur la nuit. |
| taille | entier | par défaut 10-30 chunks | Configurable. |
| chunks rattachés | référence × N | obligatoire | Ordonnés par horodatage du WAV brut source. |
| chunks écoutés | référence × M | dérivé | Mis à jour à chaque play de l'utilisateur. |

### 3.12 CSV observations (post-Tadarida)

Le CSV produit par Tadarida côté serveur Vigie-Chiro et téléchargé manuellement par l'utilisateur après le dépôt. Importé dans l'application pour la **validation taxonomique** (SHOULD / cible étirable).

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chemin sur disque | texte | obligatoire | Fichier `<uuid>-participation-<uuid>-observations.csv` ou `…_Vu.csv`. |
| format détecté | énum | `Brut` (avec guillemets) / `Vu` (réinjectable, sans guillemets) | Reconnu à l'import. |
| date d'import | datetime | obligatoire | Tracée pour la cohérence. |

### 3.13 Observation Tadarida

Une ligne du CSV observations. Une **séquence de 5 s peut générer plusieurs observations** : Tadarida produit 1 ligne par espèce distincte identifiée dans la séquence, avec son timing début/fin précis dans la séquence.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| chunk source | référence | obligatoire | Lien vers le `Chunk transformé` correspondant. |
| temps début | décimal (s) | dans `[0, 5]` | Position dans le chunk. |
| temps fin | décimal (s) | dans `[0, 5]`, ≥ temps début | Position dans le chunk. |
| fréquence médiane | entier (Hz) | obligatoire | Métrique Tadarida. |
| taxon Tadarida | référence | obligatoire | Code 6 lettres. |
| probabilité Tadarida | décimal | dans `[0, 1]` | **Pas une garantie** : un 99 % peut être faux, un 20 % peut être juste. |
| taxon autre Tadarida | référence | optionnel | 2e proposition Tadarida. |
| taxon observateur | référence | optionnel | Saisi par l'utilisateur en validation. |
| probabilité observateur | décimal | dans `[0, 1]`, optionnel | Niveau de confiance utilisateur. |
| commentaire utilisateur | texte | optionnel, ≤ 500 car. | « pic 39 kHz, morphologie atypique », etc. |
| marqué comme référence | booléen | par défaut `false` | Sélection bonus pour la bibliothèque de sons exportable (COULD). |

### 3.14 Taxon

Un code 6 lettres (3 + 3 : trois premières lettres du genre + trois premières lettres de l'espèce) selon la nomenclature Tadarida.

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| code | texte | exactement 6 caractères | Ex. `Pippip`, `Nyclei`, `Tadten`. Aussi : `noise`, `piaf` (pseudo-taxons). |
| nom latin | texte | optionnel | Ex. `Pipistrellus pipistrellus`. |
| nom vernaculaire FR | texte | optionnel | Ex. `Pipistrelle commune`. |
| groupe taxonomique | référence | obligatoire | Voir 3.15. |

### 3.15 Groupe taxonomique

Niveau hiérarchique au-dessus du taxon, utile pour les filtres groupés (« tous les murins », « toutes les pipistrelles »).

| Attribut | Type | Contraintes | Notes |
|---|---|---|---|
| niveau | énum | `Genre` / `Famille` / `Ordre` | Plusieurs niveaux possibles, on en choisit un par groupe. |
| nom | texte | obligatoire | Ex. `Myotis`, `Pipistrellus`, `Vespertilionidae`, `Chiroptera`. |
| taxons membres | référence × N | dérivée | Mise à jour à chaque ajout de taxon. |

---

## 4. Règles métier

### 4.1 Site, point, passage

- **R1** : un n° de carré est obligatoirement composé de **6 chiffres**, dont les 2 premiers correspondent au numéro de département (avec leading zero pour les départements 1 à 9).
- **R2** : un code de point est exactement de la forme **lettre + chiffre** (ex. `A1`, `Z4`). Validation à la saisie.
- **R3** : sur le **protocole Point Fixe**, deux passages sont attendus par site et par année :
    - **passage 1** : entre le 15 juin et le 31 juillet,
    - **passage 2** : entre le 15 août et le 31 septembre.
    L'application **alerte sans bloquer** si l'utilisateur déclare un passage hors fenêtre.
- **R4** : intervalle conseillé entre les deux passages d'un même site : **≥ 1 mois**. Idéalement, dates « anniversaires » (±10 j) d'une année à l'autre.
- **R5** : le triplet `(Site, Point, Année, n° de passage)` est **unique** : un même point ne peut pas avoir deux passages avec le même n° dans la même année.

### 4.2 Convention de nommage des fichiers WAV

- **R6** : tout WAV brut doit être renommé selon le préfixe `CarXXXXXX-AAAA-PassN-YY-` avant tout dépôt. Les **tirets sont des « tirets du 6 »** (`-`, U+002D HYPHEN-MINUS), pas des cadratins ni des demi-cadratins. À n'oublier sous aucun.
- **R7** : le suffixe original du PR (`PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav`) est **conservé tel quel** après le préfixe.
- **R8** : tout chunk transformé reprend le nom de son WAV brut source en **insérant un suffixe `_000`, `_001`, …** entre le nom de base et l'extension.

### 4.3 Copie protégée

- **R9** : à l'import, l'application **copie systématiquement** les fichiers depuis la carte SD vers son espace de travail. **Aucune écriture sur les originaux** sur la SD. C'est une contrainte explicite du protocole Vigie-Chiro pour éviter toute perte de données.

### 4.4 Transformation

- **R10** : la transformation d'un WAV brut produit des **chunks de 5 s ralentis ×10** (expansion de temps). Pour un WAV brut de durée `D`, on obtient `ceil(D × 10 / 5) = ceil(2 × D)` chunks. Le dernier chunk peut être plus court que 5 s.
- **R11** : la transformation est **deterministe** : relancer la transformation sur les mêmes WAV bruts produit les mêmes chunks au bit près.

### 4.5 Vérification d'enregistrement

- **R12** : un échantillon de vérification est constitué automatiquement à l'ouverture de la vue, avec la méthode `RéparTemporel` par défaut (chunks répartis uniformément sur la nuit).
- **R13** : le verdict global est saisi par l'utilisateur après écoute de **tout ou partie** de l'échantillon. Aucun seuil obligatoire d'écoute (l'utilisateur reste responsable).
- **R14** : un passage avec verdict `À jeter` ne peut pas être inclus dans un lot prêt à déposer (alerte bloquante).

### 4.6 Validation taxonomique (SHOULD)

- **R15** : une observation Tadarida est qualifiée de **validée** quand `taxon observateur = taxon Tadarida` et `probabilité observateur` renseignée.
- **R16** : une observation est qualifiée de **corrigée** quand `taxon observateur ≠ taxon Tadarida`.
- **R17** : une observation **non touchée** par l'utilisateur conserve uniquement les colonnes `tadarida_*`, et l'export `_Vu.csv` reprend ces valeurs (l'utilisateur conserve ainsi la classification automatique par défaut).
- **R18** : deux **modes de validation** coexistent (au choix de l'utilisateur) :
    - **Mode inventaire** : dès qu'une espèce est validée avec confiance sur une nuit, on ne valide plus les autres détections de la même espèce sur la même nuit.
    - **Mode activité** : toutes les observations doivent être passées en revue (utile pour les études d'activité quantitative).

### 4.7 Données

- **R19** : le LogPR est un **journal circulaire** sur le PR (place limitée). En cas de saturation de la SD, des entrées plus anciennes peuvent être effacées. L'application n'a pas à reconstituer les pertes - elle exploite ce qui est présent.
- **R20** : le THLog peut être **absent** (sonde défaillante ou non installée). Dans ce cas, l'onglet diagnostic du passage le signale clairement plutôt que de masquer la section.

---

## 5. Glossaire métier

| Terme | Définition courte | Exemple / précision |
|---|---|---|
| **Site de suivi** | Unité géographique déclarée par l'utilisateur sur Vigie-Chiro web. Donne accès à un n° de carré et à un ensemble de points. | « Étang de la Tuilière », carré `040962`, points `A1`, `B2`, `C3`. |
| **Carré** | Code à 6 chiffres identifiant un site Vigie-Chiro. Les 2 premiers chiffres = département. | `040962` (carré 0962 du département 04). |
| **Point** | Code à 2 caractères (lettre + chiffre) identifiant un point d'écoute dans un site. | `A1`, `C2`, `Z4`. |
| **Passage** | Une nuit complète d'enregistrement sur un point d'un site, lors d'un n° de passage donné dans une année. | « Passage 2 du carré `640380` au point `Z1` en 2026 ». Anciennement appelé « session » dans les maquettes V1. |
| **WAV brut renommé** | Fichier audio mono 16 bits 384 kHz produit par le PR, après copie protégée et renommage avec le préfixe `Car…-AAAA-PassN-YY-`. | `Car640380-2026-Pass2-Z1-PaRecPR1925492_20260422_202623.wav`. |
| **Chunk transformé** | Fichier audio dérivé d'un WAV brut, ralenti ×10 et découpé en séquence de 5 s. C'est la version déposée sur Vigie-Chiro. | `Car…_20260422_202623_000.wav`, `…_001.wav`, etc. |
| **Vérification d'enregistrement** | Sound check global permettant à l'utilisateur de confirmer que la nuit est exploitable, avant le dépôt. Distinct de la validation taxonomique. | Marie écoute 15 chunks répartis sur la nuit, ne détecte rien d'anormal, marque le passage `OK`. |
| **Verdict** | Statut métier d'un passage après vérification : `À vérifier`, `OK`, `Douteux`, `À jeter`. | Un passage `À jeter` ne peut pas être déposé. |
| **Échantillon représentatif** | Sous-ensemble de chunks sélectionné automatiquement pour la vérification (méthode `RéparTemporel` par défaut). | 20 chunks pris uniformément entre l'heure de début et l'heure de fin de la nuit. |
| **Lot prêt à déposer** | Dossier contenant tous les chunks transformés d'un passage, formaté selon les attentes du portail Vigie-Chiro. | Sous-dossier `transformes/` du passage, à téléverser tel quel sur vigiechiro.herokuapp.com. |
| **Préfixe** | Chaîne `CarXXXXXX-AAAA-PassN-YY-` ajoutée en début de nom de fichier lors du renommage. | `Car640380-2026-Pass2-Z1-`. |
| **Tirets du 6** | Caractère `-` (U+002D HYPHEN-MINUS), à utiliser obligatoirement dans le préfixe (ni cadratin `—` ni demi-cadratin `–`). | Validation à la saisie. |
| **Expansion de temps** | Ralentissement temporel d'un facteur ×10, qui transpose les ultrasons (8-120 kHz) dans la bande audible (0,8-12 kHz) tout en allongeant leur durée. | 1 seconde de WAV brut devient 10 secondes de chunk transformé. |
| **Validation taxonomique** | Activité postérieure au retour Tadarida : revue espèce par espèce des observations classifiées, validation ou correction. | SHOULD du MVP. Cible étirable. |
| **Mode inventaire** | Variante de validation : on cherche la liste des espèces présentes, donc on arrête de valider une espèce une fois confirmée sur la nuit. | Karim sur un suivi rapide. |
| **Mode activité** | Variante de validation : on quantifie toutes les détections, donc toutes les observations doivent être passées en revue. | Samuel sur son protocole BACIP. |
| **Groupe taxonomique** | Niveau hiérarchique au-dessus du taxon (genre, famille, ordre) servant de filtre groupé. | `Myotis`, `Pipistrellus`, `Vespertilionidae`. |

---

## 6. Glossaire des outils & ressources externes

| Outil / ressource | Rôle | Statut dans le MVP |
|---|---|---|
| [Lupas Rename](https://www.lupinho.net/lupas-rename.html) | Outil tiers de renommage en lot, utilisé manuellement aujourd'hui pour appliquer le préfixe `Car…-AAAA-PassN-YY-`. | **À remplacer par l'app** (chaîne d'import). |
| [Kaléidoscope 4.3.1](https://www.wildlifeacoustics.com/products/kaleidoscope-pro) | Logiciel commercial Wildlife Acoustics, utilisé manuellement aujourd'hui pour le découpage 5 s + expansion temps ×10. | **À remplacer par l'app** (chaîne d'import). |
| Tadarida (Bas et al., 2017) | Logiciel scientifique de classification automatique des taxons à partir des chunks transformés. Tourne **côté serveur Vigie-Chiro**. | Hors MVP côté code. **L'app produit ce que Tadarida attend** et **consomme ce que Tadarida restitue** (CSV d'observations). |
| [Chirosurf 4.1](https://vigie-chiro.forumactif.com/t108-chirosurf-4-1-telechargement-audible-ultrasons-basses-frequences-11-05-26) | Logiciel communautaire de validation taxonomique. | Référence d'**inspiration ergonomique** pour la validation taxonomique (SHOULD / cible étirable). |
| [vigiechiro.herokuapp.com](https://vigiechiro.herokuapp.com/) | Portail web officiel Vigie-Chiro. Création des sites de suivi, dépôt des chunks transformés, restitution des CSV Tadarida. | Hors MVP côté API : tous les échanges restent **manuels via navigateur**. L'app prépare des dossiers prêts à déposer. |
| [vigienature.fr](https://www.vigienature.fr/fr/le-protocole-en-detail) | Documentation officielle du protocole Point Fixe. | Référence de cadrage pour toutes les règles métier ci-dessus. |
| [PiBatRecorderProjects/TeensyRecorders](https://framagit.org/PiBatRecorderProjects/TeensyRecorders) | Projet open-source du firmware du Passive Recorder Teensy. | Référence technique pour comprendre le format du LogPR et des WAV bruts. Hors MVP côté code. |
