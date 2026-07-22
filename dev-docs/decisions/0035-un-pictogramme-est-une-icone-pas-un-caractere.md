# ADR 0035 — Un pictogramme d'IHM est une icône ; un caractère dans une phrase reste un caractère

- **Statut** : Accepté — 2026-07-19
- **Chantier** : #1933 (lots #1989, #2011, #2016, #2024, #2026), suite de #700
- **Vérification** : probable — `scripts/adr/0035-pictogramme-caractere.py` (cliquet : 3)

## Contexte

#700 avait déjà posé la règle : un pictogramme d'IHM se pose en `FontIcon` Ikonli. Elle a été appliquée là où elle se voyait le plus, puis l'usage littéral est revenu. À l'ouverture de #1933, la mesure donnait **35 glyphes distincts sur 17 vues**, contre 4 vues utilisant `FontIcon`.

Un caractère écrit dans un libellé dépend des **polices installées** sur la machine : selon le système il tombe en rectangle vide, en noir et blanc, ou en emoji couleur pleine taille qui déséquilibre la ligne. Il ne se **teinte** pas non plus avec le texte, donc il ne peut suivre aucun état (désactivé, sévérité, thème).

Le rendu était correct sur la machine de développement. C'est exactement pourquoi le défaut ne se signale pas tout seul — et pourquoi il est revenu après #700.

Deux questions étaient laissées ouvertes à l'ouverture, faute de réponse évidente : les titres de modale font-ils exception, et `✕`, `→`, `−` sont-ils des pictogrammes ou de la typographie ?

## Décision

**1. Ce qui désigne une action ou un objet est une icône.** Un `☰`, un `✕`, un `📤`, un `🗑` nomment un geste ou une chose : ils se posent en `FontIcon`, dans le `<graphic>` du nœud, et le texte reste du texte.

**2. Ce qui vit dans une phrase reste un caractère.** « A → B », « ≥ 1 mois », une longitude négative : ce sont des signes de ponctuation au sens large, pas des icônes. Les convertir produirait un nœud graphique au milieu d'un paragraphe.

La frontière se dit mécaniquement, par **bloc Unicode** : les flèches (U+2190-U+21FF) et les opérateurs mathématiques (U+2200-U+22FF) sont de la typographie ; tout le reste est un pictogramme. Le bloc plutôt que la liste, parce qu'une liste ne dit rien du caractère suivant alors que la question se repose à chaque ajout.

**3. Un signe typographique seul est une icône qui s'ignore.** La tolérance vaut tant que le signe accompagne un texte. Un bouton dont le libellé **est** une flèche est un bouton à icône qui n'a pas dit son nom, et il retombe sous la règle 1.

**4. Les titres de modale convertissent.** C'était la question 1, et c'est la réponse la moins évidente des trois possibles. Ces pictogrammes ne sont pas purement décoratifs — ils identifient la fenêtre d'un coup d'œil, donc les **retirer** appauvrirait. Mais les **garder** littéraux les expose au défaut que tout le chantier corrige : un titre invisible n'orne rien.

**5. La sévérité se rend une fois, par le composant qui la connaît.** Corollaire trouvé en chemin : les fabriques de `RetourOperation` collaient `« ✅ »`, `« ⚠️ »`, `« ℹ️ »` **dans le texte**, alors que le bandeau se colore déjà d'après la sévérité. Le marqueur était dit deux fois, et sa seconde forme dépendait des polices. Il est désormais posé par `BandeauRetour` — en couleur **et** en icône, deux canaux au même endroit, donc incapables de se contredire, et lisibles par qui distingue mal les couleurs.

**6. La règle s'arrête à la console.** La CLI écrit `⚠`, `→`, `✓` dans ses sorties, et c'est correct : un terminal ne rend pas de `FontIcon`. Le caractère y est le **seul** moyen d'écrire un avertissement. Ce n'est pas une dette, et une passe transverse future ne doit pas les « corriger ».

## Conséquences

- **Un cliquet tient la règle** : `PictogrammesFxmlTest` analyse statiquement les `.fxml` et échoue sur tout pictogramme littéral. Sans lui, #1933 aurait le même avenir que #700 — la règle était déjà écrite, elle n'était pas gardée.
- **Un `FontIcon` se dimensionne comme du texte, mais ne se colore pas comme lui.** Corrigé après mesure (#1564) : cette conséquence disait d'abord que `-fx-font-size` « n'atteint pas » l'icône, ce qui est faux pour la taille et vrai pour la couleur. Relevé sur une sonde, une `FontIcon` seule dans une scène :

    | Règle appliquée | Taille | Couleur |
    |---|---|---|
    | aucune | 13 px | noir |
    | `-fx-font-size: 22px` | **22 px** | noir |
    | `-fx-icon-size: 22px` | **22 px** | noir |
    | `-fx-text-fill: rouge` | 13 px | **noir** |
    | `-fx-icon-color: rouge` | 13 px | **rouge** |

    Conséquence pratique : en convertissant un caractère en icône dans un contrôle déjà stylé, la **taille est conservée** par le `-fx-font-size` en place, mais la **couleur retombe au noir** — il faut ajouter `-fx-icon-color`. C'est le sens de lecture inverse de l'intuition (« tout le CSS d'une icône est séparé ») : **seule la couleur l'est**. `StyleControlesCarteTest` garde la règle.
- **Une icône se réévalue comme son libellé.** Une entrée de menu dont le texte change d'état doit changer d'icône avec lui. Le socle réévaluait le libellé à chaque ouverture du menu et posait l'icône une fois pour toutes : l'asymétrie était sans conséquence tant qu'aucune icône ne bougeait, fatale à la première.
- **Tout ne peut pas devenir une icône.** Un `promptText` est une **chaîne** : il n'accueille pas de nœud. Le `🔍` du champ de recherche ne pouvait donc pas être converti — il a été retiré, le mot « Rechercher » disant déjà la chose. Le jour où un champ de recherche doit porter une loupe, elle se pose **à côté** du champ, pas dedans.
- Les libellés bâtis en **Java** restent hors du cliquet et hors de ce chantier : ils sont suivis par #1564.

## Ce qui a été écarté

**Retirer les pictogrammes des titres de modale.** Défendable — ils sont décoratifs — mais cela aurait appauvri l'identification de la fenêtre sans régler le problème de fond, qui est le mode de rendu et non la présence de l'ornement.

**Une liste blanche de caractères tolérés.** Plus simple à lire, mais muette sur le caractère suivant : chaque ajout aurait rouvert le débat sans critère. Le bloc Unicode donne une règle qui se re-applique seule.

**Convertir les sorties CLI.** Cohérence de façade : un `FontIcon` dans un terminal n'existe pas (décision 6).

**Un grand remplacement en une PR.** Le découpage en cinq lots par famille a été retenu parce que chaque lot se vérifie à la **revue visuelle**, captures régénérées à l'appui — le seul endroit où un glyphe absent se voit. Quatre défauts que ce chantier a trouvés au-delà de sa mission ont été vus en **ouvrant une capture**, aucun par un test.
