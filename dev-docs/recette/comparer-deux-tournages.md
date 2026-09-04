# Comparer deux tournages

Depuis [#4258](https://github.com/echonuit/vigiechiro-pr-companion/issues/4258), chaque version porte
les clips de ses deux bancs sur son tag. Savoir ce qui a bougé entre la dernière version et le
tournage courant demandait d'ouvrir cinquante lecteurs et de s'en souvenir. Le flux **comparer deux
tournages** fait le tri ; le regard fait le reste.

## Lancer une comparaison

Le flux `comparer-tournages.yml` (`workflow_dispatch`) prend quatre entrées :

| Entrée | Ce qu'elle attend |
|---|---|
| `avant` | ce à quoi on compare : un tag de version (`v2.188.0`) ou `clips-recette` |
| `apres` | ce qu'on regarde, mêmes valeurs |
| `banc` | `bash` ou `java`, pour choisir le préfixe sur les tags de version |
| `tolerance` | la tolérance de couleur, en pourcentage. 5 par défaut |

L'usage courant est `avant` = la dernière version, `apres` = `clips-recette`, ce qui montre **ce que le
travail non publié change à l'écran**.

Le résultat s'écrit dans le **résumé du job** et les images partent dans un artefact, gardé quatorze
jours.

!!! note "Pourquoi rien n'est committé"

    La comparaison « dernière version contre tournante » change dès que l'un des deux bouge. Une page
    committée serait périmée au prochain tournage manuel, et une page périmée sur un sujet visuel est
    pire qu'une page absente : on la croit.

## Ce qu'on obtient, par cas

Trois signaux, du plus fiable au moins fiable.

**La présence.** Le cas est dans les deux tournages, ou **apparu**, ou **disparu**. C'est le signal le
plus sûr et le moins cher, et c'est souvent celui qui compte.

**L'image finale.** Les deux dernières images accolées (`<cas>.avant-apres.png`), leur carte des
différences (`<cas>.ou.png`, rouge là où ça bouge), et la part de pixels changés.

**La durée.** Un scénario qui s'allonge a presque toujours changé.

## Comment lire le chiffre

**Il trie, il ne prouve pas.** Mesuré sur deux tournages du **même commit** :

| changement | part mesurée | rapport au plancher |
|---|---|---|
| rien (deux tournages identiques) | ≤ 0,01 % | le plancher |
| un chiffre (12 × 18 px) | 0,021 % | ×2 |
| un mot (60 × 18 px) | 0,101 % | ×10 |
| un libellé (220 × 18 px) | 0,364 % | ×36 |
| un encart (400 × 120 px) | 4,212 % | ×420 |

Un mot changé vaut dix fois le plancher : le chiffre le sort du lot. Un caractère changé n'en vaut que
deux : le chiffre ne le distingue plus, et c'est la **carte des différences** qui le localise.

## Pourquoi une tolérance de couleur

Sans elle, le plancher n'est pas de 0,01 % mais de **16 %**.

Deux tournages du même commit rendent le même écran avec un **anticrénelage** légèrement différent. La
carte des différences le montre sans ambiguïté : le rouge est sur le texte et sur les bordures, jamais
sur les aplats. Un décalage de mise en page a été soupçonné, puis **écarté par la mesure** - décaler
l'image aggrave l'écart au lieu de le réduire.

Une tolérance de 5 % absorbe cet anticrénelage sans rendre l'instrument aveugle, comme le tableau
ci-dessus le montre.

!!! warning "La tolérance se mesure, elle ne se fige pas"

    `compare_tournages.py --plancher <A> <B>` remesure le plancher sur place, en comparant deux
    tournages qu'on sait identiques.

## Le plancher du runner, mesuré

Deux tournages des 51 cas, sur le **même commit**, lancés sur deux runners GitHub distincts - la
situation réelle, chaque publication filmant sur une machine neuve.

| plancher à 5 % de tolérance | cas |
|---|---|
| ≥ 0,5 % | 1 |
| 0,1 à 0,5 % | 2 |
| < 0,05 % | 48 |

**Médiane : 0,008 %.** Le plancher n'est donc pas haut partout. Il est bas sur 48 cas, et haut sur
trois :

| cas | son plancher |
|---|---|
| `ScenarioAccueilTest.chaque_carte_ouvre_ce_qu_elle_annonce` | 0,809 % |
| `ScenarioPerceptifRefusDepotTest.le_compte_rendu_dit_les_refus_et_conseille_la_reconnexion` | 0,230 % |
| `ScenarioFicheSiteTest.les_boutons_disent_ce_qui_les_empeche` | 0,101 % |

!!! danger "Un seuil global mentirait dans les deux sens"

    Retenir le pire plancher, 0,809 %, comme seuil unique **aveuglerait 48 cas pour se protéger de
    trois** : un libellé entier changé, qui vaut 0,364 %, passerait sous le seuil sans être vu.

    Retenir la médiane laisserait au contraire ces trois cas crier au changement à chaque tournage.

    Un écart se lit donc contre **le plancher de son propre cas**, pas contre un seuil unique.

!!! warning "Ces planchers viennent d'un seul tirage"

    Une paire de tournages donne UNE mesure par cas. Un cas dont le plancher est ressorti à 0,000 %
    n'est pas prouvé stable : il l'était cette fois-là. Les rapports que ce plancher permet de calculer
    - « douze fois son bruit » - ne valent donc pas mieux que leur échantillon, et un cas ne se déclare
    instable qu'après plusieurs paires.

### Ce que cette mesure a corrigé

Sur une comparaison réelle, deux cas dépassaient 1 % et semblaient donc être les vrais changements.
Rapportés à leur propre plancher, ils se séparent :

- `les_boutons_disent_ce_qui_les_empeche` : 1,561 % pour un plancher de 0,101 %, soit **quinze fois**
  son bruit. Et l'image le confirme - une infobulle « Suppression impossible : ce site porte des
  passages » est apparue, ce qui correspond au correctif #4253.
- `chaque_carte_ouvre_ce_qu_elle_annonce` : 1,799 % pour un plancher de 0,809 %, soit **deux fois**
  seulement. Sa carte des différences ne montre que du rouge sur le texte et les bordures, jamais un
  changement localisé : c'est du bruit de rendu, et le chiffre brut le faisait passer pour un
  changement.

## Le fichier de planchers

Les planchers mesurés vivent dans `.github/assets/planchers-tournages.tsv`, une ligne par cas :

```
ScenarioAccueilTest.chaque_carte_ouvre_ce_qu_elle_annonce	0.809	1
```

Le cas, son plancher en pourcentage, et **le nombre de paires de tournages qui l'ont produit**.

Le flux de comparaison le passe automatiquement, et chaque cas est alors classé par son **rapport à
son propre bruit** plutôt que par son écart absolu. Le résumé compte les cas « au-dessus de leur
propre plancher », qui est le nombre à regarder.

### L'enrichir

```
compare_tournages.py --plancher <tournage A> <tournage B> .github/assets/planchers-tournages.tsv
```

Relancer sur une **autre** paire garde le **pire** plancher observé et compte une paire de plus.

!!! warning "Le pire, et non la moyenne"

    Un plancher qui sous-estime le bruit fabrique des faux positifs, c'est-à-dire exactement ce qu'on
    cherche à éviter. Mieux vaut rater un petit changement sur un cas instable que crier au changement
    à chaque tournage.

!!! danger "Le fichier livré ne porte qu'UNE paire"

    Il le dit dans sa troisième colonne. Un cas dont le plancher est ressorti à 0,000 % n'est donc pas
    prouvé stable, et les rapports calculés dessus - « quatre-vingts fois son bruit » pour un écart
    absolu de 0,084 %, soit moins qu'un mot changé - ne valent pas mieux que leur échantillon.

    Chaque paire supplémentaire rend le fichier plus juste. Le lire, c'est lire aussi cette colonne.

### Deux silences que le fichier ne produit pas

Un cas **absent** du fichier est annoncé « plancher inconnu ». Le prendre pour stable reviendrait à
inventer une mesure qui n'a pas été faite.

Un fichier **annoncé mais introuvable** fait échouer la comparaison. Sans ce refus, les cinquante cas
diraient tous « plancher inconnu » et personne n'irait chercher le chemin fautif.

## Les deux bouts, et ce que la seconde paire a appris

Depuis l'[ADR 4296](../decisions/4296-on-compare-les-deux-bouts-du-clip.md), la comparaison porte sur la
**première** image du clip autant que sur la dernière. Chacune a son plancher, et le classement retient
le plus grand des deux rapports.

La première image est **plus stable que la dernière** : sur 51 cas et deux paires de tournages, soit
102 mesures, son plancher vaut **0,000 % sans exception**. Elle n'est pas pour autant aveugle - les
premières images de deux cas différents diffèrent de 2,4 à 3 %.

!!! warning "Un plancher haut peut n'être qu'un mauvais tirage"

    `chaque_carte_ouvre_ce_qu_elle_annonce` a rendu **0,809 %** sur la première paire et **0,073 %** sur
    la seconde, onze fois moins. Ce plancher n'était donc pas une propriété du cas.

    Sur les 51 cas, 3 planchers diffèrent de plus de 0,1 % d'une paire à l'autre, 10 de 0,01 à 0,1 %, et
    38 de moins de 0,01 %.

    La règle du **pire observé** garde 0,809 % pour ce cas, définitivement. C'est le prix assumé de
    ne pas fabriquer de faux positifs : un plancher ne redescend jamais, donc **un seul mauvais tirage
    aveugle un cas pour de bon**. Avec assez de paires, un centile vaudrait mieux qu'un maximum ; avec
    deux, il n'y a pas de quoi le calculer.

### Les images du début ne sortent que si le début a bougé

Son plancher valant zéro partout, produire ces montages systématiquement ferait cinquante fichiers
identiques qui noieraient les deux ou trois qui comptent. Vérifié sur une comparaison réelle : 51
montages de fin, **zéro** montage de début.

## Ce que la méthode ne voit pas

**L'image finale compare la destination, pas le chemin.**

La dernière image est le seul instant où deux tournages sont comparables sans dépendre de leur cadence :
au milieu, l'image n°40 de l'un et l'image n°40 de l'autre montrent deux moments différents. Mais un cas
dont l'objet est une **transition** - « la modale s'ouvre sans saut » - garderait une fin identique
alors que son milieu aurait bougé.

La durée est le seul garde-fou bon marché contre cela, et il est grossier. C'est une limite assumée, pas
un oubli.

## Une mesure impossible n'est pas « rien n'a changé »

Deux dossiers vides font **échouer** la comparaison au lieu de rendre « aucun cas ne bouge », et une
mesure qui échoue se compte à part dans le résumé.

Et un **outil absent** est une panne d'installation, pas une mesure : le script refuse de commencer
et nomme ce qui manque, au lieu de rendre cinquante « ? » qui se liraient comme cinquante cas stables.
Les deux ne se réparent pas au même endroit, donc ils ne doivent pas se lire pareil.

Ce n'est pas de la prudence de principe : le premier jet de cet outil rendait « ? » sur les sept cas
d'un vrai tournage, et son index annonçait tranquillement « aucun cas ne bouge ». La cause était que
`identify` écrit `1.152e+06` pour une toile de 1280 × 900, et le test d'entier qui suivait refusait la
mesure. Un instrument cassé qui se présente en succès est pire que pas d'instrument
([ADR 2748](../decisions/2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)).

Le premier lancement réel l'a montré deux fois. Le workflow n'installait pas ImageMagick : `compare`
était introuvable, la part de pixels valait « ? », et les cinquante cas d'un vrai tournage se rangeaient
en « mesure impossible » - **le job restant vert**. Le compteur de mesures impossibles a dit la panne ;
sans lui, le résumé aurait annoncé « aucun cas ne bouge », et la chaîne aurait eu l'air éprouvée.
