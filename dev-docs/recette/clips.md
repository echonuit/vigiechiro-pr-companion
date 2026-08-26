# Regarder les clips de recette

Un cas **perceptif** ne se prouve pas par une assertion : il décrit ce qu'un écran fait *pendant*
qu'il le fait, et le verdict revient à qui regarde. Cette page est l'endroit où on regarde.

## Où vivent les clips, et pourquoi pas ici

Les clips sont attachés à une **pré-version roulante**,
[`clips-recette`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-recette).
Ils ne sont pas dans le dépôt, et c'est délibéré.

Une pièce jointe de publication ne pèse **rien** dans l'historique git : les 459 Mio attachés à
`v2.185.0` n'y laissent aucun blob. À l'inverse, les objets de `.git` pèsent **966 Mio**, dont
**866 Mio de PNG** - 170 captures présentes au 23/08/2026, mais **4 537 versions dans l'historique**,
parce qu'une capture qui change d'octets produit un commit et qu'une capture à carte change d'octets à
chaque exécution. Ranger des vidéos de la même façon reviendrait à recommencer, en plus lourd.

??? note "Comment ce chiffre se remesure"

    Les objets, pack et vrac : `git count-objects -vH` (`size-pack` + `size`). Les blobs PNG et leur
    poids compressé :

    ```bash
    git rev-list --objects --all | grep -E '\.png$' | cut -d' ' -f1 | sort -u > /tmp/b.txt
    git cat-file --batch-check='%(objectsize:disk)' < /tmp/b.txt | awk '{s+=$1} END {print s/1048576, "Mio"}'
    ```

    Mesuré sur un clone de travail, toutes branches distantes rapatriées. Une mesure antérieure
    annonçait 79 % : la méthode n'en était pas consignée, et c'est précisément pourquoi elle l'est ici.

La pré-version est **roulante** : ses pièces sont remplacées à chaque tournage complet et leurs
adresses ne changent pas. C'est ce qui permet à cette page de les écrire en dur.

!!! warning "Elle ne marque aucune version du produit"

    `clips-recette` porte le dernier tournage, pas un état figé. Elle est marquée pré-version pour
    qu'elle ne devienne jamais la « dernière version » du dépôt.

### Et une copie sur le tag de chaque version

Depuis #4258, le train fait tourner **les deux bancs** sur la version qu'il vient de publier, et verse
leurs clips sur son **tag** : préfixés `bash-` pour le banc historique, `java-` pour le banc en Java
pur. Les deux destinations ne servent pas à la même chose :

| | `clips-recette` | le tag `vX.Y.Z` |
|---|---|---|
| ce qu'on y trouve | le **dernier** tournage | le tournage de **cette version-là** |
| les adresses | stables, mais leur **contenu** change | immuables, contenu compris |
| à quoi ça sert | regarder la recette d'aujourd'hui | comparer, et garder une trace |

Un tag ne bouge jamais : une adresse écrite vers `v2.188.0` montrera toujours ce que `v2.188.0`
montrait. La suite des versions forme ainsi un historique visuel du produit, que **rien ne
reconstituera après coup**.

!!! warning "L'historique commence à la version qui a suivi #4258"

    Verser aujourd'hui des clips sur un tag ancien montrerait le produit d'aujourd'hui sous une
    version qui ne l'affichait pas. Les versions antérieures resteront donc sans clips, et c'est
    volontaire.

    Le constat qui a motivé ce choix : en cherchant un « avant » pour l'artefact visuel de la clôture
    de #4133, **aucun clip antérieur au calque des gestes n'a été retrouvé**. Les pièces de la
    pré-version du spike avaient été reversées par-dessus, et la date de publication d'une pré-version
    ne dit rien de la date de son contenu.

Les préfixes ne sont pas décoratifs. Les deux bancs nomment leurs pièces **exactement pareil** :
50 sur 51 sont communes. Sans préfixe, le second versement écraserait le premier **en silence**.

!!! tip "Pourquoi les deux bancs, et pas encore l'un ou l'autre"

    C'est la transition qui se prépare. Tant que le sort du banc bash n'est pas tranché, chaque
    version porte les deux tournages : ils se comparent alors sur le produit du jour, au lieu de se
    comparer sur une page datée qui mourra avec la décision. Le jour venu, il n'y aura qu'un des deux
    appels à retirer de `release.yml`.

    Une différence compte dès aujourd'hui : le banc Java compare les cas **obtenus** aux cas
    **attendus** et échoue s'il en manque un, si bien qu'un tournage amputé ne peut pas se graver sur
    un tag. Le banc bash n'a pas cet oracle, son artefact n'emportant pas le compte attendu ; son
    index versé dit donc en tête ce qu'il contient, pour qu'un trou se constate au lieu de se
    deviner.

## Produire un tournage complet

Le flux **recette filmée** (`workflow_dispatch`), avec `publier_les_clips` coché. Il dérive la liste
des classes qui citent un cas, les filme d'une traite, découpe un clip par test, les remuxe en `mp4`
et les verse sur la pré-version.

Sans cette case, le flux garde son autre rôle : filmer **une** classe pour éprouver qu'un runner
pilote réellement, ce qui est l'objet de `sans_gestionnaire_de_fenetres`.

## Tourner une seule session, ou sur une autre plateforme

Le flux **tournage de recette** (`tournage-recette.yml`, `workflow_dispatch`) prend deux entrées : la
**session** (`toutes`, ou `S1` à `S11`) et la **plateforme** (`ubuntu`, `windows`, `macos`). Il tourne
avec le banc en Java pur, qui n'a besoin ni de serveur X ni de gestionnaire de fenêtres.

C'est le flux à prendre quand on travaille une session et qu'on veut revoir ses clips sans refaire les
quatre cents autres, ou quand on veut savoir à quoi le produit ressemble sur un autre système.

Son garde compte les **cas**, pas les fichiers : un tournage qui rend « des clips » peut en avoir
perdu la moitié, et un compte de fichiers ne le dirait pas.

## Deux familles, deux pages

| | Ce qu'un clip y sert | Combien |
|---|---|---|
| [Cas perceptifs](clips-perceptifs.md) | **le regarder** : c'est le seul verdict qui existe pour ces cas | 9 |
| [Cas assertés](clips-assertes.md) | comprendre **pourquoi un test rougit**, ou vérifier qu'il joue ce que son nom annonce | 46 |

### Et une troisième source, qui ne se compare pas

Depuis #4306, un tournage peut parler à la **vraie** plateforme plutôt qu'à ses fixtures. Ses clips
vivent sur leur propre pré-version, [`clips-connectes`](clips-connectes.md), pour une raison qui n'est
pas de rangement : leur écran suit des **données vivantes**, donc le plancher de bruit y mesurerait la
plateforme au lieu du rendu. `comparer-tournages.yml` refuse cette source, et le dit.

Ce ne sont pas d'autres cas, ce sont les **mêmes** cas vus contre une autre frontière : les quelque 220
dont le verdict se lit hors de l'application, et qu'un bouchon rend convaincants et creux.

L'adresse d'un clip se déduit de son nom :

```
https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/<Classe>.<test>.mp4
```

!!! note "Un lecteur vide n'est pas un cas rouge"

    Si un clip ne se lance pas, c'est que le tournage complet n'a pas eu lieu depuis que ce cas
    existe. Relancer le flux avec `publier_les_clips` avant de conclure quoi que ce soit sur le
    produit. `PageDesClipsTest` garde la correspondance entre ces pages et les cas joués, pas la
    présence des fichiers.
