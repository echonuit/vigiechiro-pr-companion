# ADR 2358 - Un compte rendu d'opération lourde se rend en proportions, et ses règles sont tenues par le type

- **Statut** : Accepté - 2026-07-28
- **Chantier** : #2358 (lot 2 de l'EPIC #2350)
- **Prolonge** : [ADR 0031](0031-un-retour-n-est-pas-un-compte-rendu.md)
- **Vérification** : certaine - `CompteRenduChiffreTest#ventilation_non_exhaustive_refusee`

> La règle d'exhaustivité est celle que le **type** tient : la construction refuse un total que ses
> segments ne font pas. Les deux autres règles ont leurs propres tests, dans la même veine :
> `PanneauCompteRenduTest#largeurs_proportionnelles_aux_quantites` mesure les largeurs **après mise en
> page** plutôt que de relire la formule, et `PanneauCompteRenduTest#action_suivante_est_cablee`
> vérifie que le pied mène quelque part.

## Contexte

L'[ADR 0031](0031-un-retour-n-est-pas-un-compte-rendu.md) a distingué trois natures : l'**état**, le **retour d'opération** borné, et le **compte rendu** extensible : et donné au dernier sa propre surface. Elle n'a pas dit **sous quelle forme** ce compte rendu rend ses comptes.

En pratique, il les rendait en **listes**. La fin d'import annonçait « 583 fichiers importés », puis une liste de rejets, puis une liste d'avertissements. Tout y était, rien ne s'y voyait : il fallait **lire** pour savoir si la nuit était exploitable, et lire ne donne pas les proportions. Les trois questions réelles de l'utilisateur à cet instant, est-ce que ça s'est bien passé, qu'est-ce que ça m'a coûté, qu'est-ce que je fais maintenant, n'appellent aucune liste.

Le même besoin revenait à l'identique en fin de réactivation et en fin de publication, avec des données **déjà produites**. Ce qui manquait n'était pas de la donnée, c'était une surface.

Une surface de proportions pose un risque que les listes n'ont pas : **une barre fausse ment avec l'autorité du visuel**. La première maquette de ce chantier en a fait la démonstration, elle proclamait trois règles et en violait deux : 128 px/Go sur une barre contre 94 sur l'autre, et des segments empilés se recouvrant. Une règle affichée en commentaire ne protège de rien.

## Décision

**1. Un compte rendu d'opération lourde se rend en proportions.** Le modèle est `CompteRenduChiffre` (`commun.viewmodel`), la surface `PanneauCompteRendu` (`commun.view`) : une bande dense, présentationnelle pure, qui n'appartient à aucune feature. Chaque feature **traduit** ce qu'elle a déjà produit, `CompteRenduChiffreImport`, `CompteRenduChiffreReactivation`, `CompteRenduChiffrePublication`, sans rien recalculer.

**2. Les trois règles sont tenues par le type, pas par la discipline.**

| Règle | Ce qui la tient |
|---|---|
| Les proportions sont à l'échelle | la largeur de chaque segment est **liée** à la fraction que le modèle calcule ; il n'existe aucun endroit où poser une largeur à la main |
| Un ensemble se ventile entièrement | le constructeur de `Ventilation` **refuse** un total que ses segments ne font pas, en nommant le reliquat |
| Le compte rendu ne se termine pas sur « Fermer » | l'action suivante est un composant du modèle, fourni par l'écran, qui seul sait où mènent ses boutons |

La deuxième a attrapé son propre auteur : la ventilation de la réactivation donnait une part aux séquences **divergentes**, alors qu'un fichier divergent est un fichier refusé **dont la séquence reste manquante**. La construction a refusé 15 segments pour un total de 14.

**3. Chaque mention porte sa sévérité.** `Avertissement(texte, severite)` : un fait de contexte s'annonce par un « i », une bonne nouvelle par une coche, ce sur quoi il faut revenir par un triangle. Un compte rendu qui alerte sur « L'audio est de nouveau complet » apprend à ne plus regarder ses alertes.

**4. Le compte rendu textuel ne disparaît pas partout, et le critère est le consommateur.** Là où une **commande en ligne** le rend (la réactivation), il reste : un terminal ne dessine pas de barres. Là où la seule surface était l'écran (la publication), le chiffré le **remplace**, garder les deux ferait deux rédactions du même fait, donc deux endroits où corriger une erreur. Les deux lisent le même rapport et partagent titre, préambule et conclusion.

## Conséquences

- Une opération lourde qui gagne une surface de fin gagne aussi, gratuitement, l'exhaustivité vérifiée et les proportions justes : il lui suffit de traduire son bilan.
- Un bilan **doit** être ventilable pour entrer dans la bande. Un compte rendu qui n'a rien à ventiler (un passage reconstruit, dont la réactivation n'a pas eu lieu) garde le textuel ; une barre « 0 sur 30 » ferait croire à une tentative qui a échoué là où il n'y a pas eu de tentative.
- La bande vit dans des largeurs très différentes : 900 px sous l'écran d'import, ~560 px dans une modale. Les libellés doivent donc s'enrouler ou refluer, et ce qui assume de s'abréger doit le déclarer (`abregeable`). Le garde-fou anti-troncature des captures est ce qui l'a imposé, deux fois.
- Trois traductions coexistent sans socle commun : elles partagent le modèle, pas leur logique de traduction. C'est assumé, leurs ventilations n'ont ni les mêmes catégories ni les mêmes causes, mais c'est le point à surveiller si une quatrième arrive.

## Alternatives écartées

**Étendre `VueCompteRendu` (le compte rendu textuel) avec des barres.** Rejetée : les deux formes n'ont pas le même modèle. Le textuel porte des constats à sévérité et des détails ; le chiffré porte des quantités qui doivent se sommer. Les fondre aurait donné un type qui ne peut rien garantir des deux côtés.

**Laisser chaque écran dessiner ses propres barres.** C'est ce que la maquette d'origine supposait, et c'est exactement ce qui produit deux échelles dans un même bloc. La règle ne tient que si un seul endroit décide des largeurs.

**Un `Severite` global au compte rendu, sans sévérité par mention.** Rejetée après la première capture de la réactivation : le composant posait un triangle d'alerte devant une bonne nouvelle. La sévérité de l'ensemble ne dit rien du registre de chaque phrase.
