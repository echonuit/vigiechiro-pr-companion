# Fixtures : le générateur de cartes SD

Les cartes SD de recette (arbres `LogPR<serie>.txt` + `PaRecPR<serie>_THLog.csv` + `bruts/*.wav`)
pesaient autrefois plusieurs centaines de méga-octets et étaient **faites à la main** : impossibles à
committer (binaire lourd, dépôt public et forké) et **non rejouables** (rien ne garantissait de les
reconstruire à l'identique).

On les décrit désormais par des **specs déclaratives** de quelques kilo-octets, sous
`recette/fixtures/spec/`, qu'un **générateur déterministe** matérialise sur disque. La spec est la
source de vérité ; l'arbre SD n'en est qu'un artefact reconstructible **octet pour octet** : le
générateur ne tire **aucune** date de l'horloge ni **aucun** octet aléatoire.

Le générateur vit en **portée test** (`src/test/java/fr/univ_amu/iut/recette/`) : l'application
distribuée n'embarque pas la fabrique de fixtures ; ce sont les tests qui fabriquent WAV/ZIP.

## Le format de spec

Une spec est un fichier YAML. Tous les champs ont des valeurs par défaut raisonnables, donc une spec
reste concise. Exemple commenté (le cas propre) :

```yaml
fixture: sd-nominale            # nom de la carte (= nom du sous-dossier généré)
but: "Cas propre : 6 wav, un seul enregistreur, une seule nuit."

journal:                        # journal du capteur LogPR<serie>.txt
  present: true                 # false -> aucun journal (mode dégradé)
  serie: "1925492"              # n° de série déclaré (nomme aussi le fichier)
  nuit: "2026-04-22"            # date de la 1re ligne (fixe dateDebut pour l'analyseur)
  sondePresente: true           # ajoute la ligne « Sonde ... présente »
  corrompu: false               # true -> journal illisible (aucune série extractible)

thlog:                          # relevé climatique PaRecPR<serie>_THLog.csv
  present: true
  mesures: 6                    # nombre de lignes de mesures déterministes

wav:                            # paramètres communs des WAV (RIFF mono 16 bits valide)
  frequenceHz: 384000           # inscrite dans l'en-tête ; divisible par 10 (règle R10)
  dureeSecondes: 1.5            # courte, pour des fixtures légères

zip: true                       # produit aussi sd-nominale.zip (chemin de décompression)

enregistreurs:                  # un ou plusieurs enregistreurs présents dans bruts/
  - serie: "1925492"
    horodatages:                # PaRecPR<serie>_<yyyyMMdd>_<HHmmss>.wav
      - "20260422_203922"
      - "20260422_210515"
    # fauxWav: ["20260422_211500"]   # octets non-WAV : rejetés à l'import

attendu:                        # contrat de recette (voir « Le garde-fou »)
  aJournal: true
  aReleve: true
  journalLisible: true          # false -> l'inspection doit échouer
  plusieursEnregistreurs: false # bandeau « mélange »
  incoherent: false             # bandeau « incohérence »
  nuits: 1                      # nombre de nuits détectées
  etatNommage: "BRUT"           # BRUT, PREFIXE ou VIDE
  rejets: 0                     # > 0 déclenche la vérification d'import réel
```

Deux variantes utiles :

- **Préfixe** (carte déjà nommée en session) : un bloc `prefixe:` ajoute le préfixe R6
  `Car<carre>-<annee>-Pass<passage>-<point>-` devant chaque nom de brut.

    ```yaml
    prefixe:
      carre: "130711"
      annee: 2026
      passage: 1
      point: "Z1"
    ```

- **Horodatages génératifs** (grosses cartes) : au lieu de lister chaque fichier, on décrit une série
  déterministe.

    ```yaml
    enregistreurs:
      - serie: "1925492"
        horodatages:
          debut: "20260422_203000"
          nombre: 60
          intervalleSecondes: 300
    ```

## Les 9 cartes de recette

Chaque carte exerce **une** pathologie de l'assistant d'import (voir l'étape 5 de
[S2 · Importer une nuit](sessions/s2-importer.md)).

| Carte | Ce qu'elle exerce | wav |
|---|---|---|
| `sd-nominale` (+ `.zip`) | cas propre ; l'archive exerce le chemin de décompression | 6 |
| `sd-melange` | deux enregistreurs dans le même dossier -> bandeau « mélange » | 6 |
| `sd-incoherente` | journal et wav en désaccord (série + date) -> bandeau « incohérence » | 3 |
| `sd-multi-nuits` | trois nuits sous un journal unique -> table des nuits | 6 |
| `sd-sans-journal` | aucun `LogPR` -> mode dégradé (import possible sans journal) | 3 |
| `sd-journal-corrompu` | `LogPR` illisible -> l'inspection échoue avec un message clair | 3 |
| `sd-prefixee` | bruts déjà préfixés `Car...` -> état de nommage `PREFIXE` | 3 |
| `sd-rejets` | un faux wav parmi huit valides -> l'import aboutit, zone des rejets | 9 |
| `sd-grosse` | soixante wav -> test de charge (progression, parallélisme, disque) | 60 |

## Régénérer les cartes

Le générateur ne fait pas partie du binaire ; on le lance en portée test via un goal de confort
(non lié à une phase, jamais exécuté en CI ni au packaging) :

```bash
env -u DISPLAY ./mvnw -q test-compile exec:java@generer-sd \
  -Dexec.args="recette/fixtures/spec /chemin/vers/dest"
```

Chaque carte est écrite dans `dest/<fixture>/` (et `dest/<fixture>.zip` si la spec le demande). Deux
exécutions produisent des octets **identiques**. Passer une seule spec au lieu du dossier ne génère
que cette carte.

## Le garde-fou

Le bloc `attendu` de chaque spec est un **contrat** vérifié contre le **code réel**, pas contre une
liste tenue à la main. Deux tests (esprit cliquet, sans JavaFX) amarrent le générateur à la réalité :

1. `GenerationCartesSDCliquetTest` : pour **chaque** spec, génère la carte puis vérifie que
   l'**inspection réelle** (`InspecteurDossier`) constate la pathologie déclarée (mélange, incohérence,
   nombre de nuits, état de nommage, nombre d'originaux, journal illisible), plus le **round-trip** du
   zip via `ExtracteurZip`.
2. `GenerationCartesSDImportCliquetTest` : pour toute spec à rejets (`attendu.rejets > 0`), lance un
   **import réel** headless (`ServiceImport` sur base SQLite jetable) et vérifie `compte(REJETE)`.
   C'est le seul cas de recette qui se joue à l'import et non à la seule inspection.

Ces tests deviennent rouges si le générateur cesse de produire la bonne carte **ou** si un détecteur
d'import change de comportement (`AnalyseMelange`, `AnalyseCoherence`, `PartitionNuits`,
`AnalyseurLogPR`, `ServiceImport`, `ExtracteurZip`).

## Où ça vit

- `recette/fixtures/spec/*.yaml` : les specs (source de vérité, versionnées).
- `src/test/java/fr/univ_amu/iut/recette/` : le générateur (`GenerateurCartesSD`, `FabriqueWav`,
  `LecteurSpec`, `SpecCarteSd`) et les deux garde-fous.
- Le rejeu déterministe complet (générer -> importer via la [CLI](../cli.md) -> comparer à un golden)
  prolonge ce socle : voir la section « Rejouer une campagne » de la [méthode](index.md).

La décision de conception (specs déclaratives + générateur déterministe, portée test) est consignée dans
l'[ADR 0015](../decisions/0015-generateur-deterministe-cartes-sd-recette.md).
