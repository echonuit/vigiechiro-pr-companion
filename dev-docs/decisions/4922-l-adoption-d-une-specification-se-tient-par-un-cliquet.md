---
type: adr
title: "L'adoption d'une spécification vivante se tient par un cliquet, pas par une fraction"
status: stable
article: A9
chantier: "#4922 (EPIC #4511)"
decided_at: 2026-08-30
verification: probable
ratchet: 1
enforced_by:
  - ".github/scripts/verifie-specification-consignee.sh"
verified:
  - by: machine:ci
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# L'adoption d'une spécification vivante se tient par un cliquet, pas par une fraction

## L'incident

Le chantier #4511 a doté le dépôt de six commandes OpenSpec, d'une passe de clôture et d'une étape
d'ouverture. L'audit croisé qui a suivi (#4920) a posé la question que personne n'avait posée :
**comment saurait-on que l'adoption progresse ?**

La mesure a répondu : deux capacités spécifiées sur un produit de seize écrans et soixante-quatorze
commandes. Et surtout, les tâches du seul changement mené à terme avaient bien été cochées, dans les
commits du travail même, mais **rien ne le prescrivait**. Cela avait marché par habitude.

Le dépôt sait ce que devient un geste que rien ne réclame. C'est [ADR 4659] qui l'a mesuré :
**43 EPIC clos sur 64** sans trace de clôture, alors que la compétence existait et que trois
documents la demandaient.

## La fraction, et pourquoi elle ne se pose pas

Le réflexe est d'afficher « N capacités spécifiées sur M ». Il bute sur un dénominateur qui n'existe
pas, et le dépôt en offre cinq, tous défendables et incompatibles :

| Candidat | Compte |
|---|---|
| écrans documentés | 16 |
| commandes de la ligne de commande | 74 |
| cas de recette | 416 |
| EPIC | 35 |
| services de domaine | 27 |

Choisir entre eux revient à décider ce qu'est une capacité du produit. C'est un chantier, il devrait
précéder toute mesure, et il retarderait donc indéfiniment la mesure qu'il sert.

## La décision

**L'adoption se tient par un cliquet descendant sur le déficit**, à l'exemple d'[ADR 4659] : on
compte les chantiers clos sans avoir répondu à la passe de spécification, et ce compte ne remonte
pas. Un déficit se compte sans dénominateur, parce qu'il ne rapporte à rien.

**La couverture se regarde sans juger**, par une loupe qui sort 0 en signalant. Elle nomme ses
grandeurs des repères et non un total, parce qu'aucun des cinq candidats n'a titre à servir de
dénominateur.

Les deux dispositifs ne se remplacent pas. Le cliquet empêche la dérive ; la loupe montre la distance
qu'il reste, et c'est elle qui dira, dans quelques mois, si un rattrapage vaut un chantier.

## Ce qu'est une capacité, et pourquoi on n'en écrit pas de définition neuve

Le cliquet a besoin qu'on sache si un chantier **devait** spécifier. Sans critère écrit, ce jugement
est une opinion qui variera d'une session à l'autre, ce que le cliquet doit justement empêcher.

Le dépôt en porte déjà un, constitutionnel. [ADR 0014] définit la **capacité métier** au titre de
l'article A19 : *une opération, une option, un format d'export, une règle de gestion ; pas un détail
de présentation*. Elle est exercée et non théorique, quatre ADR s'en servant pour motiver un « sans
objet côté CLI ».

**La capacité d'OpenSpec et la capacité métier sont le même objet** : toutes deux désignent l'unité
qui doit exister sur les deux surfaces. La parité l'exige deux fois, la spécification la décrit une
fois. En écrire une seconde définition laisserait deux règles voisines, et le prochain lecteur
appliquerait celle qu'il trouve en premier.

La convention de nommage qui la complète, `<paquet de fonctionnalité>/<geste>`, vit dans
`openspec-propose` avec sa raison.

## Pourquoi le cliquet vaut 1

La passe est entrée dans le cycle le 2026-08-30 à 09:00:15Z, par #4840. Trois EPIC ont été clos après
cette borne : #4873 et #4874 y ont répondu par un « sans objet » motivé, et **#4841 ne l'a pas fait**.

Il a été clos **neuf minutes** après, sa trace portant encore l'ancienne numérotation : elle était
rédigée avant que la règle n'existe. Artefact de frontière, assumé par ce chiffre et non rattrapé.

## Ce que le garde ne prétend pas

Il vérifie qu'un **jugement a été rendu**, pas qu'il est juste. Une clôture peut cocher la passe à
tort, et rien ne le verra.

Il ne prouve pas non plus que la ligne trouvée **est** la passe 10 : il reconnaît une ligne cochée qui
parle d'archiver un changement, ce qui reste une heuristique (#4938). Deux limites distinctes, une
seule raison du niveau `probable` : la règle est vérifiable dans sa forme, pas dans son fond.

## Ce qu'un lecteur futur pourrait défaire

Le garde reconnaît la ligne de la passe à son **contenu**, jamais à son numéro : #4840 a renuméroté
le cycle, et dans une trace antérieure « 10 » désigne la passe des ADR. Resserrer ce motif sur le
numéro, par souci de précision, compterait douze clôtures anciennes comme spécifiées.

Il ignore aussi un EPIC clos **sans aucune trace** : celui-là fait monter le cliquet d'[ADR 4659].
Les rassembler ferait rougir deux gardes pour un seul manquement.

[ADR 0014]: 0014-parite-cli-ihm.md
[ADR 4659]: 4659-une-cloture-sans-trace-ne-se-distingue-pas-d-une-cloture-absente.md
