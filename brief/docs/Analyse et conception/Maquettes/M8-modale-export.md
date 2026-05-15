# M8 - Modale d'export VigieChiro

> **Type** : modale, déclenchée depuis [M2 - Détail session](M2-detail-session.md)
> **Parcours couverts** : [P4 Export VigieChiro](../Parcours%20utilisateurs.md#p4-export-vigiechiro)
> **Stories couvertes** : [E5.S1 Exporter le CSV](../Story%20mapping.md#e5s1-exporter-le-csv-de-validation-3-pts), [E5.S2 Récapitulatif d'export](../Story%20mapping.md#e5s2-recapitulatif-dexport-2-pts) (SHOULD), [E5.S3 Marquer comme exportée](../Story%20mapping.md#e5s3-marquer-la-session-comme-exportee-3-pts)

## Étape 1 - Vérification pré-export

```
┌──────────────────────────────────────────────────────────────────────┐
│  📤 Exporter pour VigieChiro                                      ✕  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Session : du 22/04/2026 - PR n° 1925492                             │
│                                                                      │
│  Bilan de validation :                                               │
│   ✅ Validées (Tadarida correct)         1 247                       │
│   🔄 Corrigées (autre taxon)               22                        │
│   ●  Non passées en revue              2 762  ⚠️                     │
│                                        ─────                         │
│   Total observations                   4 031                         │
│                                                                      │
│  ⚠️ Il reste 2 762 observations non passées en revue.                │
│     Si vous exportez maintenant, ces observations garderont la       │
│     classification Tadarida par défaut.                              │
│                                                                      │
│   ( ) Les laisser au taxon Tadarida (recommandé pour ne rien perdre) │
│   (●) Continuer la validation avant l'export                         │
│                                                                      │
│                          [Annuler]    [⟶ Valider d'abord]            │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 1 bis - Validation 100 % (passage direct à étape 2)

```
┌──────────────────────────────────────────────────────────────────────┐
│  📤 Exporter pour VigieChiro                                      ✕  │
├──────────────────────────────────────────────────────────────────────┤
│  ✅ Toutes les observations ont été passées en revue.                │
│                                                                      │
│  Bilan :                                                             │
│   • 4 009 validées (Tadarida correct)                                │
│   •    22 corrigées (autre taxon)                                    │
│                                                                      │
│  Destination de l'export :                                           │
│  /home/marie/PR_avril/22-04/8a4fa…-observations_Vu.csv               │
│                                          [Modifier...]               │
│                                                                      │
│                              [Annuler]    [📤 Exporter maintenant]   │
└──────────────────────────────────────────────────────────────────────┘
```

## Étape 2 - Récapitulatif post-export (E5.S2 SHOULD)

```
┌──────────────────────────────────────────────────────────────────────┐
│  ✅ Export réussi                                                     │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   📤 Fichier produit                                                 │
│      8a4fa…-observations_Vu.csv (912 Ko)                             │
│      /home/marie/PR_avril/22-04/                                     │
│                                                                      │
│   Récapitulatif :                                                    │
│   • 4 031 observations exportées                                     │
│   •   1 247 validées par vous                                        │
│   •      22 corrigées par vous                                       │
│   •   2 762 laissées au taxon Tadarida (probabilité Tadarida initiale)│
│   •      18 avec commentaire libre                                   │
│                                                                      │
│   Prochaine étape : connectez-vous à VigieChiro et téléversez le     │
│   fichier `_Vu.csv`.                                                 │
│                                                                      │
│   [📂 Ouvrir le dossier d'export]   [🌐 Aller sur VigieChiro]   [Fermer]│
└──────────────────────────────────────────────────────────────────────┘
```

## Composants & comportements

| Étape | Composant | Notes |
|---|---|---|
| 1 | Bilan ventilé en 3 catégories | Couleurs : vert validées, bleu corrigées, gris « non passées » |
| 1 | Encart d'avertissement | Visible uniquement si `non passées > 0` |
| 1 | Choix radio | Par défaut : `Continuer la validation` (le plus prudent). Marie peut basculer si elle est consciente |
| 1bis | Affichage du chemin de destination | Pré-rempli au dossier source de la session, dans un sous-dossier ou à la racine selon préférence |
| 1bis | Bouton `[Modifier...]` | Ouvre un sélecteur de dossier OS |
| 2 | Récapitulatif détaillé | Bilan complet, sans cacher d'information |
| 2 | Bouton `[📂 Ouvrir le dossier d'export]` | Ouvre l'explorateur OS sur le dossier produit |
| 2 | Bouton `[🌐 Aller sur VigieChiro]` | Ouvre `https://www.vigienature.fr/fr/chauves-souris` dans le navigateur par défaut. Ne se substitue pas à l'authentification utilisateur |

## Format du CSV produit

Le fichier respecte le format `_Vu.csv` attendu par VigieChiro :

- séparateur `;`
- pas de guillemets autour des champs (cf. exemple [`samples/kal/…_Vu.csv`](https://github.com/IUTInfoAix-S201/brief/tree/main/samples/kal))
- valeurs vides codées `""""` (4 guillemets - encodage spécifique au format `_Vu`)
- encodage UTF-8
- en-tête identique à la version `observations.csv`
- une ligne par observation, dans le même ordre que le CSV d'entrée

Pour chaque ligne :
- `nom du fichier`, `temps_debut`, `temps_fin`, `frequence_mediane`, `tadarida_taxon`, `tadarida_probabilite`, `tadarida_taxon_autre` : reprises telles quelles
- `observateur_taxon`, `observateur_probabilite` : remplis avec la décision utilisateur (vide / `tadarida_taxon` / autre taxon)
- `validateur_taxon`, `validateur_probabilite` : laissés vides (réservés à l'étape suivante côté VigieChiro)

## Erreurs possibles

| Erreur | Affichage |
|---|---|
| Dossier de destination en lecture seule | « ❌ Impossible d'écrire dans ce dossier. Choisissez un autre emplacement. » |
| Fichier `_Vu.csv` déjà présent | « ⚠️ Un fichier `_Vu.csv` existe déjà à cet emplacement. [Écraser] [Choisir un autre nom] » |
| Disque plein | « ❌ Espace disque insuffisant. » |

## Statut de la session après export

- Statut passe à `📤 Exportée` (E5.S3).
- Date+heure et chemin de l'export tracés en base et visibles dans [M2](M2-detail-session.md).
- Si l'utilisateur modifie une observation après l'export, un bandeau apparaît dans M2 : « ⚠️ Modifications non exportées. [Ré-exporter] ».

## À ne PAS faire

- Pas d'export silencieux : toujours afficher le recapitulatif (même si la session est validée à 100 %).
- Pas de cocher « Ne plus me demander » sur l'avertissement « obs non validées » : c'est un garde-fou intentionnel.
- Pas de produire un format différent en mode bonus (ex. JSON) : la sortie de référence est le `_Vu.csv` réinjectable par VigieChiro, point.
