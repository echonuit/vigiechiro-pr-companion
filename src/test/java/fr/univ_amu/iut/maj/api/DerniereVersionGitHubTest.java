package fr.univ_amu.iut.maj.api;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// L'adaptateur réseau : le **seul** endroit du chantier qui sort de la machine.
///
/// Son contrat tient en une phrase - **toute défaillance rend `Optional.empty()`** - et c'est
/// précisément ce qu'on ne peut pas vérifier en appelant GitHub, puisqu'il faudrait provoquer une
/// panne à volonté.
///
/// Le dépôt ne peut pas non plus monter un serveur local : `jdk.httpserver` n'est pas lisible depuis
/// ce module (contrainte JPMS connue). On exerce donc l'**analyse de la réponse** directement, là où
/// vivent tous les cas inattendus, plus le **hors-ligne** qui ne demande aucun serveur. Reste non
/// couvert le seul aiguillage sur le code HTTP, qui est un `if` sur `statusCode != 200`.
class DerniereVersionGitHubTest {

    private final DerniereVersionGitHub adaptateur = new DerniereVersionGitHub();

    @Test
    @DisplayName("une réponse conforme donne le numéro et l'adresse de la page")
    void reponseConforme() {
        Optional<VersionDisponible> lue = adaptateur.lire("""
                {"tag_name": "v2.26.0",
                 "html_url": "https://github.com/exemple/releases/tag/v2.26.0"}""");

        assertThat(lue).isPresent();
        assertThat(lue.orElseThrow().numero()).hasToString("2.26.0");
        assertThat(lue.orElseThrow().adresse())
                .as("l'annonce ne sert à rien si elle ne dit pas où aller")
                .isEqualTo("https://github.com/exemple/releases/tag/v2.26.0");
    }

    @Test
    @DisplayName("une réponse sans les champs attendus se tait")
    void reponseIncompleteSeTait() {
        assertThat(adaptateur.lire("{\"message\": \"Not Found\"}")).isEmpty();
        assertThat(adaptateur.lire("{\"tag_name\": \"v2.26.0\"}"))
                .as("un numéro sans adresse ne permet pas d'agir")
                .isEmpty();
    }

    @Test
    @DisplayName("un tag qui n'est pas un numéro se tait")
    void tagNonNumeriqueSeTait() {
        assertThat(adaptateur.lire("{\"tag_name\": \"nightly\", \"html_url\": \"https://exemple\"}"))
                .isEmpty();
        assertThat(adaptateur.lire("{\"tag_name\": \"v2.26.0-rc.1\", \"html_url\": \"https://exemple\"}"))
                .as("une pré-version ne se propose pas à un naturaliste")
                .isEmpty();
    }

    @Test
    @DisplayName("hors ligne : rien, et surtout aucune exception")
    void horsLigneSeTait() {
        // Port fermé : l'équivalent testable d'une machine sans réseau. C'est LE cas qui ne doit
        // jamais remonter à l'utilisateur, puisqu'il survient au démarrage.
        DerniereVersionGitHub horsLigne = new DerniereVersionGitHub(URI.create("http://127.0.0.1:1/"));

        assertThat(horsLigne.consulter()).isEmpty();
    }
}
