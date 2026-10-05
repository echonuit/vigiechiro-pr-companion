---
type: adr
title: "Une commande lectrice peut cesser de l'être sur une option, et le verrou suit l'invocation"
status: stable
article: A16
chantier: "#5851, clôture du chantier #5596 (décision prise par le lot 24, #5784)"
decided_at: 2026-10-04
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/cli/CliVerrouWorkspaceTest.java"
  - "ClassementLectureEcritureTest#aucune_commande_n_est_sans_classement"
verification_note: "le premier tient les deux sens sur un dossier occupé : la commande passe sans l option et est refusée avec. Le second tient toujours que chaque commande est classée. Rien ne vérifie qu une commande qui redéfinit la réponse la redéfinit juste : c est un jugement, comme le classement lui-même"
relations:
  amende: ["3498-la-declaration-porte-sur-les-lectrices"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Une commande lectrice peut cesser de l'être sur une option, et le verrou suit l'invocation
## Contexte

L'[ADR 3498](3498-la-declaration-porte-sur-les-lectrices.md) pose que le verrou du dossier de travail
se prend par défaut, et qu'une commande s'en dispense en portant l'interface marqueur `LectureSeule`.
Elle ajoute que la liste des lectrices est stable : « une commande qui lit le restera ».

Le lot 24 de #5596 a donné à `etat-traitement-vigiechiro` l'option `--importer`, qui écrit des
observations en base ([ADR 5784](5784-un-releve-qui-trouve-l-analyse-terminee-importe.md)). La même
commande lit sans l'option, et écrit avec. Le marqueur, porté par la classe, ne savait pas le dire.

## Décision

**Le marqueur porte une réponse, et le verrou suit l'invocation.** `LectureSeule` gagne
`neFaitQueLire()`, vraie par défaut. Une commande qui lit sauf sur une option la redéfinit ;
`StrategieExecutionCli` dispense du verrou une commande lectrice **qui répond vrai pour cette
invocation**.

Le sens de la déclaration ne change pas : oublier de redéfinir la réponse fait refuser une
consultation, ce qui se voit ; il n'existe toujours aucun moyen de laisser une écriture échapper au
verrou par omission d'un marqueur d'écrivaine.

## Ce qui a été écarté

**Retirer le marqueur de la commande.** Le simple relevé serait refusé dès que l'application
graphique est ouverte, alors qu'un script le lance justement pour attendre la fin d'une analyse.

**Une commande séparée pour importer.** Le porteur a préféré l'option, et `importer-vigiechiro`
existe déjà pour qui veut importer sans relever.

## Ce que cette décision n'autorise pas

Elle ne fait pas de `neFaitQueLire()` un interrupteur de confort. Une option qui écrit dans le
dossier de travail rend la commande écrivaine pour cette invocation, sans exception.

## Conséquences

« Une commande qui lit le restera » cesse d'être vrai de la classe ; cela reste vrai de l'invocation
sans option. `ClassementLectureEcritureTest` compte toujours les classes qui portent le marqueur, et
son compte n'a pas bougé.
