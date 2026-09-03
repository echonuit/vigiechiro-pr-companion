# Les clips des cas assertés

Les cas **assertés** sont ceux qu'un test tranche tout seul : leur verdict ne demande pas qu'on
regarde. Leur clip existe quand même, et il sert à autre chose - voir *ce que le test a vu* quand on
cherche pourquoi il rougit, ou vérifier qu'il joue bien ce que son nom annonce.

Pour les cas **perceptifs**, ceux dont le verdict revient à qui regarde, c'est l'autre page :
[Les clips des cas perceptifs](clips-perceptifs.md).

!!! note "Un clip noir n'est pas un clip cassé"

    Un test qui n'ouvre aucune fenêtre - un ViewModel, par exemple - produit un clip **noir**, et
    c'est le résultat juste. Il cite un cas, donc il a un extrait ; il ne montre rien, donc il
    s'audite en **lisant** le test. La part d'images utiles de chaque clip est publiée avec eux, dans
    [`index.md`](https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/index.md) - elle n'est pas recopiée ici, parce qu'elle change à chaque
    tournage et qu'une page ne saurait la tenir à jour.

Les adresses viennent de la pré-version roulante `clips-recette`, alimentée par le flux
**recette filmée** avec `publier_les_clips`. Un lecteur vide dit que le tournage n'a pas eu lieu
depuis que ce cas existe, pas que le produit est cassé.

## ScenarioPerceptifIssuesConnexionTest

### S1-04 · `les_trois_etapes_de_la_modale`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.les_trois_etapes_de_la_modale.mp4"></video>

> Aucun navigateur ne s'ouvre sur le banc : ce clip montre le clic, pas la page qu'il ouvre. Ce qui se vérifie est l'adresse transmise au système, et cela se lit dans l'assertion, pas à l'image.

### S1-11 · `la_deconnexion_demande_confirmation`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.la_deconnexion_demande_confirmation.mp4"></video>

### S1-05 · jeton vide

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.jeton_vide_dit_quoi_faire.mp4"></video>

Le message dit le geste qui manque, sans laisser croire que le jeton est en cause.

### S1-06 · jeton refusé

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.jeton_refuse_nomme_le_geste.mp4"></video>

La cause est nommée, et le geste qui répare aussi : on sait quoi faire sans deviner.

### S1-07 · plateforme injoignable

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.injoignable_ne_fait_pas_accuser_le_jeton.mp4"></video>

Une panne réseau ne doit pas se lire « jeton invalide » : on jetterait un jeton valide.

### S1-08 · connexion réussie

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.succes_dit_ce_qui_a_ete_rapatrie.mp4"></video>

Le bandeau dit ce qui a été rapatrié, et l'identité paraît : on sait sous quel compte on déposera.

## ScenarioAccueilTest

### S1-01 · `chaque_carte_ouvre_ce_qu_elle_annonce`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioAccueilTest.chaque_carte_ouvre_ce_qu_elle_annonce.mp4"></video>

### S1-28 · `le_menu_ne_porte_plus_la_fiche_espece`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioAccueilTest.le_menu_ne_porte_plus_la_fiche_espece.mp4"></video>

## MainViewTest

### S1-01 · `accueil_regroupe_en_deux_prismes`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.accueil_regroupe_en_deux_prismes.mp4"></video>

### S1-02 · `bandeau_masque_si_base_vide`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.bandeau_masque_si_base_vide.mp4"></video>

### S1-03 · `ctrl_f_active_la_recherche`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.ctrl_f_active_la_recherche.mp4"></video>

### S1-03 · `fil_ariane_reflete_le_parcours`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.fil_ariane_reflete_le_parcours.mp4"></video>

### S1-09 · `bandeau_suit_une_mutation_sans_navigation`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.bandeau_suit_une_mutation_sans_navigation.mp4"></video>

### S1-10 · `bandeau_suit_une_restauration_sans_navigation`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.bandeau_suit_une_restauration_sans_navigation.mp4"></video>

### S1-29 · `menu_reglages_ouvre_l_ecran`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.menu_reglages_ouvre_l_ecran.mp4"></video>

## MesSitesEtatVideViewTest

