# Regarder les clips de recette

Un cas **perceptif** ne se prouve pas par une assertion : il décrit ce qu'un écran fait *pendant*
qu'il le fait, et le verdict revient à qui regarde. Cette page est l'endroit où on regarde.

## Où vivent les clips, et pourquoi pas ici

Les clips sont attachés à une **pré-version roulante**,
[`clips-recette`](https://github.com/echonuit/vigiechiro-pr-companion/releases/tag/clips-recette).
Ils ne sont pas dans le dépôt, et c'est délibéré.

Une pièce jointe de publication ne pèse **rien** dans l'historique git : les 459 Mio attachés à
`v2.185.0` n'y laissent aucun blob. À l'inverse, `.git` pèse aujourd'hui 957 Mio, dont **79 % de
PNG** - 154 captures présentes, mais 4 507 versions dans l'historique, parce qu'une capture qui
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

## Les neuf cas perceptifs

Ce sont ceux qu'aucune assertion ne tranche. La phrase sous chaque clip dit **ce qu'il faut y voir** ;
si ce n'est pas ce que vous voyez, le cas est rouge.

### S1-26 · la modale de connexion s'ouvre

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifConnexionTest.la_modale_de_connexion_s_ouvre.mp4"></video>

Rien ne doit se replacer après coup : la saisie est en place dès l'ouverture.

### S1-27 · pendant la récupération

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifConnexionTest.la_recuperation_ne_pousse_rien_hors_du_cadre.mp4"></video>

Rien ne sort du cadre avant que le bandeau d'état ait pris sa place.

### S1-37 · récupérer un carré

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifRecuperationCarreTest.la_recuperation_s_enchaine_jusqu_a_la_fiche.mp4"></video>

L'enchaînement « je récupère, la fenêtre se ferme, la fiche s'ouvre » paraît naturel.

### S4-33 · le refus de dépôt

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifRefusDepotTest.le_compte_rendu_dit_les_refus_et_conseille_la_reconnexion.mp4"></video>

La phrase se lit d'un trait, et le conseil de reconnexion ne se noie pas dans le constat.

### S6-25 · une puce fraîchement ajoutée

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifFiltresTest.une_puce_fraichement_ajoutee_n_ecarte_rien.mp4"></video>

La table ne bouge pas tant qu'aucune valeur n'est choisie.

### S6-26 · rouvrir une liste après un autre filtre

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifFiltresTest.rouvrir_une_liste_apres_un_autre_filtre_montre_moins_de_valeurs.mp4"></video>

Elle offre moins de valeurs qu'à la première ouverture, et celles qui restent sont bien celles que
l'autre filtre laisse passer.

### S6-27 · une valeur devenue impossible

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifFiltresTest.une_valeur_cochee_devenue_impossible_se_distingue.mp4"></video>

Elle reste cochée, rangée à part, et se **distingue à l'œil** d'une valeur ordinaire à taille d'écran
habituelle. C'est ce dernier point que le test ne sait pas trancher.

### S6-28 · une vue rejouée sans l'une de ses valeurs

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifFiltresTest.rejouer_une_vue_dont_une_valeur_a_disparu_fait_paraitre_le_bandeau.mp4"></video>

Le bandeau paraît, et la phrase nomme la valeur manquante sans jargon ni clé technique.

### S6-29 · tout effacer

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifFiltresTest.tout_effacer_rend_la_table_entiere.mp4"></video>

La table revient entière et le tri d'origine est remis, en un seul clic.

## Les clips des cas assertés

Le tournage produit **un clip par test**, pas seulement pour les cas perceptifs. Les quarante-six
autres sont sur leur propre page, groupés par classe :
[Les clips des cas assertés](clips-assertes.md).

Ils ne demandent pas qu'on les regarde - un test asserté tranche tout seul - mais ils servent quand
on cherche pourquoi l'un d'eux rougit.

L'adresse d'un clip se déduit de son nom :

```
https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/<Classe>.<test>.mp4
```

!!! note "Un lecteur vide n'est pas un cas rouge"

    Si un clip ne se lance pas, c'est que le tournage complet n'a pas eu lieu depuis que ce cas
    existe. Relancer le flux avec `publier_les_clips` avant de conclure quoi que ce soit sur le
    produit. `PageDesClipsTest` garde la correspondance entre ces pages et les cas joués, pas la
    présence des fichiers.
