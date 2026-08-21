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

## ConnexionModaleViewTest

### S1-04 · `connecter_sans_token`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ConnexionModaleViewTest.connecter_sans_token.mp4"></video>

### S1-04 · `copier_marque_page`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ConnexionModaleViewTest.copier_marque_page.mp4"></video>

### S1-04 · `ouvrir_site`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ConnexionModaleViewTest.ouvrir_site.mp4"></video>

## ConnexionModaleConnecteeViewTest

### S1-11 · `deconnexion_confirme_avant_effacement`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ConnexionModaleConnecteeViewTest.deconnexion_confirme_avant_effacement.mp4"></video>

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

## ModaleSiteViewTest

### S1-13 · `bouton_ferme_tant_que_le_carre_est_incomplet`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteViewTest.bouton_ferme_tant_que_le_carre_est_incomplet.mp4"></video>

### S1-23 · `edition_pre_remplit_et_enregistre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteViewTest.edition_pre_remplit_et_enregistre.mp4"></video>

### S1-25 · `annuler_ne_cree_rien`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteViewTest.annuler_ne_cree_rien.mp4"></video>

## ValidationFormulaireTest

### S1-13 · `gater_bouton_suit_la_validite`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ValidationFormulaireTest.gater_bouton_suit_la_validite.mp4"></video>

### S1-13 · `marquer_invalide_bascule_la_classe`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ValidationFormulaireTest.marquer_invalide_bascule_la_classe.mp4"></video>

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

### S1-16 · `bouton_synchro_visible`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.bouton_synchro_visible.mp4"></video>

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

## SiteDetailRenommageViewTest

### S1-22 · `renommer_met_a_jour_l_entete`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/SiteDetailRenommageViewTest.renommer_met_a_jour_l_entete.mp4"></video>

## ModalePointViewTest

### S1-24 · `ajouter_un_point_valide`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModalePointViewTest.ajouter_un_point_valide.mp4"></video>

## ModaleSiteVerifierCarreViewTest

### S1-30 · `carre_libre_le_verdict_s_affiche`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteVerifierCarreViewTest.carre_libre_le_verdict_s_affiche.mp4"></video>

### S1-31 · `carre_deja_declare_avertit_dans_la_modale`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteVerifierCarreViewTest.carre_deja_declare_avertit_dans_la_modale.mp4"></video>

### S1-32 · `corriger_le_carre_efface_le_verdict`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteVerifierCarreViewTest.corriger_le_carre_efface_le_verdict.mp4"></video>

### S1-33 · `hors_connexion_l_encart_ne_nie_pas_le_carre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteVerifierCarreViewTest.hors_connexion_l_encart_ne_nie_pas_le_carre.mp4"></video>

### S1-34 · `recuperer_ferme_la_modale_et_passe_le_carre`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteVerifierCarreViewTest.recuperer_ferme_la_modale_et_passe_le_carre.mp4"></video>

### S1-36 · `creer_se_ferme_avec_son_motif`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ModaleSiteVerifierCarreViewTest.creer_se_ferme_avec_son_motif.mp4"></video>

## NavigationSitesRapatriementTest

### S1-34 · `le_rapatriement_rafraichit_mes_sites_et_y_rend_compte`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/NavigationSitesRapatriementTest.le_rapatriement_rafraichit_mes_sites_et_y_rend_compte.mp4"></video>

## RapatriementCarreTest

### S1-35 · `un_carre_rapatrie_est_rattache`

<video controls muted playsinline preload="none" width="100%"
  src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/RapatriementCarreTest.un_carre_rapatrie_est_rattache.mp4"></video>
