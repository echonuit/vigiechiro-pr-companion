# ADR 3840 - Le signal couvre ce qui change sans qu'on parte ; le retour couvre le reste

- **Statut** : Accepté - 2026-08-16
- **Chantier** : #3840, trouvaille consignée pendant #3645
- **Amende** : [ADR 3537](3537-un-signal-se-pose-a-l-ecriture.md)
- **Vérification** : certaine - `ParcoursAnalyseVersValidationE2ETest#un_taxon_remplace_est_relu_au_retour`

Vue rouge en neutralisant `rafraichirAuRetour()` sur `AnalyseController` : l'écran reste sur
`["Nyclei", "Pippip"]`, c'est-à-dire exactement à la dernière écriture **qui a annoncé**, et manque le
remplacement qui a suivi.

## Contexte

L'[ADR 3537](3537-un-signal-se-pose-a-l-ecriture.md) pose que toute écriture **structurelle** validée -
celle qui change l'un des quatre comptes de l'accueil - appelle `mutationStructurelleValidee()`.

Ce critère laisse dehors les `update` : remplacer le taxon d'une observation ne change aucun compte.
`ValidationManuelle.valider` retourne d'ailleurs avant l'annonce quand une observation manuelle existe
déjà.

**Or l'écran Analyse affiche ce taxon.** Il lit le taxon **retenu**, `COALESCE(taxon_observer,
taxon_tadarida)` (`ServiceActivite:105`) : le taxon de l'observateur prime, et le remplacer change ce
qui est à l'écran. Le critère « les quatre comptes » ne suffit donc pas à expliquer pourquoi le produit
reste juste.

## La décision

**Il ne suffit pas, et il n'a pas à suffire : la fraîcheur est portée par deux mécanismes, pas un.**

| Mécanisme | Couvre | Porté par |
|---|---|---|
| `SuitLaRevision` | ce qui change **pendant** qu'on regarde l'écran | 5 écrans : Analyse, Saison, Multisite, Site-detail, Passage |
| `RafraichirAuRetour` | ce qui a changé **parce qu'on était ailleurs** | dont Analyse |

La vue audio - seul endroit du produit où l'on remplace un taxon - **empile** (`NavigationAudio:56`).
Valider un taxon depuis Analyse, c'est donc quitter Analyse, et le retour la recharge.

**Le signal reste donc réservé aux écritures structurelles.** L'élargir aux `update` ferait relire cinq
écrans pour un changement qu'aucun compte ne reflète, et rendrait à `RevisionDonnees` le rôle de
notificateur général que l'ADR 3537 lui a refusé.

Corollaire à retenir avant de « simplifier » : `SuitLaRevision` et `RafraichirAuRetour` ne sont **pas**
redondants. Retirer le second d'un écran qui porte déjà le premier casse silencieusement la fraîcheur -
c'est la mutation par laquelle cette décision est vérifiée.

## Ce que la mesure a corrigé, et qui vaut mieux que la conclusion

**Ma première version de ce test ne prouvait rien**, et elle est restée verte en retirant
`RafraichirAuRetour`.

Elle validait deux fois de suite. Or la **première** validation *insère* une observation manuelle - une
observation manuelle est celle dont `results_id` est nul, et la séquence semée en portait un - donc elle
**annonce**. Analyse, toujours dans l'historique et toujours abonnée, se rechargeait alors en tâche de
fond, et ce rechargement asynchrone lisait la base **après** le second geste. Le remplacement se faisait
donc relire par l'annonce du voisin.

Une attente explicite entre les deux gestes rétablit ce qu'on croyait mesurer : le rechargement annoncé
doit avoir **abouti** avant que le remplacement silencieux n'ait lieu.

C'est le motif de l'[ADR 3624](3624-un-fait-que-rien-ne-peut-faire-rougir-s-ancre-autrement.md) sous une
forme nouvelle : non pas un fait que rien ne peut faire rougir, mais un fait tenu par **un autre
dispositif que celui qu'on croit**. Les deux se démasquent de la même façon - en mutant ce qu'on prétend
vérifier.

## Conséquences

Aucun changement au signal. Un test de parcours tient désormais la promesse, et l'ADR 3537 est amendée :
son critère décrit ce que le **signal** couvre, pas ce que le **produit** garantit.

⚠️ Ce qui n'est pas couvert, et qui n'est pas atteignable aujourd'hui : un remplacement de taxon
survenant **pendant** qu'un écran suiveur est affiché. Tous les chemins d'`update` partent de la CLI -
un autre processus - ou du modèle de vue audio, qui est l'écran affiché quand il s'exécute. Si un
traitement de fond venait à en remplacer un, la décision se rouvrirait.

## Amendement (#3964, clôture du lot #3900) : le contrat s'ajoute sur mesure, jamais par symétrie

Le relevé exhaustif des trente contrôleurs a rendu **trois** écrans sans aucun mécanisme de fraîcheur,
et l'ADR ci-dessus donnait le critère pour trancher chacun.

| Écran | Verdict, après mesure |
|---|---|
| `Synthese` | il agrège des observations **et ouvre lui-même** la fiche du passage, d'où la validation est atteignable : `RafraichirAuRetour` |
| `Activite` | même donnée, même chemin : `RafraichirAuRetour` |
| `Audit` | il audite la base, que tout écran qui écrit périme : `RafraichirAuRetour` |
| `Importation` | **laissé tel quel** : il porte `AuDepartEcran`, donc il se recharge déjà |

⚠️ **`Importation` est le point de cet amendement.** Ajouté « par symétrie », le contrat l'aurait fait
relire deux fois à chaque entrée, sans qu'aucun test ne s'en plaigne. C'est en ouvrant le fichier
qu'on voit qu'un autre mécanisme le couvre.

### Un écran à deux portes retient laquelle a servi

`Activite` s'ouvre sur **un** passage, ou sur **tous** ceux d'un utilisateur, et ne retenait que le
premier cas. Relire par la mauvaise porte afficherait le mauvais périmètre - **pire que ne pas
relire**, parce que l'écran aurait alors l'air frais.

### Le prix payé ailleurs

Ajouter le contrat portait `ActiviteController` à WMC=49, au-dessus du plafond God-class du portail
qualité. `//NOPMD` étant exclu par convention, le dessin d'une nuit - repère nocturne, séries,
infobulles - est parti dans `CourbesActivite`. Il n'avait déjà plus sa place dans l'écran :
`versSeries` était appelée par `ExportImageActivite`, `texteInfobulle` par son test. **Un morceau
qu'on appelle du dehors n'appartient pas à l'écran qui le contenait.**

⚠️ En le déplaçant, j'ai d'abord réécrit `minutesDepuis18h` de mémoire au lieu de le reprendre tel
quel - exactement la divergence contre laquelle le doc-comment de la nouvelle classe met en garde.
Un déplacement se fait par copie littérale, puis vérification.
