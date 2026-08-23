# Regarder les clips de recette

Un cas **perceptif** ne se prouve pas par une assertion : il décrit ce qu'un écran fait *pendant*
qu'il le fait, et le verdict revient à qui regarde. Cette page est l'endroit où on regarde.

## Où vivent les clips, et pourquoi pas ici

Les clips sont attachés à une **pré-version roulante**,
[`clips-recette`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-recette).
Ils ne sont pas dans le dépôt, et c'est délibéré.

Une pièce jointe de publication ne pèse **rien** dans l'historique git : les 459 Mio attachés à
`v2.185.0` n'y laissent aucun blob. À l'inverse, `.git` pèse aujourd'hui 957 Mio, dont **79 % de
PNG** - 155 captures présentes au 22/08/2026, mais 4 507 versions dans l'historique, parce qu'une capture qui
change d'octets produit un commit et qu'une capture à carte change d'octets à chaque exécution.
Ranger des vidéos de la même façon reviendrait à recommencer, en plus lourd.

La pré-version est **roulante** : ses pièces sont remplacées à chaque tournage complet et leurs
adresses ne changent pas. C'est ce qui permet à cette page de les écrire en dur.

!!! warning "Elle ne marque aucune version du produit"

    `clips-recette` porte le dernier tournage, pas un état figé. Elle est marquée pré-version pour
    qu'elle ne devienne jamais la « dernière version » du dépôt.

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

⚠️ Son garde compte les **cas**, pas les fichiers : un tournage qui rend « des clips » peut en avoir
perdu la moitié, et un compte de fichiers ne le dirait pas.

## Deux familles, deux pages

| | Ce qu'un clip y sert | Combien |
|---|---|---|
| [Cas perceptifs](clips-perceptifs.md) | **le regarder** : c'est le seul verdict qui existe pour ces cas | 9 |
| [Cas assertés](clips-assertes.md) | comprendre **pourquoi un test rougit**, ou vérifier qu'il joue ce que son nom annonce | 46 |

L'adresse d'un clip se déduit de son nom :

```
https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/<Classe>.<test>.mp4
```

!!! note "Un lecteur vide n'est pas un cas rouge"

    Si un clip ne se lance pas, c'est que le tournage complet n'a pas eu lieu depuis que ce cas
    existe. Relancer le flux avec `publier_les_clips` avant de conclure quoi que ce soit sur le
    produit. `PageDesClipsTest` garde la correspondance entre ces pages et les cas joués, pas la
    présence des fichiers.
