# Audit de cohérence

Votre travail vit à **trois endroits** : sur le **disque** (les fichiers audio), dans la **base** (ce que
l'application en sait) et sur **Vigie-Chiro** (ce que la plateforme en a reçu). Ces trois-là peuvent
diverger — un disque débranché, un fichier renommé à la main, un dépôt incomplet — et le plus souvent,
**sans rien dire**.

L'écran **Audit de cohérence** existe pour que plus rien ne diverge en silence. Il **confronte** les trois
et vous **énumère** les écarts.

![L'audit de cohérence : une nuit dont les fichiers ont disparu du disque, et un nom de fichier qui ne respecte pas la convention.](../assets/captures/apercu-audit.png)

## Lire un constat

Chaque ligne est un **écart**, et se lit de gauche à droite :

| Colonne | Ce qu'elle dit |
|---|---|
| **Gravité** | `ERREUR` (à traiter), `AVERTISSEMENT` (à regarder), `INFO` (normal, mais bon à savoir) |
| **Catégorie** | la nature de l'écart (fichier manquant, préfixe non conforme, dépôt divergent…) |
| **Passage** | la nuit concernée — **double-cliquez** pour l'ouvrir |
| **Cible** | le fichier ou l'élément en cause |
| **Détail** | ce qui ne va pas, en clair |

Le bandeau du haut résume : *« 5 écarts : 1 erreur, 0 avertissement, 4 infos »*.

!!! tip "Tout n'est pas une anomalie"
    Un constat en **INFO** ne demande souvent **aucune action** : une nuit **archivée** n'a plus ses
    fichiers sur le disque, et c'est **voulu**. L'audit le dit — parce qu'un audit qui se tairait sur un
    état normal vous laisserait croire que le disque est intact, et un audit qui **crierait** sur un état
    normal finirait par ne plus être lu du tout.

## Aller au passage accusé

Un constat **nomme** la nuit fautive. **Double-cliquez** sur la ligne : la fiche du passage s'ouvre, avec
son contexte (carré, point). Vous n'avez pas à la retrouver à la main.

Un **clic droit** sur le constat propose la même chose sous forme de menu, avec de quoi **copier** le
n° de passage ou le motif du constat pour les recoller ailleurs. Les entrées sont **grisées** quand le
constat ne cite aucun passage : voir [Agir sur une ligne](../personnaliser-les-tableaux.md#agir-sur-une-ligne-double-clic-et-clic-droit).

## Auditer une seule nuit

Après avoir **réparé** une nuit (réimporté des fichiers, réactivé un passage archivé), vous voulez vérifier
**celle-là** — pas relancer l'audit de tout un workspace qui en compte des dizaines.

Sélectionnez un constat qui cite un passage, puis **« Auditer ce passage »**. Le bouton reste **désactivé**
tant qu'aucune nuit n'est sélectionnée, et son infobulle vous dit **pourquoi**. La même action figure au
**clic droit** de la ligne, si vous préférez rester sur le tableau.

## Vérifier en ligne

**« Vérifier en ligne »** ajoute les écarts qui demandent le réseau : ce que Vigie-Chiro a **réellement
reçu** de vos dépôts, et les **points d'écoute** que la plateforme connaît. Hors connexion, l'audit
fonctionne quand même : il se limite au disque et à la base, et vous le dit.

### Les points d'écoute, dans les deux sens

L'audit compare vos points à ceux de la plateforme **dans les deux sens**, mais il ne dit pas la même
chose des deux.

- **Un point d'ici que Vigie-Chiro ne connaît pas** est toujours signalé : vous l'avez créé localement, ou
  il a été supprimé côté plateforme.
- **Une localité que Vigie-Chiro connaît et que vous n'avez pas** ne l'est **pas systématiquement**.
  L'application la **crée** au prochain rapprochement, et c'est **voulu** : c'est exactement ce qui permet
  de tout retrouver après une réinstallation ou un [redémarrage sur base neuve](index.md#repartir-dune-base-neuve).
  Le signaler à chaque fois ne ferait que du bruit.

!!! warning "Sauf quand ce silence cache du travail"
    Si cette localité inconnue **porte des nuits que vous n'avez pas ici**, l'audit le **dit** - et il
    **nomme les nuits**. Le point serait créé sans un mot, et vous ne sauriez jamais qu'il existe, sur la
    plateforme, des nuits entières qui vous manquent : déposées depuis un autre poste, ou avant une
    réinstallation.

    C'est le seul cas où l'absence d'un point raconte vraiment quelque chose. Vous pouvez alors
    **reconstruire** ces nuits (voir [Passage](passage.md)), ou vérifier que vous travaillez bien sur le
    poste que vous croyez.

## En ligne de commande

```bash
./vigiechiro audit-coherence                    # tout le workspace
./vigiechiro audit-coherence --passage 12       # une seule nuit
./vigiechiro audit-coherence --online --json    # avec le réseau, pour un script
```

La commande sort en **`0`** si aucun écart d'erreur n'est trouvé : un script peut donc s'en servir comme
d'un feu vert.
