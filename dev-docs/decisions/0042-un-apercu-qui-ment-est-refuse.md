---
type: adr
title: "Un aperçu qui ment est refusé, et l'exception se déclare dans la vue"
status: stable
article: A4
chantier: "#2049, #1641, #1873, #1579, #2129, #5113 (EPIC #5504)"
decided_at: 2026-07-20
verification: certaine
enforced_by:
  - "ApercuFxElisionTest#bouton_tronque_refuse"
  - "LisibiliteCaptureCelluleDeTableTest#une_cellule_coupee_est_refusee"
  - "LisibiliteCaptureCelluleDeTableTest#une_marque_sans_infobulle_est_refusee"
verified:
  - by: machine:ci
    at: 2026-07-20
  - by: machine:ci
    at: 2026-10-07
---

# Un aperçu qui ment est refusé, et l'exception se déclare dans la vue

## Contexte

Cinq issues ouvertes disaient la même chose sous cinq formes : « des libellés sont tronqués ». Toutes étaient nées d'une **revue à l'œil**, en passe 8 d'une clôture. Aucun test ne rougissait : un test vérifie qu'un bouton **fait** ce qu'il doit, il ne vérifie pas qu'on puisse **lire** ce qu'il dit.

Le harnais de capture rend une scène de taille fixe. L'application, elle, monte ses vues dans un `ScrollPane` permanent : ce qui déborde **défile**. La capture n'a pas ce recours, donc ce qui déborde se **déforme**, de deux façons distinctes :

| Mécanisme | Ce qu'on voit | Ce qui le cause |
|---|---|---|
| Compression verticale | Un libellé `wrapText` se rabat sur une ligne et s'ellipse | La scène est trop courte |
| Ellipse horizontale | Un bouton, un en-tête de colonne s'ellipse | Le contrôle est trop étroit pour son texte |

Dans les deux cas le PNG est produit, il a l'air normal, et il **documente** un écran qui n'existe pas. Personne ne le voit, parce que personne n'a de raison d'ouvrir l'image.

## Décision

**1. `ApercuFx` refuse d'écrire un PNG déformé, il ne se contente pas d'avertir.** Un avertissement dans un journal de CI que personne ne lit ne vaut pas mieux que le silence d'avant. Le refus arrête la chaîne de captures, donc il se traite.

Le message nomme le libellé fautif et **chiffre** ce qui lui manque : sans le chiffre, la correction se cherche en tâtonnant.

**2. Le critère porte sur le libellé, jamais sur la scène.** Comparer la hauteur du contenu à celle de la scène ne marche pas : mesuré sur le Diagnostic, cet écart vaut 1,6 px sur un écran où **rien** n'est élidé, ses conteneurs extensibles absorbant la place sans rien perdre. Pire, mesurée en hauteur **préférée**, une carte annonce 767 580 px de débordement. Un libellé comprimé, lui, occupe moins de hauteur que celle qu'il demanderait pour la largeur dont il dispose : c'est local, vérifiable, et sans faux positif.

**3. Le déficit ne se supprime pas, il se déplace, et on choisit sur qui.** Figer tous les contrôles d'une barre ne fait pas rentrer son contenu : cela le fait déborder. Il faut donc désigner un porteur. La règle : **un sélecteur ou une métadonnée avant un libellé d'action**. Un mode se relit au déroulé, un nom de fichier se relit dans la table d'à côté ; un bouton coupé ne se relit nulle part.

**4. L'exception se déclare dans la vue, par la classe CSS `abregeable`.** Elle vit dans le FXML et non dans une liste tenue par l'outil, pour se lire **à l'endroit où elle s'applique**, par qui modifie la vue. C'est une classe **marqueur** : elle ne porte aucune règle de style, et ne doit pas être supprimée comme CSS morte. Elle s'hérite jusqu'aux libellés internes des contrôles composés (`ComboBox`, `MenuButton`), qu'un FXML ne peut pas marquer directement.

