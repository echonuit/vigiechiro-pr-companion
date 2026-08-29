# Les clips des cas perceptifs

Un cas **perceptif** ne se prouve pas par une assertion : il décrit ce qu'un écran fait *pendant*
qu'il le fait, et le verdict revient à qui regarde. Ces neuf-là sont ceux qu'aucun test ne tranche.

La phrase sous chaque clip dit **ce qu'il faut y voir**. Si ce n'est pas ce que vous voyez, le cas est
rouge.

Comment ces clips sont produits et où ils vivent : [Regarder les clips de recette](clips.md).

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
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifRecuperationCarreTest.la_recuperation_ramene_sur_mes_sites.mp4"></video>

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


## ScenarioCourbeActiviteTest

Les six cas partagent **un seul clip** : ils décrivent la même image, regardée six fois pour six
raisons. Le banc garde ce qui est mécanique - cinq courbes et pas six, un croisement présent, l'aplat
sous les courbes, aucun trou entre les tranches, un axe qui n'excède pas la nuit, aucune étiquette de
pic. Ce qui suit est ce qu'il **ne peut pas** garder.

> **Les détections sont semées, non analysées.** Mesuré : la nuit nominale importée rend zéro série et
> l'état vide, parce que les espèces viennent de l'analyse Tadarida, qui ne tourne pas sur le banc. Le
> préambule importe donc pour de vrai, puis le banc pose six espèces sur ce passage-là. Ce que le clip
> montre est l'écran ; ce qu'il ne montre pas, c'est d'où viennent les contacts.

### S6-04 · `lire_la_courbe_d_activite`

**Les cinq couleurs se distinguent.**

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCourbeActiviteTest.lire_la_courbe_d_activite.mp4"></video>

Cinq courbes, cinq teintes : se distinguent-elles à cette taille d'écran, sans effort ? Le banc garde
qu'il y en a cinq et que la sixième espèce détectée reste dehors ; que les teintes se séparent à l'œil
ne se prouve pas.

### S6-05 · `lire_la_courbe_d_activite`

**Deux courbes qui se croisent.**

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCourbeActiviteTest.lire_la_courbe_d_activite.mp4"></video>

Deux des courbes se croisent - l'une décroît quand l'autre monte, semé exprès. Peut-on suivre chacune
à travers le croisement, ou se perdent-elles l'une dans l'autre ?

### S6-06 · `lire_la_courbe_d_activite`

**L'aplat nocturne.**

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCourbeActiviteTest.lire_la_courbe_d_activite.mp4"></video>

L'aplat pâle marque la fenêtre coucher vers lever. Se distingue-t-il du fond sans masquer les courbes
qui le traversent ?

### S6-07 · `lire_la_courbe_d_activite`

**Les tranches sans contact.**

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCourbeActiviteTest.lire_la_courbe_d_activite.mp4"></video>

Une tranche sans contact descend à zéro au lieu d'interrompre la ligne. Le trait touche-t-il l'axe
sans s'y confondre ?

### S6-08 · `lire_la_courbe_d_activite`

**Les bornes de la nuit.**

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCourbeActiviteTest.lire_la_courbe_d_activite.mp4"></video>

Rien n'est tracé avant le début ni après la fin de l'enregistrement, sur un cadre fixe de 18 h à 8 h.
Le regard voit-il où la nuit commence et finit ?

### S6-09 · `lire_la_courbe_d_activite`

**Nommer une courbe sans la légende.**

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCourbeActiviteTest.lire_la_courbe_d_activite.mp4"></video>

Les étiquettes de pic ont été retirées : sans regarder la légende, on ne doit pas pouvoir nommer une
courbe. C'est le prix assumé de ce retrait, et il se juge ici.

> **À confronter à un relecteur daltonien si possible**, comme la case le demande. Le dépôt ne peut pas
> le tenir seul, et le banc ne simule aucune déficience de vision : cela donnerait une assurance qu'il
> n'a pas. La vérification reste humaine, et non tenue.
