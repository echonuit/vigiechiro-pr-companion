---
name: rendre-le-bilan
description: Use at closure pass 12, once every ADR is written, to render what the chantier delivered. Three distinct things get confused here: the bilan tells, the artefact shows and must be assented to before closing, and the checkbox attests.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Rendre le bilan

## Loi d'airain

```
LE BILAN RACONTE, L'ARTEFACT MONTRE, LA CASE ATTESTE
```

Trois choses distinctes, et l'on croit en avoir fait trois en en faisant une. C'est la **case** qu'on
oublie, parce qu'elle ne s'écrit pas : elle se coche. Au 28 août 2026, **43 des 64 EPIC clos** du
dépôt n'en portaient aucune.

## Annoncer

« J'utilise la compétence rendre-le-bilan pour clore <l'EPIC>. »

## Fonction de garde

```
1. ECRIRE    la synthese : livre, dette restante, decisions et leur pourquoi.
2. RENVOYER  aux ADR de la passe 11 plutot que de rederouler leur raisonnement.
3. PRODUIRE  l artefact avant/apres, une entree par consequence du chantier.
4. SOUMETTRE l artefact et OBTENIR un assentiment, AVANT de clore.
5. COLLER    le modele de cloture en commentaire sur l EPIC, cases cochees.
6. FERMER    l EPIC, et seulement alors.
```

## Le bilan renvoie, il ne redéroule pas

Une synthèse courte : ce qui a été livré, la dette restante, les décisions prises et leur pourquoi.
Elle se dépose sur l'EPIC, et se répercute dans `AGENTS.md` ou `CONTRIBUTING.md` si elle change une
règle du dépôt.

Elle **renvoie** aux ADR écrites en passe 11 : c'est la raison pour laquelle elle vient après elles.
Redérouler leur raisonnement produit deux textes qui divergeront.

**Un bilan est une hypothèse, pas un verdict.** Sa section « dette restante » décrit ce qu'on croyait
comprendre au moment de l'écrire. Quand une suite est traitée, relire ce que le bilan en disait et le
corriger s'il s'est trompé : une analyse fausse laissée en place oriente le chantier suivant.

## Ce qu'un bilan DIFFÈRE se renvoie aussi, et son classement dit sa portée

Un bilan qui ne peut pas conclure tout de suite - un taux qui demande des tirages, une mesure qui
demande du recul - ouvre une issue pour plus tard et y écrit une **prédiction**, pour ne pas lire
ensuite dans les chiffres ce qui arrange. Deux règles s'y appliquent, et ce sont celles de la section
précédente.

**La prédiction vit à UN endroit, et l'issue différée y renvoie.** Recopiée en abrégé, elle devient
deux prédictions, et rien ne dit laquelle fait foi - or c'est la copie qu'on a sous les yeux au moment
de conclure, puisque c'est l'issue différée qu'on ouvre pour tenir le report. Si la reprise est
nécessaire, elle s'identifie comme copie.

**Et chaque classement dit DE QUELLE PORTÉE il parle.** C'est le piège propre au différé : entre
l'écriture de la prédiction et la mesure, d'autres lots passent. Un banc « non traité par le lot 2 »
peut être « traité au début de la fenêtre », et les deux phrases sont vraies. Un classement qui ne
nomme pas sa portée rend la mesure ininterprétable, sans jamais se contredire.

**Vécu sur #5277, mesuré en #5557.** Sa prédiction séparait quinze bancs en traités et témoins, en
justifiant les témoins par « ils n'ont jamais eu de site ». Mesuré avec la fonction du garde :
**deux des six témoins en portaient**, deux et cinq, invisibles au motif d'alors et convertis le
lendemain par une autre cascade. La clause de réfutabilité - les voir tomber **confirme** - ne tenait
donc sur aucun des deux, et la seule mesure ayant de la force portait précisément sur l'un d'eux.

**Le contrôle** : relire la prédiction en demandant, pour chaque ligne, « vraie à quelle date, et
selon quel instrument ? ». Un motif de garde élargi entre-temps suffit à déplacer un banc de groupe.

## Et sa CONDITION se pose dans l'unité que l'instrument compte

Un report dit quand il se relève. Écrit en jours de calendrier, il promet ce que le calendrier ne peut
pas tenir dès lors que l'instrument compte autre chose que des jours.

**Vécu sur #5327, mesuré en #5556.** Sa condition disait « à relever à partir du 2026-09-20 »,
quatorze jours après la clôture, et sa raison était juste : une fenêtre de quatorze jours est
nécessaire pour que le relevé dise quelque chose. Mais l'instrument compte des **tirages**, et un
tirage n'existe que si quelqu'un pousse.

```
ligne de base (2026-09-06)  fenetre=14j | tirages=884
releve du 2026-09-28        fenetre=21j | tirages=76
```

**Vingt-et-un jours ont produit onze fois moins que quatorze**, le dépôt n'ayant eu aucun run du 09-10
au 09-17. Quinze lignes du relevé sur seize sont sorties « non concluant », et il aurait été facile de
les lire comme des succès : aux taux de la ligne de base, **observer zéro était le résultat attendu
que le remède ait agi ou non**.

