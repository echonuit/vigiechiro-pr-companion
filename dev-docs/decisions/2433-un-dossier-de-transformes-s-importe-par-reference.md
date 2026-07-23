# ADR 2433 - Un dossier de transformés déjà présents s'importe par référence, sans brut

- **Statut** : Accepté - 2026-07-23
- **Chantier** : #2258 (l'application cesse de posséder l'audio), lot #2433
- **Vérification** : certaine — `ServiceImportReferenceTest#reference_pose_identite_et_placeholder`
- **Complète** : #2255 (dont la clôture avait replié le versant « import » sur la réactivation) ; prolonge [ADR 0048](0048-l-utilisateur-possede-ses-fichiers-l-app-observe.md)
- **Quatrième couture** : rejoint import groupé, reconstruction par CSV (#1305) et hydratation (#1571) sous le concept unique d'import (#1662)

## Contexte

La demande d'origine du chantier avait deux moitiés : « réactiver une nuit sans rapatrier les fichiers »
**et** « ou j'ai déjà les fichiers transformés dans mon dossier de travail habituel ». La réactivation par
référence (#2255, mode `REFERENCE`) a livré la première. La clôture de #2255 avait acté la seconde comme
« déjà faite », au motif que « la réactivation le fait déjà, il s'agit de rebrancher ».

La cartographie de la clôture de #2258 a montré que c'est vrai **seulement si un passage existe déjà** :

| Situation | Chemin existant |
|---|---|
| Transformés locaux **+ passage en base** | `reactiver --source … --referencer` (voie `TRANSFORMES`) |
| Transformés **+ passage sur Vigie-Chiro** | `reconstruire-passage <idParticipation>` puis `reactiver … --referencer` |
| **Transformés locaux seuls, hors plateforme, sans passage** | *rien* |

La reconstruction (#1305) part d'un `idParticipation` **en ligne** : elle ne sait pas partir d'un dossier
local. Il manquait donc un **premier import** capable de créer un passage à partir d'un dossier de
transformés, sans brut et sans plateforme.

## Décision

Un dossier local de séquences déjà transformées (`×10 + découpées`) s'importe en créant le passage qui les
**référence en place**, sans rejouer la transformation. C'est la seule étape du pipeline d'import
([`MoteurImport`]) qu'on ne joue pas ; tout le reste (construction du graphe passage→session→originaux→
séquences, persistance atomique O7) est réutilisé tel quel.

Deux points structurent la décision :

1. **L'original est un placeholder.** Il n'y a pas de brut derrière ces séquences. Chaque
   `EnregistrementOriginal` est inscrit **sans fréquence d'acquisition** (`frequenceEchantillonnageHz =
   null`), sans SHA-256, sans durée ni taille - le **même marqueur** que la reconstruction (#1305) pose, et
   que la réactivation reconnaît (`ServiceReactivationPassage.sansInventaireExploitable`) pour ne jamais
   tenter de régénérer depuis des bruts absents. Son `cheminFichier` est une sentinelle `NOT NULL` qui
   n'ouvre rien.

2. **L'identité est calculée à l'inscription.** Pour un premier import, il n'y a pas d'empreinte de
   référence à confronter : le geste **établit** la référence. Chaque séquence est donc inscrite avec sa
   taille et son empreinte courte (`Empreintes.empreinteCourte`), calculées à l'inventaire, et sa durée
   réelle (en-tête WAV ÷ 10). Ce sont ces preuves que la **réactivation revérifiera** au réveil, si le
   support référencé s'absente puis revient (garde d'identité #1299/#1309, inchangée).

Le geste offre les **deux modes** de la réactivation : `--referencer` (défaut : copie dans l'espace de
travail, audio possédé) laisse les WAV en place, la base pointe l'emplacement externe et aucun octet audio
n'entre dans l'espace de travail.

## Conséquences

- Le versant « import » du « sans copie » est **fermé** : les trois situations du tableau sont couvertes.
- Parité **CLI + IHM** : commande `importer-transformes` et entrée d'écran équivalente.
- Le geste **dérive** des seuls noms de fichiers ce qu'il peut (série et date via `JournalDeRepli`,
  horodatage via `Prefixe.horodatageDe`) ; le reste (point d'écoute, année, numéro de passage) est fourni
  par l'appelant, comme à l'import ordinaire. Sans nom conforme R6, le rattachement carré/point n'est pas
  déductible : c'est une limite assumée, pas un échec silencieux.
- La réactivation, une fois le passage créé, s'applique **sans changement** : c'est la continuité de la
  garde d'identité qui rend l'ensemble cohérent (E2E référence → sommeil → réveil, #2425).
