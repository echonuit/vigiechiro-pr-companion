# Le registre : d'où viennent les règles, et ce que la mesure a écarté

`CONTRIBUTING.md` retient **sept tics** comme règles opposables. Cette page dit d'où ils viennent,
et surtout **pourquoi les autres n'ont pas été retenus**. Une règle éditoriale affirmée sans mesure
se discute à chaque relecture ; une règle écartée après mesure ne se rediscute pas.

## Deux listes, et ce qu'on en a fait

Les motifs viennent de [« Signs of AI writing »](https://en.wikipedia.org/wiki/Wikipedia:Signs_of_AI_writing)
(WikiProject AI Cleanup, trente-cinq motifs) et de son équivalent français,
[« Aide:Identifier l'usage d'une IA générative »](https://fr.wikipedia.org/wiki/Aide:Identifier_l%27usage_d%27une_IA_g%C3%A9n%C3%A9rative),
qui en ajoute six propres au français.

Chaque motif a été compté sur le corpus de ce dépôt, puis les lignes désignées ont été **ouvertes**.
Un comptage seul ne décide de rien : il désigne où regarder.

Le corpus mesuré : **47 119 lignes** de `dev-docs`, `docs` et `brief`, dans 415 fichiers. Mesures du
2026-08-24, refaites par `scripts/methode/mesure-registre.py`.

**Cette page ne se compte pas elle-même**, et il a fallu s'en apercevoir alors que la décision
existait : l'[ADR 3645](decisions/3645-un-detecteur-textuel-s-exclut-de-son-corpus.md) l'a établie sur
un détecteur qui certifiait gardées les cinq classes que sa propre documentation nommait. Elle cite les motifs en
exemple : à la première mesure, elle rendait sept « mise en place » pour six réels et 134 connecteurs
pour 116. Un compteur qui lit sa propre démonstration mesure sa démonstration. L'exemption est
nominative, dans le script.

## Le résultat qui compte : les connecteurs rendent zéro

Le connecteur lourd en tête de phrase est le tic français le plus cité. Il ne s'applique pas ici.

| Connecteur | occurrences | en ouverture de phrase |
|---|---:|---:|
| de plus | 76 | **0** |
| par ailleurs | 17 | **0** |
| en conséquence | 8 | **0** |
| en outre | 7 | **0** |
| toutefois | 3 | **0** |
| néanmoins, dès lors | 4 | **0** |
| cependant | 1 | **0** |
| Par conséquent, En somme, En résumé, En définitive, De surcroît, Dorénavant, À cet égard, De ce fait, En effet | 0 | **0** |

116 occurrences au total, **aucune en ouverture**. Employé au milieu d'une phrase, un connecteur est
du français ordinaire ; c'est sa position et son taux qui font le tic.

**Le zéro a été éprouvé avant d'être annoncé.** Un compteur qui ne trouve rien peut être juste ou
cassé, et rien ne les distingue. `mesure-registre.py --auto-test` fabrique six phrases au verdict
connu : en tête de ligne, après un point, après un deux-points, en tête de puce, toutes vues à 1 ;
au milieu d'une phrase et dans un mot plus long, toutes vues à 0.

**Et ce zéro est gardé.** `mesure-registre.py --verifie` tourne en intégration continue et refuse un
connecteur lourd revenu en ouverture. C'est la seule affirmation de cette page qui puisse devenir
fausse sans que personne ne s'en aperçoive : les autres chiffres sont des mesures datées, que
l'article A5 admet comme telles et qui vieillissent en disant leur date. Celui-ci est le motif pour
lequel une règle n'a pas été retenue, et il se retournerait en silence.

## Les autres motifs comptables, et ce qu'ils rendent

| Famille | Occurrences | Le détail |
|---|---:|---|
| formules creuses | 6 | « mise en place » (6) |
| mots surchargés | 2 | « crucial » (1), « significatif » (1) |
| remplissage | 2 | « afin de pouvoir » (1), « du fait que » (1) |
| fausse révélation | 1 | « la vraie question » (1) |
| calques de l'anglais | 0 | |
| le « nous » de commentaire | 0 | |
| annonce avant la chose | 0 | |

**Onze occurrences sur 47 185 lignes.** Aucune de ces familles ne justifie une règle : une règle
opposable pour onze cas coûterait plus qu'elle ne rapporte, et se désactiverait.

## Ce que cette méthode ne peut pas voir

Une grille de motifs mesure sa **précision**, jamais son **rappel**. Ouvrir les lignes qu'un motif
désigne dit combien sont fautives ; cela ne dit rien de celles qu'aucun motif ne désigne.

Ce qui échappe n'est pas lexical, c'est **relationnel** :

- une javadoc qui paraphrase la signature qu'elle surmonte ;
- une garantie annoncée que le code ne tient pas ;
- une unité qui ne correspond pas, `@return la fréquence en Hz` sur un calcul en kHz ;
- un plan identique sur chaque section, des synonymes alternés mécaniquement.

Aucune de ces lignes ne porte un mot suspect. Le défaut est dans l'écart entre le texte et ce qu'il
décrit, et il faut les lire ensemble. C'est pourquoi l'article A31 exige une **relecture**, et non un
comptage.

## Les motifs déjà tenus, et par quoi

| Motif | Ce qui le tient | État au 2026-08-26 |
|---|---|---|
| tiret cadratin | `scripts/adr/2843-tiret-cadratin.py` | dix-sept zones à tolérance zéro, cliquet à <!--inv:cliquet-cadratin-->1<!--/inv--> |
| pictogramme d'alerte | `scripts/adr/4366-avertissement-en-pictogramme.py`, article A28 | cliquet à <!--inv:cliquet-pictogramme-->0<!--/inv--> |
| javadoc narrative | `scripts/adr/4359-javadoc-narratif.py`, article A30 | cliquet à <!--inv:cliquet-javadoc-->742<!--/inv--> |
| apostrophe mêlée | `scripts/adr/4368-apostrophe-droite.py`, `verifie-titre-pr.sh` | zéro partout, trois exemptions déclarées |
| élision sans apostrophe | `.github/scripts/verifie-titre-pr.sh` et `verifie-corps-pr.sh` | tolérance zéro sur le titre ET le corps d'une PR ; la lettre isolée employée comme symbole en est sortie (#4483) |
| javadoc jamais relue | `scripts/adr/4468-javadoc-non-relue.py`, article A31 | cliquet à <!--inv:cliquet-relecture-->0<!--/inv--> fichier : tout le corpus est relu, et une javadoc réécrite y retombe |
| commentaire long en corps de méthode | `scripts/adr/4472-commentaire-en-corps.py`, article A30 | cliquet à <!--inv:cliquet-commentaire-corps-->43<!--/inv-->, plus une loupe de densité qui ne bloque pas |
| javadoc qui raconte son extraction | `scripts/adr/4476-javadoc-raconte-son-extraction.py`, article A30 | cliquet à <!--inv:cliquet-javadoc-extraction-->0<!--/inv--> |
| ADR qui raconte plus que sa décision | `scripts/adr/4477-longueur-des-adr.py`, article A30 | cliquet à <!--inv:cliquet-longueur-adr-->58<!--/inv--> |
| traces d'outil, cinq familles comptables | `scripts/adr/4783-traces-d-outil.py`, article A31 | tolérance zéro, cliquet à <!--inv:cliquet-traces-outil-->0<!--/inv-->, trois exemptions déclarées |
| source vague, conjecture présentée en fait | article A5 | la mesure fait foi et dit d'où elle vient |
| section « défis et perspectives » | le gabarit d'ADR | contexte, décision, conséquences, alternatives |

Un motif tenu par un garde n'a pas besoin d'être une règle de plus : il est déjà refusé.

## Le gras, et pourquoi la règle reste humaine

`dev-docs` et `docs` portent **11 995 emplois du gras pour 36 377 lignes**, soit un tiers de ligne, et
**214 entrées de liste** ouvrant sur un mini-titre gras.

Un gras **code** quelque chose quand il désigne un libellé d'interface, un nom de colonne, un terme
du domaine ou une contrainte forte. Il **emballe** quand il tombe sur un mot ordinaire. Aucun
contrôle mécanique ne fait cette différence : la syntaxe voit le gras, pas son utilité.

C'est la seule règle du registre qu'on aurait pu mécaniser et qu'on a délibérément laissée à la
relecture. Un cliquet y rendrait un chiffre sans jugement, et ferait baisser le compte en retirant
les gras utiles autant que les autres.

La mesure sert quand même : #4344 a ramené `AGENTS.md` de 0,81 emploi par ligne à 0,39, à la lecture,
et le relevé des faits avant et après a prouvé qu'aucun mot n'avait disparu.

## Ce que les sources françaises ont ajouté

L'**apostrophe mêlée** ne figure dans aucune des trente-cinq. Elle est venue du second passage, sur
les relevés de tics de ChatGPT en français, et elle a rendu 220 occurrences suivies, dont douze
atteignaient un écran. C'est le seul motif de cette page trouvé par la seconde liste et pas par la
première, et le seul dont la règle a été élargie après coup : ce dépôt n'écrit que l'ASCII.

Les **connecteurs lourds** en viennent aussi, et ils rendent zéro. Une liste qui apporte un motif
utile et un motif vide vaut d'être lue en entier.

## L'inventaire, et ce qu'il ne prétend pas

Les cinq traces d'outil sont comptables elles aussi, et elles ne sont pas dans les tableaux
ci-dessus : leur compte vit dans le garde qui les tient, et un inventaire se cite plutôt qu'il ne se
duplique (ADR 3535). Mesure du 2026-08-29, sur 2 726 fichiers et 372 370 lignes : **zéro** pour les
cinq. Sans l'exemption de la compétence qui les énumère, le même compteur rend 22 marques de
citation, toutes aux lignes qui les définissent.

Cette page rend compte des motifs **comptables**. Ceux qui ne le sont pas, l'importance gonflée,
l'analyse creuse en participe présent, la langue de la brochure, le tricolon forcé, l'aphorisme de
fin, ne se mesurent pas par une expression : ils se voient à la lecture, et quatre d'entre eux sont
dans les sept tics opposables parce que la relecture les a vus revenir, pas parce qu'un compteur les
a comptés.

C'est une différence de nature, et elle est assumée : une règle opposable n'a pas besoin d'être
mécanisable, elle a besoin d'être **refusable**.