**La condition porte donc les deux, et pas l'un ou l'autre.**

```
à relever quand <l instrument> rend au moins N <unites>, N derive de la ligne de base ;
et à remesurer 2026-12-01 quoi qu il arrive.
```

L'unité décide **quand la mesure conclut**. La date ne décide rien, elle rend le report **visible** :
`scripts/adr/loupe-5539-echeances-non-tenues.py` veille sur les engagements différés en cherchant un
**verbe collé à une date**, et une condition sans date lui est invisible. Une condition juste que rien
ne surveille est un report qu'on oublie.

**La forme de la date est libre depuis #5556, et elle ne l'était pas.** Le motif de la loupe exigeait
la date **collée** au verbe, si bien que « à relever **le** 2026-12-01 » - la formulation la plus
naturelle - lui échappait. Mesuré sur neuf formulations le 2026-09-30 : trois vues, trois invisibles,
et les invisibles étaient celles qu'on écrit spontanément.

Le motif traverse désormais l'article, et l'élargissement n'a pas mangé le signal : « constaté le
2026-09-20 » et « mesure du 2026-09-07 » restent invisibles, car ce qui engage est le **verbe**, pas la
date. Deux cas de son auto-test le tiennent, dont un **négatif** - sans lui, élargir aurait pu faire
passer n'importe quel verbe suivi d'une date.

Écrivez donc la date comme elle vient. Ce qui reste à vérifier est qu'elle soit **précédée d'un verbe
qui engage** : « à relever », « à remesurer », « à tenir », « à rejouer », « à refaire ».

**Le contrôle, et il se fait avant de poser le report** : appeler `engagements()` de la loupe sur le
corps qu'on s'apprête à écrire. Une ligne, et elle répond par la date ou par le vide.

## L'artefact, et ce qu'il sert deux fois

Le bilan est un texte : il décrit des états que son lecteur n'a pas sous les yeux. La passe 8 les a
pourtant tous ouverts et regardés, et ce travail reste dans la tête de qui l'a fait.

L'artefact met l'**avant** et l'**après** côte à côte, une entrée par conséquence du chantier, avec la
phrase qui dit ce qu'on doit y voir. Il sert :

- **à valider** : c'est le seul support sur lequel un relecteur peut dire « non » sans relire le code.
  Un bilan qui affirme « les huit boutons s'affichent en entier » demande qu'on le croie ; une image
  le montre ;
- **à dater** : il fige l'état à la clôture, ce que le prochain chantier pourra comparer.

**Il se soumet avant de clore, pas après.** Son objet est d'obtenir un assentiment, pas de documenter
une décision déjà prise.

## Il vaut aussi sans pixel

« Aucune conséquence visible » ne dispense pas de l'artefact. Un chantier de méthode ou d'outillage
change de la **prose lue** et du **comportement**, et ces deux-là se montrent côte à côte aussi bien
qu'une capture : le corps d'une issue avant et après, la sortie d'un garde qui ne refusait rien puis
qui refuse, un tableau dont trois cellules étaient vides.

Vécu deux fois le 30 août 2026, aux clôtures de #4643 et #4873, où l'artefact a été produit alors
qu'aucun écran n'avait bougé. Dans les deux cas il a servi : c'est en le construisant qu'on relit ce
qu'on a fait.

## Ce qui n'a pas été corrigé y a sa place

Une troncature laissée en l'état, montrée et assumée, vaut mieux qu'une omission. Les défauts trouvés
en chemin y figurent, recadrés et agrandis quand ils sont petits : un glyphe de douze pixels ne se
juge pas à l'échelle 1.

## La case, et ce qu'elle atteste

Le modèle vit dans `dev-docs/cycle-de-chantier.md`, section « Modèle de clôture (à coller dans
l'EPIC) ». Il se colle **en commentaire**, cases cochées, avant de fermer.

Son en-tête `## Clôture de chantier` est ce que le garde cherche. Une clôture qui l'omet ne se
distingue d'aucune manière d'une clôture qui n'a pas eu lieu.

**Une passe non tenue se coche quand même, en le disant.** Une case vide laisse croire à un oubli ;
une case qui porte « sans objet : aucun écran touché » est une décision, et se relit comme telle.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Le bilan est écrit, la clôture est faite » | Le bilan raconte, la **case** atteste. Sans elle, rien ne distingue votre clôture d'une absence |
| « Aucun écran n'a bougé, pas d'artefact » | La prose et le comportement se montrent aussi. Vécu deux fois le 30 août |
| « Je publierai l'artefact après avoir clos » | Son objet est d'obtenir un assentiment, pas de documenter l'acquis |
| « Je réexplique la décision dans le bilan » | Il renvoie aux ADR. Deux textes sur la même décision divergent |
| « Cette passe n'avait pas lieu d'être, je laisse la case vide » | Une case vide se lit comme un oubli. On la coche en disant pourquoi |
| « La dette restante est claire » | Un bilan est une hypothèse. Quand la suite est traitée, on relit ce qu'il en disait |
| « J'ai fermé la dernière issue, l'EPIC est clos » | Fermer les issues n'est pas clore. Les quatorze passes portent sur le delta |
