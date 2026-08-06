# Polices embarquées

Le produit **embarque** sa typographie au lieu de l'emprunter à la machine qui l'exécute
([ADR 3361](../../../../dev-docs/decisions/3361-la-typographie-est-embarquee.md)). Sans cela, la
feuille de style nomme des familles qui ne sont installées nulle part, le rendu retombe sur un
**alias** (`sans-serif`, `monospace`) que chaque système résout à sa façon, et deux utilisateurs ne
voient pas la même chose.

| Fichier | Famille | Demandée par |
| --- | --- | --- |
| `NotoSans-Regular.ttf`, `NotoSans-Bold.ttf` | `Noto Sans` | `base.css` - toute l'application |
| `NotoSansMono-Regular.ttf`, `NotoSansMono-Bold.ttf` | `Noto Sans Mono` | `lot.css` (`.chemin`), `importation.css` (`.apercu-valeur`) |

Les quatre fichiers viennent du projet **Noto** de Google et sont distribués sous la **SIL Open Font
License 1.1**, dont le texte intégral est dans `LICENSE-Noto-OFL-1.1.txt`. Cette licence permet
l'incorporation dans un logiciel, y compris commercial, et n'impose pas au logiciel hôte de changer de
licence - le produit reste sous GPLv3.

`Typographie` les charge, et `TypographieTest` vérifie qu'ils sont bien **dans le jar** : c'est le
risque réel, un changement de packaging ou un filtrage de ressources les ferait disparaître sans que
rien n'échoue.
