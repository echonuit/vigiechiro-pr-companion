package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La chaîne complète `point → commune → territoire → fuseau` (#3442).
///
/// Les maillons sont testés séparément (`FuseauDuSiteTest` pour la table, le DAO pour la table
/// latérale) : ces cas vérifient qu'ils sont **branchés dans le bon ordre**, et surtout ce qui arrive
/// quand un maillon manque.
class FuseauDuPointTest {

    /// Un annuaire en dur, plutôt qu'un mock : le port est une fonction d'un argument, et le simuler
    /// ainsi dit mieux ce qu'on éprouve.
    ///
    /// Le `idPoint == null` n'est pas une précaution décorative : la première version écrivait
    /// `Optional.ofNullable(annuaire.get(idPoint))` et **levait** sur un identifiant nul, `Map.of`
    /// refusant les clés nulles. C'était le **double** qui violait le contrat du port - lequel promet
    /// un `Optional` vide - pas le code éprouvé. Un double qui ment sur son contrat fait échouer, ou
    /// pire réussir, pour une raison qui n'existe pas en production.
    private static FuseauDuPoint avec(Map<Long, Commune> annuaire) {
        return new FuseauDuPoint(
                idPoint -> idPoint == null ? Optional.empty() : Optional.ofNullable(annuaire.get(idPoint)));
    }

    @Test
    @DisplayName("un point de La Réunion porte l'heure de La Réunion")
    void un_point_ultramarin_porte_son_heure() {
        FuseauDuPoint fuseaux = avec(Map.of(7L, new Commune("Saint-Denis", "97415")));

        assertThat(fuseaux.pour(7L)).isEqualTo(ZoneId.of("Indian/Reunion"));
    }

    @Test
    @DisplayName("un point de métropole reste à l'heure de Paris")
    void un_point_metropolitain_reste_a_paris() {
        FuseauDuPoint fuseaux = avec(Map.of(7L, new Commune("Ahetze", "64001")));

        assertThat(fuseaux.pour(7L)).isEqualTo(FuseauDuSite.ZONE);
    }

    @Test
    @DisplayName("commune non résolue : on rend le comportement d'avant, pas une erreur")
    void commune_non_resolue_retombe_sur_le_repli() {
        // Cas RÉEL, pas théorique : point sans GPS, création hors ligne, rattrapage des communes non
        // passé (ADR 2791). Le produit ne doit pas cesser de déposer parce qu'il ignore un territoire.
        FuseauDuPoint fuseaux = avec(Map.of());

        assertThat(fuseaux.pour(7L)).isEqualTo(FuseauDuSite.ZONE);
    }

    @Test
    @DisplayName("point inconnu ou nul : même repli, jamais d'exception")
    void point_inconnu_retombe_sur_le_repli() {
        FuseauDuPoint fuseaux = avec(Map.of(7L, new Commune("Saint-Denis", "97415")));

        assertThat(fuseaux.pour(null)).isEqualTo(FuseauDuSite.ZONE);
        assertThat(fuseaux.pour(999L)).isEqualTo(FuseauDuSite.ZONE);
    }

    @Test
    @DisplayName("un port absent est refusé à la construction, pas découvert au premier dépôt")
    void un_port_absent_est_refuse_tot() {
        assertThatThrownBy(() -> new FuseauDuPoint(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("deux points de territoires différents ne partagent pas leur fuseau")
    void deux_territoires_ne_se_melangent_pas() {
        // Le fuseau se lit PAR POINT : une même base peut porter des nuits de Guyane et de métropole,
        // et un cache partagé les confondrait sans que rien ne le signale.
        FuseauDuPoint fuseaux = avec(Map.of(
                1L, new Commune("Cayenne", "97302"),
                2L, new Commune("Ahetze", "64001")));

        assertThat(fuseaux.pour(1L)).isEqualTo(ZoneId.of("America/Cayenne"));
        assertThat(fuseaux.pour(2L)).isEqualTo(FuseauDuSite.ZONE);
    }
}
