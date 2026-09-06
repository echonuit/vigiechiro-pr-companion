---
type: adr
title: "Une exemption se déclare, elle ne s'infère pas"
status: stable
article: A3
chantier: "#5380 (le dispositif sait dire « sans objet »), lot #5398"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "scripts/batterie.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Une exemption se déclare, elle ne s'infère pas

## Le contexte

`scripts/batterie.py` lance tous les gardes qu'un diff engage. L'un d'eux, `compte-les-reliquats.py`,
est un cliquet **différentiel** : lancé nu il imprime son usage et ne juge rien. Le compter refus
ferait croire à un défaut du diff.

La porte l'écartait donc, en lisant sa sortie : première ligne commençant par « Usage ». Aucune autre
condition. Un garde qui **refuse** et dont le message débute par ce mot était donc classé « n'a pas
jugé », retiré du compte des refus, et la porte finissait verte. « Usage abusif de » est une tournure
que la prose de ce dépôt emploie : le cas n'était pas théorique.

## La mesure qui décide

Deux gardes du dépôt exigent des arguments, et ils n'ont pas la même forme.

| | `compte-les-reliquats.py` | `convertit-adr-okf.py` |
|---|---|---|
| idiome | `raise SystemExit(...)` | `argparse` |
| code de sortie | 1 | 2 |
| lignes | 1 | 2 |

Toute condition sur le code ou sur le nombre de lignes est donc calibrée sur l'un **contre** l'autre.
Une première rédaction de ce lot proposait `len(lignes) == 1` : elle aurait compté refus tout garde
passant par `argparse`, c'est-à-dire l'idiome majoritaire, employé par cinq gardes du dépôt.

## La décision

**L'exemption est déclarée, dans `EXIGENT_DES_ARGUMENTS`, avec son motif. Elle n'est plus inférée.**

C'est l'[ADR 5373] appliquée à une exemption plutôt qu'à une population : la déclaration est
confrontée à ce que le garde fait vraiment, à chaque lancement, donc elle n'est pas une liste.

## Les deux sens ne se valent pas, et c'est le fond

La confrontation est **asymétrique**, et ce n'est pas un compromis.

**Une déclaration périmée refuse.** Le jour où un garde déclaré cessera d'exiger ses arguments, son
exemption masquerait un vrai refus. C'est un faux vert, la direction dangereuse, et la porte rend
alors `declaration-perimee`, qui compte comme un refus.

**Un garde neuf non déclaré est simplement compté refus.** La porte ne cherche pas à le reconnaître.
Sa ligne d'usage s'affiche, le lecteur comprend, et il déclare. C'est un faux **rouge** : visible,
sans danger, et il se corrige en une ligne.

Vouloir la symétrie rouvrirait le défaut. Reconnaître un garde neuf demanderait de deviner à partir
de sa sortie, or c'est précisément ce que la mesure ci-dessus interdit, et ce que la première
rédaction de ce lot a tenté avant qu'un cas de son propre auto-test ne la démente.

## L'aiguillage est total, et refuse par défaut

`rendre` décidait sur `verdict == "rouge"`, si bien que `declaration-perimee` tombait dans la branche
verte. Le verdict juste était calculé et rangé du mauvais côté.

La table `SANS_REFUS` nomme donc les deux seuls verdicts qui ne sont pas des refus. Tout verdict neuf
compte comme un refus tant que personne ne l'y a rangé, ce qui est le même parti que l'[ADR 5340] :
le défaut penche du côté coûteux, jamais du côté muet.

## Comment on le sait

`scripts/batterie.py --auto-test` porte trois cas, chacun vu rouge sur sa propre mutation : un refus
commençant par « Usage » reste un refus, une déclaration périmée refuse au lieu d'exempter, et un
verdict de désaccord est compté refus par `rendre`.

[ADR 5340]: 5340-un-garde-qui-ne-declare-pas-ses-chemins-est-lance.md
[ADR 5373]: 5373-une-liste-qu-un-garde-confronte-est-un-inventaire.md
