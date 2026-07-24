# ADR 0028 — Un état n'est pas un compte rendu, et ils ne partagent pas de canal

- **Statut** : Accepté — 2026-07-19, **amendé** par [ADR 0031](0031-un-retour-n-est-pas-un-compte-rendu.md) (sur le vocabulaire ; la décision reste entière)
- **Chantier** : #1870 (lots #1886 à #1917)
- **Vérification** : humaine — la séparation conceptuelle entre état et compte rendu se lit dans l'intention, pas dans un motif de code

## Contexte

L'[ADR 0023](0023-rendre-compte-bandeau-par-defaut-modal-si-irreversible.md) a fait du bandeau le véhicule par défaut de tout compte rendu d'opération. En l'appliquant aux onze écrans, une seconde confusion est apparue, indépendante de la première : **des canaux nommés « message » portaient deux natures à la fois**.

Trois formes du même défaut, trouvées à trois endroits différents.

**Le partage d'une propriété.** `LotViewModel` publiait dans la même propriété le compte rendu de ses opérations *et* l'état du lot dérivé du statut workflow (« Passage déposé le… »). Le code compensait par l'**ordre des appels** :

```java
appliquer(service.consulterLot(idPassage));        // repose l'état
message.set("Archives de dépôt supprimées (…)");   // l'écrase aussitôt
```

Inverser ces deux lignes changeait ce que voyait l'utilisateur. Rien ne le signalait.

**Le compte rendu déduit d'un état.** La même propriété disait « ✓ Dépôt préparé : N séquences validées » sur la seule foi du statut `PRET_A_DEPOSER`. Le succès de l'étape ① n'était donc pas *annoncé*, il était *déduit* — et s'affichait aussi à la simple ouverture d'un passage préparé la veille, annonçant une action qui n'avait pas eu lieu. Un test figeait ce comportement en ouvrant l'écran sans jamais préparer.

**La sévérité logée dans les noms.** `ReconstructionViewModel` séparait bien trois natures, mais en multipliant les propriétés : `message`, `erreur`, `compteRendu`. Ailleurs, un canal nommé `messageErreur` portait « Métadonnées récupérées depuis Vigie-Chiro. » — un succès, présenté comme un échec faute de pouvoir dire autre chose.

## Décision

**1. Un état et un compte rendu ne partagent jamais de propriété.** L'état décrit ce qui *est* : il se repose à chaque chargement, il ne se ferme pas, il survit à la fermeture du bandeau. Le compte rendu décrit ce qui *vient de se passer* : il appartient à l'action, il porte une sévérité, il se ferme.

**2. Un compte rendu se dit, il ne se déduit pas.** Une opération qui réussit publie son bilan explicitement. Aucun compte rendu ne se dérive d'un statut : le même statut est atteint en agissant *et* en ouvrant un écran déjà dans cet état, et seul le premier cas mérite d'être rapporté.

**3. La sévérité vit dans la valeur, pas dans le nom.** Un canal s'appelle `retour`, jamais `messageErreur` : le nom d'une propriété ne doit pas décider par avance de ce qu'elle a le droit de dire.

**4. Un collaborateur choisit sa sévérité s'il en émet plusieurs.** Quand un collaborateur n'émet que des échecs, il peut rester agnostique et laisser le point de jonction qualifier (`PositionsEnAttente`, lot #1888). Quand il émet des guidages **et** des succès, il choisit lui-même (`SaisiePassageConditions` via `MessagesRattachement`, lot #1917) : le faire deviner ailleurs reviendrait à réinterpréter des messages d'après leur texte.

**5. Ce qui décide de la fenêtre n'est pas ce qui décide de la couleur.** `CompteRenduEnvoi.peutFermer()` et `CompteRenduEnvoi.retour()` dérivent des mêmes drapeaux et coïncident aujourd'hui — un succès ferme, tout le reste retient. Ils restent **distincts** : `peutFermer` n'a de sens que dans une modale alors que `RetourOperation` vit dans `commun` et sert des écrans où « fermer » n'en a aucun ; et rien ne garantit que la correspondance reste totale, un succès qu'on voudrait laisser lire étant un `SUCCES` qui ne ferme pas.

## Conséquences

Trois classes portent désormais cette séparation — `MessagesAudio`, `MessagesLot`, `MessagesRattachement` — chacune exposant l'état de son écran et un `RetourOperation`. Un quatrième cas se factorisera plutôt qu'il ne se recopiera.

Les règles de sévérité se lisent dans les valeurs : un **guidage** (champ mal rempli, rien à faire, opération sans objet) est une `INFO` et non une `ERREUR` ; un **résultat partiel** (relevé incomplet, dépôt interrompu) est une `INFO`, parce qu'annoncer un succès mentirait sur ce qui est acquis et une erreur nierait ce qui est passé.

`FormatsLot.messageEtat` a perdu sa branche « Prêt à déposer », devenue le compte rendu explicite de `preparer()`.

## Ce qui a été écarté

**Porter `peutFermer()` sur `RetourOperation`.** La correspondance est totale aujourd'hui, la tentation est réelle. Mais elle salirait un type partagé par une préoccupation de modale, et figerait une coïncidence en contrat.

**Laisser l'état et le retour cohabiter en ordonnant les appels.** C'est l'état antérieur : il fonctionnait, personne ne s'en plaignait, et il tenait à une ligne qu'un refactoring pouvait déplacer sans rien casser de visible.
