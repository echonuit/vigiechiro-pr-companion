# Comparer les deux bancs, cas par cas

Cette page existe pour **une décision** : le banc filmé en Java pur peut-il remplacer le banc bash ?
Elle ne l'affirme pas, elle donne de quoi trancher - les mêmes cas, filmés par les deux, côte à côte.

## Ce qui est déjà mesuré

| | banc bash | banc Java |
|---|---|---|
| Tournage complet des cas cités | **9 min 15 s**, 54 clips | **5 min 39 s**, 49 clips (43 cas sur 43) |
| Plateformes | `ubuntu-latest` seulement | `ubuntu`, `windows`, `macos`, mesurées |
| À installer | Xvfb, openbox, xdotool, x11-utils, ffmpeg | ffmpeg |
| Après le tournage | remuxer `mkv` en `mp4` | rien |
| Filmer une seule session | impossible | un `-Dtest=`, cf. `tournage-recette.yml` |
| Le pointeur de la souris | dessiné par `x11grab` | dessiné par le banc, avec le halo du clic et le badge des raccourcis |
| Ses gardes | **50 cas** d'auto-test, dont **14 rouges attendus** | 35 cas unitaires |

⚠️ Les deux durées ci-dessus sont sur la **même plateforme** et le **même périmètre**. Elles ne se
comparent pas clip par clip : les cas perceptifs, qui portent des temps d'arrêt délibérés, coûtent le
même prix aux deux bancs.

## Ce qui reste à trancher, et que cette page ne dit pas

- **Trois ADR nomment `lance-test-filme.sh` comme leur vérification** : 3774, 3788 et 3794. Retirer le
  banc bash fera rougir `DocumentationAJourTest`, qui vérifie que la référence existe. Il faut donc
  décider ce que deviennent ces trois vérifications **avant** de retirer quoi que ce soit.
- L'auto-test du banc bash **se vérifie lui-même en permanence** : 14 de ses 50 cas nomment un rouge
  attendu. Les gardes du banc Java ont été éprouvés par mutation, mais **à la main**, une fois.

## Comment regarder

À gauche le banc **bash**, à droite le banc **Java**. Même cas, même test, deux tournages.

Ce qu'il vaut la peine de comparer, dans cet ordre :

1. **Voit-on le geste ?** C'est la différence la plus visible : le banc Java dessine le pointeur, le
   halo de l'appui et le badge des raccourcis.
2. **La mise en page est-elle la même ?** Un cadre différent change ce qu'un relecteur juge.
3. **Le clip montre-t-il son objet ?** Un clip qui s'arrête avant ce que le cas fait juger ne sert à
   rien, quel que soit le banc qui l'a produit.

