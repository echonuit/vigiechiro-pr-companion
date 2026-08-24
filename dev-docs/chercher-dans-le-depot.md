# Chercher dans le dépôt

Trois outils, et ils ne répondent pas aux mêmes questions. Se tromper d'outil coûte rarement une
erreur visible : cela coûte une réponse **fausse et plausible**, ce qui est pire.

| La question porte sur… | L'outil | Ce qu'il rend |
|---|---|---|
| un concept, ses voisins, qui l'appelle | `graphify` | un sous-graphe déjà réduit |
| une **forme de code** (appel, constructeur, structure) | `semgrep` | les occurrences, avec leur position |
| un **texte** (message, libellé, ligne de journal) | `grep` / `rg` | les lignes qui contiennent le motif |

## Le graphe d'abord

C'est la règle du dépôt, et elle est dans `AGENTS.md` : quand `graphify-out/graph.json` existe,
`graphify query "<question>"` passe avant tout le reste. Il oriente ; les deux autres outils
précisent ensuite.

## `semgrep` pour les questions de forme

`semgrep` lit l'arbre syntaxique, pas les lignes. Il répond juste là où `grep` ne peut que deviner :

```bash
semgrep --lang java --metrics=off --pattern 'CritereListe.$M(...)' src/main
semgrep --lang java --metrics=off --pattern 'new Scene(...)' src/main
```

Le second exemple n'est pas gratuit : la règle du dépôt est de passer par `Habillage.scene(...)`, et
la question « qui construit une `Scene` à la main ? » n'a pas de réponse textuelle fiable - le nom
`Scene` apparaît dans les imports, les commentaires et les types de paramètres.

!!! warning "Une réponse à zéro n'est pas une preuve d'absence"

    Le moteur libre de `semgrep` ne traite **pas** les annotations Java comme motif autonome :
    `--pattern '@CasDeRecette(...)'` rend **zéro occurrence** sur un dépôt qui en compte plusieurs
    dizaines, et **zéro erreur**. Rien ne distingue « je n'ai rien trouvé » de « je n'ai pas su
    chercher ».

    Avant de conclure d'un zéro, éprouver le motif sur un cas dont on **sait** qu'il existe. C'est
    la même discipline que pour un test : un dispositif qui ne peut pas échouer ne prouve rien.

Les motifs qui marchent en Java sur le moteur libre : appels de méthode, constructeurs, expressions.
Ceux qui demandent le moteur propriétaire : annotations seules, motifs inter-fichiers, flux de
données.

## `grep` pour le texte, avec ses pièges

`grep` reste le bon outil pour un message d'erreur ou un libellé. Trois pièges l'ont fait mentir dans
ce dépôt, et aucun n'a produit d'erreur visible :

- **Un sous-motif se compte lui-même.** `grep -c "couvert"` compte aussi « non couvert ». Ancrer, ou
  filtrer le contraire ;
- **Une puce markdown se replie.** Les fichiers de `dev-docs/recette/sessions/` continuent une puce
  sur la ligne suivante, indentée de deux espaces. `grep` n'en rend que le premier morceau, et la
  phrase tronquée peut dire le contraire de la phrase entière ;
- **Les octets NUL rendent `grep` muet.** Un fichier que `grep` juge binaire ne rend rien du tout, et
  une mesure vide se lit comme un zéro. `tr -d '\0'` en amont.

## Compter

Compter en **lançant**, pas en cherchant. Le dépôt a ses propres compteurs, et ils sont plus justes
que n'importe quelle expression : `CorrespondanceRecetteTest` imprime le compte des cas de recette et
nomme chacun, les auto-tests des bancs impriment leur nombre de cas et de rouges attendus, la CLI a
son inventaire.

Un chiffre transporté d'un contexte à l'autre garde sa forme et perd son objet : « 33 » a déjà été
relu comme « 33 clips » alors qu'il comptait les cas d'un auto-test.
