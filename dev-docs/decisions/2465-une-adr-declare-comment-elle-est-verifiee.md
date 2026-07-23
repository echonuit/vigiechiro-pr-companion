# ADR 2465 — Une ADR déclare comment elle est vérifiée, sur trois niveaux

- **Statut** : Accepté — 2026-07-23
- **Chantier** : EPIC #2465
- **Vérification** : certaine — `DocumentationAJourTest#la_verification_declaree_par_une_adr_existe_vraiment`

## Contexte

Une ADR énonce une règle structurante, puis vit sa vie. Rien ne garantissait, six mois plus tard, que le code la respecte encore : il aurait fallu relire les 49 décisions à chaque chantier, ce que personne ne fait. Une décision oubliée se re-débat ; une décision **contredite en silence** est pire, parce qu'on croit qu'elle tient.

Le dépôt avait déjà des garde-fous ponctuels sur ce principe (`DocumentationAJourTest` pour les commandes et les écrans, les quatre contrôles de captures). Mais les décisions elles-mêmes n'étaient reconfrontées au code par rien.

Le piège, dès qu'on veut « vérifier les ADR », est d'en faire trop : coller un contrôle à une décision de méthode (« le plan précède l'écriture ») fabrique un test creux, dont le vert ne mesure aucun fait. Un contrôle creux est **pire** que pas de contrôle : il occupe la place et donne un faux sentiment de couverture.

## Décision

**Chaque ADR déclare, dans son en-tête, comment on sait si elle est tenue.** Une puce `- **Vérification** : <niveau> — <référence>`, à côté de `Statut` et `Chantier`, sur trois niveaux :

1. **`certaine`** — un invariant qui se prouve. Un test (`DecisionsRespecteesTest`) ou un script déterministe échoue en CI si la règle est violée. La puce nomme le test ou le script.
2. **`probable`** — pas de preuve possible, mais un script (`scripts/adr/NNNN-*.py`) liste des **suspects** qu'un humain trie. Le signal utile n'est pas « zéro » mais « aucun **nouveau** » : un **cliquet**, inscrit dans la puce, borne la dette. Le portail qualité fait rougir la CI dès qu'un suspect s'ajoute.
3. **`humaine`** — aucun invariant mécanique. Le motif dit **pourquoi**. Quand un pattern reconnaissable existe malgré tout, une **loupe** (`scripts/adr/loupe-NNNN-*.py`) surface une *surface de revue* pour la passe humaine : elle ne bloque jamais, elle aide à ne rien oublier.

**Le lien ADR ↔ vérification est mécanique.** `DocumentationAJourTest` exige que chaque ADR déclare un niveau valide, et que le test, le script ou la loupe nommé **existe vraiment** — une ADR ne peut pas annoncer une garde disparue. Le backlog de classement est clos : une ADR nouvelle déclare sa vérification dans la PR qui l'introduit, au même titre qu'elle porte un `Statut`.

**Le cliquet vit dans l'ADR, pas dans le script**, pour que le lecteur de la décision voie la marge en vigueur au même endroit que la règle. Un **rapport hebdomadaire** (`scripts/adr/rapport.py`) mesure l'écart et **resserre** les cliquets sur la réalité mesurée, par une PR : resserrer est mécanique, desserrer reste un geste humain.

## Conséquences

- Une décision structurante n'est plus un texte qu'on espère respecté : elle porte son moyen de contrôle, ou dit explicitement qu'elle repose sur la revue.
- Le niveau `humaine` est **majoritaire** (35 des 49 ADR) et c'est un résultat honnête, pas un renoncement : une décision de méthode ou de comportement ne se prouve pas par un scan.
- Le bar `certaine` reste haut : un invariant n'y entre que s'il **tient déjà** et se formule comme un motif observable. Un invariant violé le jour de sa naissance part en `probable` avec un cliquet, pas en test rouge.
- **Chaque garde a été vue rouge sur une mutation** avant d'être déclarée. La clôture de ce chantier a d'ailleurs trouvé deux scripts `probable` aveugles aux commentaires (0010, 0046), dont l'un masquait que l'ADR était en réalité respectée : la vérification par mutation n'est pas une formalité.

## Alternatives écartées

- **Un contrôle par ADR, sans niveaux.** Aurait produit des tests creux sur les décisions de méthode. Le classement à trois niveaux rend le « non vérifiable mécaniquement » explicite au lieu de le maquiller.
- **ArchUnit.** Plus expressif pour les règles de dépendances, mais une dépendance de plus ; les garde-fous maison (`DocumentationAJourTest`) suffisaient, et le scan de sources couvre les cas visés.
- **Un rapport qui échoue sur régression.** Le rapport observe et mesure ; c'est le portail qualité, sur la PR fautive, qui juge. Séparer les deux évite qu'un rapport hebdomadaire devienne un blocage désynchronisé du geste qui a introduit le défaut.
