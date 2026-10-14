---
type: adr
title: "Un écran reconstruit à chaque ouverture ne tient pas les 50 ms d'O5, et les feuilles du chrome vivent sur la scène"
status: stable
article: A5
chantier: "#6178 (l'interface tient O3 et O5), lot #6207"
decided_at: 2026-10-14
verification: certaine
enforced_by:
  - "FeuillesDuChromeSurLaSceneTest#le_chrome_porte_ses_feuilles_sur_la_scene_et_un_ecran_ne_les_redeclare_pas"
  - "HabillageTest#hisser_monte_les_feuilles_de_la_racine_sur_la_scene"
verification_note: "les tests tiennent la moitié mécanique : les feuilles du chrome sur la scène, et un écran accueilli qui ne les redéclare pas. Que le seuil de 50 ms reste et ne soit pas tenu est un relevé du banc de l IHM, écrit ici avec ses chiffres : ce banc ne tourne pas en CI"
verified:
  - by: machine:ci
    at: 2026-10-14
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# Un écran reconstruit à chaque ouverture ne tient pas les 50 ms d'O5, et les feuilles du chrome vivent sur la scène

## Contexte

`O5.md` demande que, sur cent cinquante carrés de deux points, chaque écran de la topologie s'ouvre
sous 50 ms et ne croisse pas avec le nombre de carrés. Il le donne pour un ordre de grandeur « à
affiner par un premier benchmark ». Mes sites ne croît plus avec les carrés depuis #6172, sans tenir
50 ms pour autant (de 24 à 92 ms à 150 carrés). La vue multisite et l'écran des
sons ne le tenaient pas (#6207).

## Ce que la mesure a établi

`BancIhmO5Test.o5_topologie`, second poste (16 cœurs, carte Intel), échelle 1, à chaud, trois
ouvertures relevées par tir après une ouverture jetée :

| À 150 carrés, rendu GPU | Vue multisite | Écran des sons |
|---|---|---|
| 2026-10-13, 16 h 12, trois tirs | de 134 à 200 ms | de 92 à 130 ms |
| 2026-10-14, 6 h 38, quatre tirs, avant le remède | de 199 à 294 ms, médiane 213 | de 123 à 172 ms, médiane 152 |
| même heure, quatre tirs alternés, après | de 141 à 256 ms, médiane 187 | de 133 à 190 ms, médiane 145 |

**Le navigateur reconstruit l'écran à chaque ouverture** : FXML rechargé, nœuds recréés, stylés, mis
en page. Un profil JFR de ces ouvertures place la moitié à deux tiers des échantillons du fil JavaFX
dans le CSS des nœuds neufs, jusqu'à un tiers dans le chargeur FXML, et moins de 2 % dans les
lectures en base. Le rendu logiciel est plus lent encore (de 124 à 472 ms pour la vue multisite).

**Une méthode de JavaFX prenait 18 % des échantillons** de la vue multisite à 150 carrés :
`StyleManager.RefList.add`. Pour chaque feuille déclarée sur un parent, JavaFX enregistre tous les
parents qu'il contient, et reparcourt cette liste à chaque parent neuf qu'il style. `palette.css`,
`base.css` et `design.css` étaient déclarées sur la racine du chrome et redéclarées sur celle de chaque
écran : après vingt ouvertures, chacune comptait 187 parents, que seul le ramasse-miettes retire.

## Décision

**Les feuilles du chrome vivent sur la scène, et un écran accueilli ne les redéclare pas.**
`Habillage.hisser` les monte de la racine à la scène quand la fenêtre principale reçoit la sienne ;
`Habillage.alleger` retire de chaque vue accueillie ce que la scène porte déjà. La feuille de
fonctionnalité reste sur sa vue. Les FXML ne changent pas : un écran chargé seul, dans une modale, une
capture ou un test, garde ses feuilles.

Effet mesuré : 13 parents par feuille commune au lieu de 187, et la méthode passe de 18 à 6 % des
échantillons. Huit écrans ouverts par le chrome rendent la même image avant et après, au pixel près hors du
curseur clignotant du champ de recherche.
**Le gain en temps n'est pas établi** : la médiane de la vue multisite baisse de 12 % en rendu GPU,
mais les étendues se recouvrent, l'écran des sons ne bouge pas, et deux tirs en rendu logiciel vont
dans l'autre sens. Le remède est gardé pour ce qu'il retire, un coût qui croissait avec la session.

**Le seuil de 50 ms reste, et ces deux écrans ne le tiennent pas.** Il ne se révise pas sur une durée
qui varie de 134 à 294 ms d'un jour à l'autre sur le même code, et rien de ce que le profil désigne ne
l'approche tant qu'un écran se reconstruit à chaque ouverture. La seule voie mesurable est de garder
les écrans vivants entre deux ouvertures, ce qui revient sur leur cycle de vie (ADR 3651 et 6184) et
demande son propre chantier. L'écart est une dette du chantier #6178, qui dit à sa clôture où elle
part.

## Ce qui n'est pas établi

- **Pourquoi une même ouverture coûte jusqu'à moitié plus d'un jour à l'autre**, sur le même code et
  le même poste. Un conteneur d'une autre session a pu croiser le premier tir du 14 ; le reste n'est pas
  expliqué.
- **La croissance avec les carrés.** La vue multisite passe d'une médiane de 144 ms à 15 carrés à 213 ms
  à 150, avant le remède ; O5 demande qu'elle ne croisse pas. Sa part dans le profil n'est pas isolée.
- **Le coût de la feuille de fonctionnalité**, qui reste sur la vue et garde le même mécanisme : 93
  parents enregistrés pour `sites.css` après vingt ouvertures.
- **Un autre poste.**

## Conséquences

- `docs/benchmarks/README.md` porte les relevés, avant et après, dans les deux dessins.
- Le banc de l'IHM écrit l'heure du poste à chaque ouverture, pour qu'un enregistrement JFR se
  découpe par écran.
