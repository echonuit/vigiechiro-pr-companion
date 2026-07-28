package fr.univ_amu.iut.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ResultatLancement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.LancementCalculGroupe;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [LancementCalculGroupe] (#2357, lot 3, PR 5/5).
///
/// Deux choses sont éprouvées, et ce sont les deux qui pourraient faire des dégâts :
///
/// - l'action **ne force jamais** - relancer un calcul détruit les observations d'une nuit déposée en
///   ZIP, et un lot qui pourrait forcer serait un moyen d'en perdre vingt d'un clic ;
/// - un résultat qui n'est **pas** un succès **lève**. Le service rend une valeur au lieu de lever ;
///   sans cette traduction, le moteur compterait « fait » un calcul qui n'a pas eu lieu, et le compte
///   rendu mentirait.
class LancementCalculGroupeTest {

    private static final CiblePassage CIBLE = new CiblePassage(42L, "640380 / A1 / 2026 n°1");

    private DepotVigieChiro depot;

    @BeforeEach
    void preparer() {
        depot = mock(DepotVigieChiro.class);
    }

    private LancementCalculGroupe action() {
        return new LancementCalculGroupe(Optional.of(depot));
    }

    @Test
    @DisplayName("le libellé est celui que l'observateur lit dans le suivi et le compte rendu")
    void libelle_lisible() {
        assertThat(action().libelle()).isEqualTo("Déclencher le calcul");
    }

    @Test
    @DisplayName("une nuit déposée est éligible")
    void deposee_est_eligible() {
        when(depot.participationLiee(42L)).thenReturn(true);

        assertThat(action().motifNonEligible(CIBLE)).isEmpty();
    }

    @Test
    @DisplayName("hors connexion, tout est écarté")
    void hors_connexion_est_ecarte() {
        LancementCalculGroupe horsLigne = new LancementCalculGroupe(Optional.empty());

        assertThat(horsLigne.motifNonEligible(CIBLE)).contains("hors connexion à Vigie-Chiro");
    }

    @Test
    @DisplayName("une nuit non déposée est écartée : il n'y a rien à calculer")
    void non_deposee_est_ecartee() {
        when(depot.participationLiee(42L)).thenReturn(false);

        assertThat(action().motifNonEligible(CIBLE)).contains("pas encore déposé sur Vigie-Chiro");
    }

    @Test
    @DisplayName("le lancement ne FORCE jamais : c'est ce qui protège les observations")
    void ne_force_jamais() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.accepte());

        action().executer(CIBLE, new JetonAnnulation());

        verify(depot).lancerTraitement(42L, false);
        verify(depot, never()).lancerTraitement(anyLong(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    @DisplayName("une relance bloquée devient un ÉCHEC : sans quoi le compte rendu mentirait")
    void relance_bloquee_leve() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.relanceBloquee(Traitement.absent()));

        assertThatThrownBy(() -> action().executer(CIBLE, new JetonAnnulation()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("relancer effacerait les observations");
    }

    @Test
    @DisplayName("plateforme injoignable : échec explicite, et rien n'a été lancé")
    void injoignable_leve() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.injoignable());

        assertThatThrownBy(() -> action().executer(CIBLE, new JetonAnnulation()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("rien n'a été lancé");
    }

    @Test
    @DisplayName("un refus serveur porte son détail dans le compte rendu")
    void refus_porte_son_detail() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.refuse(409, "already running"));

        assertThatThrownBy(() -> action().executer(CIBLE, new JetonAnnulation()))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("409");
    }

    @Test
    @DisplayName("un calcul déjà en route compte comme un succès : il est bien parti")
    void deja_lance_est_un_succes() {
        when(depot.lancerTraitement(42L, false)).thenReturn(ResultatLancement.dejaLance(Traitement.absent()));

        action().executer(CIBLE, new JetonAnnulation());
    }
}
