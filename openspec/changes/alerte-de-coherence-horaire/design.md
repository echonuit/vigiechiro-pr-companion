## Context

`AnalyseCoherenceHoraire` compare aujourd'hui deux instants à la fenêtre nocturne et rend deux
booléens, `demarrageHorsNuit` et `arretHorsNuit`. Le calcul astronomique lui-même est juste et
éprouvé, `EphemerideSolaire` n'est pas en cause : c'est la question posée qui l'est.

`CycleAcquisition` sait déjà, depuis le journal du capteur, si une nuit s'est refermée normalement et
porte le motif quand ce n'est pas le cas. Cette donnée existe et n'est pas lue par le diagnostic.

Deux surfaces restituent le verdict : l'écran de diagnostic et la commande `diagnostiquer`.

## Goals / Non-Goals

**Goals :**

- Un verdict qui distingue trois situations là où deux booléens n'en distinguaient qu'une.
- Une marge de 30 minutes qui se lit comme **la règle du protocole**, jamais comme un réglage de
  tolérance qu'on pourrait vouloir ajuster.
- Un type qui rende impossible de reposer la mauvaise question.

**Non-Goals :**

- Rendre la marge configurable. Elle vient du protocole Vigie-Chiro, pas de nous.
- Détecter une interruption autrement que par le journal du capteur.
- Toucher `EphemerideSolaire`.

## Decisions

**Un niveau nommé, et non deux booléens.** Le couple de booléens invitait à la question « est-on hors
de la nuit », qui est celle qui a produit le défaut. Un type énuméré à trois valeurs, plus un état
indisponible, ferme cette porte : on ne peut plus demander « hors nuit », on demande le niveau.

Le niveau le plus grave ne se déduit pas des deux autres : il vient d'une source différente. Le
représenter comme un degré sur la même échelle serait une commodité qui masquerait cela.

**La fenêtre exigée est calculée, la plage effective est donnée.** `CoherenceHoraire` porte les deux,
plutôt que le seul verdict. C'est ce que le story mapping demandait déjà sans que ce soit livré, et
c'est ce qui permet à l'observateur de comprendre le niveau au lieu de le subir.

**L'interruption vient de `CycleAcquisition`.** Elle est déjà calculée, elle est un fait consigné par
le capteur, et elle ne demande aucun seuil. L'alternative, déduire un trou d'un intervalle sans
enregistrement, a été écartée : une nuit calme et une nuit interrompue s'y ressemblent, et le dépôt
n'a qu'une seule nuit réelle, dix-huit fois recopiée, donc aucune population sur laquelle asseoir le
seuil qui les séparerait.

**La marge de 30 minutes est une constante nommée du protocole**, portée là où la règle métier vit,
et non un paramètre. La nommer « tolérance » serait déjà la trahir : ce n'est pas une marge d'erreur
qu'on s'accorde, c'est ce que le programme exige.

## Risks / Trade-offs

**Le journal circulaire peut avoir effacé l'interruption.** R19 le dit, et le niveau le plus grave
dépend donc d'une trace qui peut manquer. Le risque est assumé, et il a une conséquence sur la prose :
l'écran ne doit jamais laisser entendre que l'absence d'avertissement prouve une nuit entière. Le lot
#4990 traite l'autre bout de ce même sujet.

**Le changement casse les lecteurs de `CoherenceHoraire`.** Les deux booléens disparaissent, et les
deux surfaces les lisent. Elles changent dans la même demande, sans quoi l'une des deux dirait autre
chose que l'autre pendant un temps.

**Un niveau « information » peut faire du bruit.** Il se déclenche sur toute nuit plus large que la
fenêtre, c'est-à-dire, si les observateurs suivent le protocole avec de la marge, sur la plupart. Il
n'est pas un défaut et ne doit pas se présenter comme tel : c'est un fait, au même titre que la plage
elle-même. Si l'écran le rend visuellement équivalent à un avertissement, il aura reproduit le défaut
qu'on corrige.
