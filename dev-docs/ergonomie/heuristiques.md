# Les heuristiques d'ergonomie

Le vocabulaire **clos** auquel une décision d'ergonomie se rattache, par le champ `heuristiques:`
de son en-tête OKF.

**Version 2, arrêtée le 2026-08-23.** Close veut dire : aucune clé ne s'invente au fil de l'eau.
Une clé nouvelle est une décision, elle passe par cette page et par une version.

La version 2 ajoute la famille **WCAG**, cinq clés. La version 1 en portait dix-huit, toutes de
conception ; aucune ne disait ce qu'une interface doit à qui la lit mal, la lit au clavier, ou
distingue mal les couleurs. Ce n'était pas un oubli d'inventaire, c'était le même angle mort que
pour la Gestalt : le produit tenait déjà quatre de ces cinq propriétés, et aucune n'avait de nom.

Le champ est **toujours une liste**, même à une seule entrée, et chaque clé est confrontée
séparément à ce vocabulaire. Une décision qui sert deux heuristiques les nomme toutes les deux :
c'est le cas ordinaire, pas l'exception.

## Pourquoi une annexe, et pas le corps des ADR

Le corps d'une ADR ne porte rien de tout ceci. Le cliquet de longueur est à 50 pour un seuil de
800 mots, et deux ADR l'ont franchi le 2026-08-22 en gagnant un seul paragraphe. Un rattachement
qui coûterait cent mots par décision ne serait pas posé, ou le serait au prix d'un cliquet qui
monte. Il tient donc en une clé.

## Les dix heuristiques de Nielsen

Jakob Nielsen, *10 Usability Heuristics for User Interface Design*, 1994 - révision de 2020 pour
les libellés. Les noms français sont ceux de cette page, et font foi ici.

| Clé | Nom | Ce qu'elle demande |
|---|---|---|
| `nielsen-1` | Visibilité de l'état du système | l'utilisateur sait à tout moment ce qui se passe, par un retour approprié et à temps |
| `nielsen-2` | Correspondance avec le monde réel | les mots, les phrases et les concepts sont ceux de l'utilisateur, pas ceux du système |
| `nielsen-3` | Contrôle et liberté | une sortie de secours claire quand on s'est trompé de chemin : annuler, revenir, refaire |
| `nielsen-4` | Cohérence et standards | deux mots différents ne désignent pas la même chose, et les conventions de la plateforme sont suivies |
| `nielsen-5` | Prévention de l'erreur | la condition qui rend l'erreur possible est retirée, ou confirmée avant qu'elle ne coûte |
| `nielsen-6` | Reconnaissance plutôt que rappel | ce dont on a besoin est visible ; rien n'exige de se souvenir d'un écran à l'autre |
| `nielsen-7` | Flexibilité et efficience | un raccourci pour l'habitué, sans gêner celui qui découvre |
| `nielsen-8` | Esthétique et sobriété | rien d'inutile à l'écran : chaque élément de plus dispute sa visibilité aux autres |
| `nielsen-9` | Reconnaître, diagnostiquer, corriger | un message d'erreur en langage clair, qui dit le problème et propose une issue |
| `nielsen-10` | Aide et documentation | l'aide se trouve, se cherche, et tient en des étapes concrètes |

## Les deux notions de Norman

Donald A. Norman, *The Design of Everyday Things*, 1988 - édition révisée de 2013, dont vient la
distinction entre les deux.

| Clé | Nom | Ce qu'elle demande |
|---|---|---|
| `affordance` | Affordance | ce que la chose permet de faire, indépendamment de ce qu'on en voit |
| `signifiant` | Signifiant | ce qui **montre** où agir : sans lui, une affordance existe et personne ne la trouve |

## Les six lois de la Gestalt

Max Wertheimer, 1923 ; Kurt Koffka, *Principles of Gestalt Psychology*, 1935. Six lois retenues,
celles qui décident d'un groupement à l'écran.

| Clé | Nom | Ce qu'elle demande |
|---|---|---|
| `gestalt-proximite` | Proximité | ce qui est proche est lu comme un groupe ; l'espace sépare mieux qu'un trait |
| `gestalt-similarite` | Similarité | ce qui se ressemble est lu comme de même nature ; deux rôles ne partagent pas une forme |
| `gestalt-continuite` | Continuité | l'œil suit une ligne ; un alignement rompu casse la lecture |
| `gestalt-cloture` | Clôture | une forme incomplète se complète toute seule ; un cadre ouvert se referme dans la tête |
| `gestalt-figure-fond` | Figure et fond | ce qui est au premier plan se distingue du reste ; sans contraste, tout est fond |
| `gestalt-destin-commun` | Destin commun | ce qui bouge ensemble appartient ensemble ; une animation groupe autant qu'une bordure |

## Les cinq critères WCAG retenus

*Web Content Accessibility Guidelines* 2.2, niveau AA. Le standard compte une cinquantaine de
critères, dont une bonne moitié n'a pas de sens pour une application de bureau JavaFX. Cinq sont
retenus : ceux dont ce produit a de quoi parler, et qui se mesurent sur ses surfaces.

