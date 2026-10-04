## Context

La carte « Traitement Vigie-Chiro » de l'écran de lot est câblée par `SuiviTraitementUI`, qui délègue à `TraitementViewModel`. « Actualiser » lance `viewModel.relever(passage)` hors du fil JavaFX par `ExecuteurTache`, puis `viewModel.appliquer(traitement)` sur le fil. Le même relevé part aussi juste après un lancement réussi. À l'ouverture, `chargerDernierReleve` affiche le cache, sans réseau.

L'import des observations vit dans `validation` (`ImportVigieChiro`). Le socle expose déjà un port, `commun.model.ImportObservations`, créé pour que `passage` déclenche l'import sans dépendre de `validation`. Il sait importer, pas dire si une nuit a déjà ses observations. L'action groupée `ImportResultatsGroupe`, elle, le sait par `ResultatsIdentificationDao`, mais elle vit dans `validation`.

Côté ligne de commande, `EtatTraitementVigieChiro` porte le marqueur `LectureSeule`. `StrategieExecutionCli` s'en sert pour dispenser la commande du verrou du dossier de travail, que l'application graphique tient pendant toute sa durée. Le marqueur est porté par la classe, pas par l'invocation.

Trois demandes ont retouché cette carte cette semaine (#5764, #5788, #5799), et le scénario filmé S4-47 la lit par ses identifiants.

## Goals / Non-Goals

**Goals:**
- Un « Actualiser » qui révèle une analyse terminée laisse les observations en base, et la carte le dit.
- Jamais de réimport silencieux, jamais de remplacement depuis cette carte.
- Un échec d'import lisible à côté d'un état d'analyse resté juste.
- La même capacité en ligne de commande, sans retirer à la commande sa lecture seule par défaut.

**Non-Goals:**
- Sonder la plateforme, ou importer à l'ouverture de l'écran.
- Remplacer des observations déjà importées : c'est un geste de « Sons & validation », nuit par nuit.
- Toucher à `ImportVigieChiro`, à l'écran « Sons & validation », ou à l'action groupée.
- Renommer un identifiant ou un libellé de la carte.

## Decisions

**D1. Le déclencheur est le relevé réseau, pas l'état affiché.** L'import part dans la tâche qui vient de relever, quand ce relevé rend `resultatsDisponibles()`. Il part donc au clic sur « Actualiser », et aussi sur le relevé automatique qui suit un lancement, si la plateforme a déjà fini. `chargerDernierReleve` n'importe jamais. Écarté : importer à l'ouverture sur un cache « terminée », qui ferait du réseau sans geste.

**D2. Le relevé et l'import sont une seule tâche de fond.** `TraitementViewModel` gagne une opération bloquante qui relève puis, si besoin, importe, et rend un résultat à deux volets : le traitement, et l'issue de l'import (aucun, fait avec son compte rendu, déjà là, échoué avec son motif). `appliquer` restitue les deux sur le fil JavaFX. Le bouton reste « Relevé en cours… » pendant tout le geste. Écarté : deux tâches enchaînées, qui laisseraient un instant où le bouton est libre et l'import encore en vol.

**D3. Le port du socle gagne une lecture.** `ImportObservations.aDejaSesObservations(Long idPassage)`, implémentée dans `validation` par le même `ResultatsIdentificationDao` que l'action groupée. C'est une lecture locale. Écarté : appeler `importer(id, false)` et interpréter son refus, qui ferait dépendre un comportement d'un texte d'erreur.

**D4. L'import est optionnel, comme le suivi.** `TraitementViewModel` reçoit `Optional<ImportObservations>`. Absent (outils de capture, hors connexion), la carte relève sans importer et ne montre aucune ligne d'import.

**D5. Une ligne de plus dans la carte, sous l'état.** Un `Label` neuf, `#lblImportTraitement`, absent tant qu'il est vide. L'état reste dans `#lblEtatTraitement`, l'alerte de délai dans `#lblAlerteTraitement`. Un échec d'import va dans la ligne d'import, pas dans l'alerte, qui parle du calcul.

**D6. Les textes**, à valider par le porteur avant le code :

| Situation | État (`#lblEtatTraitement`) | Ligne d'import (`#lblImportTraitement`) |
|---|---|---|
| Terminée, import fait | « Analyse terminée le 3 octobre à 16 h 07. » | le compte rendu de l'import, tel que « Sons & validation » le donne : « Observations importées depuis Vigie-Chiro : 1 284 observation(s). » |
| Terminée, déjà importée | idem | « Les observations de cette nuit sont déjà importées. Pour les remplacer, passez par « Sons & validation ». » |
| Terminée, import en échec | idem | « L'import des observations a échoué : <motif>. Cliquez de nouveau « Actualiser », ou importez depuis « Sons & validation ». » |
| Terminée, lue du cache à l'ouverture | idem | « Cliquez « Actualiser » pour importer les observations. » si la nuit n'en a pas ; la phrase « déjà importées » sinon |

L'état perd donc sa fin actuelle, « : les observations sont prêtes à être importées », que la ligne d'import remplace dans les quatre cas. La lecture « a déjà ses observations » est locale : l'ouverture ne fait toujours aucun réseau.

**D7. La ligne de commande : une option, et un verrou qui suit l'invocation.** `--importer` fait le geste de D1 à D3. `LectureSeule` gagne une méthode par défaut, `neFaitQueLire()`, vraie ; `EtatTraitementVigieChiro` la redéfinit en « sans `--importer` ». `StrategieExecutionCli` dispense du verrou une commande `LectureSeule` qui répond vrai. Écarté : retirer le marqueur, ce qui ferait refuser le simple relevé dès que l'application graphique est ouverte ; et une commande séparée, que le porteur a écartée pour l'option.

**D8. Les codes de retour.** Inchangés pour l'état. Avec `--importer` : `0` si l'analyse est terminée et que les observations sont là en sortant (importées ou déjà présentes), `2` si l'import demandé n'a pas pu se faire, et les codes d'état habituels (`3`, `1`, `4`) quand il n'y avait rien à importer.

## Risks / Trade-offs

- **Un import long bloque « Actualiser »** → c'est voulu (D2) ; le bouton dit « Relevé en cours… ». Si la recette montre que ce libellé trompe pendant un import de plusieurs dizaines de pages, un second libellé se pose en suite.
- **`ScenarioConnecteLancementTest` (S4-47) lit la carte et n'est joué par aucune CI de demande** → aucun identifiant ni libellé qu'il lit ne change, et la session qui le porte le rejoue sur la branche avant la fusion.
- **Le scénario de #5796 importe depuis « Sons & validation »** → rien n'y change ; mais une nuit passée d'abord par la carte y arrivera déjà importée.
- **`LectureSeule` devient une réponse et non plus un simple marqueur** → `ClassementLectureEcritureTest` compte les classes qui le portent : le compte ne bouge pas, et un cas neuf tient les deux sens de la réponse.

## Open Questions

- Les textes de D6 sont à valider par le porteur.
- Pendant l'import, le bouton garde « Relevé en cours… ». Le porteur préfère-t-il « Import en cours… » dès que le relevé a répondu ? Le plan retient le libellé unique.