!!! warning "Deux tournages, deux dates"

    Le banc bash alimente `clips-recette` par le flux **recette filmée** ; le banc Java a versé sur
    `clips-java-planche` le 22 août 2026, à la main, depuis l'artefact du tournage
    [32599101955](https://github.com/echonuit/vigiechiro-pr-companion/actions/runs/32599101955).
    Un lecteur vide d'un côté dit que ce banc-là n'a pas retourné ce cas depuis qu'il existe.


### S1-04 · `les_trois_etapes_de_la_modale`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.les_trois_etapes_de_la_modale.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioPerceptifIssuesConnexionTest.les_trois_etapes_de_la_modale.mp4"></video></td>
</tr></table>

### S1-11 · `la_deconnexion_demande_confirmation`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioPerceptifIssuesConnexionTest.la_deconnexion_demande_confirmation.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioPerceptifIssuesConnexionTest.la_deconnexion_demande_confirmation.mp4"></video></td>
</tr></table>

### S1-01 · `chaque_carte_ouvre_ce_qu_elle_annonce`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioAccueilTest.chaque_carte_ouvre_ce_qu_elle_annonce.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioAccueilTest.chaque_carte_ouvre_ce_qu_elle_annonce.mp4"></video></td>
</tr></table>

### S1-28 · `le_menu_ne_porte_plus_la_fiche_espece`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioAccueilTest.le_menu_ne_porte_plus_la_fiche_espece.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioAccueilTest.le_menu_ne_porte_plus_la_fiche_espece.mp4"></video></td>
</tr></table>

### S1-01 · `accueil_regroupe_en_deux_prismes`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.accueil_regroupe_en_deux_prismes.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.accueil_regroupe_en_deux_prismes.mp4"></video></td>
</tr></table>

### S1-02 · `bandeau_masque_si_base_vide`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.bandeau_masque_si_base_vide.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.bandeau_masque_si_base_vide.mp4"></video></td>
</tr></table>

### S1-03 · `ctrl_f_active_la_recherche`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.ctrl_f_active_la_recherche.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.ctrl_f_active_la_recherche.mp4"></video></td>
</tr></table>

### S1-03 · `fil_ariane_reflete_le_parcours`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.fil_ariane_reflete_le_parcours.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.fil_ariane_reflete_le_parcours.mp4"></video></td>
</tr></table>

### S1-09 · `bandeau_suit_une_mutation_sans_navigation`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.bandeau_suit_une_mutation_sans_navigation.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.bandeau_suit_une_mutation_sans_navigation.mp4"></video></td>
</tr></table>

### S1-10 · `bandeau_suit_une_restauration_sans_navigation`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.bandeau_suit_une_restauration_sans_navigation.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.bandeau_suit_une_restauration_sans_navigation.mp4"></video></td>
</tr></table>

### S1-29 · `menu_reglages_ouvre_l_ecran`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MainViewTest.menu_reglages_ouvre_l_ecran.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MainViewTest.menu_reglages_ouvre_l_ecran.mp4"></video></td>
</tr></table>

### S1-12 · `l_etat_vide_explique_le_prerequis`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesEtatVideViewTest.l_etat_vide_explique_le_prerequis.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesEtatVideViewTest.l_etat_vide_explique_le_prerequis.mp4"></video></td>
</tr></table>

### S1-12 · `l_etat_vide_parait_sur_une_base_vierge`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesEtatVideViewTest.l_etat_vide_parait_sur_une_base_vierge.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesEtatVideViewTest.l_etat_vide_parait_sur_une_base_vierge.mp4"></video></td>
</tr></table>

### S1-12 · `l_etat_vide_porte_sa_porte_de_sortie`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesEtatVideViewTest.l_etat_vide_porte_sa_porte_de_sortie.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesEtatVideViewTest.l_etat_vide_porte_sa_porte_de_sortie.mp4"></video></td>
</tr></table>

### S1-13 · `creer_s_ouvre_au_sixieme_chiffre_et_ajoute_le_carre`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.creer_s_ouvre_au_sixieme_chiffre_et_ajoute_le_carre.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.creer_s_ouvre_au_sixieme_chiffre_et_ajoute_le_carre.mp4"></video></td>
</tr></table>

### S1-23 · `renommer_le_carre_met_a_jour_l_entete`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.renommer_le_carre_met_a_jour_l_entete.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.renommer_le_carre_met_a_jour_l_entete.mp4"></video></td>
</tr></table>

### S1-25 · `annuler_ne_change_rien_a_la_liste`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.annuler_ne_change_rien_a_la_liste.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.annuler_ne_change_rien_a_la_liste.mp4"></video></td>
</tr></table>

### S1-14 · `affiche_les_cartes`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.affiche_les_cartes.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.affiche_les_cartes.mp4"></video></td>
</tr></table>

### S1-15 · `entree_ouvre_le_detail`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.entree_ouvre_le_detail.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.entree_ouvre_le_detail.mp4"></video></td>
</tr></table>

### S1-15 · `espace_ouvre_le_detail`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.espace_ouvre_le_detail.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.espace_ouvre_le_detail.mp4"></video></td>
</tr></table>

### S1-15 · `une_carte_est_atteignable_au_clavier`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.une_carte_est_atteignable_au_clavier.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.une_carte_est_atteignable_au_clavier.mp4"></video></td>
</tr></table>

### S1-16 · `hors_connexion_la_recuperation_est_fermee`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.hors_connexion_la_recuperation_est_fermee.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.hors_connexion_la_recuperation_est_fermee.mp4"></video></td>
</tr></table>

### S1-16 · `une_fois_connecte_la_recuperation_s_ouvre_et_rend_compte`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.une_fois_connecte_la_recuperation_s_ouvre_et_rend_compte.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.une_fois_connecte_la_recuperation_s_ouvre_et_rend_compte.mp4"></video></td>
</tr></table>

### S1-17 · `overlay_occupation_masque_apres_chargement`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/MesSitesViewTest.overlay_occupation_masque_apres_chargement.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/MesSitesViewTest.overlay_occupation_masque_apres_chargement.mp4"></video></td>
</tr></table>

### S1-18 · `le_bandeau_dit_l_identite_du_carre`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.le_bandeau_dit_l_identite_du_carre.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.le_bandeau_dit_l_identite_du_carre.mp4"></video></td>
</tr></table>

### S1-19 · `les_boutons_disent_ce_qui_les_empeche`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.les_boutons_disent_ce_qui_les_empeche.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.les_boutons_disent_ce_qui_les_empeche.mp4"></video></td>
</tr></table>

### S1-20 · `les_cartes_de_points_portent_gps_et_distance`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.les_cartes_de_points_portent_gps_et_distance.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.les_cartes_de_points_portent_gps_et_distance.mp4"></video></td>
</tr></table>

### S1-21 · `le_tableau_des_passages_porte_ses_sept_colonnes`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.le_tableau_des_passages_porte_ses_sept_colonnes.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.le_tableau_des_passages_porte_ses_sept_colonnes.mp4"></video></td>
</tr></table>

### S1-35 · `le_carre_rattache_porte_son_badge`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.le_carre_rattache_porte_son_badge.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.le_carre_rattache_porte_son_badge.mp4"></video></td>
</tr></table>

### S1-22 · `renommer_le_carre_met_a_jour_l_entete`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.renommer_le_carre_met_a_jour_l_entete.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.renommer_le_carre_met_a_jour_l_entete.mp4"></video></td>
</tr></table>

### S1-24 · `ajouter_un_point_le_fait_paraitre_sur_la_fiche`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioFicheSiteTest.ajouter_un_point_le_fait_paraitre_sur_la_fiche.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioFicheSiteTest.ajouter_un_point_le_fait_paraitre_sur_la_fiche.mp4"></video></td>
</tr></table>

### S1-30 · `le_carre_libre_s_annonce_libre`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.le_carre_libre_s_annonce_libre.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.le_carre_libre_s_annonce_libre.mp4"></video></td>
</tr></table>

### S1-31 · `le_carre_deja_declare_nomme_le_site`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.le_carre_deja_declare_nomme_le_site.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.le_carre_deja_declare_nomme_le_site.mp4"></video></td>
</tr></table>

### S1-32 · `corriger_un_chiffre_efface_le_verdict`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.corriger_un_chiffre_efface_le_verdict.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.corriger_un_chiffre_efface_le_verdict.mp4"></video></td>
</tr></table>

### S1-33 · `hors_connexion_verifier_est_ferme_mais_declarer_reste_possible`

<video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.hors_connexion_verifier_est_ferme_mais_declarer_reste_possible.mp4"></video>

> ⚠️ Pas de clip côté Java : ce test est **plus récent que le tournage** de comparaison.

### S1-33 · `plateforme_injoignable_l_encart_ne_nie_pas_le_carre`

<video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.plateforme_injoignable_l_encart_ne_nie_pas_le_carre.mp4"></video>

> ⚠️ Pas de clip côté Java : ce test est **plus récent que le tournage** de comparaison.

### S1-34 · `recuperer_ferme_la_modale_et_rend_compte`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.recuperer_ferme_la_modale_et_rend_compte.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.recuperer_ferme_la_modale_et_rend_compte.mp4"></video></td>
</tr></table>

### S1-36 · `creer_reste_ferme_puis_se_rouvre`

<table style="width:100%; table-layout:fixed"><tr>
<td style="width:50%; padding:0 6px 0 0"><strong>bash</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-recette/ScenarioModaleCarreTest.creer_reste_ferme_puis_se_rouvre.mp4"></video></td>
<td style="width:50%; padding:0 0 0 6px"><strong>Java</strong><br><video controls muted playsinline preload="none" width="100%" src="https://github.com/echonuit/vigiechiro-pr-companion/releases/download/clips-java-planche/ScenarioModaleCarreTest.creer_reste_ferme_puis_se_rouvre.mp4"></video></td>
</tr></table>

## Ce que la comparaison ne couvre pas

2 entrée(s) sur 38 n'ont pas leur clip Java, parce que leur test est né après le tournage de comparaison :

- `S1-33` · `hors_connexion_verifier_est_ferme_mais_declarer_reste_possible`
- `S1-33` · `plateforme_injoignable_l_encart_ne_nie_pas_le_carre`

Un nouveau tournage les couvrirait ; elles sont laissées telles quelles pour que la page dise ce qu'elle montre et ce qu'elle ne montre pas.