### S1-12 · `l_etat_vide_explique_le_prerequis`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesEtatVideViewTest.l_etat_vide_explique_le_prerequis.mp4"></video>

### S1-12 · `l_etat_vide_parait_sur_une_base_vierge`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesEtatVideViewTest.l_etat_vide_parait_sur_une_base_vierge.mp4"></video>

### S1-12 · `l_etat_vide_porte_sa_porte_de_sortie`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesEtatVideViewTest.l_etat_vide_porte_sa_porte_de_sortie.mp4"></video>

## Modale de site · joués sur la fenêtre réelle

### S1-13 · `creer_s_ouvre_au_sixieme_chiffre_et_ajoute_le_carre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.creer_s_ouvre_au_sixieme_chiffre_et_ajoute_le_carre.mp4"></video>

### S1-23 · `renommer_le_carre_met_a_jour_l_entete`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.renommer_le_carre_met_a_jour_l_entete.mp4"></video>

### S1-25 · `annuler_ne_change_rien_a_la_liste`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.annuler_ne_change_rien_a_la_liste.mp4"></video>

## MesSitesViewTest

### S1-14 · `affiche_les_cartes`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.affiche_les_cartes.mp4"></video>

### S1-15 · `entree_ouvre_le_detail`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.entree_ouvre_le_detail.mp4"></video>

### S1-15 · `espace_ouvre_le_detail`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.espace_ouvre_le_detail.mp4"></video>

### S1-15 · `une_carte_est_atteignable_au_clavier`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.une_carte_est_atteignable_au_clavier.mp4"></video>

### S1-16 · `hors_connexion_la_recuperation_est_fermee`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.hors_connexion_la_recuperation_est_fermee.mp4"></video>

### S1-16 · `une_fois_connecte_la_recuperation_s_ouvre_et_rend_compte`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.une_fois_connecte_la_recuperation_s_ouvre_et_rend_compte.mp4"></video>

### S1-17 · `overlay_occupation_masque_apres_chargement`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.overlay_occupation_masque_apres_chargement.mp4"></video>

## ScenarioFicheSiteTest

### S1-18 · `le_bandeau_dit_l_identite_du_carre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.le_bandeau_dit_l_identite_du_carre.mp4"></video>

### S1-19 · `les_boutons_disent_ce_qui_les_empeche`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.les_boutons_disent_ce_qui_les_empeche.mp4"></video>

### S1-20 · `les_cartes_de_points_portent_gps_et_distance`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.les_cartes_de_points_portent_gps_et_distance.mp4"></video>

### S1-21 · `le_tableau_des_passages_porte_ses_sept_colonnes`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.le_tableau_des_passages_porte_ses_sept_colonnes.mp4"></video>

### S1-35 · `le_carre_rattache_porte_son_badge`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.le_carre_rattache_porte_son_badge.mp4"></video>

## ScenarioFicheSiteTest · S1-22

### S1-22 · `renommer_le_carre_met_a_jour_l_entete`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.renommer_le_carre_met_a_jour_l_entete.mp4"></video>

## ScenarioFicheSiteTest · S1-24

### S1-24 · `les_coordonnees_en_dms_valent_une_position`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.les_coordonnees_en_dms_valent_une_position.mp4"></video>

### S1-24 · `ajouter_un_point_le_fait_paraitre_sur_la_fiche`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.ajouter_un_point_le_fait_paraitre_sur_la_fiche.mp4"></video>

## ScenarioModaleCarreTest

### S1-30 · `le_carre_libre_s_annonce_libre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.le_carre_libre_s_annonce_libre.mp4"></video>

### S1-31 · `le_carre_deja_declare_nomme_le_site`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.le_carre_deja_declare_nomme_le_site.mp4"></video>

### S1-32 · `corriger_un_chiffre_efface_le_verdict`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.corriger_un_chiffre_efface_le_verdict.mp4"></video>

### S1-33 · `hors_connexion_verifier_est_ferme_mais_declarer_reste_possible`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.hors_connexion_verifier_est_ferme_mais_declarer_reste_possible.mp4"></video>

