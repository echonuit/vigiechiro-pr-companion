package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La configuration lue avant le démarrage (#1038).
class ConfigurationAmorcageTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Aucun fichier : configuration vide, l'application démarre sur ses défauts")
    void fichier_absent_donne_une_configuration_vide() {
        ConfigurationAmorcage configuration = ConfigurationAmorcage.lueDepuis(dossier);

        assertThat(configuration.espaceDeTravail()).isEmpty();
        assertThat(configuration.cheminBase()).isEmpty();
    }

    @Test
    @DisplayName("Aller-retour : ce qu'on relit est ce qu'on a écrit")
    void ecrire_puis_relire_redonne_la_meme_configuration() throws IOException {
        ConfigurationAmorcage ecrite = new ConfigurationAmorcage(
                Optional.of(Path.of("/donnees/vigiechiro")), Optional.of(Path.of("/coffre/vigiechiro.db")));

        ecrite.enregistrerDans(dossier);

        assertThat(ConfigurationAmorcage.lueDepuis(dossier)).isEqualTo(ecrite);
    }

    @Test
    @DisplayName("Un seul emplacement choisi : l'autre reste au défaut, il n'est pas écrit vide")
    void une_seule_cle_laisse_l_autre_absente() throws IOException {
        new ConfigurationAmorcage(Optional.empty(), Optional.of(Path.of("/coffre/vigiechiro.db")))
                .enregistrerDans(dossier);

        ConfigurationAmorcage relue = ConfigurationAmorcage.lueDepuis(dossier);

        assertThat(relue.espaceDeTravail())
                .as("une valeur absente est OMISE du fichier : la relire ne doit pas rendre un chemin vide")
                .isEmpty();
        assertThat(relue.cheminBase()).contains(Path.of("/coffre/vigiechiro.db"));
    }

    @Test
    @DisplayName("Valeur blanche : traitée comme absente, jamais comme un chemin vide")
    void valeur_blanche_vaut_absence() throws IOException {
        Files.writeString(dossier.resolve("amorcage.properties"), "espace-de-travail=   \nbase=\n");

        ConfigurationAmorcage configuration = ConfigurationAmorcage.lueDepuis(dossier);

        assertThat(configuration.espaceDeTravail()).isEmpty();
        assertThat(configuration.cheminBase()).isEmpty();
    }

    @Test
    @DisplayName("Fichier abîmé : configuration vide, l'application s'ouvre quand même")
    void fichier_abime_ne_bloque_pas_le_demarrage() throws IOException {
        // Une séquence d'échappement invalide fait échouer Properties.load : c'est le cas d'un fichier
        // édité à la main. Refuser de démarrer laisserait l'utilisateur sans recours, puisque le seul
        // moyen de corriger serait l'application elle-même.
        Files.writeString(dossier.resolve("amorcage.properties"), "base=C:\\\\coffre\\uZZZZ\n");

        assertThat(ConfigurationAmorcage.lueDepuis(dossier)).isEqualTo(ConfigurationAmorcage.vide());
    }

    @Test
    @DisplayName("Windows : %APPDATA%\\vigiechiro")
    void placement_windows_suit_appdata() {
        Path resolu = ConfigurationAmorcage.dossierPour(
                "Windows 11", "C:\\Users\\Camille\\AppData\\Roaming", null, "C:\\Users\\Camille");

        assertThat(resolu).isEqualTo(Path.of("C:\\Users\\Camille\\AppData\\Roaming", "vigiechiro"));
    }

    @Test
    @DisplayName("Windows sans APPDATA : repli sur AppData\\Roaming du dossier personnel")
    void placement_windows_sans_appdata() {
        Path resolu = ConfigurationAmorcage.dossierPour("Windows 10", null, null, "C:\\Users\\Camille");

        assertThat(resolu).isEqualTo(Path.of("C:\\Users\\Camille", "AppData", "Roaming", "vigiechiro"));
    }

    @Test
    @DisplayName("Ailleurs : $XDG_CONFIG_HOME/vigiechiro, celui que Flatpak renseigne")
    void placement_xdg_suit_la_variable() {
        Path resolu = ConfigurationAmorcage.dossierPour(
                "Linux", null, "/home/camille/.var/app/fr.echonuit/config", "/home/camille");

        assertThat(resolu)
                .as("sous Flatpak, ~/.config est masqué : seul XDG_CONFIG_HOME désigne un dossier accessible")
                .isEqualTo(Path.of("/home/camille/.var/app/fr.echonuit/config", "vigiechiro"));
    }

    @Test
    @DisplayName("Ailleurs, sans XDG : repli sur ~/.config/vigiechiro")
    void placement_xdg_repli_sur_config() {
        assertThat(ConfigurationAmorcage.dossierPour("Linux", null, null, "/home/camille"))
                .isEqualTo(Path.of("/home/camille/.config", "vigiechiro"));
        assertThat(ConfigurationAmorcage.dossierPour("Mac OS X", null, "  ", "/Users/camille"))
                .as("une variable posée mais vide vaut une variable absente")
                .isEqualTo(Path.of("/Users/camille/.config", "vigiechiro"));
    }

    @Test
    @DisplayName("APPDATA n'est lu que sous Windows, XDG que hors Windows")
    void les_deux_variables_ne_se_melangent_pas() {
        assertThat(ConfigurationAmorcage.dossierPour("Linux", "C:\\AppData", "/xdg", "/home/camille"))
                .as("hors Windows, APPDATA est ignoré même s'il est renseigné")
                .isEqualTo(Path.of("/xdg", "vigiechiro"));
        assertThat(ConfigurationAmorcage.dossierPour("Windows 11", "C:\\AppData", "/xdg", "C:\\Users\\Camille"))
                .as("sous Windows, XDG est ignoré même s'il est renseigné")
                .isEqualTo(Path.of("C:\\AppData", "vigiechiro"));
    }

    @Test
    @DisplayName("La propriété système détourne le dossier : c'est ce qui isole les tests")
    void propriete_systeme_detourne_le_dossier() {
        String avant = System.getProperty(ConfigurationAmorcage.PROP_DOSSIER);
        try {
            System.setProperty(ConfigurationAmorcage.PROP_DOSSIER, dossier.toString());

            assertThat(ConfigurationAmorcage.dossier()).isEqualTo(dossier);
        } finally {
            restaurer(ConfigurationAmorcage.PROP_DOSSIER, avant);
        }
    }

    private static void restaurer(String cle, String valeur) {
        if (valeur == null) {
            System.clearProperty(cle);
        } else {
            System.setProperty(cle, valeur);
        }
    }
}
