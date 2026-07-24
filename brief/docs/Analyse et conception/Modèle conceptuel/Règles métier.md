# Règles métier

Les règles métier du modèle conceptuel. Chaque règle a un identifiant ancré (`#r1` à `#r33`) qui sert de point de référence depuis le reste du dossier (parcours, stories, maquettes).

## Site, point, passage

- **R1**{ #r1 } : un n° de carré est obligatoirement composé de **6 chiffres**, dont les 2 premiers correspondent au numéro de département (avec leading zero pour les départements 1 à 9).
- **R2**{ #r2 } : un code de point est une **lettre majuscule suivie d'un ou plusieurs chiffres** (regex `[A-Z][0-9]+`, ex. `A1`, `Z4`, `Z41`). Validation à la saisie : un code à plusieurs chiffres comme `B100` est donc accepté.
- **R3**{ #r3 } : sur les sites en mode **`PointFixeStandard`** (cf. [C2](C2%20-%20Site%20de%20suivi.md)), deux passages sont attendus par site et par année :
    - **passage 1** : entre le 15 juin et le 31 juillet,
    - **passage 2** : entre le 15 août et le 30 septembre.
    L'application doit **alerter sans bloquer** si l'utilisateur déclare un passage hors fenêtre. Sur les sites en mode **`PointFixeRecherche`** (dates personnalisées pour des besoins recherche), cette règle est **muette** : aucune alerte n'est générée.
    ⚠️ *État réel* : la vérification de fenêtre est **calculée** (`ServicePassage.verifierProtocole`), mais sa **restitution à l'utilisateur** relève du **solde de saison** ([#2349](https://github.com/echonuit/vigiechiro-pr-companion/issues/2349), [#2356](https://github.com/echonuit/vigiechiro-pr-companion/issues/2356)), non d'une alerte à la saisie : R3 est un **indicateur de pilotage** de la campagne, pas une garde de rattachement (arbitrage tracé en [#2387](https://github.com/echonuit/vigiechiro-pr-companion/issues/2387)).
- **R4**{ #r4 } : sur les sites en mode **`PointFixeStandard`**, intervalle conseillé entre les deux passages d'un même **point d'écoute** : **≥ 1 mois** (la vérification compare les passages d'un même point dans la **même année**). La clause « dates anniversaires (±10 j) d'une année à l'autre » **n'est pas implémentée**. Comme pour [R3](#r3), cette vérification est **calculée** et sera **restituée par le solde de saison** ([#2349](https://github.com/echonuit/vigiechiro-pr-companion/issues/2349)), non par une alerte de saisie (arbitrage tracé en [#2387](https://github.com/echonuit/vigiechiro-pr-companion/issues/2387)). Sur les sites en mode **`PointFixeRecherche`**, cette règle est également **muette** (l'utilisateur enregistre à la fréquence qui convient à son protocole, y compris plusieurs nuits successives).
- **R5**{ #r5 } : le triplet `(Site, Point, Année, n° de passage)` est **unique** : un même point ne peut pas avoir deux passages avec le même n° dans la même année.
- **R25**{ #r25 } : un n° de carré est **unique par utilisateur** : un même utilisateur ne peut pas déclarer deux fois le même carré (alerte bloquante à la création ou au renommage d'un site ; la vérification exclut le site courant, pour qu'un simple renommage ne se bloque pas lui-même).

## Géographie des carrés et des points

- **R26**{ #r26 } : un point d'écoute appartient à un **carré de 2 km de côté** du **carroyage national Vigie-Chiro** (« carrenat »). Ses coordonnées GPS doivent tomber **dans l'emprise de ce carré**. À l'édition cartographique des positions, le marqueur est **contraint (clampé) au bord de la maille** : on ne peut pas glisser un point hors de son carré. L'emprise du carré est déduite du **centroïde officiel** de la maille (référentiel `carrenat` embarqué, ≈ 137 000 mailles couvrant la France métropolitaine), avec un **repli** : si le carré est hors référentiel, l'emprise est reconstruite autour des points **géolocalisés** du site.
- **R27**{ #r27 } : un point dont les coordonnées GPS ne sont **pas (encore) saisies** est tout de même situé, **au centre de son carré** (position **approchée**, distinguée visuellement d'une position mesurée). Si plusieurs points d'un même carré sont sans GPS, ils sont **répartis en éventail** autour du centre pour ne pas se superposer. Un point n'est **non plaçable** que si son carré est hors référentiel **et** qu'aucun point du site n'est géolocalisé (centre inconnu).

## Convention de nommage des fichiers

- **R6**{ #r6 } : tout enregistrement original doit être renommé selon le préfixe `CarXXXXXX-AAAA-PassN-YY-` avant tout dépôt. Les **tirets sont des « tirets du 6 »** (`-`, U+002D HYPHEN-MINUS), pas des cadratins ni des demi-cadratins. À n'oublier sous aucun.
- **R7**{ #r7 } : le suffixe original de l'enregistreur (`PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav`) est **conservé tel quel** après le préfixe.
- **R8**{ #r8 } : toute séquence d'écoute reprend le nom de son enregistrement original source, avec l'**horodatage décalé de `index × 5 s`** (chaque tranche porte l'heure réelle de son début) et un suffixe **`_000` systématique** entre le nom de base et l'extension. Les suffixes `_001`, `_002`, … ne **numérotent pas les tranches** : ils servent uniquement à **départager une collision de noms** entre deux originaux qui se chevauchent sur la grille de 5 s (le plus ancien garde `_000`, le suivant passe en `_001`).

## Copie protégée

- **R9**{ #r9 } : à l'import, l'application **ne modifie jamais les originaux** sur la carte SD (lecture seule). C'est une contrainte explicite du protocole Vigie-Chiro pour éviter toute perte de données. Elle **produit toujours** les séquences d'écoute transformées dans son espace de travail ; **copier** les enregistrements bruts est en revanche une **option désactivée par défaut** (cf. [R22](#r22) et [ADR 0036](https://companion-dev.echonuit.fr/decisions/0036-la-copie-des-bruts-est-une-option/)). Quand cette conservation est active, chaque copie de brut est **vérifiée bit-à-bit** : l'empreinte **SHA-256** de la destination est recalculée et comparée à celle de la source ; toute divergence est une erreur (copie non fidèle). Dans le mode par défaut (sans copie), l'application **capture** malgré tout l'empreinte SHA-256 de chaque original (preuve d'identité) sans en garder de copie.

## Import : résilience et inspection

- **R29**{ #r29 } : l'import est **résilient**. Un enregistrement original source **illisible ou de format invalide** est **rejeté individuellement** et consigné dans un **rapport d'import** (importés / rejetés / fichiers non pertinents) **sans interrompre** le traitement des autres fichiers. Le rapport porte aussi ce que l'import a rencontré sans le rejeter : le **doublon de nuit** quand l'observateur a choisi d'importer une nuit déjà présente, et les **anomalies relevées au journal du capteur** (réveil non programmé, batterie faible) - elles n'expliquent pas toujours un problème, mais elles éclairent une nuit qui paraît incomplète. Sont rejetés notamment : un en-tête WAV illisible ou de format invalide, et une **fréquence d'acquisition non divisible par 10** (non ralentissable ×10 proprement, cf. [R31](#r31)). Un brut **nativement expansé ×10** par l'enregistreur n'est **pas** un motif de rejet : c'est le cas normal ([R31](#r31)). Le **remplacement d'une session** existante est en revanche **atomique** : si l'import échoue globalement (annulation, tous les WAV rejetés, erreur disque), la session précédente est **restaurée** - rien n'est perdu.
- **R30**{ #r30 } : à l'**inspection** d'un dossier (avant import), l'application **signale sans bloquer** trois situations, à charge de l'utilisateur de décider : un **mélange** (le dossier contient des fichiers de **plusieurs enregistreurs**), une **incohérence** (le journal du capteur - n° de série, nuit - **contredit** les WAV) et une **nuit déjà importée** (un passage existe déjà pour le même enregistreur et la même date). Aucune de ces alertes n'empêche de lancer l'import.
- **R31**{ #r31 } : l'import accepte les **enregistrements bruts de l'enregistreur**, y compris ceux que le Passive Recorder écrit **déjà expansés ×10** - en-tête estampillé à `Fe/10` (ex. 38 400 Hz pour une acquisition à 384 kHz), échantillons conservés. C'est le **cas normal**, pas un motif de rejet. L'application résout la **vraie fréquence d'acquisition** `Fe` avant de transformer : celle du **journal** s'il est présent ; sinon l'**en-tête** s'il est déjà une acquisition plausible (≥ 96 kHz, brut direct) ; sinon l'**en-tête multiplié par 10** (repli sur le comportement natif du PR). C'est donc l'inverse d'un rejet : un en-tête « trop bas » est **corrigé**, pas refusé. Le seul rejet lié à la fréquence est une **fréquence d'acquisition non divisible par 10** (non ralentissable ×10 proprement) : le fichier est alors rejeté **individuellement** ([R29](#r29)), sans interrompre l'import des autres.

## Transformation

- **R10**{ #r10 } : la transformation d'un enregistrement original le **découpe en tranches de 5 s réelles**, puis **ralentit ×10** chaque tranche (expansion de temps). Le découpage porte donc sur le signal **brut**, pas sur le signal déjà ralenti : une séquence d'écoute contient **5 s d'enregistrement** et **dure 50 s à l'écoute**. Pour un enregistrement original de durée `D` (en secondes réelles), on obtient `ceil(D / 5)` séquences. La dernière séquence peut être plus courte que 5 s. Le découpage et l'expansion sont pilotés par la **vraie fréquence d'acquisition** `Fe`, résolue avant transformation (cf. [R31](#r31)) et **non** par l'en-tête WAV - un brut PR est nativement estampillé à `Fe/10`. Seule une fréquence d'acquisition **non divisible par 10** est rejetée.
- **R11**{ #r11 } : la transformation est **deterministe** : relancer la transformation sur les mêmes enregistrements originaux produit les mêmes séquences d'écoute au bit près.

## Vérification d'enregistrement

- **R12**{ #r12 } : une sélection d'écoute est constituée automatiquement à l'ouverture de la vue, avec la méthode `RéparTemporel` par défaut (séquences réparties uniformément sur la nuit).
- **R13**{ #r13 } : le verdict de chaque **fichier son écouté** (`Bon` / `Mauvais` / `Inexploitable`) est saisi par l'utilisateur ; le **verdict final du passage** (`OK` / `Utilisable` / `Inexploitable`, état initial `Non vérifié`) en est **dérivé automatiquement** mais reste **surchargeable** à la main. Aucun seuil obligatoire d'écoute : l'utilisateur écoute **tout ou partie** de la sélection et reste responsable.
- **R14**{ #r14 } : un passage au verdict final `Inexploitable` ne peut pas être déposé (alerte bloquante). Pour déposer, il faut le **requalifier** (re-vérifier jusqu'à un verdict déposable) ; une fois **déposé**, le verdict est **figé**.

## Préparation du dépôt

- **R33**{ #r33 } : préparer le dépôt s'appuie sur une **checklist de cohérence vivante** (chaque contrôle est affiché, même satisfait : ✓ / ⚠ / ✗). Un contrôle **bloquant** (✗) **interdit la préparation** tant qu'il n'est pas corrigé : verdict `Inexploitable` ([R14](#r14)), **transformation incomplète**, **préfixe de fichier non conforme** ([R6](#r6)), **journal du capteur absent**. Les contrôles **non bloquants** (⚠) - par exemple un **relevé climatique absent** ([R20](#r20)) - sont signalés mais **laissent préparer**. Quand la préparation réussit, le passage passe au statut `Prêt à déposer`.

## Validation taxonomique (SHOULD)

- **R15**{ #r15 } : une observation est qualifiée de **validée** quand l'observateur a saisi un `taxon observateur` **égal** au `taxon Tadarida`. La **probabilité observateur n'entre pas** dans ce statut : un `_Vu` réel porte une confiance **textuelle** (« SUR ») que le parseur lit comme probabilité inconnue, et l'exiger ferait passer une observation pourtant validée pour « non revue ». La confiance de l'observateur se déclare séparément par la **certitude** (`SUR` / `PROBABLE` / `POSSIBLE`, cf. [C13](C13%20-%20Observation.md)), indépendante du statut.
- **R16**{ #r16 } : une observation est qualifiée de **corrigée** quand `taxon observateur ≠ taxon Tadarida`. ⚠️ **Cas particulier** : une observation **créée à la main** (sans `taxon Tadarida`, cf. [C13](C13%20-%20Observation.md)) tombe mécaniquement dans « corrigée » dès qu'un taxon observateur y est saisi, alors qu'il n'y avait rien à corriger. Le statut dérivé (`StatutObservation`) ne distingue pas encore ce cas d'une vraie correction.
- **R17**{ #r17 } : une observation **non touchée** par l'utilisateur conserve uniquement les colonnes `tadarida_*`, et l'export `_Vu.csv` reprend ces valeurs (l'utilisateur conserve ainsi la classification automatique par défaut).
- **R18**{ #r18 } : deux **modes de validation** coexistent au MVP (au choix de l'utilisateur) :
    - **Mode inventaire** : dès qu'une espèce est validée avec confiance sur une nuit, on ne valide plus les autres détections de la même espèce sur la même nuit.
    - **Mode activité** : toutes les observations doivent être passées en revue (utile pour les études d'activité quantitative).
    - 🟠 **SHOULD / variante future - Mode inventaire pondéré** : variante du mode inventaire qui **rouvre la validation pour toute nouvelle détection de l'espèce dont la probabilité Tadarida est *supérieure*** à celle de l'observation déjà validée comme référence. Hybride entre inventaire (gain de temps) et activité (capter les signaux plus francs qui apparaissent en cours de nuit). Origine : suggestion de Samuel - *« on pourrait imaginer un 3e mode qui ne valide que les détections dont la proba est supérieure à celle de l'observation validée, qui se rapproche de ce qu'on fait sur certains projets »*.
- **R24**{ #r24 } : chaque observation porte un **mode de validation** (`manuel` | `auto` | `null`) qui trace comment sa valeur courante de `taxon observateur` a été établie :
    - `manuel` : l'utilisateur a explicitement saisi ou modifié le taxon observateur (validation classique en mode `activité`, ou validation explicite en mode `inventaire`).
    - `auto` : le taxon a été propagé automatiquement par le mode `inventaire` (cf. [R18](#r18)) sans intervention manuelle - la classification dérive d'une validation antérieure sur la même espèce de la même nuit.
    - `null` : aucune validation n'a encore été effectuée (l'observation conserve uniquement les colonnes `tadarida_*`, cf. [R17](#r17)).

    Ce mode est tracé en BD et restituable dans l'export `_Vu.csv` (colonne optionnelle, à activer selon attentes Vigie-Chiro). Sa principale utilité : permettre à un évaluateur scientifique de distinguer ce qui a été réellement vérifié à l'oreille de ce qui a été propagé sur confiance.

- **R32**{ #r32 } : pendant la validation, une observation peut être marquée **« séquence de référence »**. L'export de la **bibliothèque de sons de référence** est constitué **exactement de ces observations-là** : pour chacune, le **taxon retenu** est le taxon **observateur** s'il a été validé, sinon le taxon **Tadarida**. Les fichiers audio sont copiés **à plat** dans le dossier d'export, accompagnés d'un **CSV récapitulatif** (`bibliotheque-sons.csv`) où le **taxon est une colonne** ; ils ne sont **pas** rangés dans des sous-dossiers par taxon.

## Suppression de sites et de points

- **R28**{ #r28 } : un **point d'écoute** ou un **site** qui **porte des passages** ne peut pas être supprimé (garde-fou contre la perte de données rattachées). La suppression est **bloquée** tant qu'au moins un passage y est rattaché ; l'interface l'indique (action désactivée avec info-bulle explicative, ou message au clic). Pour supprimer, il faut d'abord retirer les passages concernés.

## Données

- **R19**{ #r19 } : le journal du capteur est un **journal circulaire** sur l'enregistreur (place limitée). En cas de saturation de la SD, des entrées plus anciennes peuvent être effacées. L'application n'a pas à reconstituer les pertes - elle exploite ce qui est présent.
- **R20**{ #r20 } : le relevé climatique peut être **absent** (sonde défaillante ou non installée). Dans ce cas, l'onglet diagnostic du passage le signale clairement plutôt que de masquer la section.

## Arborescence sur disque

- **R21**{ #r21 } : toutes les données et tous les paramètres de l'application vivent dans **un unique dossier « workspace »** sur le disque local de l'utilisateur. Ce workspace contient les sessions d'enregistrement, la base SQLite et tout fichier de réglages utilisateur. Aucune donnée n'est dispersée ailleurs.
    - **Défaut proposé à l'installation** : `<Documents>/VigieChiro-Companion/` (résolu par OS : `~/Documents/VigieChiro-Companion/` sous Linux/macOS, `%USERPROFILE%\Documents\VigieChiro-Companion\` sous Windows).
    - **Configurable depuis les préférences** (onglet « Emplacements ») : utile en particulier pour les gros volumes ([Samuel](../Personas/Samuel.md) : 24 enregistreurs × 40-50 nuits → To cumulés), qui demanderont à pointer un disque externe. Le changement est **pris en compte au prochain démarrage** et ne déplace pas les données déjà présentes.
- **R22**{ #r22 } : une session d'enregistrement occupe **un sous-dossier du workspace dont le nom est exactement le préfixe** défini par [R6](#r6) (`Car<carre>-<annee>-Pass<n>-<point>`). Dans ce sous-dossier :
    - les **enregistrements originaux**, **si l'utilisateur a demandé de les conserver**, sont rangés dans `bruts/` (cf. [R7](#r7)). Ce dossier est **absent par défaut** : l'application lit les WAV directement depuis la carte et n'en garde pas de copie, la conservation étant une option de ré-analyse (cf. [ADR 0036](https://companion-dev.echonuit.fr/decisions/0036-la-copie-des-bruts-est-une-option/)) ;
    - les **séquences d'écoute** sont rangées dans `transformes/` (cf. [R8](#r8)) ;
    - les **archives ZIP de dépôt** (`<préfixe>-N.zip`), quand elles ont été générées, sont rangées dans `depot/` ; elles sont **régénérables à l'identique** et peuvent être supprimées une fois téléversées ;
    - le **journal du capteur** (`LogPR<n>.txt`) est à la **racine** de la session d'enregistrement (cf. [C9](C9%20-%20Journal%20du%20capteur.md)) ;
    - le **relevé climatique** (`PaRecPR<sn>_THLog.csv`), si présent, est aussi à la **racine** de la session d'enregistrement (cf. [C10](C10%20-%20Relevé%20climatique.md), [R20](#r20)).
- **R23**{ #r23 } : le **fichier de résultats Tadarida** (`*-observations.csv` ou `*-observations_Vu.csv`) est **lu à l'emplacement choisi par l'utilisateur** (ou rapatrié de la plateforme). L'application **n'en fait pas de copie** et ne le déplace pas dans la session : seul son **chemin d'origine** est mémorisé comme provenance (colonne `file_path` des résultats d'identification). Il n'est donc **pas** rangé dans `transformes/`.

Arborescence type d'un workspace contenant deux sessions d'enregistrement (un passage 1 et un passage 2 sur le même point) :

```text
<workspace>/                              ← R21 : configurable, défaut <Documents>/VigieChiro-Companion/
├── Car040962-2026-Pass1-A1/              ← R22 : nom = préfixe R6
│   ├── bruts/                            ← R22 : originaux, SEULEMENT si conservation demandée
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213045.wav
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213101.wav
│   │   └── ...
│   ├── transformes/                      ← R22 : séquences d'écoute R8 (horodatage + _000)
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213045_000.wav
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213050_000.wav
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213055_000.wav
│   │   └── ...
│   ├── depot/                            ← R22 : archives ZIP de dépôt (régénérables)
│   │   ├── Car040962-2026-Pass1-A1-1.zip
│   │   └── Car040962-2026-Pass1-A1-2.zip
│   ├── LogPR1925492.txt                  ← R22 : journal capteur à la racine
│   └── PaRecPR1925492_THLog.csv          ← R22 : relevé climatique (optionnel)
├── Car040962-2026-Pass2-A1/
│   └── ...                               ← même structure que ci-dessus
└── vigiechiro.db                         ← R21 : BD SQLite + réglages à la racine
```

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