### S1-33 · `plateforme_injoignable_l_encart_ne_nie_pas_le_carre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.plateforme_injoignable_l_encart_ne_nie_pas_le_carre.mp4"></video>

### S1-34 · `recuperer_ferme_la_modale_et_rend_compte`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.recuperer_ferme_la_modale_et_rend_compte.mp4"></video>

### S1-36 · `creer_reste_ferme_puis_se_rouvre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.creer_reste_ferme_puis_se_rouvre.mp4"></video>

### S1-38 · `partir_d_un_lieu_remplit_le_carre`

Le geste qui dispense d'aller chercher son numéro sur le portail : une position collée, un clic, et le
champ des six chiffres se remplit.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.partir_d_un_lieu_remplit_le_carre.mp4"></video>

### S1-39 · `situer_marche_hors_connexion`

Celui-ci se regarde pour ce qu'il met **côte à côte**. « Vérifier sur Vigie-Chiro » est grisé, faute de
jeton ; « Situer » fonctionne, parce qu'il ne demande rien à personne. Le carroyage national est
embarqué, le portail non, et c'est la seule image qui le montre.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.situer_marche_hors_connexion.mp4"></video>

### S1-40 · `sur_une_frontiere_rien_ne_se_remplit`

Rien ne se remplit, et ce n'est pas une panne. Deux carrés sont à distance strictement égale, l'écran
les nomme, et l'observateur tranche - lui seul sait de quel côté était le micro.

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.sur_une_frontiere_rien_ne_se_remplit.mp4"></video>


## ScenarioImportNominalTest

L'import nominal de `S2`, en trois gestes qui s'enchaînent sur une seule carte SD. Chacun repart du
détail du carré 640380 et redésigne la carte : trois clips, trois histoires complètes. Un geste qui
reprendrait l'écran laissé par le précédent ne montrerait pas d'où il part.

La carte n'est pas versionnée. Elle est reconstruite depuis sa spec à chaque lancement, à l'octet
près : aucune date tirée de l'horloge, aucun octet au hasard. Ce que le clip montre est donc une
vraie inspection d'un vrai arbre de fichiers, et deux tournages du même commit rendent la même carte.

### S2-01 à S2-07 · `designer_la_source_et_l_inspecter`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioImportNominalTest.designer_la_source_et_l_inspecter.mp4"></video>

> `S2-02` porte un **carton** : le glisser-déposer d'un dossier ne s'enregistre pas, faute de pouvoir
> forger un `Dragboard` hors d'un vrai geste système. La marche est décrite plutôt que montrée, et le
> geste reste entier.

### S2-08 à S2-11 · `rattacher_la_nuit_a_son_point`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioImportNominalTest.rattacher_la_nuit_a_son_point.mp4"></video>

> Le point se choisit par le modèle de sélection : le popup d'un `ComboBox` ne se déroule pas de façon
> fiable sur ce banc. Ce que le clip montre est la conséquence - la valeur qui paraît, et le marqueur
> qui vire à l'indigo sur la carte de confirmation.

### S2-12 à S2-17 · `importer_la_nuit`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioImportNominalTest.importer_la_nuit.mp4"></video>

> **Ce clip est freiné, et ne dit rien de la vitesse du produit.** Sur des fixtures générées, l'import
> dure des millisecondes - mesuré sur six wav comme sur soixante -, et les cinq cas qui portent sur ce
> qui se passe pendant l'opération n'auraient rien à montrer. Le banc ralentit donc son exécuteur
> d'environ une seconde par fichier. Ce que le clip démontre : ces cinq surfaces existent et
> s'enchaînent. Ce qu'il ne démontre pas : combien de temps un import prend.


## ScenarioPassagePivotTest

Le passage est le pivot de `S2` : l'import s'y termine, et l'analyse en repart. Ses trois gestes se
jouent chacun sur une carte SD fraîche, et chacun **rejoue l'import complet** avant d'arriver à
l'écran qu'il montre. Ce préambule occupe la première moitié de chaque clip : c'est le chemin réel
de l'utilisateur, qui n'atteint le passage qu'en ayant importé.

