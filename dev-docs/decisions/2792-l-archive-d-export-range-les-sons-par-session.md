# ADR 2792 - L'archive d'export range les sons par session, le CSV fait foi

- **Statut** : Accepté - 2026-07-30
- **Chantier** : #2792 (lot A de l'EPIC #2790)
- **Vérification** : certaine - `ExportObservationsEtSonsTest#nominal_avec_deduplication`

## Contexte

L'export « observations + sons » (EPIC #2790) produit une archive destinée à **quitter
l'application** : un expert l'ouvrira avec un explorateur de fichiers et un lecteur audio, un script
la traitera peut-être. Sa structure est donc un **contrat** : une fois des archives en circulation,
la changer casserait les habitudes et les scripts des destinataires. Elle mérite d'être actée, pas
improvisée par l'implémentation.

Deux contraintes la façonnent. Les noms de fichiers son ne sont **uniques que dans leur nuit** (deux
sessions produisent chacune un `..._000.wav`) ; et une observation n'est pas un fichier : plusieurs
observations partagent une séquence, un son peut avoir disparu du disque (nuit archivée, support
débranché).

## Décision

### 1. `observations.csv` à la racine, octet-identique à l'export CSV seul

L'archive contient un unique CSV, produit par le **même formateur** que « ☰ → Exporter les
observations (CSV) » (`ExportObservationsCsv`) : mêmes colonnes, même encodage UTF-8 avec BOM, même
séparateur. Un destinataire qui connaît l'un connaît l'autre, et aucun second format n'est à
maintenir.

### 2. Les sons sous `sons/<dossier-session>/<fichier>`, dédupliqués

Un sous-dossier par **session d'enregistrement** (le nom du dossier de la nuit, déjà parlant :
`Car130711-2026-Pass1-Z41`) : c'est ce qui rend l'archive multi-nuits possible sans collision de
noms. Deux sessions aux dossiers **homonymes** (disques différents) sont départagées par un suffixe
`-s<id>`, posé **seulement** en cas de collision. Une séquence partagée par plusieurs observations
n'est emballée qu'une fois : l'archive est un ensemble de **fichiers**, le CSV porte la
multiplicité.

### 3. Un son introuvable est compté, jamais bloquant

L'observation reste dans le CSV (la donnée existe), le fichier est simplement absent de `sons/` et le
**compte rendu le chiffre**. Refuser l'export entier parce qu'une nuit est archivée rendrait
l'archive inutilisable précisément dans le cas réel (sur le jeu de recette : 721 sons présents, 615
partis avec leur disque).

### 4. Ni manifeste supplémentaire, ni compression paramétrable

Pas de `LISEZMOI.txt` ni de JSON d'accompagnement : le CSV **est** le manifeste (il nomme chaque
fichier, session comprise). Pas d'option de compression : le WAV se comprime mal, le défaut de
`EcrivainZip` suffit et n'expose aucun choix à l'utilisateur.

## Conséquences

- La CLI `exporter-sons` (#2795) produit **la même archive** : la structure est un contrat partagé
  par les deux surfaces, pas un détail de la vue audio.
- L'écrivain sous-jacent est le patron socle `EcrivainZip`
  ([patterns](../patterns.md#ecrivain-zip-generaliste-socle-commun)) : annulation sans archive
  partielle, mémoire bornée.
- Tout futur export « assemblé » (rapport + pièces) a un précédent à suivre : contenu en mémoire à la
  racine, fichiers rangés par leur unité d'origine.

## Alternatives écartées

- **Sons rangés par espèce** (patron de l'export bibliothèque, P10) : ici le CSV porte déjà
  l'espèce, et une séquence peut porter **plusieurs** espèces - le rangement par espèce dupliquerait
  des fichiers ou trancherait arbitrairement.
- **Un dossier plutôt qu'un ZIP** : l'archive est faite pour être **envoyée** ; un dossier de 700 Mo
  se zippe de toute façon, autant produire directement le fichier qui part.
- **Suffixer toutes les sessions par leur identifiant** : lisible par personne hors de
  l'application ; le suffixe n'apparaît qu'en cas de collision réelle, jamais par précaution.
