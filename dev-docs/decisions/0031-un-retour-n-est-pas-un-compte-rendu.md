# ADR 0031 — Un retour d'opération n'est pas un compte rendu : le mot « compte rendu » se libère pour l'extensible

- **Statut** : Accepté — 2026-07-19
- **Chantier** : EPIC #1990 (#2000)
- **Vérification** : humaine — distinguer un retour d'opération d'un compte rendu tient au sens du message, non à sa forme
- **Amende** : [ADR 0023](0023-rendre-compte-bandeau-par-defaut-modal-si-irreversible.md) et [ADR 0028](0028-un-etat-n-est-pas-un-compte-rendu.md), sur le **vocabulaire** ; leurs décisions restent entières.

## Contexte

L'[ADR 0028](0028-un-etat-n-est-pas-un-compte-rendu.md) a séparé l'**état** (ce qui *est*, se repose à chaque chargement, ne se ferme pas) de ce qu'elle nomme **compte rendu** (ce qui *vient de se passer*, porte une sévérité, se ferme). Cette séparation tient et n'est pas remise en cause.

Mais elle laisse une confusion en amont, apparue en voulant décider si la modale de réactivation devait migrer au bandeau (#1964). Sous le même mot cohabitent **deux natures** :

| | Ce que ça dit | Forme |
|---|---|---|
| « Passage enregistré. » · « 3 point(s) rapatrié(s). » | un fait accompli, clos | **bornée** |
| « 4229 séquences réactivées (identité vérifiée : forte). 7 restent introuvables. • X.wav : enregistrement absent du dossier (6 séquences) • Y.wav : tranche non régénérée. L'audio reste incomplet : 4229 sur 4236. » | ce qui est revenu et sur quelle preuve, ce qui a été refusé et pourquoi, ce qui manque et de quel manque il s'agit | **extensible** |

Le second a gagné **deux sections en une seule session** (les absences nommées avec leur motif, le compte rendu de rapatriement d'ancrage). Il n'a pas de type, pas de socle, et quatre implémentations maison qui ne se ressemblent pas.

**Le code avait déjà tranché sans qu'on le remarque** : le type du premier s'appelle `RetourOperation`, pas `CompteRenduOperation`. Et l'ADR 0028 écrit elle-même « un canal s'appelle `retour` ».

## Décision

**1. Trois natures, trois mots.**

| Nature | Mot | Type | Canal |
|---|---|---|---|
| ce qui **est** | **état** | propre à l'écran | ne se ferme pas, se repose au chargement |
| ce qui vient de se passer, **borné** | **retour d'opération** | `RetourOperation` | `BandeauRetour` |
| ce qui vient de se passer, **extensible** | **compte rendu** | *(à créer, #2001)* | sa propre surface, **jamais le bandeau** |

Les décisions de 0023 et 0028 restent valides : là où elles écrivent « compte rendu », lire **retour d'opération**.

**2. Le critère est l'extensibilité, pas la longueur.** Un message qui concatène une partie de longueur variable est un compte rendu, quelle que soit sa taille du jour. Le choix du véhicule se fait **une fois**, à la conception ; le contenu grandit ensuite.

**3. Un compte rendu ne va jamais au bandeau.** Le bandeau est une ligne : y loger un compte rendu revient à le tronquer, donc à retirer ce qui permet à l'utilisateur de savoir quoi faire. Un compte rendu qui atterrit dans un bandeau est **mal routé**, pas mal formaté.

**4. Un compte rendu se reconnaît au premier coup d'œil.** Il a **un** composant, identifiable comme tel partout où il apparaît. Un observateur qui enchaîne un import, un dépôt et une réactivation doit reconnaître la **structure** avant même de lire — c'est ce que quatre implémentations maison lui refusent aujourd'hui.

**5. Un compte rendu est structuré, pas assemblé.** Il porte des **rubriques** nommées, pas une chaîne construite au `StringBuilder`. Le rapport d'inspection de l'import (`InspectionImportViewModel`) le fait déjà à la main : `resumeJournal`, `avertissementMelange`, `avertissementIncoherence`, `avertissementNuitExistante`, `messageErreur`.

Ce que les rubriques débloquent et que le texte assemblé interdit : styler une section, en masquer une, l'indenter (#1987 est **directement causée** par le `Label` unique de la réactivation), et **tester une rubrique** sans chercher une sous-chaîne dans un pavé.

## Conséquences

**L'audit du vocabulaire montre que la règle de 0028 n'est pas encore tenue.** Sur les canaux de retour des ViewModels :

| Nom | ViewModels | Verdict |
|---|---|---|
| `retour` | 13 | conforme |
| `message` | 4 | à renommer |
| `messageErreur` | 2 — `InspectionImportViewModel`, `ImportationViewModel` | **interdit** par 0028 règle 3 : la sévérité dans le nom |
| `resume`, `recap`, `bilan` | 6 — `AnalyseViewModel`, `AuditViewModel`, `LotViewModel`, `ReconstructionViewModel`, `MultisiteViewModel`, `RattachementViewModel` | à classer : retour, compte rendu ou état ? |

Une ADR acceptée n'est pas une règle appliquée. Chacun de ces canaux doit être **classé** avant d'être renommé — c'est un jugement par cas, pas une substitution.

**`RattachementViewModel` est le cas à trancher en premier** : il construit un `CompteRenduEnvoi` puis le publie **au bandeau**. Soit c'est un retour et son nom trompe, soit c'est un compte rendu et son routage est faux (#2003).

**Les frontaliers existent déjà.** `RecapImport` concatène `avertissements()`, de longueur variable, à une phrase bornée — dans la *vue*, donc intestable. Traité par le sous-EPIC #2004.

Purement présentationnel côté IHM, mais **pas sans objet côté CLI** ([ADR 0014](0014-parite-cli-ihm.md)) : `reactiver` rend le même compte rendu sans plafond d'affichage, et la migration ne doit pas désolidariser les deux surfaces.

## Alternatives écartées

**Garder « compte rendu » pour le borné et inventer un mot pour l'extensible.** C'eût évité de retoucher deux ADR acceptées. Mais le type s'appelle déjà `RetourOperation` : le vocabulaire aurait continué de diverger du code, et c'est précisément ce qui a produit #1964.

**Étendre `RetourOperation` avec des rubriques optionnelles.** Un seul type pour les deux natures, donc un seul canal, donc le bandeau par défaut — et la troncature silencieuse revient par la porte de derrière.
