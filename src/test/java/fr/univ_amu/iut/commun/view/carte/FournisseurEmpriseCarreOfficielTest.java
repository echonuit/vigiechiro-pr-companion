package fr.univ_amu.iut.commun.view.carte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests purs du carroyage **officiel** (#325) : le centroïde d'un carré connu du référentiel embarqué
/// donne une emprise 2 km centrée au bon endroit ; un carré inconnu renvoie vide ; et la **chaîne**
/// bascule sur le repli pour les carrés hors référentiel.
class FournisseurEmpriseCarreOfficielTest {

    private final FournisseurEmpriseCarreOfficiel officiel = new FournisseurEmpriseCarreOfficiel();

    @Test
    @DisplayName("le carré 640380 (dépt 64) est calé sur le centroïde carrenat réel, emprise ~2 km")
    void carre_connu_cale_sur_le_centroide_officiel() {
        EmpriseCarre emprise = officiel.emprise("640380", List.of()).orElseThrow();

        // Centroïde carrenat réel de 640380 (Pyrénées-Atlantiques), converti L2é → WGS84.
        assertThat(emprise.latCentre()).isCloseTo(43.403072, within(1e-4));
        assertThat(emprise.lonCentre()).isCloseTo(-1.570834, within(1e-4));
        assertThat(emprise.contient(43.403072, -1.570834)).isTrue();
        // ~2 km de côté : ~0,018° en latitude (2 / 111).
        assertThat(emprise.latMax() - emprise.latMin()).isCloseTo(2.0 / 111.0, within(1e-4));
    }

    @Test
    @DisplayName("l'emprise officielle ne dépend pas des points (centroïde du référentiel, pas barycentre)")
    void emprise_independante_des_points() {
        // Même sans aucun point géolocalisé, le carré connu est tracé (contrairement au repli).
        assertThat(officiel.emprise("640381", List.of())).isPresent();
    }

    @Test
    @DisplayName("un carré absent du référentiel renvoie vide")
    void carre_inconnu_renvoie_vide() {
        assertThat(officiel.emprise("999999", List.of())).isEmpty();
    }

    @Test
    @DisplayName("le référentiel couvre toute la France métropolitaine (≈ 137 000 mailles)")
    void couverture_nationale() {
        assertThat(officiel.taille())
                .as("toute la grille carrenat métropole est embarquée")
                .isGreaterThan(100_000);
        // Un carré quelconque hors jeu d'exemples (ici Paris, 750002) est résolu près de la capitale.
        EmpriseCarre paris = officiel.emprise("750002", List.of()).orElseThrow();
        assertThat(paris.latCentre()).isCloseTo(48.85, within(0.1));
        assertThat(paris.lonCentre()).isCloseTo(2.30, within(0.1));
    }

    @Test
    @DisplayName("la chaîne : officiel pour un carré connu, repli (points) pour un carré inconnu")
    void chaine_officiel_puis_repli() {
        FournisseurEmpriseCarre chaine = new FournisseurEmpriseCarreEnChaine(officiel, new EmpriseAutourDesPoints());

        // Connu → emprise officielle (centrée sur le centroïde carrenat), indépendante des points.
        EmpriseCarre officielle = chaine.emprise("640380", List.of()).orElseThrow();
        assertThat(officielle.lonCentre()).isCloseTo(-1.570834, within(1e-4));

        // Inconnu → repli autour des points fournis.
        PointGeo point = new PointGeo("X", 48.85, 2.35, javafx.scene.paint.Color.RED);
        EmpriseCarre repli = chaine.emprise("999999", List.of(point)).orElseThrow();
        assertThat(repli.contient(48.85, 2.35)).isTrue();
    }
}