| Clé | Critère | Ce qu'elle demande |
|---|---|---|
| `wcag-contraste` | 1.4.3 et 1.4.11 | 4,5:1 pour ce qui habille du texte, 3:1 pour un élément d'interface qui n'en habille aucun |
| `wcag-couleur-seule` | 1.4.1 | la couleur n'est jamais le seul porteur d'une information ; un mot, une forme ou une position la double |
| `wcag-nom-accessible` | 1.1.1 et 4.1.2 | tout contrôle a un nom, même sans texte visible : une icône seule ne se lit pas |
| `wcag-focus-visible` | 2.4.7 | le contrôle qui a le focus clavier se voit, y compris quand une feuille réécrit son fond |
| `wcag-cible` | 2.5.8 | une cible cliquable fait au moins 24 × 24 px |

Une clé WCAG et une clé de conception peuvent se poser ensemble : le contraste sert à la fois la
loi de figure et fond, et le critère 1.4.3. Ce n'est pas une redondance, ce sont deux raisons de
tenir la même propriété - et le jour où l'une s'affaiblit, l'autre la retient.

## Ce que cette page n'est pas

Ce n'est pas un guide de style, ni une méthode de conception. C'est un **vocabulaire de
rattachement** : il sert à retrouver les décisions qui traitent d'un même problème d'usage, et à
voir celles qu'aucune décision ne couvre.

Le regroupement sert à **retrouver**, pas à fusionner d'office. Deux ADR qui partagent une clé
restent deux décisions tant que rien ne prouve qu'elles disent la même chose : elles peuvent être
voisines de sujet sans être des doublons. Toute fusion se décide par paire, et se justifie.

---

<!-- matrice engendree : ne pas editer a la main -->

## Matrice : ce que chaque heuristique sert

Engendrée depuis les en-têtes des ADR par `scripts/methode/matrice-ergonomie.py`, et gardée par lui.

**3 rattachement(s), portés par 1 décision(s).** Les deux nombres diffèrent dès qu'une décision sert plusieurs heuristiques : c'est le cas ordinaire, et les confondre ferait croire à une couverture qui n'existe pas.

| Clé | Heuristique | ADR | Lesquelles |
|---|---|---:|---|
| `nielsen-1` | Visibilité de l'état du système | 0 | **aucune** |
| `nielsen-2` | Correspondance avec le monde réel | 1 | [4366-un-avertissement-se-dit-en-mots](../decisions/4366-un-avertissement-se-dit-en-mots.md) |
| `nielsen-3` | Contrôle et liberté | 0 | **aucune** |
| `nielsen-4` | Cohérence et standards | 0 | **aucune** |
| `nielsen-5` | Prévention de l'erreur | 0 | **aucune** |
| `nielsen-6` | Reconnaissance plutôt que rappel | 0 | **aucune** |
| `nielsen-7` | Flexibilité et efficience | 0 | **aucune** |
| `nielsen-8` | Esthétique et sobriété | 1 | [4366-un-avertissement-se-dit-en-mots](../decisions/4366-un-avertissement-se-dit-en-mots.md) |
| `nielsen-9` | Reconnaître, diagnostiquer, corriger | 0 | **aucune** |
| `nielsen-10` | Aide et documentation | 0 | **aucune** |
| `affordance` | Affordance | 0 | **aucune** |
| `signifiant` | Signifiant | 0 | **aucune** |
| `gestalt-proximite` | Proximité | 0 | **aucune** |
| `gestalt-similarite` | Similarité | 1 | [4366-un-avertissement-se-dit-en-mots](../decisions/4366-un-avertissement-se-dit-en-mots.md) |
| `gestalt-continuite` | Continuité | 0 | **aucune** |
| `gestalt-cloture` | Clôture | 0 | **aucune** |
| `gestalt-figure-fond` | Figure et fond | 0 | **aucune** |
| `gestalt-destin-commun` | Destin commun | 0 | **aucune** |
| `wcag-contraste` | 1.4.3 et 1.4.11 | 0 | **aucune** |
| `wcag-couleur-seule` | 1.4.1 | 0 | **aucune** |
| `wcag-nom-accessible` | 1.1.1 et 4.1.2 | 0 | **aucune** |
| `wcag-focus-visible` | 2.4.7 | 0 | **aucune** |
| `wcag-cible` | 2.5.8 | 0 | **aucune** |

**20 heuristique(s) sur 23 que rien ne sert.** Ce n'est pas une faute : c'est ce dont personne n'a eu à décider, et il faut le voir pour savoir si c'est un choix ou un angle mort.

- `nielsen-1` · Visibilité de l'état du système
- `nielsen-3` · Contrôle et liberté
- `nielsen-4` · Cohérence et standards
- `nielsen-5` · Prévention de l'erreur
- `nielsen-6` · Reconnaissance plutôt que rappel
- `nielsen-7` · Flexibilité et efficience
- `nielsen-9` · Reconnaître, diagnostiquer, corriger
- `nielsen-10` · Aide et documentation
- `affordance` · Affordance
- `signifiant` · Signifiant
- `gestalt-proximite` · Proximité
- `gestalt-continuite` · Continuité
- `gestalt-cloture` · Clôture
- `gestalt-figure-fond` · Figure et fond
- `gestalt-destin-commun` · Destin commun
- `wcag-contraste` · 1.4.3 et 1.4.11
- `wcag-couleur-seule` · 1.4.1
- `wcag-nom-accessible` · 1.1.1 et 4.1.2
- `wcag-focus-visible` · 2.4.7
- `wcag-cible` · 2.5.8

<!-- fin de la matrice engendree -->
