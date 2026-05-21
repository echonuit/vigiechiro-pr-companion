# Règles métier

Les 23 règles métier du modèle conceptuel. Chaque règle a un identifiant ancré (`#r1` à `#r23`) qui sert de point de référence depuis le reste du dossier (parcours, stories, maquettes).

## Site, point, passage

- **R1**{ #r1 } : un n° de carré est obligatoirement composé de **6 chiffres**, dont les 2 premiers correspondent au numéro de département (avec leading zero pour les départements 1 à 9).
- **R2**{ #r2 } : un code de point est exactement de la forme **lettre + chiffre** (ex. `A1`, `Z4`). Validation à la saisie.
- **R3**{ #r3 } : sur le **protocole Point Fixe**, deux passages sont attendus par site et par année :
    - **passage 1** : entre le 15 juin et le 31 juillet,
    - **passage 2** : entre le 15 août et le 31 septembre.
    L'application **alerte sans bloquer** si l'utilisateur déclare un passage hors fenêtre.
- **R4**{ #r4 } : intervalle conseillé entre les deux passages d'un même site : **≥ 1 mois**. Idéalement, dates « anniversaires » (±10 j) d'une année à l'autre.
- **R5**{ #r5 } : le triplet `(Site, Point, Année, n° de passage)` est **unique** : un même point ne peut pas avoir deux passages avec le même n° dans la même année.

## Convention de nommage des fichiers

- **R6**{ #r6 } : tout enregistrement original doit être renommé selon le préfixe `CarXXXXXX-AAAA-PassN-YY-` avant tout dépôt. Les **tirets sont des « tirets du 6 »** (`-`, U+002D HYPHEN-MINUS), pas des cadratins ni des demi-cadratins. À n'oublier sous aucun.
- **R7**{ #r7 } : le suffixe original de l'enregistreur (`PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav`) est **conservé tel quel** après le préfixe.
- **R8**{ #r8 } : toute séquence d'écoute reprend le nom de son enregistrement original source en **insérant un suffixe `_000`, `_001`, …** entre le nom de base et l'extension.

## Copie protégée

- **R9**{ #r9 } : à l'import, l'application **copie systématiquement** les fichiers depuis la carte SD vers son espace de travail. **Aucune écriture sur les originaux** sur la SD. C'est une contrainte explicite du protocole Vigie-Chiro pour éviter toute perte de données.

## Transformation

- **R10**{ #r10 } : la transformation d'un enregistrement original produit des **séquences d'écoute de 5 s ralenties ×10** (expansion de temps). Pour un enregistrement original de durée `D`, on obtient `ceil(D × 10 / 5) = ceil(2 × D)` séquences. La dernière séquence peut être plus courte que 5 s.
- **R11**{ #r11 } : la transformation est **deterministe** : relancer la transformation sur les mêmes enregistrements originaux produit les mêmes séquences d'écoute au bit près.

## Vérification d'enregistrement

- **R12**{ #r12 } : une sélection d'écoute est constituée automatiquement à l'ouverture de la vue, avec la méthode `RéparTemporel` par défaut (séquences réparties uniformément sur la nuit).
- **R13**{ #r13 } : le verdict global est saisi par l'utilisateur après écoute de **tout ou partie** de la sélection. Aucun seuil obligatoire d'écoute (l'utilisateur reste responsable).
- **R14**{ #r14 } : un passage avec verdict `À jeter` ne peut pas être inclus dans un lot prêt à déposer (alerte bloquante).

## Validation taxonomique (SHOULD)

- **R15**{ #r15 } : une observation est qualifiée de **validée** quand `taxon observateur = taxon Tadarida` et `probabilité observateur` renseignée.
- **R16**{ #r16 } : une observation est qualifiée de **corrigée** quand `taxon observateur ≠ taxon Tadarida`.
- **R17**{ #r17 } : une observation **non touchée** par l'utilisateur conserve uniquement les colonnes `tadarida_*`, et l'export `_Vu.csv` reprend ces valeurs (l'utilisateur conserve ainsi la classification automatique par défaut).
- **R18**{ #r18 } : deux **modes de validation** coexistent (au choix de l'utilisateur) :
    - **Mode inventaire** : dès qu'une espèce est validée avec confiance sur une nuit, on ne valide plus les autres détections de la même espèce sur la même nuit.
    - **Mode activité** : toutes les observations doivent être passées en revue (utile pour les études d'activité quantitative).

## Données

- **R19**{ #r19 } : le journal du capteur est un **journal circulaire** sur l'enregistreur (place limitée). En cas de saturation de la SD, des entrées plus anciennes peuvent être effacées. L'application n'a pas à reconstituer les pertes - elle exploite ce qui est présent.
- **R20**{ #r20 } : le relevé climatique peut être **absent** (sonde défaillante ou non installée). Dans ce cas, l'onglet diagnostic du passage le signale clairement plutôt que de masquer la section.

## Arborescence sur disque

- **R21**{ #r21 } : toutes les données et tous les paramètres de l'application vivent dans **un unique dossier « workspace »** sur le disque local de l'utilisateur. Ce workspace contient les sessions d'enregistrement, la base SQLite et tout fichier de réglages utilisateur. Aucune donnée n'est dispersée ailleurs.
    - **Défaut proposé à l'installation** : `<Documents>/VigieChiro-Companion/` (résolu par OS : `~/Documents/VigieChiro-Companion/` sous Linux/macOS, `%USERPROFILE%\Documents\VigieChiro-Companion\` sous Windows).
    - **Configurable depuis les préférences** : utile en particulier pour les gros volumes ([Samuel](../Personas/Samuel.md) : 24 enregistreurs × 40-50 nuits → To cumulés), qui demanderont à pointer un disque externe.
- **R22**{ #r22 } : une session d'enregistrement occupe **un sous-dossier du workspace dont le nom est exactement le préfixe** défini par [R6](#r6) (`Car<carre>-<annee>-Pass<n>-<point>`). Dans ce sous-dossier :
    - les **enregistrements originaux** sont rangés dans `bruts/` (cf. [R7](#r7)) ;
    - les **séquences d'écoute** sont rangées dans `transformes/` (cf. [R8](#r8)) ;
    - le **journal du capteur** (`LogPR<n>.txt`) est à la **racine** de la session d'enregistrement (cf. [C9](C9%20-%20Journal%20du%20capteur.md)) ;
    - le **relevé climatique** (`PaRecPR<sn>_THLog.csv`), si présent, est aussi à la **racine** de la session d'enregistrement (cf. [C10](C10%20-%20Relevé%20climatique.md), [R20](#r20)).
- **R23**{ #r23 } : le **fichier de résultats Tadarida** (`*-observations.csv` ou `*-observations_Vu.csv`) est importé **dans le dossier `transformes/`** de la session d'enregistrement qu'il annote, à côté des séquences d'écoute auxquelles ses observations renvoient.

Arborescence type d'un workspace contenant deux sessions d'enregistrement (un passage 1 et un passage 2 sur le même point) :

```text
<workspace>/                              ← R21 : configurable, défaut <Documents>/VigieChiro-Companion/
├── Car040962-2026-Pass1-A1/              ← R22 : nom = préfixe R6
│   ├── bruts/                            ← R22 : enregistrements originaux R7
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213045.wav
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213101.wav
│   │   └── ...
│   ├── transformes/                      ← R22 : séquences d'écoute R8
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213045_000.wav
│   │   ├── Car040962-2026-Pass1-A1-PaRecPR1925492_20260622_213045_001.wav
│   │   ├── ...
│   │   └── 7a4b8c1d-participation-...-observations.csv     ← R23 : CSV Tadarida
│   ├── LogPR1925492.txt                  ← R22 : journal capteur à la racine
│   └── PaRecPR1925492_THLog.csv          ← R22 : relevé climatique (optionnel)
├── Car040962-2026-Pass2-A1/
│   └── ...                               ← même structure que ci-dessus
└── vigiechiro.db                         ← R21 : BD SQLite + réglages à la racine
```

## Retour

- [⬅ Modèle conceptuel - Vue d'ensemble](index.md)
