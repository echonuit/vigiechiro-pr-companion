---
type: adr
title: "Un renommage de conversion emporte les pointeurs, pas la prose"
status: stable
article: A30
chantier: "#5219 (sous-chantier #5218, chantier #5215)"
decided_at: 2026-09-05
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/documentation/DocumentationAJourTest.java"
verified:
  - by: machine:ci
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-05
---

# Un renommage de conversion emporte les pointeurs, pas la prose

## Contexte

Cinquante scripts ont changé de nom en passant en Python : `verifie-titre-pr.sh` est devenu
`verifie_titre_pr.py`. Le dépôt les nomme à des centaines d'endroits - en-têtes d'ADR, prose d'ADR,
pages de `dev-docs/`, spikes, javadoc, workflows, commentaires.

Tout renommer casse des faits datés. Ne rien renommer laisse des pointeurs morts. La question s'est
posée au premier lot et elle s'est reposée à chacun des sept suivants.

## Décision

**Le nom du fichier prend la forme d'un module Python** : des soulignés, pas des tirets. Un tiret
interdit l'import, et un garde importé par un autre existe déjà - `interroge_le_jeton.py` sert trois
verdicts. Choisir la forme au cas par cas produirait deux conventions dans un même dossier, ce qui est
l'état où `scripts/adr` se trouve encore et que personne ne sait plus expliquer.

**Ce qui DÉSIGNE le fichier suit le renommage.** Un `enforced_by:` d'ADR, un renvoi de javadoc, un
appel de workflow, une entrée d'inventaire : ce sont des pointeurs, et un pointeur faux ne date rien,
il casse. `DocumentationAJourTest` les tient déjà, et il a rougi sur neuf `enforced_by` oubliés au lot
des gardes de texte - c'est ce rouge qui a fait écrire cette règle.

**Ce qui RACONTE garde le nom d'époque.** La prose d'une ADR, un spike, un commentaire qui rapporte un
incident : « le 12 août, `verifie-titre-pr.sh` a laissé passer quatre titres » reste vrai, et le
réécrire ferait dire à un fait daté qu'il s'est produit sur un fichier qui n'existait pas encore.
L'index des ADR pose d'ailleurs qu'une ADR est **immuable** une fois acceptée.

## Conséquences

**Ce qu'on gagne.** La frontière est mécanique : si un dispositif LIT la chaîne pour agir, elle suit ;
si un humain la LIT pour comprendre, elle reste. On ne délibère pas à chaque occurrence.

**Ce qu'on paie.** Le dépôt nomme donc, en prose, trente-trois scripts qui n'existent plus. Un lecteur
qui cherche `verifie-fraicheur-actions.sh` ne le trouvera pas. C'est le prix de l'immuabilité des
décisions, et il est assumé plutôt que découvert.

**Ce qui a été mesuré à la clôture de #5218.** Sur soixante-cinq renvois en prose à des scripts
disparus, **un seul** était devenu faux pour un lecteur qui agirait : une page de recette affirmait
comment une ADR se vérifie en nommant un fichier supprimé. Les autres datent des faits. La règle tient
donc en pratique, et le cas limite est la page vivante qui décrit un dispositif d'aujourd'hui.

## Ce que cette ADR ne tranche pas

**Les dossiers déjà mixtes.** `scripts/adr` porte treize noms en souligné et trente et un en tiret,
sans règle qui explique le partage - vérifié, ce n'est pas l'importabilité. Cette ADR ne les
renomme pas : elle dit ce que fait une conversion, pas ce qu'on doit à l'existant.

**La javadoc en retard.** Quarante-quatre renvois de javadoc nomment encore un script converti, et
#5237 les porte. Ce sont des pointeurs, donc ils doivent suivre ; c'est un reliquat, pas une exception.