> **Révisé le 2026-10-07 (#5113, livré par #6111).** « Dans le FXML » n'est plus exact : la marque vit dans la vue, en FXML ou en Java. Et pour une **cellule de table**, elle ne vaut plus seule : il lui faut une infobulle qui rende le texte. Le détail est dans la révision du 2026-10-07, plus bas.

**5. Un composant tiers est hors du contrôle.** `AudioView` vient d'un artefact séparé : ses boutons de transport tronquent, et aucun FXML d'ici ne peut y remédier. Un verrou qui exige une correction impossible ne protège rien, il bloque. Ces défauts se traitent en amont (audio-view#56) et l'exclusion tombera quand ce sera publié.

> **Levée le 2026-07-20.** audio-view#56 est corrigée (sa barre de transport est passée en `FlowPane`, elle plie au lieu de tronquer) et publiée en **1.15.1**. L'exclusion est **retirée** : le sous-arbre `AudioView` repasse sous le contrôle. Le principe énoncé ici reste valide et se réappliquera si un composant tiers redevient infixable d'ici, mais tant qu'une chaîne *peut* être verte, la rendre aveugle coûterait plus qu'elle ne rapporte : une régression amont ne se verrait plus.

## Révision du 2026-10-07 : pour une cellule de table, la marque ne vaut qu'avec son infobulle

Le lot #5113, livré par #6111, a appris au garde un mode de troncature qu'il ne voyait pas et a posé une condition sur l'exception. La décision tient. Quatre passages de cette page ne décrivent plus ce que `LisibiliteCapture` fait.

| Ce que cette page dit | Ce que le garde fait depuis #6111 |
|---|---|
| Contexte : « de deux façons distinctes » | Il juge **quatre** modes : les deux du tableau, l'**invite** d'un champ de saisie coupée sans ellipse (#3170), et la **cellule de table** qui dessine autre chose que le texte reçu (#5113). Le critère de largeur y était aveugle : une `TableCell` demande la largeur de sa colonne, jamais celle de son texte. |
| Point 1 : le message « chiffre ce qui lui manque » | Pour une cellule, il ne chiffre aucun manque. Il donne le texte **dessiné**, le titre de la colonne et sa largeur, précédés de l'identifiant de la colonne (`#colDetail`) ou, à défaut, des quarante premiers caractères du texte reçu. |
| Point 4 : la marque « vit dans le FXML » | Elle vit dans la vue, en FXML **ou en Java**. Trois colonnes la reçoivent par `ColonneAbregeable.assumer(colonne)`, qui pose l'infobulle puis la marque ; `PanneauCompteRendu` la posait déjà en Java. |
| Conséquences : « assumer par `abregeable` » | L'en-tête du message propose toujours cette option sans condition. Pour une cellule de table elle ne suffit pas : qui la suit reçoit un second refus, et c'est celui-là qui nomme l'infobulle. |

**La condition, telle que le code la tient.** Une cellule coupée n'est exemptée que si elle porte la marque, ou l'un de ses parents, **et** si son infobulle contient le texte qu'elle a reçu. JavaFX recopie sur la cellule les classes de sa colonne : marquer la colonne suffit, la table aussi. La marque seule est refusée, avec la mention « sans infobulle qui rende le texte entier » ; l'infobulle seule aussi, comme celle qui dit autre chose.

**Elle s'arrête aux cellules de table.** Un libellé marqué reste exempté de l'ellipse horizontale par la marque seule : `choixMode`, `lblSeqMeta` et le résumé des motifs de `PanneauCompteRendu` se relisent ailleurs qu'au survol, et l'[ADR 3760](3760-le-deficit-se-porte-il-ne-se-repartit-pas.md) dit ce que cet aveu coûte. La marque n'a jamais exempté ni la compression verticale ni l'invite coupée.

**Ce que cette révision n'est pas.** Ni une ADR neuve, le porteur ayant tranché à la clôture de #5504 que la règle précise le point 4 sans en poser une autre ; ni un encart « Ce qui fait foi aujourd'hui », réservé aux relations déclarées en en-tête. Le banc est `LisibiliteCaptureCelluleDeTableTest`, et ce que le garde ne juge pas dans une table est dans [`captures.md`](../captures.md).

## Conséquences

- La troncature cesse d'être un défaut qu'on découvre en regardant : elle arrête la chaîne.
- Le remède se lit dans le message d'erreur, avec ses trois options : figer par `minWidth="-Infinity"`, élargir la colonne, ou assumer par `abregeable`.
- **Le coût est réel** : tout futur libellé trop long bloque la production des aperçus tant qu'il n'est pas traité. C'est le prix pour que la revue à l'œil cesse d'être le seul filet, et il a été accepté en connaissance de cause.
- La mesure d'ouverture a trouvé 65 constats sur 25 outils, qui se réduisaient à **12 contrôles distincts sur 5 écrans** : le reste était le même écran capturé dans plusieurs états. Un chiffre brut de constats ne dit pas l'ampleur d'un défaut.

## Ce qui a été écarté

**Avertir sans bloquer.** C'est l'état d'avant, sous un autre nom. Les cinq issues prouvent que ce qui n'arrête pas la chaîne n'est pas traité.

**Élargir les scènes de capture jusqu'à ce que tout tienne.** Fait disparaître le symptôme de l'image sans rien changer pour l'utilisateur qui travaille en fenêtre étroite : déjà écarté par [ADR 0037](0037-une-barre-d-actions-plie-elle-ne-tronque-pas.md) pour #1701.

**Une liste d'exceptions dans `ApercuFx`.** Elle se serait périmée en silence : rien n'oblige qui modifie une vue à aller lire un outil de capture. La classe CSS est sous ses yeux.
