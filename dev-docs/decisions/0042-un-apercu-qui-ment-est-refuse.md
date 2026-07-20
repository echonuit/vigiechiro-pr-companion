# ADR 0042 — Un aperçu qui ment est refusé, et l'exception se déclare dans la vue

- **Statut** : Accepté — 2026-07-20
- **Chantier** : #2049, #1641, #1873, #1579, #2129

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

**3. Le déficit ne se supprime pas, il se déplace — et on choisit sur qui.** Figer tous les contrôles d'une barre ne fait pas rentrer son contenu : cela le fait déborder. Il faut donc désigner un porteur. La règle : **un sélecteur ou une métadonnée avant un libellé d'action**. Un mode se relit au déroulé, un nom de fichier se relit dans la table d'à côté ; un bouton coupé ne se relit nulle part.

**4. L'exception se déclare dans la vue, par la classe CSS `abregeable`.** Elle vit dans le FXML et non dans une liste tenue par l'outil, pour se lire **à l'endroit où elle s'applique**, par qui modifie la vue. C'est une classe **marqueur** : elle ne porte aucune règle de style, et ne doit pas être supprimée comme CSS morte. Elle s'hérite jusqu'aux libellés internes des contrôles composés (`ComboBox`, `MenuButton`), qu'un FXML ne peut pas marquer directement.

**5. Un composant tiers est hors du contrôle.** `AudioView` vient d'un artefact séparé : ses boutons de transport tronquent, et aucun FXML d'ici ne peut y remédier. Un verrou qui exige une correction impossible ne protège rien, il bloque. Ces défauts se traitent en amont (audio-view#56) et l'exclusion tombera quand ce sera publié.

## Conséquences

- La troncature cesse d'être un défaut qu'on découvre en regardant : elle arrête la chaîne.
- Le remède se lit dans le message d'erreur, avec ses trois options : figer par `minWidth="-Infinity"`, élargir la colonne, ou assumer par `abregeable`.
- **Le coût est réel** : tout futur libellé trop long bloque la production des aperçus tant qu'il n'est pas traité. C'est le prix pour que la revue à l'œil cesse d'être le seul filet, et il a été accepté en connaissance de cause.
- La mesure d'ouverture a trouvé 65 constats sur 25 outils, qui se réduisaient à **12 contrôles distincts sur 5 écrans** : le reste était le même écran capturé dans plusieurs états. Un chiffre brut de constats ne dit pas l'ampleur d'un défaut.

## Ce qui a été écarté

**Avertir sans bloquer.** C'est l'état d'avant, sous un autre nom. Les cinq issues prouvent que ce qui n'arrête pas la chaîne n'est pas traité.

**Élargir les scènes de capture jusqu'à ce que tout tienne.** Fait disparaître le symptôme de l'image sans rien changer pour l'utilisateur qui travaille en fenêtre étroite — déjà écarté par [ADR 0037](0037-une-barre-d-actions-plie-elle-ne-tronque-pas.md) pour #1701.

**Une liste d'exceptions dans `ApercuFx`.** Elle se serait périmée en silence : rien n'oblige qui modifie une vue à aller lire un outil de capture. La classe CSS est sous ses yeux.