Le préambule est **freiné**, pour la raison dite plus haut : sur des fixtures générées l'import dure
des millisecondes, et l'écran d'avancement traverserait le clip sans qu'aucune image n'en sorte.
Aucun de ces clips ne dit donc quoi que ce soit de la vitesse du produit.

### S2-18 à S2-26 · `lire_le_passage_pivot`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPassagePivotTest.lire_le_passage_pivot.mp4"></video>

> Le passage s'ouvre par l'**action du compte rendu** de fin d'import, et non par le bouton de la zone
> d'avertissement de numéro : celui-là reste invisible après un import nominal. Ce que le clip montre
> est le chemin qu'un observateur emprunte réellement.

> Le stepper distingue trois états, et le cas lit les étapes **acquises** : un passage naît au statut
> `TRANSFORME`, donc « Importé » est franchie et « Transformé » est courante. Il vérifie aussi que les
> trois suivantes restent à venir - sans quoi un stepper qui marquerait tout passerait.

### S2-27 à S2-32 · `modifier_le_passage`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPassagePivotTest.modifier_le_passage.mp4"></video>

> `S2-30` substitue le **fournisseur de météo** : l'appel réseau est ce que le banc ne peut pas faire.
> Ce que la case observe est le remplissage - les valeurs rendues atterrissent dans les champs, et
> remplacent ce qui était saisi à la main.

> `S2-32` prend **deux relevés** du récapitulatif plutôt qu'un. Un instantané ne distingue pas un récap
> vivant d'un récap figé, et c'est ce récap qui annonce ce que « Appliquer » va faire.

### S2-33 · `renommer_le_passage_sur_le_disque`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPassagePivotTest.renommer_le_passage_sur_le_disque.mp4"></video>

> **Le dialogue de confirmation n'est pas à l'image, et c'est voulu** : `Alert.showAndWait()` fige
> TestFX, et le geste deviendrait infilmable alors que le produit est en bon état. Le confirmateur est
> donc substitué, et sa question capturée. Ce que le clip démontre : « Appliquer » ne part pas sans
> demander, et la question nomme le numéro qui va changer. Ce qu'il ne montre pas : la fenêtre qui
> porte cette question.






## ScenarioJournalAbsentTest

Sans journal, Companion ne sait rien de la fin de la nuit. Avant #4990, cette nuit recevait le badge
vert le plus rassurant : l'absence de preuve était lue comme une preuve.

### S2-44 · `l_absence_de_journal_est_signalee`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioJournalAbsentTest.l_absence_de_journal_est_signalee.mp4"></video>

> L'absence est **dite**, et l'import reste possible : un journal manquant n'empêche pas de récupérer
> les enregistrements, qui sont ce que l'observateur est venu chercher (mode dégradé, #107).

### S2-68 et S2-69 · `sans_journal_la_nuit_est_inconnue`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioJournalAbsentTest.sans_journal_la_nuit_est_inconnue.mp4"></video>

> **Ce que ce clip ne montre pas** : l'infobulle qui s'ouvre. Un popup ne se rend pas en headless. Le
> banc lit l'infobulle **installée** - comme `ScenarioPassagePivotTest` le fait déjà - et affirme donc
> ce qu'elle dit, qui est le fond de `S2-69` : pourquoi on ne sait pas, sans affirmer de cause.

> La carte est `sd-sans-journal-multi`, écrite pour ce lot. `sd-sans-journal` ne porte qu'**une** nuit,
> et la table n'est visible qu'à partir de deux : le badge n'était donc jamais à l'écran, et ces deux
> cas ne pouvaient pas être joués (#5145).

### S2-45 · `un_journal_corrompu_fait_echouer_l_inspection`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioJournalAbsentTest.un_journal_corrompu_fait_echouer_l_inspection.mp4"></video>

> Le seul cas du geste qui attend un **échec**. Un écran resté vide ne dirait pas si le refus a eu lieu
> ou si rien ne s'est passé : c'est le message qui fait la différence, et il **nomme** ce qui cloche -
> « Journal LogPR inexploitable : aucun numéro de série d'enregistreur trouvé. »


## ScenarioDiagnosticSansJournalTest

### S2-75 · `sans_journal_le_second_encart_se_tait`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioDiagnosticSansJournalTest.sans_journal_le_second_encart_se_tait.mp4"></video>

> **Filmer un silence.** L'encart se tait, et c'est le bon comportement : ni interruption à signaler,
> ni fin normale à attester. Son silence n'est pas une preuve que la nuit fut entière, le journal étant
> circulaire (R19).

> Le banc affirme **deux** choses ensemble - l'encart se tait, et le reste du diagnostic est là - faute
> de quoi l'absence pourrait venir d'un écran qui ne s'est pas ouvert. Témoin négatif : en faisant dire
> « fin de nuit normale » à l'état inconnu, le clip rougit.
## ScenarioCarteMultiNuitsTest

On pose l'enregistreur et on le laisse. Le journal, lui, est **circulaire** : il perd les nuits les
plus anciennes (R19). La carte porte donc trois nuits sous un journal qui n'en couvre qu'une, et c'est
la situation la plus fréquente du terrain, pas un cas limite.

### S2-42 et S2-70 · `la_table_des_nuits_et_ses_badges`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCarteMultiNuitsTest.la_table_des_nuits_et_ses_badges.mp4"></video>

> Ce qui se juge est la **distinction** : « complète » sur la première nuit, « complétude inconnue »
> sur les deux autres. Trois badges identiques ne diraient rien, et c'était l'état d'avant #5071.

> Trois lots ont construit ce badge sans le montrer en mouvement : #5071 pour la règle - une nuit sans
> preuve est **inconnue**, jamais complète - #5030 pour sa colonne, #5135 pour le trajet qui l'amène à
> la base. `apercu-import-multi-nuits.png` en donne une image fixe (#5101), mais elle ne dit pas d'où
> vient ce badge.

> Le banc lit les **cellules**, non les objets du modèle : un banc qui lirait `NuitVM#badge()`
> rejouerait le calcul au lieu d'éprouver ce que l'observateur voit. Il cherche « complétude inco »
> et non le libellé entier, la colonne de 130 px le tronquant (#5111).

### S2-43 · `deux_nuits_deux_configurations`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioCarteMultiNuitsTest.deux_nuits_deux_configurations.mp4"></video>

> Le capteur a été repris et reconfiguré entre les deux nuits. #3460 a corrigé le fait qu'une nuit
> repartait avec les réglages d'une **autre** ; ce cas garde la correction.
## ScenarioBandeauLectureSeuleTest

Le quatrième bandeau de l'inspection : le support est monté en **lecture seule**. Ces cas dormaient
faute qu'un banc sache monter un volume ; la couture posée en #5091 les rend filmables.

### S2-71 et S2-72 · `le_support_en_lecture_seule_s_annonce_et_l_import_aboutit`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioBandeauLectureSeuleTest.le_support_en_lecture_seule_s_annonce_et_l_import_aboutit.mp4"></video>

> Les deux cas sont joués **d'un trait**, et c'est voulu. Séparés, chacun ment à moitié : un bandeau
> seul laisse croire à un refus, ce qu'un observateur craint précisément en voyant un avertissement
> sur sa carte. Le clip montre le bandeau, puis l'import qui aboutit derrière.

> L'ordre des trois phrases porte le sens : d'abord que l'import fonctionne, ensuite le geste à faire,
> enfin que c'est la **prochaine** nuit qui est en jeu. Le banc l'affirme dans cet ordre.

### S2-73 · `une_carte_inscriptible_n_annonce_rien`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioBandeauLectureSeuleTest.une_carte_inscriptible_n_annonce_rien.mp4"></video>

> Contrôle négatif, et il n'est pas décoratif : sans lui, un bandeau qui paraîtrait **toujours**
> passerait les deux cas précédents. La sonde reste ici celle du produit, qui interroge le vrai volume.


## ScenarioBandeauxDInspectionTest

Les bandeaux que l'inspection lève sur une carte discutable. Chaque cas prend **sa** carte : c'est la
carte qui fait la pathologie.

### S2-40 · `le_bandeau_du_melange`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioBandeauxDInspectionTest.le_bandeau_du_melange.mp4"></video>

> Le bandeau **nomme** les séries mêlées, et le bouton d'import reste là : il informe, il ne bloque
> pas. Un avertissement qui retirerait le bouton serait un refus déguisé.

### S2-41 · `le_bandeau_de_l_incoherence`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioBandeauxDInspectionTest.le_bandeau_de_l_incoherence.mp4"></video>

> Plus ferme que le mélange, et toujours pas bloquant. Le bandeau dit **quoi** peut être faux - la
> série ou la date du passage - car c'est cela que l'observateur devra corriger après l'import.

### S2-46 · `le_bandeau_de_la_discordance_de_prefixe`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioBandeauxDInspectionTest.le_bandeau_de_la_discordance_de_prefixe.mp4"></video>

> **Celui-ci bloque**, contrairement aux trois autres, et c'est **mesuré** : le bouton d'import est
> désactivé. La session le range pourtant sous le geste des bandeaux « non bloquants », et le
> commentaire du contrôleur l'annonçait ainsi depuis #111. Le comportement est le bon - importer des
> fichiers préfixés pour un autre carré les enverrait au dépôt sous ce nom-là - et ce sont les écrits
> qui ont vieilli. Le commentaire est corrigé ici ; le classement du cas revient au porteur.
## ScenarioReveilParBoutonTest

Samuel est venu regarder l'écran de son enregistreur pendant la nuit. Le firmware sort alors de la
veille pour le laisser agir, et écrit `Wakeup by PINPUSH`. Au retour, le diagnostic lui reprochait un
« réveil non programmé » : son propre geste, porté à son débit.

### S2-76 · `un_reveil_par_bouton_n_est_pas_une_anomalie`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioReveilParBoutonTest.un_reveil_par_bouton_n_est_pas_une_anomalie.mp4"></video>

> Le remède **retire** une ligne, et une absence ne se filme pas. Le clip montre donc la **paire** :
> le réveil paraît au journal des évènements, qui relate, et ne paraît pas aux anomalies, qui
> accusent. Une liste d'anomalies vide, seule, prouverait aussi bien que le journal n'a pas été lu.

> La carte est `sd-reveil-bouton` (#5126), dont le journal porte l'appui au milieu de la nuit.


## ScenarioNuitInterrompueTest

Une nuit peut s'arrêter avant son terme - carte pleine, batterie vide, arrêt subi - et rien ne le
disait. Le diagnostic porte désormais un second encart sous la cohérence horaire.

### S2-74 · `une_nuit_interrompue_le_dit`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioNuitInterrompueTest.une_nuit_interrompue_le_dit.mp4"></video>

> Deux axes **distincts** sous la cohérence horaire : le premier dit si l'enregistrement couvre la
> fenêtre exigée par le protocole, celui-ci s'il s'est interrompu. Une nuit peut porter les deux, et
> les confondre reviendrait à taire l'un des deux faits.

> Ce clip n'existait pas parce que le produit ne savait pas atteindre l'état : l'import jetait la
> complétude une ligne avant la base (#5135). Il est donc, autant qu'une démonstration, la preuve que
> le trajet est réparé.
## ScenarioDiagnosticPassageTest

Le diagnostic est l'écran où l'on va voir **pourquoi** une nuit est ce qu'elle est. Le clip repart de
l'import, comme les trois précédents, et rejoint le diagnostic par sa carte sur le passage : c'est le
chemin de l'observateur, et il commence donc lui aussi par un import freiné.

### S2-34 à S2-39 · `lire_le_diagnostic_d_un_passage`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioDiagnosticPassageTest.lire_le_diagnostic_d_un_passage.mp4"></video>

> `S2-39` **ne se lit pas sur cet écran**. L'enregistreur et le nombre de mesures sont en barre de
> statut, qui appartient au chrome : le contrôleur du diagnostic y publie un résumé (#693, #3548), et
> c'est le chrome qui le rend. Le clip montre les deux à la fois, ce qu'un banc monté sur la seule vue
> du diagnostic n'aurait pas pu filmer.

> La carte nominale **n'a aucune anomalie**, et c'est son substitut qui parle - « Aucune anomalie
> détectée. » La case admet les deux formes, et le banc ne tranche pas à la place du produit : exiger
> une liste peuplée reviendrait à demander des défauts à une nuit saine.

> L'alerte « hors nuit » paraît, et ce n'est pas un montage : mesuré, l'enregistrement de la carte
> nominale déborde de sa fenêtre nocturne des deux côtés. Le cas est donc jouable sans fabriquer une
> seconde carte.


## ScenarioSelectionEcouteTest

La modale qui décide de ce que l'observateur va écouter, et dont « Régénérer » **efface sa
progression**. Le clip repart de l'import et rejoint la vérification par la carte du passage : c'est
le chemin réel, et il commence donc lui aussi par un import freiné.

### S3-12 à S3-17 · `personnaliser_la_selection_d_ecoute`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioSelectionEcouteTest.personnaliser_la_selection_d_ecoute.mp4"></video>

> **Le banc supplée le périphérique audio, qu'il n'a pas.** Mesuré : le clic sur « Lecture » laisse
> `playing` à faux, le composant retombant deux fois faute de carte son. La propriété est donc posée à
> vrai ensuite, et c'est le câblage réel du produit qui prend le relais - `playingProperty` déclenche
> le marquage de la séquence, qui fait la progression. Ce que le clip démontre : une séquence écoutée
> change ce que « Régénérer » coûte. Ce qu'il ne démontre pas : que « Lecture » émette du son.

> **La progression se lit par le produit lui-même**, et non par un compteur interne. `S3-12` établit
> que la confirmation ne paraît **que** s'il y a de quoi perdre ; les cas suivants s'en servent comme
> oracle - après « Annuler » le produit prévient encore, après « Régénérer » il ne prévient plus. Un
> avertissement systématique s'apprend à ignorer, et un relevé unique ne dirait pas s'il paraît à bon
> escient.

> **Ce clip a trouvé un défaut du produit, et le montre corrigé.** « Personnaliser… » n'appliquait
> rien : la modale recevait de l'injecteur une sélection **neuve** au lieu de celle de l'écran, écrivait
> dedans, régénérait cet orphelin. Elle se rouvrait de surcroît sur les valeurs par défaut. Aucun test
> ne le voyait, celui de la modale lui injectant un modèle partagé.

> Le dialogue de confirmation et le compte rendu de régénération ne sont pas à l'image : tous deux
> passent par `Alert.showAndWait()`, qui fige TestFX. Leurs porteurs sont substitués et leurs messages
> capturés.


## ScenarioRaccourcisVerificationTest

Le clavier de l'écran de vérification. Trois de ses six cas gardent une **absence** - une touche qui
ne doit pas agir - et chacun relève d'abord l'autre condition : la touche agit là où elle doit agir,
puis se tait là où elle doit se taire. C'est le seul moyen qu'un non-effet prouve quelque chose.

### S3-39 à S3-44 · `les_raccourcis_clavier_de_la_verification`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioRaccourcisVerificationTest.les_raccourcis_clavier_de_la_verification.mp4"></video>

> **Le clip montre des touches, donc peu de mouvement.** Ce qui change à l'image est ce qu'elles
> produisent : la ligne suivante se sélectionne, le bouton du verdict se marque, la lettre s'inscrit
> dans le commentaire, le bandeau de succès paraît. Le geste lui-même est invisible par nature.

> `S3-40` **ne démontre pas que le son sorte**, et c'est la même limite que `S3-12` : mesuré pendant
> #4734, la lecture ne démarre pas faute de périphérique audio. Ce que le cas établit est que la barre
> d'espace atteint le lecteur - ses tentatives en témoignent - et qu'elle **ne déclenche pas** le
> bouton qui a le focus, défaut que #1504 a eu à corriger.

> `S3-44` est un **contrôle croisé** et non une lecture : la légende doit nommer les libellés que les
> boutons portent aujourd'hui. #1513 a eu à corriger une légende qui avait cessé de suivre l'écran.

> **Une limite déclarée.** La moitié « `Entrée` ne fait rien sans verdict » constate un fait vrai sans
> qu'aucune mutation ait pu la faire rougir : trois couches le tiennent en série, et les retirer une à
> une - puis deux ensemble - laisse le cas vert. L'en-tête du banc le dit, pour qu'on ne lui prête pas
> une solidité qu'il n'a pas.

## ScenarioEmportExpediteurTest

Le poste qui **possède** la nuit : il la prépare, l'emporte, et reçoit en retour l'avis de qui l'a
relue. Les deux clips se lisent ensemble, dans cet ordre, parce que le second suppose le premier.

### S3-45 à S3-47 · `emporter_une_nuit_et_ses_deux_refus`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioEmportExpediteurTest.emporter_une_nuit_et_ses_deux_refus.mp4"></video>

> **Deux des trois cas sont des refus**, et c'est voulu : un geste qui écrit des gigaoctets se juge
> autant sur ce qu'il refuse d'écrire que sur ce qu'il écrit. `S3-46` est une nuit sans sélection,
> `S3-47` une séquence que le disque ne porte plus.

> **Le volume s'annonce avant que rien ne parte.** Ce qui change à l'image est le compte rendu, pas la
> barre de progression : l'écriture est brève, l'annonce est ce que l'utilisateur lit.

### S3-48 et S3-49 · `reprendre_un_avis_et_le_second_qui_se_confirme`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioEmportExpediteurTest.reprendre_un_avis_et_le_second_qui_se_confirme.mp4"></video>

> `S3-49` est le cas qui **demande** avant de remplacer. Un second avis sur les mêmes séquences écrase
> le premier, et la question nomme ce qui serait perdu plutôt que de dire « êtes-vous sûr ». Sans
> verdicts posés au préalable l'avis serait vide, et la question ne se poserait jamais : la fixture les
> pose donc en premier.

## ScenarioEmportRelecteurTest

Le poste qui **ne possède pas** la nuit. Toute la difficulté est là : chez le relecteur la nuit existe
déjà, ouvrir l'écran de vérification y pose une sélection, et le paquet reçu doit remplacer celle-là.
C'est en filmant ce rôle que le défaut corrigé par #4728 s'est vu, et aucun test unitaire ne pouvait
le voir puisque tous partaient du poste de l'expéditeur.

### S3-50 et S3-51 · `relire_une_nuit_confiee`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioEmportRelecteurTest.relire_une_nuit_confiee.mp4"></video>

> **Le remplacement se confirme, il n'est pas tacite.** La question nomme les verdicts qui seront
> perdus. `S3-51` constate ensuite que le compte rendu dit **qui** signera : l'identité est relevée à
> l'ouverture du paquet, pas au moment du jugement.

### S3-52 · `renvoyer_son_avis`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioEmportRelecteurTest.renvoyer_son_avis.mp4"></video>

> **L'avis n'emporte pas les séquences**, que l'expéditeur possède déjà : il ne porte que les verdicts
> et le pseudo qui les signe. C'est ce que le manifeste relu après coup établit.

> **Ce que ces trois cas ne peuvent pas montrer.** Le voyage du fichier d'un poste à l'autre. Le paquet
> arrive par la fixture, composé par le service : aucune des deux familles ne filme le transport, et
> c'est une limite du dispositif plutôt qu'un manque du tournage.

## ScenarioPerceptifRefusDepotTest

### S4-18 · `ouvrir_le_dossier_de_depot_demande_un_dossier`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifRefusDepotTest.ouvrir_le_dossier_de_depot_demande_un_dossier.mp4"></video>

> Aucun gestionnaire de fichiers ne s'ouvre sur le banc : ce clip montre le clic, pas la fenêtre qu'il ouvre. Ce qui se vérifie est le GESTE demandé au système - ouvrir un dossier, et non ouvrir un lien - et cela se lit dans l'assertion.

**Pourquoi ce cas a une section à lui.** Il existait depuis toujours dans `s4-deposer-suivre.md` et
**aucun test ne le citait**. Le document disait juste : « ouvre le dossier `depot/` ». Le code, lui,
passait une URI `file://` au navigateur, qui rendait un listing de répertoire. Un observateur y a
cherché comment déposer ses archives, n'a rien trouvé, et est reparti chercher les ZIP à la main
(#4982). Un cas de recette qu'aucun banc ne cite ne garde rien.
